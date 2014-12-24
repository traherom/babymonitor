package com.moreharts.babymonitor.service;

import android.os.Handler;
import android.os.SystemClock;

import com.morlunk.jumble.model.User;

import java.util.ArrayList;

/**
 * Track when a user is talking. Only consider
 * a user done mNoiseTimeoutLength seconds after the last time they were heard
 * Created by traherom on 11/22/2014.
 */
public class NoiseTracker {
    public static final long DEFAULT_NOISE_TIMEOUT_LENGTH = 5 * 1000;

    private MonitorService mService = null;

    // Perhaps get this from settings?
    private long mNoiseTimeoutLength = DEFAULT_NOISE_TIMEOUT_LENGTH;

    private boolean mThereIsNoise = false;
    private long mLocalAbsoluteLastNoiseHeard = SystemClock.elapsedRealtime();
    private long mRemoteAbsoluteLastNoiseHeard = SystemClock.elapsedRealtime();

    private Handler mHandler = new Handler();
    private MonitorService.OnUserStateListener mUserStateListener = new MonitorService.OnUserStateListener() {
        @Override
        public void onUserTalk(MonitorService service, User user) {
            long curr = SystemClock.elapsedRealtime();
            mLocalAbsoluteLastNoiseHeard = curr;
        }
    };

    private RemoteMonitorTracker.OnRemoteMonitorUpdateListener mRemoteListener = new RemoteMonitorTracker.OnRemoteMonitorUpdateListener() {
        @Override
        public void onRemoteMonitorAdded(RemoteMonitorTracker.MonitorState monitor) {
            // Ignore
        }

        @Override
        public void onRemoteMonitorRemoved(RemoteMonitorTracker.MonitorState monitor) {
            // Ignore
        }

        @Override
        public void onRemoteMonitorUpdated(RemoteMonitorTracker.MonitorState monitor) {
            long curr = SystemClock.elapsedRealtime();

            // Are they making noise that's newer than the latest we've heard?
            if(curr - monitor.getLastNoiseHeard() > mRemoteAbsoluteLastNoiseHeard) {
                mRemoteAbsoluteLastNoiseHeard = curr - monitor.getLastNoiseHeard();
            }

            checkForNoiseStateChange(curr, mRemoteAbsoluteLastNoiseHeard);
        }
    };

    private Runnable mLocalNoiseRunnable = new Runnable() {
        @Override
        public void run() {
            // Determine if there is a change to the noise state and refresh notification
            // It would be more accurate to refresh the notification immediately in onUserTalk
            // for when noise is heard, but it would lead to a lot of unnecessary rebuilding
            checkForNoiseStateChange(SystemClock.elapsedRealtime(), mLocalAbsoluteLastNoiseHeard);

            // Run again soon
            mHandler.postDelayed(this, DEFAULT_NOISE_TIMEOUT_LENGTH/2);
        }
    };

    // Listeners
    private ArrayList<OnNoiseListener> mListeners = new ArrayList<OnNoiseListener>();

    public NoiseTracker(MonitorService service) {
        mService = service;

        // Run the correct components now
        if(service.isTransmitterMode()) {
            startTx();
        }
        else {
            startRx();
        }

        // Register to stop and start ourselves as appropriate
        service.addOnTxModeChangedListener(new MonitorService.OnTxModeChangedListener() {
            @Override
            public void onTXModeChanged(MonitorService service, boolean isTxMode) {
                if(isTxMode) {
                    stopRx();
                    startTx();
                }
                else {
                    stopTx();
                    startRx();
                }
            }
        });
    }

    private void startTx() {
        // Perform our own tracking of noise
        mHandler.postDelayed(mLocalNoiseRunnable, 1000);
        getService().addOnUserStateListener(mUserStateListener);
    }

    private void stopTx() {
        mHandler.removeCallbacks(mLocalNoiseRunnable);
        getService().removeOnUserStateListener(mUserStateListener);
    }

    private void startRx() {
        // Keep trying to do set up until we succeed. (waiting for remote monitor to exist)
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mService.getRemoteMonitorTracker() != null)
                    mService.getRemoteMonitorTracker().addOnRemoteMonitorUpdateListener(mRemoteListener);
                else
                    mHandler.postDelayed(this, 500);
            }
        }, 100);
    }

    private void stopRx() {
        mService.getRemoteMonitorTracker().removeOnRemoteMonitorUpdateListener(mRemoteListener);
    }

    private synchronized void checkForNoiseStateChange(long currentTime, long last) {
        long diff = currentTime - last;

        boolean isNoise = (diff < mNoiseTimeoutLength);

        if(isNoise != mThereIsNoise) {
            // We're hearing noise and we weren't before OR
            // we have no noise and we were before. IE, there was
            // a change, so rebuild
            mThereIsNoise = isNoise;

            if(mThereIsNoise) {
                // First noise heard in a while
                notifyOnNoiseStart();
            }
            else {
                // No more noise
                notifyOnNoiseStop();
            }
        }
    }

    public MonitorService getService() {
        return mService;
    }

    /**
     * Returns if there is noise locally if we are a transmitter or remotely if we are
     * a receiver.
     */
    public boolean isThereNoise() {
        if(mService.isTransmitterMode())
            return isThereRemoteNoise();
        else
            return isThereLocalNoise();
    }

    public boolean isThereLocalNoise() {
        return mThereIsNoise;
    }

    public boolean isThereRemoteNoise() {
        for(RemoteMonitorTracker.MonitorState state : mService.getRemoteMonitorTracker()) {
            if(state.getLastNoiseHeard() < mNoiseTimeoutLength)
                return true;
        }

        return false;
    }

    public long getLastNoiseHeard() {
        if(mService.isTransmitterMode()) {
            return SystemClock.elapsedRealtime() - mLocalAbsoluteLastNoiseHeard;
        }
        else {
            return SystemClock.elapsedRealtime() - mRemoteAbsoluteLastNoiseHeard;
        }
    }

    /**
     * Converts the given time in milliseconds to a human-friendly format of
     * seconds, minutes, hours, etc as appropriate
     */
    public static String millisecondsToHuman(long milli) {
        final long SECONDS_CONV = 1000;
        final long MINUTES_CONV = SECONDS_CONV * 60;
        final long HOURS_CONV = MINUTES_CONV * 60;

        StringBuilder sb = new StringBuilder();
        if(milli < MINUTES_CONV) {
            sb.append(milli / SECONDS_CONV);
            sb.append(" second");
            if(milli != SECONDS_CONV)
                sb.append('s');
        }
        else if(milli < HOURS_CONV) {
            sb.append(milli / MINUTES_CONV);
            sb.append(" minute");
            if(milli != MINUTES_CONV)
                sb.append('s');
        }
        else {
            sb.append(milli / HOURS_CONV);
            sb.append(" hour");
            if(milli != HOURS_CONV)
                sb.append('s');
        }

        sb.append(" ago");

        return sb.toString();
    }

    // Listener management
    public void addOnNoiseListener(OnNoiseListener listener) {
        mListeners.add(listener);
    }

    public void removeOnNoiseListener(OnNoiseListener listener) {
        mListeners.remove(listener);
    }

    protected void notifyOnNoiseStart() {
        for(OnNoiseListener listener : mListeners) {
            listener.onNoiseStart(mService);
        }
    }

    protected void notifyOnNoiseStop() {
        for(OnNoiseListener listener : mListeners) {
            listener.onNoiseStop(mService);
        }
    }

    public interface OnNoiseListener {
        public void onNoiseStart(MonitorService service);
        public void onNoiseStop(MonitorService service);
    }
}
