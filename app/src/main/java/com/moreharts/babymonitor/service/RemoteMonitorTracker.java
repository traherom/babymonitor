package com.moreharts.babymonitor.service;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Keeps track of
 * Created by traherom on 11/26/2014.
 */
public class RemoteMonitorTracker {
    public static final String TAG = "RemoteMonitorTracker";

    private class MonitorState {
        String user = null;
        long lastUpdateReceived;

        // Actual data
        boolean isTx = true;
        float threshold = -1;
        long lastNoiseHeard = -1;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("User: ");
            sb.append(user);

            if(isTx)
                sb.append(" TX ");
            else
                sb.append(" RX ");

            sb.append("Threshold: ");
            sb.append(threshold);

            return sb.toString();
        }
    }

    private HashMap<String, MonitorState> mRemoteMonitors = new HashMap<String, MonitorState>();

    private MonitorService mService = null;

    public RemoteMonitorTracker(MonitorService service) {
        mService = service;

        mService.getTextMessageManager().addOnStateMessageListener(new TextMessageManager.OnStateMessageListener() {
            @Override
            public void onStateMessageReceived(MonitorService service, String user, boolean isTxMode, float threshold, long lastNoiseHeard) {
                MonitorState state = new MonitorState();
                state.lastUpdateReceived = SystemClock.elapsedRealtime();
                state.user = user;

                state.isTx = isTxMode;
                state.threshold = threshold;
                state.lastNoiseHeard = lastNoiseHeard;

                mRemoteMonitors.put(user, state);
            }
        });
    }

    public void printRemoteState() {
        for(MonitorState it : mRemoteMonitors.values()) {
            Log.d(TAG, it.toString());
        }
    }
}
