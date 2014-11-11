package com.moreharts.babymonitor.service;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Binder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.ui.ClientStatus;
import com.moreharts.babymonitor.preferences.BabyTrustStore;
import com.moreharts.babymonitor.preferences.Settings;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.util.ParcelableByteArray;

public class MonitorService extends JumbleService {
    public static final String TAG = "MonitorService";

    public static final String MUMBLE_USER_START = "bm";
    public static final String PREF_MUMBLE_CHANNEL = "BabyMonitor";
    public static final String MUMBLE_TOKEN = "com.moreharts.babymonitor";

    public static final float DEFAULT_THRESHOLD = 0.7f;

    public static final String CMD_MUTE = "mute";
    public static final String CMD_UNMUTE = "unmute";
    public static final String CMD_RESET = "reset";
    public static final String CMD_THRESHOLD = "threshold";
    public static final String CMD_TX_PING = "txping";

    public static final String RESP_PING = "txpong";
    public static final String RESP_THRESHOLD = "threshold_is";

    // Service info
    private final IBinder mBinder = new LocalBinder();
    private Settings mSettings = null;
    private boolean mIsTransmitter = false;
    private float mThreshold = DEFAULT_THRESHOLD;
    private float mRxThreshold = DEFAULT_THRESHOLD; /** Threshold last heard from someone else */

    // People listening to us (they can also bind to the Jumble binder)
    Handler mMainHandler = null;
    private MonitorServiceListener mServiceListener = null;

    // Jumble
    private Server mConnectedInfo = null;

    private JumbleObserver mJumbleObserver = new JumbleObserver() {
        @Override
        public void onConnected() throws RemoteException {
            Log.i(TAG, "Connected to server");

            rebuildNotification();

            // Give Jumble a second to settle, then initialize fully
            new Timer().schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            try {
                                setDefaultMuteDeafenStatus();
                                setDefaultTxMode();
                                setVADThreshold(mThreshold);
                                joinBabyMonitorChannel();
                                sendChannelMessage(CMD_THRESHOLD);
                            }
                            catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    , 1000);
        }

        @Override
        public void onDisconnected() throws RemoteException {
            Log.i(TAG, "Disconnected");
            rebuildNotification();
        }

