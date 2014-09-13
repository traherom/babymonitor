package com.moreharts.babymonitor.client;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.content.ServiceConnection;
import android.widget.Toast;

import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.preferences.BabyTrustStore;
import com.moreharts.babymonitor.preferences.Settings;
import com.moreharts.babymonitor.server.ServerBackgroundService;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.util.JumbleObserver;
import com.morlunk.jumble.model.Server;
import com.morlunk.jumble.util.ParcelableByteArray;

public class ClientBackgroundService extends Service {
    public static final String TAG = "ClientBackground";

    // Settings
    private Settings mSettings;

    // Network
    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener;
    private NsdServiceInfo mNsdService;

    // Jumble
    private Server mServer;
    private JumbleService.JumbleBinder mJumbleBinder;
    private ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Connected to Jumble service");
            try {
                mJumbleBinder = (JumbleService.JumbleBinder) iBinder;
                mJumbleBinder.registerObserver(mJumbleObserver);
            }
            catch(RemoteException e) {
                Log.e(TAG, "RemoteException caught while trying to bind to Jumble: " + e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "Disconnected from Jumble service");
            mJumbleBinder = null;
        }
    };
    private JumbleObserver mJumbleObserver = new JumbleObserver() {
        @Override
        public void onTLSHandshakeFailed(ParcelableByteArray cert) throws RemoteException {
            byte[] certBytes = cert.getBytes();
            final Server lastServer = mJumbleBinder.getConnectedServer();

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
                    connectToServer(InetAddress.getByName(lastServer.getHost()), lastServer.getPort()); // FIXME unreliable
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), R.string.trust_add_failed, Toast.LENGTH_LONG).show();
                }
            } catch (CertificateException e) {
                e.printStackTrace();
            }
        }
    };

    // Notification
    private static final int ONGOING_NOTIFICATION_ID = 2324;
    Notification mNotification;
    NotificationManager mNotificationManager;

    public void connectToServer(InetAddress addr, int port) {
        mServer = new Server(1, "monitor", addr.getHostAddress(), port, "test", "");

        Intent connectIntent = new Intent(this, JumbleService.class);

        connectIntent.putExtra(JumbleService.EXTRAS_SERVER, mServer);
        connectIntent.putExtra(JumbleService.EXTRAS_CLIENT_NAME, this.getString(R.string.app_name));
        connectIntent.putExtra(JumbleService.EXTRAS_TRANSMIT_MODE, Constants.TRANSMIT_PUSH_TO_TALK);

        connectIntent.putExtra(JumbleService.EXTRAS_CERTIFICATE, mSettings.getCertificate());
        connectIntent.putExtra(JumbleService.EXTRAS_CERTIFICATE_PASSWORD, "");

        connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT, true);
        connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT_DELAY, 10);
        //connectIntent.putExtra(JumbleService.EXTRAS_INPUT_RATE, mSettings.getInputSampleRate());
        //connectIntent.putExtra(JumbleService.EXTRAS_INPUT_QUALITY, mSettings.getInputQuality());

        connectIntent.putStringArrayListExtra(JumbleService.EXTRAS_ACCESS_TOKENS, new ArrayList<String>());
        //connectIntent.putExtra(JumbleService.EXTRAS_AUDIO_SOURCE, audioSource);
        //connectIntent.putExtra(JumbleService.EXTRAS_AUDIO_STREAM, audioStream);
        //connectIntent.putExtra(JumbleService.EXTRAS_FRAMES_PER_PACKET, mSettings.getFramesPerPacket());

        connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE, BabyTrustStore.getTrustStorePath(getApplicationContext()));
        connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_PASSWORD, BabyTrustStore.getTrustStorePassword());
        connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_FORMAT, BabyTrustStore.getTrustStoreFormat());

        //connectIntent.putExtra(JumbleService.EXTRAS_HALF_DUPLEX, mSettings.isHalfDuplex());
        //connectIntent.putExtra(JumbleService.EXTRAS_ENABLE_PREPROCESSOR, mSettings.isPreprocessorEnabled());*/

        connectIntent.setAction(JumbleService.ACTION_CONNECT);

        startService(connectIntent);
        bindService(connectIntent, mServiceConn, 0);
    }

    public void disconnectFromServer() {
        // Disconnect from server
        try {
            if(mJumbleBinder != null) {
                mJumbleBinder.disconnect();
            }
        }
        catch(RemoteException e) {
            Log.e(TAG, "Disconnect from server failed with RemoteException: " + e);
        }

        try {
            if(mServiceConn != null) {
                unbindService(mServiceConn);
            }
        }
        catch(IllegalArgumentException e) {
            Log.d(TAG, "Illegal arument exception, likely not bound " + e);
        }
    }

    public void startServiceDiscovery() {
        mNsdManager = (NsdManager)getApplicationContext().getSystemService(Context.NSD_SERVICE);

        mResolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Called when the resolve fails.  Use the error code to debug.
                Log.e(TAG, "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                mNsdService = serviceInfo;
                Log.i(TAG, "Found BabyMonitor at " + mNsdService.getHost().toString() + ":" + mNsdService.getPort());

                // Save info and connect to server
                connectToServer(mNsdService.getHost(), mNsdService.getPort());

                // TODO Disable service discovery for now?
            }
        };

        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String s, int i) {
                Log.e(TAG, "Discovery failed to start, error " + i);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String s, int i) {
                Log.e(TAG, "Discovery failed to stop, error " + i);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onDiscoveryStarted(String s) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d(TAG, "Service discovery success " + service);

                // TODO also only look for BabyMonitor servers
                if (service.getServiceType().equals(ServerBackgroundService.SERVICE_TYPE)) {
                    if(!service.getServiceName().contains(ServerBackgroundService.DEFAULT_SERVICE_NAME)) {
                        Log.d(TAG, "Connecting to Mumble server despite being " + service.getServiceName());
                    }
                    mNsdManager.resolveService(service, mResolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e(TAG, "service lost" + service);
            }
        };

        mNsdManager.discoverServices(ServerBackgroundService.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void stopServiceDiscovery() {
        try {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
        catch(IllegalArgumentException e) {
            // Likely not running
            Log.d(TAG, "Stop service discovery threw IllegalArgumentException. Likely fine: " + e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Started ClientBackground service");

        mSettings = Settings.getInstance(this);
        runInForeground();

        // Start the discoverer
        startServiceDiscovery();

        return START_FLAG_REDELIVERY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying ClientBackground service");

        // Stop searching on network (if we haven't already)
        stopServiceDiscovery();
        disconnectFromServer();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void runInForeground() {
        // Run in foreground so we're never killed
        rebuildNotification();
        startForeground(ONGOING_NOTIFICATION_ID, mNotification);
        mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private Notification rebuildNotification() {
        // Use notification to display number of people monitoring
        // TODO Add linking of notification to status activity?
        mNotification = new NotificationCompat.Builder(this)
                .setContentTitle("Baby Monitor - Client")
                .setContentText("Searching for server...")
                .setSmallIcon(R.drawable.notification_icon)
                .setOngoing(true)
                .build();
        return mNotification;
    }
}
