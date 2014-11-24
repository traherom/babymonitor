package com.moreharts.babymonitor.service;

import android.os.RemoteException;
import android.os.SystemClock;

import com.morlunk.jumble.model.Message;
import com.morlunk.jumble.model.User;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Track when a user is talking. Only consider
 * a user done mNoiseTimeoutLength seconds after the last time they were heard
 * Created by traherom on 11/22/2014.
 */
public class NoiseTracker {
    public static final long DEFAULT_NOISE_TIMEOUT_LENGTH = 5 * 1000;

    // Perhaps get this from settings?
    private long mNoiseTimeoutLength = DEFAULT_NOISE_TIMEOUT_LENGTH;
    private MonitorService mService = null;

    Timer mTimer = new Timer();

    private boolean mThereIsNoise = false;
    private long mLastNoiseHeard = 0;

    // Listeners
    private ArrayList<MonitorService.OnNoiseListener> mListeners = new ArrayList<MonitorService.OnNoiseListener>();

    public NoiseTracker(MonitorService service) {
        mService = service;

        // Perform our own tracking of noise
        mTimer.schedule(new TimerTask() {
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

                    if(mThereIsNoise) {
                        // First noise heard in a while
                        notifyOnNoiseStart();

                        if(mService.isTransmitterMode()) {
                            try {
                                mService.sendChannelMessage(MonitorService.RESP_NOISE_ON);
                            }
                            catch(RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    else {
                        // No more noise
                        notifyOnNoiseStop();

                        if(mService.isTransmitterMode()) {
                            try {
                                mService.sendChannelMessage(MonitorService.RESP_NOISE_OFF);
                            }
                            catch(RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                // If we are a receiver, periodically ensure we are in sync with the tx
                if(!mService.isTransmitterMode()) {
                    try {
                        mService.sendChannelMessage(MonitorService.CMD_NOISE_STATE);
                    }
                    catch(RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, 1000, 5000);

        mService.addOnUserStateListener(new MonitorService.OnUserStateListener() {
            @Override
            public void onUserTalk(MonitorService service, User user) {
                mLastNoiseHeard = SystemClock.elapsedRealtime();
            }
        });

        // If we are deafened, rely on others sending out messages to know when noise occurs
        // Only do so if we're a receiver... transmitters don't care
        // TODO this has the issue of it won't refresh when new noise comes in
        mService.addOnMessageHandler(new MonitorService.OnMessageReceivedListener() {
            @Override
            public void onMessageReceived(MonitorService service, Message msg) {
                if(service.isTransmitterMode())
                    return;

                if(msg.getMessage().trim().equals(MonitorService.RESP_NOISE_ON)) {
                    mLastNoiseHeard = SystemClock.elapsedRealtime();
                }
            }
        });
    }

    public boolean isThereNoise() {
        return mThereIsNoise;
    }

    public long lastNoiseHeard() {
        return mLastNoiseHeard;
    }

    // Listener management
    public void addOnNoiseListener(MonitorService.OnNoiseListener listener) {
        mListeners.add(listener);
    }

    public void removeOnNoiseListener(MonitorService.OnNoiseListener listener) {
        mListeners.remove(listener);
    }

    private void notifyOnNoiseStart() {
        for(MonitorService.OnNoiseListener listener : mListeners) {
            listener.onNoiseStart(mService);
        }
    }

    private void notifyOnNoiseStop() {
        for(MonitorService.OnNoiseListener listener : mListeners) {
            listener.onNoiseStop(mService);
        }
    }
}