        @Override
        public void onConnectionError(String message, boolean reconnecting) throws RemoteException {
            Log.i(TAG, "Connection error: " + message);
            rebuildNotification();

            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast toast = Toast.makeText(MonitorService.this, "BabyMonitor connection error", Toast.LENGTH_LONG);
                    toast.show();
                }
            });
        }

        @Override
        public void onTLSHandshakeFailed(ParcelableByteArray cert) throws RemoteException {
            byte[] certBytes = cert.getBytes();
            final Server lastServer = getBinder().getConnectedServer();

            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                final X509Certificate x509 = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));

                // Try to add to trust store
                try {
                    String alias = lastServer.getHost(); // FIXME unreliable
                    KeyStore trustStore = BabyTrustStore.getTrustStore(getApplicationContext());
                    trustStore.setCertificateEntry(alias, x509);
                    BabyTrustStore.saveTrustStore(getApplicationContext(), trustStore);
                    Toast.makeText(getApplicationContext(), R.string.trust_added, Toast.LENGTH_LONG).show();

                    // Reconnect
                    connect(lastServer.getHost(), lastServer.getPort(), lastServer.getUsername()); // FIXME unreliable
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), R.string.trust_add_failed, Toast.LENGTH_LONG).show();
                }
            } catch (CertificateException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onUserConnected(User user) throws RemoteException {
            Log.i(TAG, "User connected: " + user.getName());
        }

        @Override
        public void onUserStateUpdated(User user) throws RemoteException {
            Log.i(TAG, "user state updated: " + user.getName());
        }

        @Override
        public void onUserTalkStateUpdated(User user) throws RemoteException {
            Log.i(TAG, "user talk: " + user.getName());
        }

        @Override
        public void onUserJoinedChannel(User user, Channel newChannel, Channel oldChannel) throws RemoteException {
            Log.i(TAG, "user joined channel: " + user.getName());

            // Make sure we send the current status every time someone joins
            if(mIsTransmitter) {
                sendThresholdResponse();
            }
            else {
                sendChannelMessage(CMD_THRESHOLD);
            }
        }

        @Override
        public void onUserRemoved(User user, String reason) throws RemoteException {
            Log.i(TAG, "user removed: " + user.getName());
        }

        @Override
        public void onPermissionDenied(String reason) throws RemoteException {
            Log.i(TAG, "permission denied: " + reason);
        }

        @Override
        public void onMessageLogged(Message message) throws RemoteException {
            Log.i(TAG, "Message: " + message.getMessage());

            // Parse message
            String[] parts = message.getMessage().trim().split(" ");
            String cmd = parts[0];
            Log.d(TAG, "Cmd: " + cmd + " part cnt: " + parts.length);

            // Handle command messages
            if(isTransmitterMode()) {
                if(cmd.equals(CMD_THRESHOLD)) {
                    // Threshold changes
                    if(parts.length == 2) {
                        try {
                            float newThresh = Float.parseFloat(parts[1]);
                            if (newThresh < 0)
                                newThresh = 0f;
                            if (newThresh > 1)
                                newThresh = 1f;

                            Log.i(TAG, "TX threshold set to " + newThresh);
                            setVADThreshold(newThresh);
                        }
                        catch(IllegalFormatException e) {
                            Log.e(TAG, "Ignoring threshold change request: " + e);
                        }
                    }

                    // No matter what, return the current threshold setting
                    sendThresholdResponse();
                }
                else if(cmd.equals(CMD_TX_PING)) {
                    Log.i(TAG, "Ping");
                    sendChannelMessage(RESP_PING);
                }
            }
            else {
                // Messages the receiver handles
                if(cmd.equals(RESP_THRESHOLD)) {
                    try {
                        mThreshold = Float.parseFloat(parts[1]);
                        if(mServiceListener != null)
                            mServiceListener.onVADThresholdChange(mThreshold);
                    }
                    catch(IllegalFormatException e) {
                        Log.i(TAG, "Ignoring threshold message: " + e.toString());
                    }
                }
            }
        }
    };

    public interface MonitorServiceListener {
        public void onVADThresholdChange(float threshold);
        public void onTXModeChange(boolean isTxMode);
    }

    public float getVADThreshold() {
        return mThreshold;
    }

    public void setVADThreshold(float threshold) throws RemoteException {
        if(threshold < 0 || threshold > 1)
            throw new IllegalArgumentException("Threshold values must be between 0 and 1");

        // If we are a receiver, just pass the info off to the tx
        if(!isTransmitterMode()) {
            mThreshold = threshold;
            if(mServiceListener != null)
                mServiceListener.onVADThresholdChange(threshold);

            sendThreshold();

            return;
        }

        // Transmitter has to actually apply the change
        if(threshold != mThreshold) {
            mSettings.setThreshold(threshold);

            if(mServiceListener != null)
                mServiceListener.onVADThresholdChange(threshold);
        }

        mThreshold = threshold;
        try {
            getBinder().setVADThreshold(mThreshold);
        }
        catch(NullPointerException e) {
            // This occurs within Jumble if you try to set the threshold before the audiohandler is
            // up and running
            Log.d(TAG, "Unable to apply threshold yet");
        }

        if(getBinder().getTransmitMode() != Constants.TRANSMIT_VOICE_ACTIVITY) {
            getBinder().setTransmitMode(Constants.TRANSMIT_VOICE_ACTIVITY);
        }
    }

    private void sendChannelMessage(String msg) throws RemoteException {
        if(isConnected()) {
            int channelId = getBinder().getSessionChannel().getId();
            getBinder().sendChannelTextMessage(channelId, msg, false);
        }
    }

    private String buildCmd(String cmd, String param) {
        return cmd + " " + param;
    }

    private void sendThreshold() throws RemoteException {
        sendChannelMessage(buildCmd(CMD_THRESHOLD, Float.toString(mThreshold)));
    }

    private void sendThresholdResponse() throws RemoteException {
        sendChannelMessage(buildCmd(RESP_THRESHOLD, Float.toString(mThreshold)));
    }

    // Notification
    private static final int ONGOING_NOTIFICATION_ID = 2324;
    Notification mNotification;
    NotificationManager mNotificationManager;

    /**
     * Joins the BabyMonitor channel on the current service, if one exists
     * @throws RemoteException
     */
    public void joinBabyMonitorChannel() throws RemoteException {
        if(isConnected()) {
            List<Channel> channels = getBinder().getChannelList();

            for (Channel channel : channels) {
                if (channel.getName().contains(PREF_MUMBLE_CHANNEL)) {
                    Log.i(TAG, "Attempting to join channel " + channel.getName());
                    getBinder().joinChannel(channel.getId());
                    break;
                }
            }
        }
        else {
            Log.e(TAG, "Not connected to server, not attempting to join any room");
        }
    }

    /**
     * Sets Jumble to muted/defeaned based on whether we are a client or monitor
     */
    public void setDefaultMuteDeafenStatus() throws RemoteException {
        if(mIsTransmitter) {
            Log.i(TAG, "Broadcasting ourselves");
            getBinder().setSelfMuteDeafState(false, false);
        }
        else {
            Log.i(TAG, "Muting ourselves");
            getBinder().setSelfMuteDeafState(true, false);
        }
    }

    public void setDeafen() throws RemoteException {
        getBinder().setSelfMuteDeafState(true, true);
    }

    public void setDefaultTxMode() throws RemoteException {
        if(mIsTransmitter) {
            Log.i(TAG, "Setting to voice activity mode");
            getBinder().setTransmitMode(Constants.TRANSMIT_VOICE_ACTIVITY);
        }
        else {
            Log.i(TAG, "Setting to PTT mode");
            getBinder().setTransmitMode(Constants.TRANSMIT_PUSH_TO_TALK);
        }
    }

    public String getHost() {
        try {
            if (isConnected())
                return getBinder().getConnectedServer().getHost();
            else
                return null;
        }
        catch(RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    public int getPort() {
        try {
            if (isConnected())
                return getBinder().getConnectedServer().getPort();
            else
                return -1;
        }
        catch(RemoteException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public boolean isTransmitterMode() {
        return mIsTransmitter;
    }

    public void setTransmitterMode(final boolean isTx) {
        if(mIsTransmitter == isTx)
            return;

        mIsTransmitter = isTx;
        if(mServiceListener != null)
            mServiceListener.onTXModeChange(mIsTransmitter);

        if(isConnected()) {
            // Ensure all our mode info is correct
            try {
                setDefaultMuteDeafenStatus();
                setDefaultTxMode();

                // Get/send current threshold level
                if(mIsTransmitter) {
                    sendThresholdResponse();
                }
                else {
                    sendChannelMessage(CMD_THRESHOLD);
                }
            }
            catch (RemoteException e) {
                e.printStackTrace();
            }

            // Disconnect and reconnect to get a new user name and make sure everything refreshes
            /*try {
                Server currentServer = getBinder().getConnectedServer();
                disconnect();
                connect();
            }
            catch(RemoteException e) {
                e.printStackTrace();
            }*/
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Monitor service created");

        mMainHandler = new Handler(Looper.getMainLooper());

        // Settings
        mSettings = Settings.getInstance(this);
        mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        // Observer
        super.onCreate();
        try {
            getBinder().registerObserver(mJumbleObserver);
        }
        catch(RemoteException e) {
            e.printStackTrace();
        }

        // Apply settings appropriately
        try {
            setTransmitterMode(!mSettings.getIsRxMode());
            setVADThreshold(mSettings.getThreshold());

            if(mSettings.getStartOnBoot() && mSettings.getMumbleHost() != null) {
                connect(mSettings.getMumbleHost(), mSettings.getMumblePort(), mSettings.getUserName());
            }
        }
        catch(RemoteException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Started MonitorService");

        // Pass off intent to Jumble
        super.onStartCommand(intent, flags, startId);

        // I WILL NEVER DIE
        runInForeground();
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying MonitorService");

        disconnect();
        stopForeground(true);

        try {
            getBinder().unregisterObserver(mJumbleObserver);
        }
        catch(RemoteException e) {
            e.printStackTrace();
        }

        super.onDestroy();
    }

    // Binding utilities
    public class LocalBinder extends Binder {
        public MonitorService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MonitorService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void setServiceListener(MonitorServiceListener listener) {
        mServiceListener = listener;
    }

    public void connect(String host, int port, String user) {
        Server server = new Server(2000, "manual", host, port, user, "");

        AsyncTask<Server, Void, Void> async = new AsyncTask<Server, Void, Void>() {
            @Override
            protected Void doInBackground(Server... params) {
                Server info = params[0];

                // Start Jumble
                Intent connectIntent = new Intent(getApplicationContext(), MonitorService.class);

                connectIntent.putExtra(JumbleService.EXTRAS_SERVER, info);
                connectIntent.putExtra(JumbleService.EXTRAS_CLIENT_NAME, getString(R.string.app_name));
                connectIntent.putExtra(JumbleService.EXTRAS_TRANSMIT_MODE, Constants.TRANSMIT_PUSH_TO_TALK);
                connectIntent.putExtra(JumbleService.EXTRAS_DETECTION_THRESHOLD, DEFAULT_THRESHOLD);

                connectIntent.putExtra(JumbleService.EXTRAS_CERTIFICATE, mSettings.getCertificate());
                connectIntent.putExtra(JumbleService.EXTRAS_CERTIFICATE_PASSWORD, "");

                connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT, true);
                connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT_DELAY, 10);

                connectIntent.putStringArrayListExtra(JumbleService.EXTRAS_ACCESS_TOKENS, new ArrayList<String>(){{
                    add(MonitorService.MUMBLE_TOKEN);
                }});

                connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE, BabyTrustStore.getTrustStorePath(getApplicationContext()));
                connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_PASSWORD, BabyTrustStore.getTrustStorePassword());
                connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_FORMAT, BabyTrustStore.getTrustStoreFormat());

                connectIntent.setAction(JumbleService.ACTION_CONNECT);

                startService(connectIntent);

                return null;
            }
        }.execute(server);
    }

    public void disconnect() {
        if(isConnected()) {
            super.disconnect();
        }
    }

    public boolean isConnected() {
        try {
            if(getBinder() != null) {
                return getBinder().isConnected();
            }
            else {
                // If we're not connected to Jumble, we're definitely not connected
                return false;
            }
        }
        catch(RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isReconnecting() {
        try {
            if(getBinder() != null) {
                return getBinder().isReconnecting();
            }
            else {
                // If we're not connected to Jumble, we're definitely not connected
                return false;
            }
        }
        catch(RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void runInForeground() {
        // Run in foreground so we're never killed
        rebuildNotification();
        startForeground(ONGOING_NOTIFICATION_ID, mNotification);
    }

    private Notification rebuildNotification() {
        // Use notification to display number of people monitoring
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        if(mIsTransmitter) {
            builder.setContentTitle("Monitor - Transmitter");
        }
        else {
            builder.setContentTitle("Monitor - Receiver");
        }

        if(isConnected()) {
            builder.setContentText("Connected");
        }
        else if(isReconnecting()) {
            builder.setContentText("Reconnecting");
        }
        else {
            builder.setContentText("Idle");
        }

        builder.setSmallIcon(R.drawable.notification_icon);
        builder.setOngoing(true);

        // Open status activity on click
        // FLAG_CANCEL_CURRENT ensures that the extra always gets sent.
        Intent statusIntent = statusIntent = new Intent(this, ClientStatus.class);
        statusIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, statusIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);

        // Control buttons
        //builder.addAction();

        mNotification = builder.build();
        mNotificationManager.notify(ONGOING_NOTIFICATION_ID, mNotification);

        return mNotification;
    }

    public static void killMonitor(Context context) {
        // If running, ends the background monitor service
        context.stopService(new Intent(context, MonitorService.class));
    }

    public static void startMonitor(Context context) {
        context.startService(new Intent(context, MonitorService.class));
    }
}
