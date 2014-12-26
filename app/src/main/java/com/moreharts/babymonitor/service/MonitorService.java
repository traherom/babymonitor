package com.moreharts.babymonitor.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.preferences.BabyTrustStore;
import com.moreharts.babymonitor.preferences.Settings;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.jumble.util.ParcelableByteArray;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

public class MonitorService extends JumbleService {
    public static final String TAG = "MonitorService";

    public static final String MUMBLE_USER_START = "bm";
    public static final String PREF_MUMBLE_CHANNEL = "BabyMonitor";
    public static final String MUMBLE_TOKEN = "com.moreharts.babymonitor";
    private static final long RECONNECT_DELAY = 10000;
    private static final int RETRY_LIMIT = 5;

    public static final float DEFAULT_THRESHOLD = 0.7f;

    public enum NotificationMode {FULL_AUDIO, NOTIFICATION_SOUND_ONLY, VISUAL_ONLY, NONE};

    // Service info and universal settings for RX and TX mode
    private final IBinder mBinder = new LocalBinder();
    private Settings mSettings = null;

    private int mAudioStream = AudioManager.STREAM_MUSIC;

    private Handler mHandler = new Handler();
    private ConnectivityManager mConnectivityManager = null;

    // Local monitor state settings
    private boolean mIsTransmitter = false;
    private float mThreshold = DEFAULT_THRESHOLD;

    private int mRetryCount = 0;

    private String mDesiredChannelName = null;
    private int mDesiredChannelId = -1;

    // Helpers
    private Handler mMainHandler = null;
    private NoiseTracker mNoiseTracker = null;
    private TextMessageManager mTextMessageManager = null;
    private RemoteMonitorTracker mRemoteMonitorTracker = null;

    private Settings.OnChangeListener mPreferenceChangeListener = new Settings.OnChangeListener() {
        @Override
        public void onChange(Settings settings) {
            applyCurrentSettings();
        }
    };

    // People listening to us (they can also bind to the Jumble binder)
    private ArrayList<OnTxModeChangedListener> mTxModeHandlers = new ArrayList<OnTxModeChangedListener>();
    private ArrayList<OnMessageReceivedListener> mMessageHandlers = new ArrayList<OnMessageReceivedListener>();
    private ArrayList<OnVADThresholdChangedListener> mThresholdHandlers = new ArrayList<OnVADThresholdChangedListener>();
    private ArrayList<OnConnectionStatusListener> mConnectionHandlers = new ArrayList<OnConnectionStatusListener>();
    private ArrayList<OnUserStateListener> mUserHandlers = new ArrayList<OnUserStateListener>();

    // Notification
    NotificationHelper mNotification = null;

    // Jumble
    private Server mPendingConnectInfo = null;

