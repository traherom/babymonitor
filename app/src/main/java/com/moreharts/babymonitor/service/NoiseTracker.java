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
    private long mAbsoluteLastNoiseHeard = -1;

    private Handler mHandler = new Handler();
    private MonitorService.OnUserStateListener mUserStateListener = new MonitorService.OnUserStateListener() {
        @Override
        public void onUserTalk(MonitorService service, User user) {
            mAbsoluteLastNoiseHeard = SystemClock.elapsedRealtime();
        }
    };

    private TextMessageManager.OnStateMessageListener mStateListener = new TextMessageManager.OnStateMessageListener() {
        @Override
        public void onStateMessageReceived(MonitorService service, String user, boolean isTxMode, float threshold, long relativeLastNoiseHeard) {
            // New noise from this tx?
            if(isTxMode) {
                long currentTime = SystemClock.elapsedRealtime();

                // There could be multiple TXs out there. Our last noise heard is based
                // on the most recent noise. Also, lastNoiseHeard in this context is based on
                long convertedLastHeard = currentTime - relativeLastNoiseHeard;
                if(mAbsoluteLastNoiseHeard < convertedLastHeard) {
                    mAbsoluteLastNoiseHeard = convertedLastHeard;
                }

                checkForNoiseStateChange(currentTime);
            }
        }
    };

    private Runnable mTxNoiseRunnable = new Runnable() {
        @Override
        public void run() {
            // Determine if there is a change to the noise state and refresh notification
            // It would be more accurate to refresh the notification immediately in onUserTalk
            // for when noise is heard, but it would lead to a lot of unnecessary rebuilding
            checkForNoiseStateChange(SystemClock.elapsedRealtime());

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
        mHandler.postDelayed(mTxNoiseRunnable, 1000);
        getService().addOnUserStateListener(mUserStateListener);
    }

    private void stopTx() {
        mHandler.removeCallbacks(mTxNoiseRunnable);
        getService().removeOnUserStateListener(mUserStateListener);
    }

    private void startRx() {
        mService.getTextMessageManager().addOnStateMessageListener(mStateListener);
    }

    private void stopRx() {
        mService.getTextMessageManager().removeOnStateMessageListener(mStateListener);
    }

    private void checkForNoiseStateChange(long currentTime) {
        long diff = currentTime - mAbsoluteLastNoiseHeard;
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

    public boolean isThereNoise() {
        return mThereIsNoise;
    }

    public long getLastNoiseHeard() {
        if(mAbsoluteLastNoiseHeard < 0)
            return -1;

        return SystemClock.elapsedRealtime() - mAbsoluteLastNoiseHeard;
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
