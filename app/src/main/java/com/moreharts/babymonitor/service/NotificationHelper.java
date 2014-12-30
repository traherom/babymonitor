package com.moreharts.babymonitor.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.preferences.Settings;
import com.moreharts.babymonitor.ui.ClientStatus;

/**
 * Manages MonitorService notifications
 * Created by traherom on 11/22/2014.
 */
public class NotificationHelper {
    public static final int ONGOING_NOTIFICATION_ID = 2324;

    public static final long[] NOTIFICATION_VIBRATION_PATTERN = {500,100,100,100,100,100};

    private MonitorService mService = null;

    private Settings mSettings = null;

    private Notification mServiceNotification = null;
    private Notification mNoiseNotification = null;
    private NotificationManager mNotificationManager;

    private boolean mUseNoiseNotification = true;

    public NotificationHelper(MonitorService service) {
        mService = service;
        mNotificationManager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);

        mSettings = Settings.getInstance(service);

        mService.addOnTxModeChangedListener(new MonitorService.OnTxModeChangedListener() {
            @Override
            public void onTXModeChanged(MonitorService service, boolean isTxMode) {
                buildAndDisplayNotification();
            }
        });

        mService.addOnConnectionStatusListener(new MonitorService.OnConnectionStatusListener() {
            @Override
            public void onConnected(MonitorService service) {
                buildAndDisplayNotification();
            }

            @Override
            public void onDisconnected(MonitorService service) {
                buildAndDisplayNotification();
            }

            @Override
            public void onConnectionError(MonitorService service, String message, boolean reconnecting) {
                buildAndDisplayNotification();
            }
        });

        mService.getNoiseTracker().addOnNoiseListener(new NoiseTracker.OnNoiseListener() {
            @Override
            public void onNoiseStart(MonitorService service) {
                buildAndDisplayNotification();
            }

            @Override
            public void onNoiseStop(MonitorService service) {
                buildAndDisplayNotification();
            }
        });
    }

    public void runInForeground() {
        // Run in foreground so we're never killed
        buildAndDisplayNotification();
        mService.startForeground(ONGOING_NOTIFICATION_ID, mServiceNotification);
    }

    /**
     * Utility to actually build correct notifications for mode (tx/rx)
     * @return Notification displayed
     */
    private void buildAndDisplayNotification() {
        rebuildServiceNotification();
    }

    private Notification rebuildServiceNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService);

        // Title for mode
        if(mService.isTransmitterMode()) {
            builder.setContentTitle("Monitor - Transmitter");
        }
        else {
            builder.setContentTitle("Monitor - Receiver");
        }

        // By default, everyone uses this stuff
        builder.setSmallIcon(R.drawable.ic_notify);
        builder.setOngoing(true);

        // Open status activity on click
        // FLAG_CANCEL_CURRENT ensures that the extra always gets sent.
        Intent statusIntent = statusIntent = new Intent(mService, ClientStatus.class);
        statusIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(mService, 0, statusIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);

        if(mService.isConnected()) {
            if(mService.isThereNoise()) {
                builder.setContentText("A transmitter detected noise");
                builder.setSmallIcon(R.drawable.ic_notify_wsound);

                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);

                // Additional notification pieces... sound, vibration, lights
                if(mSettings.isVibrationOn())
                    builder.setVibrate(NOTIFICATION_VIBRATION_PATTERN);
                if(mSettings.isLEDOn())
                    builder.setLights(Color.BLUE, 1000, 1000);
                if(!mService.shouldPlayFullAudio()) {
                    Uri sound = mSettings.getNotificationSound();
                    builder.setSound(sound);
                }
            }
            else {
                builder.setContentText("Connected, no noise");
            }
        }
        else {
            // Specify type of "not connected"
            if(mService.isWaitingToConnect()) {
                builder.setContentText("Waiting for allowed network");
            }
            else if(mService.isReconnecting()) {
                builder.setContentText("Reconnecting");
            }
            else {
                builder.setContentText("Not connected");
            }


        }

        // Control buttons
        //builder.addAction();

        mServiceNotification = builder.build();
        mNotificationManager.notify(ONGOING_NOTIFICATION_ID, mServiceNotification);

        return mServiceNotification;
    }
}