    private JumbleObserver mJumbleObserver = new JumbleObserver() {
        @Override
        public void onConnected() throws RemoteException {
            Log.i(TAG, "Connected to server");

            // Reset retries
            mRetryCount = 0;

            notifyOnConnectionStatusListenerConnected();
            applyCurrentSettings();
        }

        @Override
        public void onDisconnected() throws RemoteException {
            Log.i(TAG, "Disconnected");
            notifyOnConnectionStatusListenerDisconnected();

            // Reset retries, just to be sure it's good to go
            mRetryCount = 0;
        }

        @Override
        public void onConnectionError(String message, boolean reconnecting) throws RemoteException {
            Log.i(TAG, "Connection error: " + message);
            notifyOnConnectionStatusListenerConnectionError(message, reconnecting);

            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    StringBuilder sb = new StringBuilder("Baby Monitor connection failed (");
                    sb.append(mRetryCount);
                    sb.append('/');
                    sb.append(RETRY_LIMIT);
                    sb.append(" retries)");

                    Toast toast = Toast.makeText(MonitorService.this, sb.toString(), Toast.LENGTH_SHORT);
                    toast.show();
                }
            });

            // Attempt to reconnect
            if(mRetryCount < RETRY_LIMIT) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mRetryCount++;
                        connectToPending();

                        // Only continue attempting if we're allowed to connect on this type of network
                        //if(connectionAllowed()) {
                        //    mHandler.postDelayed(this, RECONNECT_DELAY);
                        //}
                    }
                }, RECONNECT_DELAY);
            }
            else {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast toast = Toast.makeText(MonitorService.this, "Baby Monitor connection failed", Toast.LENGTH_LONG);
                        toast.show();
                    }
                });
            }
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
            Log.i(TAG, "User state updated: " + user.getName());
        }

        @Override
        public void onUserTalkStateUpdated(User user) throws RemoteException {
            Log.i(TAG, "User talk: " + user.getName());
            notifyOnUserStateListenerTalking(user);
        }

        @Override
        public void onUserJoinedChannel(User user, Channel newChannel, Channel oldChannel) throws RemoteException {
            Log.i(TAG, "User joined channel: " + user.getName());

            // Make sure we send the current status every time someone joins
            if(mIsTransmitter) {
                mTextMessageManager.broadcastState();
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
        public void onChannelAdded(Channel channel) throws RemoteException {
            if(mDesiredChannelName != null && channel.getName().equals(mDesiredChannelName)) {
                mDesiredChannelId = channel.getId();
            }
        }

        @Override
        public void onChannelRemoved(Channel channel) throws RemoteException {
            if(mDesiredChannelName == null || channel.getName().equals(mDesiredChannelName)) {
                mDesiredChannelId = -1;
            }
        }

        @Override
        public void onMessageLogged(Message message) throws RemoteException {
            notifyMessageHandlers(message);
        }
    };

    // Settings
    public float getVADThreshold() {
        return mThreshold;
    }

    public void setVADThreshold(float threshold) throws RemoteException {
        if(threshold < 0 || threshold > 1)
            throw new IllegalArgumentException("Threshold values must be between 0 and 1");

        // Don't do anything if it's the same as before, just pretend
        if(threshold == mThreshold) {
            notifyVADThresholdListeners(threshold);
            return;
        }

        // Save threshold for future runs and notify everyone we're changing
        mSettings.setThreshold(threshold);
        mThreshold = threshold;
        notifyVADThresholdListeners(mThreshold);

        if(!isTransmitterMode()) {
            // Receiver just passes the info off over the network
            sendThreshold();
        }
        else {
            // Transmitter has to actually apply the change
            try {
                getBinder().setVADThreshold(mThreshold);
            }
            catch(NullPointerException e) {
                // This occurs within Jumble if you try to set the threshold before the audio handler is
                // up and running
                Log.w(TAG, "Unable to apply threshold yet, trying again after a delay");
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            getBinder().setVADThreshold(mThreshold);
                        } catch (NullPointerException e) {
                            Log.d(TAG, "Remote exception occurred on VAD set retry: " + e);
                            e.printStackTrace();
                            mHandler.postDelayed(this, 1000);
                        } catch (RemoteException e) {
                            Log.d(TAG, "Remote exception occurred on VAD set retry: " + e);
                            e.printStackTrace();
                        }
                    }
                }, 1000);
            }
        }
    }

    // Channel messages
    public void sendThreshold() throws RemoteException {
        mTextMessageManager.sendChannelMessage(buildCmd(TextMessageManager.CMD_THRESHOLD, Float.toString(mThreshold)));
    }

    private String buildCmd(String cmd, String param) {
        return cmd + " " + param;
    }

    // Access helper classes
    public NoiseTracker getNoiseTracker() {
        return mNoiseTracker;
    }

    public TextMessageManager getTextMessageManager() {
        return mTextMessageManager;
    }

    public RemoteMonitorTracker getRemoteMonitorTracker() {
        return mRemoteMonitorTracker;
    }

    /**
     * Joins the configured channel on the current server, if one exists
     * @throws RemoteException
     */
    public void joinBabyMonitorChannel() throws RemoteException {
        if(isConnected()) {
            if(mDesiredChannelId > 0) {
                getBinder().joinChannel(mDesiredChannelId);
            }
        }
        else {
            Log.i(TAG, "Not connected to server, not attempting to join any room");
        }
    }

    /** Applies the correct settings to Jumble/notifications based on mode,
     * connection type, and user preferences
     */
    private void applyCurrentSettings() {
        if(!isConnected())
            return;

        try {
            // Mute/deafen state
            boolean shouldDeafen = false;
            if(!isTransmitterMode()) {
                // Receivers may deafen themselves if they only want to receive status
                // text messages and output their own audio
                if(isWifiNetwork() && !mSettings.getWifiFullAudioOn())
                    shouldDeafen = true;
                else if(!isWifiNetwork() && !mSettings.getMobileFullAudioOn())
                    shouldDeafen = true;
            }

            try {
                // Mute and deafen
                getBinder().setSelfMuteDeafState(!mIsTransmitter, shouldDeafen);
            }
            catch(RemoteException e) {
                e.printStackTrace();
            }

            // Transmit style
            if(isTransmitterMode()) {
                Log.i(TAG, "Setting to voice activity mode");
                getBinder().setTransmitMode(Constants.TRANSMIT_VOICE_ACTIVITY);
            }
            else {
                Log.i(TAG, "Setting to PTT mode");
                getBinder().setTransmitMode(Constants.TRANSMIT_PUSH_TO_TALK);
            }

            // Voice activity detection
            setVADThreshold(mThreshold);

            // Try to get to the correct place
            mDesiredChannelName = mSettings.getMumbleChannel();
            joinBabyMonitorChannel();
        }
        catch (RemoteException e) {
            // Try again soon
            Log.i(TAG, "Failed to apply all settings, trying again");
            e.printStackTrace();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    applyCurrentSettings();
                }
            }, 1000);
        }
    }

    public void setDeafen() {
        setDeafen(true);
    }

    public void setDeafen(boolean deafen) {
        try {
            //getBinder().setSelfMuteDeafState(false, deafen);
            getBinder().getSessionUser().setDeafened(deafen);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public boolean isDefeaned() {
        try {
            return getBinder().getSessionUser().isDeafened();
        } catch (RemoteException e) {
            e.printStackTrace();
            return mIsTransmitter;
        }
    }

    // Misc utilities to reach into Jumble
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

        // Disconnect if we're connected now
        boolean isConnected = isConnected();
        Server lastServer = null;
        if(isConnected) {
            try {
                lastServer = getBinder().getConnectedServer();
                disconnect();
            }
            catch(RemoteException e) {
                e.printStackTrace();
            }
        }

        // Change and tell everyone
        mIsTransmitter = isTx;
        notifyOnTxModeChangedListeners(isTx);

        // Reconnect if needed
        if(isConnected) {
            connect(lastServer.getHost(), lastServer.getPort(), lastServer.getUsername());
        }
    }

    public String getUser() {
        try {
            if(isConnected()) {
                return getBinder().getSessionUser().getName();
            }
            else {
                return mSettings.getUserName();
            }
        }
        catch (NullPointerException e) {
            e.printStackTrace();
            return mSettings.getUserName();
        }
        catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Monitor service created");

        mMainHandler = new Handler(Looper.getMainLooper());

        // Settings
        mSettings = Settings.getInstance(this);
        mSettings.addOnChangeListener(mPreferenceChangeListener);

        // Jumble Observer
        super.onCreate();
        try {
            getBinder().registerObserver(mJumbleObserver);
        }
        catch(RemoteException e) {
            e.printStackTrace();
        }

        // Add helper components. ORDER IS IMPORTANT.
        mTextMessageManager = new TextMessageManager(this);
        mNoiseTracker = new NoiseTracker(this);
        mNotification = new NotificationHelper(this); // Must come after noise tracker
        mRemoteMonitorTracker = new RemoteMonitorTracker(this); // Must come after text message manager

        // Load up settings
        if(mSettings.getMumbleHost() != null) {
            mPendingConnectInfo = new Server(0, "manual", mSettings.getMumbleHost(), mSettings.getMumblePort(), mSettings.getUserName(), "");
        }

        try {
            setTransmitterMode(mSettings.getIsTxMode());
            setVADThreshold(mSettings.getThreshold());

            if(mPendingConnectInfo != null) {
                connectToPending();
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
        mNotification.runInForeground();
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying MonitorService");

        disconnect();
        stopForeground(true);

        mSettings.removeOnChangeListener(mPreferenceChangeListener);

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

    public boolean connectionAllowed() {
        if(isWifiNetwork()) {
            return mSettings.getWifiEnabled();
        }
        else {
            return mSettings.getMobileEnabled();
        }
    }

    public boolean isWifiNetwork() {
        if(mConnectivityManager == null)
            mConnectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo network = mConnectivityManager.getActiveNetworkInfo();
        if(network == null)
            return false;

        if(network.getType() == ConnectivityManager.TYPE_WIFI || network.getType() == ConnectivityManager.TYPE_ETHERNET) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Helper to determine if audio should be played based on user settings and the current network
     */
    public boolean shouldPlayFullAudio() {
        if((isWifiNetwork() && mSettings.getWifiFullAudioOn()) || (!isWifiNetwork() && mSettings.getMobileFullAudioOn()))
            return true;
        else
            return false;
    }

    private void connectToPending() {
        connect(mPendingConnectInfo.getHost(), mPendingConnectInfo.getPort(), mPendingConnectInfo.getUsername());
    }

    public void connect(String host, int port, String user) {
        Server server = new Server(2000, "manual", host, port, user, "");
        mPendingConnectInfo = server;

        // Only honor request if our settings allow it
        if(!connectionAllowed()) {
            return;
        }

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

                connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT, false);
                connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT_DELAY, 10);

                connectIntent.putStringArrayListExtra(JumbleService.EXTRAS_ACCESS_TOKENS, new ArrayList<String>(){{
                    add(MonitorService.MUMBLE_TOKEN);
                }});

                connectIntent.putExtra(EXTRAS_AUDIO_STREAM, mAudioStream);

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

    /**
     * Returns the server we are connected/awaiting connection to. Null if there is none set
     */
    public Server getPendingConnectionInfo() {
        return mPendingConnectInfo;
    }

    /**
     * Returns true if there has been noise heard in the past few seconds,
     * as determined by the NoiseTracker
     * @return true if noise has been heard lately, false otherwise
     */
    public boolean isThereNoise() {
        return mNoiseTracker.isThereNoise();
    }

    // Utilities to start and stop monitor
    public static void killMonitor(Context context) {
        // If running, ends the background monitor service
        context.stopService(getIntent(context));
    }

    public static void startMonitor(Context context, ServiceConnection binder) {
        Intent intent = getIntent(context);
        context.startService(intent);

        if(binder != null)
            context.bindService(intent, binder, Context.BIND_AUTO_CREATE);
    }

    /**
     * Grabs the plain intent to start/bind to this service
     */
    public static Intent getIntent(Context context) {
        return new Intent(context, MonitorService.class);
    }

    public void onNetworkStateChanged() {
        Log.d(TAG, "New internet connection detected");

        // IF we have connection info, try!
        if(mPendingConnectInfo != null) {
            connectToPending();
        }
    }

    // Listeners
    // Text messages
    public void addOnMessageHandler(OnMessageReceivedListener handler) {
        mMessageHandlers.add(handler);
    }

    public void removeOnMessageHandler(OnMessageReceivedListener handler) {
        mMessageHandlers.remove(handler);
    }

    private void notifyMessageHandlers(Message msg) {
        for(OnMessageReceivedListener listener : mMessageHandlers) {
            listener.onMessageReceived(this, msg);
        }
    }

    // Threshold listeners
    public void addOnVADThresholdChangedListener(OnVADThresholdChangedListener listener) {
        mThresholdHandlers.add(listener);
    }

    public void removeOnVADThresholdChangedListener(OnVADThresholdChangedListener listener) {
        mThresholdHandlers.remove(listener);
    }

    private void notifyVADThresholdListeners(float threshold) {
        for(OnVADThresholdChangedListener listener : mThresholdHandlers) {
            listener.onVADThresholdChanged(this, threshold);
        }
    }

    // Tx Mode Change
    public void addOnTxModeChangedListener(OnTxModeChangedListener listener) {
        mTxModeHandlers.add(listener);
    }

    public void removeOnTxModeChangedListener(OnTxModeChangedListener listener) {
        mTxModeHandlers.remove(listener);
    }

    private void notifyOnTxModeChangedListeners(boolean isTxMode) {
        for(OnTxModeChangedListener listener : mTxModeHandlers) {
            listener.onTXModeChanged(this, isTxMode);
        }
    }

    // Connection status
    public void addOnConnectionStatusListener(OnConnectionStatusListener listener) {
        mConnectionHandlers.add(listener);
    }

    public void removeOnConnectionStatusListener(OnConnectionStatusListener listener) {
        mConnectionHandlers.remove(listener);
    }

    private void notifyOnConnectionStatusListenerConnected() {
        for(OnConnectionStatusListener listener : mConnectionHandlers) {
            listener.onConnected(this);
        }
    }

    private void notifyOnConnectionStatusListenerDisconnected() {
        for(OnConnectionStatusListener listener : mConnectionHandlers) {
            listener.onDisconnected(this);
        }
    }

    private void notifyOnConnectionStatusListenerConnectionError(String msg, boolean reconnecting) {
        for(OnConnectionStatusListener listener : mConnectionHandlers) {
            listener.onConnectionError(this, msg, reconnecting);
        }
    }

    // User state
    public void addOnUserStateListener(OnUserStateListener listener) {
        mUserHandlers.add(listener);
    }

    public void removeOnUserStateListener(OnUserStateListener listener) {
        mUserHandlers.remove(listener);
    }

    private void notifyOnUserStateListenerTalking(User user) {
        for(OnUserStateListener listener : mUserHandlers) {
            listener.onUserTalk(this, user);
        }
    }

    // Event listener interfaces
    public interface OnMessageReceivedListener {
        public void onMessageReceived(MonitorService service, Message msg);
    }

    public interface OnVADThresholdChangedListener {
        public void onVADThresholdChanged(MonitorService service, float newThreshold);
    }

    public interface OnTxModeChangedListener {
        public void onTXModeChanged(MonitorService service, boolean isTxMode);
    }

    public interface OnConnectionStatusListener {
        public void onConnected(MonitorService service);
        public void onDisconnected(MonitorService service);
        public void onConnectionError(MonitorService service, String message, boolean reconnecting);
    }

    public interface OnUserStateListener {
        public void onUserTalk(MonitorService service, User user);
    }
}
