package com.moreharts.babymonitor.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.ui.ClientStatus;
import com.morlunk.jumble.model.User;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages MonitorService notifications
 * Created by traherom on 11/22/2014.
 */
public class NotificationDisplay {
    public static final int ONGOING_NOTIFICATION_ID = 2324;
    public static final long DEFAULT_NOISE_TIMEOUT_LENGTH = 10 * 1000;

    private MonitorService mService = null;

    private Notification mNotification;
    private NotificationManager mNotificationManager;

    // Perhaps get this from settings?
    private long mNoiseTimeoutLength = DEFAULT_NOISE_TIMEOUT_LENGTH;

    /**
     * Track when a user is talking and display a special note in the notification. Only consider
     * a user done mNoiseTimeoutLength seconds after the last time they were heard
     */
    private NoiseTimeoutListener mUserStateListener = new NoiseTimeoutListener();
    private class NoiseTimeoutListener extends TimerTask implements MonitorService.OnUserStateListener {
        private long mLastNoiseHeard = 0;
        private boolean mNotificationShowsNoise = false;
        private boolean mThereIsNoise = false;

        Timer mTimer = new Timer();

        public NoiseTimeoutListener() {
            mTimer.schedule(this, 1000, 5000);
        }

        public boolean isThereNoise() {
            return mThereIsNoise;
        }

        @Override
        public void run() {
            // Determine if there is a change to the noise state and refresh notification
            // It would be more accurate to refresh the notification immediately in onUserTalk
            // for when noise is heard, but it would lead to a lot of unnecessary rebuilding
            long diff = SystemClock.elapsedRealtime() - mLastNoiseHeard;
            boolean isNoise = (diff < mNoiseTimeoutLength);

            if(isNoise != mThereIsNoise) {
                // We're hearing noise and we weren't before OR
                // we have no noise and we were before. IE, there was
                // a change, so rebuild
                mThereIsNoise = isNoise;
                rebuildNotification();
            }
        }

        @Override
        public void onUserTalk(MonitorService service, User user) {
            mLastNoiseHeard = SystemClock.elapsedRealtime();
        }
    }

    public NotificationDisplay(MonitorService service) {
        mService = service;
        mNotificationManager = (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);

        mService.addOnConnectionStatusListener(new MonitorService.OnConnectionStatusListener() {
            @Override
            public void onConnected(MonitorService service) {
                rebuildNotification();
            }

            @Override
            public void onDisconnected(MonitorService service) {
                rebuildNotification();
            }

            @Override
            public void onConnectionError(MonitorService service, String message, boolean reconnecting) {
                rebuildNotification();
            }
        });

        mService.addOnUserStateListener(mUserStateListener);
    }

    public void runInForeground() {
        // Run in foreground so we're never killed
        rebuildNotification();
        mService.startForeground(ONGOING_NOTIFICATION_ID, mNotification);
    }

    private Notification rebuildNotification() {
        // Use notification to display number of people monitoring
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService);

        if(mService.isTransmitterMode()) {
            builder.setContentTitle("Monitor - Transmitter");
        }
        else {
            builder.setContentTitle("Monitor - Receiver");
        }

        if(mService.isConnected()) {
            if(mUserStateListener.isThereNoise()) {
                builder.setContentText("Noise! (connected)");
            }
            else {
                builder.setContentText("No noise (connected)");
            }
        }
        else if(mService.isReconnecting()) {
            builder.setContentText("Reconnecting");
        }
        else {
            builder.setContentText("Disconnected");
        }

        builder.setSmallIcon(R.drawable.notification_icon);
        builder.setOngoing(true);

        // Open status activity on click
        // FLAG_CANCEL_CURRENT ensures that the extra always gets sent.
        Intent statusIntent = statusIntent = new Intent(mService, ClientStatus.class);
        statusIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(mService, 0, statusIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);

        // Control buttons
        //builder.addAction();

        mNotification = builder.build();
        mNotificationManager.notify(ONGOING_NOTIFICATION_ID, mNotification);

        return mNotification;
    }
}
