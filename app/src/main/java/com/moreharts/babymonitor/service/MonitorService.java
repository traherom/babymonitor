package com.moreharts.babymonitor.service;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.Context;
import android.os.IBinder;
import android.os.Binder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.client.ClientStatus;
import com.moreharts.babymonitor.preferences.BabyTrustStore;
import com.moreharts.babymonitor.preferences.Settings;
import com.moreharts.babymonitor.server.ServerStatus;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.util.ParcelableByteArray;

public class MonitorService extends JumbleService {
    public static final String TAG = "MonitorService";

    public static final String MUMBLE_SERVER_USER = "babymonitor_server_";
    public static final String MUMBLE_CLIENT_USER = "babymonitor_client_";
    public static final String PREF_MUMBLE_CHANNEL = "BabyMonitor";
    public static final String MUMBLE_TOKEN = "com.moreharts.babymonitor";

    // Service info
    private final IBinder mBinder = new LocalBinder();
    private Settings mSettings = null;
    private boolean mIsServer = false;

    // Jumble
    private Server mConnectedInfo = null;

    private JumbleObserver mJumbleObserver = new JumbleObserver() {
        @Override
        public void onConnected() throws RemoteException {
            Log.i(TAG, "Connected to server, muting ourselves");
            //getBinder().setSelfMuteDeafState(true, false);

            rebuildNotification();
        }

        @Override
        public void onDisconnected() throws RemoteException {
            Log.i(TAG, "disconnected");
            rebuildNotification();
        }

        @Override
        public void onConnectionError(String message, boolean reconnecting) throws RemoteException {
            Log.i(TAG, "connection error");
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
                    //connectToServer(lastServer); // FIXME unreliable
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), R.string.trust_add_failed, Toast.LENGTH_LONG).show();
                }
            } catch (CertificateException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onChannelAdded(Channel channel) throws RemoteException {
            Log.i(TAG, "channel added: " + channel.getName());
        }

        @Override
        public void onChannelStateUpdated(Channel channel) throws RemoteException {
            Log.i(TAG, "channel state updated");
        }

        @Override
        public void onChannelRemoved(Channel channel) throws RemoteException {
            Log.i(TAG, "channel removed");
        }

        @Override
        public void onChannelPermissionsUpdated(Channel channel) throws RemoteException {
            Log.i(TAG, "channel permissions updated");
        }

        @Override
        public void onUserConnected(User user) throws RemoteException {
            Log.i(TAG, "user connected: " + user.getName());

            // Mute everyone who isn't the monitor
            if(!user.getName().startsWith(MUMBLE_SERVER_USER)) {
                Log.i(TAG, "Locally muting user " + user.getName());
                user.setLocalMuted(true);
            }
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
            Log.i(TAG, "message: " + message.getMessage());
        }
    };

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

    @Override
    public void onCreate() {
        Log.d(TAG, "Monitor service created");

        // Settings
        mSettings = Settings.getInstance(this);

        // Observer
        super.onCreate();
        try {
            getBinder().registerObserver(mJumbleObserver);
        }
        catch(RemoteException e) {
            e.printStackTrace();
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
            Log.e(TAG, "Error in isConnected: " + e);
            return false;
        }
    }

    private void runInForeground() {
        // Run in foreground so we're never killed
        rebuildNotification();
        startForeground(ONGOING_NOTIFICATION_ID, mNotification);
        mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private Notification rebuildNotification() {
        // Use notification to display number of people monitoring
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        if(mIsServer) {
            builder.setContentTitle("Baby Monitor - Server");
        }
        else {
            builder.setContentTitle("Baby Monitor - Client");
        }

        if(isConnected()) {
            try {
                builder.setContentText("Connected to " + getBinder().getConnectedServer().getName());
            }
            catch(RemoteException e) {
                builder.setContentText(e.toString());
            }
        }
        else {
            builder.setContentText("Idle");
        }

        builder.setSmallIcon(R.drawable.notification_icon);
        builder.setOngoing(true);

        // Open status activity on click
        // FLAG_CANCEL_CURRENT ensures that the extra always gets sent.
        Intent statusIntent = null;
        if(mIsServer) {
            statusIntent = new Intent(this, ServerStatus.class);
        }
        else {
            statusIntent = new Intent(this, ClientStatus.class);
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, statusIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);

        mNotification = builder.build();
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
