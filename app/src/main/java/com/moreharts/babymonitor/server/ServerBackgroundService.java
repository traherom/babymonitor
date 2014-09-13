package com.moreharts.babymonitor.server;

import android.app.Notification;
import android.app.Service;
import android.app.NotificationManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import android.util.Log;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.v4.app.NotificationCompat;
import android.content.Intent;
import android.widget.Toast;

import com.moreharts.babymonitor.R;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerBackgroundService extends Service {
    private static final String TAG = "ServerBackground";

    // Thread management
    boolean shouldStop = false;

    // Network
    public static final String DEFAULT_SERVICE_NAME = "BabyMonitor";
    public static final String SERVICE_TYPE = "_mumble._tcp.";
    private NsdManager mNsdManager;
    private String mServiceName;
    private ServerSocket mListenSocket;
    private Thread mListenThread;
    private NsdManager.RegistrationListener mRegistrationListener;

    // Audio
    private boolean useAudio = false;
    private static final int AUDIO_HERTZ = 8000;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    int audioBufferSize = 0;
    AudioRecord mAudio = null;
    byte[] audioBuffer;

    // TODO Camera
    private boolean useCamera = false;

    // Notification
    private static final int ONGOING_NOTIFICATION_ID = 89423;
    Notification mNotification;
    NotificationManager mNotificationManager;
    int connectedClientCnt = 0;

    public void onCreate() {
        super.onCreate();
    }

    private class ConnectionListener implements Runnable {
        public void run() {
            Log.i(TAG, "Started server listener on port " + mListenSocket.getLocalPort());
            while(!shouldStop && !mListenSocket.isClosed()) {
                try {
                    Socket conn = mListenSocket.accept();

                    new Thread(new ConnectionHandler(conn)).start();
                }
                catch(IOException e) {
                    Log.d(TAG, "Interrupted while listening for new connections: " + e);
                }
            }
        }
    }

    private class ConnectionHandler implements Runnable {
        private Socket mConn;

        public ConnectionHandler(Socket conn) {
            mConn = conn;
        }

        public void run() {
            Log.i(TAG, "New connection handler spawned");

            while(!shouldStop && mConn.isConnected()) {
                try {
                    synchronized (this) {
                        wait(1000);
                    }
                }
                //catch(IOException e) {
                //    Log.d(TAG, "Interrupted while listening for new connections: " + e);
                //}
                catch(InterruptedException e) {

                }
            }
        }
    }

    private void registerOnNetwork(int port) {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(DEFAULT_SERVICE_NAME);
        info.setServiceType(SERVICE_TYPE);
        info.setPort(port);

        mNsdManager = (NsdManager)getApplicationContext().getSystemService(Context.NSD_SERVICE);

        mRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
                Log.e(TAG, "Failed to register with NSD, error " + i);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
                // A'ight, whatever
                Log.e(TAG, "Failed to unregister from NSD, error " + i);
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                mServiceName = nsdServiceInfo.getServiceName();
                Log.i(TAG, "Registered with NSD as " + mServiceName);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo nsdServiceInfo) {
                Log.i(TAG, "Unregistered with NSD");
            }
        };

        mNsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Log.i(TAG, "Started ServerBackground service");

            runInForeground();

            // Start listening
            mListenSocket = new ServerSocket(0);
            mListenThread = new Thread(new ConnectionListener());
            mListenThread.start();
            registerOnNetwork(mListenSocket.getLocalPort());
        }
        catch(IOException e) {
            Log.e(TAG, "Unable to create listening socket: " + e.toString());
        }


        return START_FLAG_REDELIVERY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying ServerBackground service");

        // Stop NSD advertisement
        mNsdManager.unregisterService(mRegistrationListener);

        // Kill listener thread
        try {
            shouldStop = true;
            if(mListenSocket != null) {
                mListenSocket.close();
            }
        }
        catch (IOException e) {
            Log.d(TAG, "Error occured while closing server listening socket: " + e);
        }

        // Release mic and camera
        releaseMic();
        releaseCamera();

        // Kill notification
        mNotificationManager.cancel(ONGOING_NOTIFICATION_ID);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind requested, ignoring");
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
                .setContentTitle("Baby Monitor - Server")
                .setContentText("Running - " + connectedClientCnt + " Connected")
                .setSmallIcon(R.drawable.notification_icon)
                .setOngoing(true)
                .build();
        return mNotification;
    }

    private void acquireMic() {
        // Get microphone
        audioBufferSize = 10 * AudioRecord.getMinBufferSize(AUDIO_HERTZ, AUDIO_CHANNELS, AUDIO_ENCODING);
        if(audioBufferSize > 0) {
            useAudio = true;

            Log.d(TAG, "buffer size " + audioBufferSize);

            audioBuffer = new byte[audioBufferSize/5];
        }
        else {
            Log.e(TAG, "Unable to acquire audio recorder: error " + audioBufferSize);
            Toast.makeText(this, "Unable to acquire microphone", Toast.LENGTH_LONG);
            useAudio = false;
        }

        if(useAudio) {
            mAudio = new AudioRecord(
                    MediaRecorder.AudioSource.CAMCORDER,
                    AUDIO_HERTZ,
                    AUDIO_CHANNELS,
                    AUDIO_ENCODING,
                    audioBufferSize);
            mAudio.startRecording();
        }
    }

    private void acquireCamera() {
        // TODO
        useCamera = false;
    }

    private void releaseMic() {
        useAudio = false;
        if(mAudio != null) {
            mAudio.stop();
            mAudio.release();
        }
    }

    private void releaseCamera() {
        if(useCamera) {

        }
    }

    private void readFromMic() {
        if(useAudio) {
            // Open mic
            int bytesRead = mAudio.read(audioBuffer, 0, audioBuffer.length);

            if (bytesRead < 0) {
                Log.e(TAG, "Error reading from microphone: " + bytesRead);
            }
        }
    }
}
