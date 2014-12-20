package com.moreharts.babymonitor.service;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.moreharts.babymonitor.R;

import java.util.ArrayList;

/**
 * Keeps track of
 * Created by traherom on 11/26/2014.
 */
public class RemoteMonitorTracker implements SpinnerAdapter {
    public static final String TAG = "RemoteMonitorTracker";

    /**
     * After MONITOR_TIMEOUT milliseconds, a monitor we have not heard from is considered gone
     */
    public static long MONITOR_TIMEOUT = TextMessageManager.BROADCAST_STATE_DELAY * 3;

    private ArrayList<OnRemoteMonitorUpdateListener> mListeners = new ArrayList<OnRemoteMonitorUpdateListener>();
    private DataSetObservable mObservers = new DataSetObservable();
    private LayoutInflater mInflater = null;

    private ArrayList<MonitorState> mRemoteMonitors = new ArrayList<MonitorState>();
    public class MonitorState {
        private int id;

        private String mUser = null;
        private long mLastUpdateReceived;

        // Actual data
        private boolean mIsTx = true;
        private float mThreshold = -1;
        private long mLastNoiseHeard = -1;

        public MonitorState(String user) {
            mUser = user;
        }

        public String getUser() {
            return mUser;
        }

        public void setIsTx(boolean isTx) {
            mIsTx = true;
        }

        public boolean getIsTx() {
            return mIsTx;
        }

        public void setThreshold(float threshold) {
            mThreshold = threshold;
        }

        public float getThreshold() {
            return mThreshold;
        }

        public void setLastNoiseHeard(long last) {
            mLastNoiseHeard = last;
        }

        public long getLastNoiseHeard() {
            return mLastNoiseHeard;
        }

        public void setLastUpdate(long last) {
            mLastUpdateReceived = last;
        }

        public long getLastUpdate() {
            return mLastUpdateReceived;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("User: ");
            sb.append(mUser);

            if(mIsTx)
                sb.append(" TX ");
            else
                sb.append(" RX ");

            sb.append("Threshold: ");
            sb.append(mThreshold);

            return sb.toString();
        }
    }

    private MonitorService mService = null;
    private Handler mHandler = new Handler();

    public RemoteMonitorTracker(MonitorService service) {
        mService = service;
        mInflater = (LayoutInflater)mService.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mService.getTextMessageManager().addOnStateMessageListener(new TextMessageManager.OnStateMessageListener() {
            @Override
            public void onStateMessageReceived(MonitorService service, String user, boolean isTxMode, float threshold, long lastNoiseHeard) {
                boolean isAdd = false;
                MonitorState state = getMonitor(user);
                if(state == null) {
                    isAdd = true;
                    state = new MonitorState(user);
                    mRemoteMonitors.add(state);
                }

                state.setLastUpdate(SystemClock.elapsedRealtime());

                state.setIsTx(isTxMode);
                state.setThreshold(threshold);
                state.setLastNoiseHeard(lastNoiseHeard);

                if(isAdd) {
                    notifyOnRemoteMonitorAdded(state);
                }
                else {
                    notifyOnRemoteMonitorUpdated(state);
                }

                mObservers.notifyChanged();
            }
        });

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long tooLongAgo = SystemClock.elapsedRealtime() - MONITOR_TIMEOUT;

                for(MonitorState monitor : mRemoteMonitors) {
                    if(monitor.getLastNoiseHeard() <= tooLongAgo) {
                        Log.d(TAG, monitor.getUser() + " has timed out");
                        mRemoteMonitors.remove(monitor.getUser());
                        notifyOnRemoteMonitorRemoved(monitor);
                    }
                }

                mHandler.postDelayed(this, MONITOR_TIMEOUT);
            }
        }, MONITOR_TIMEOUT);
    }

    public void printRemoteState() {
        for(MonitorState it : mRemoteMonitors) {
            Log.d(TAG, it.toString());
        }
    }

    public MonitorState getMonitor(String name) {
        for(MonitorState monitor : mRemoteMonitors) {
            if(monitor.getUser().equals(name))
                return monitor;
        }
        return null;
    }

    public int getMonitorIndex(String name) {
        for(int i = 0; i < mRemoteMonitors.size(); i++) {
            if(mRemoteMonitors.get(i).getUser().equals(name))
                return i;
        }
        return -1;
    }

    @Override
    public View getDropDownView(int i, View view, ViewGroup viewGroup) {
        return getView(i, view, viewGroup);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver dataSetObserver) {
        mObservers.registerObserver(dataSetObserver);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
        mObservers.unregisterObserver(dataSetObserver);
    }

    @Override
    public int getCount() {
        return mRemoteMonitors.size();
    }

    @Override
    public Object getItem(int i) {
        return mRemoteMonitors.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        View view = null;
        TextView primaryLine;

        if(convertView == null) {
            // Need to make the view
            view = mInflater.inflate(android.R.layout.simple_spinner_dropdown_item, null);
        }
        else {
            view = convertView;
        }

        primaryLine = (TextView)view.findViewById(android.R.id.text1);
        primaryLine.setText(mRemoteMonitors.get(i).getUser());

        return primaryLine;
    }

    @Override
    public int getItemViewType(int i) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }

    public void addOnRemoteMonitorUpdateListener(OnRemoteMonitorUpdateListener listener) {
        mListeners.add(listener);
    }

    public void removeOnRemoteMonitorUpdateListener(OnRemoteMonitorUpdateListener listener) {
        mListeners.remove(listener);
    }

    private void notifyOnRemoteMonitorAdded(MonitorState monitor) {
        for(OnRemoteMonitorUpdateListener listener : mListeners) {
            listener.onRemoteMonitorAdded(monitor);
        }
    }

    private void notifyOnRemoteMonitorRemoved(MonitorState monitor) {
        for(OnRemoteMonitorUpdateListener listener : mListeners) {
            listener.onRemoteMonitorRemoved(monitor);
        }
    }

    private void notifyOnRemoteMonitorUpdated(MonitorState monitor) {
        for(OnRemoteMonitorUpdateListener listener : mListeners) {
            listener.onRemoteMonitorUpdated(monitor);
        }
    }

    public interface OnRemoteMonitorUpdateListener {
        public void onRemoteMonitorAdded(MonitorState monitor);
        public void onRemoteMonitorRemoved(MonitorState monitor);
        public void onRemoteMonitorUpdated(MonitorState monitor);
    }
}
