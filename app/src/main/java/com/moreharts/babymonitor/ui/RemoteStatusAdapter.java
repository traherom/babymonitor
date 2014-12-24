package com.moreharts.babymonitor.ui;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.moreharts.babymonitor.service.MonitorService;
import com.moreharts.babymonitor.service.NoiseTracker;
import com.moreharts.babymonitor.service.RemoteMonitorTracker;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;

/**
 * Created by traherom on 10/26/2014.
 */
public class RemoteStatusAdapter implements ListAdapter {
    public static final String TAG = "RemoteStatusAdapter";

    public static final int REFRESH_TIME = 5000;

    public static final int USERNAME_LINE = 0;
    public static final int THRESHOLD_LINE = 1;
    public static final int NOISE_DETECTED_LINE = 2;
    public static final int LINE_COUNT = NOISE_DETECTED_LINE + 1;

    private DataSetObservable mObservers = new DataSetObservable();
    private Handler mHandler = new Handler();
    private MonitorService mService = null;
    private LayoutInflater mInflater = null;

    private String mMonitorOfInterest = null;
    private RemoteMonitorTracker.MonitorState mState = null;

    public RemoteStatusAdapter(Context context) {
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setService(MonitorService service) {
        if(service == mService)
            return;

        if(service != null) {
            try {
                service.getBinder().registerObserver(mJumbleObserver);
            }
            catch(RemoteException e) {
                e.printStackTrace();
            }

            service.getRemoteMonitorTracker().addOnRemoteMonitorUpdateListener(new RemoteMonitorTracker.OnRemoteMonitorUpdateListener() {
                @Override
                public void onRemoteMonitorAdded(RemoteMonitorTracker.MonitorState monitor) {
                    forceRefresh();
                }

                @Override
                public void onRemoteMonitorRemoved(RemoteMonitorTracker.MonitorState monitor) {
                    forceRefresh();
                }

                @Override
                public void onRemoteMonitorUpdated(RemoteMonitorTracker.MonitorState monitor) {
                    forceRefresh();
                }
            });
        }
        else {
            // We already had a service, so unregister from them first
            try {
                //mService.getRemoteMonitorTracker().removeOnRemoteMonitorUpdateListener();
                mService.getBinder().unregisterObserver(mJumbleObserver);
            }
            catch(RemoteException e) {
                e.printStackTrace();
            }
        }

        // Force a refresh on our display periodically
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                forceRefresh();
                mHandler.postDelayed(this, REFRESH_TIME);
            }
        }, REFRESH_TIME);

        // Do the swap at the end so we still have access to the old service
        mService = service;
        forceRefresh();
    }

    public void setMonitorState(RemoteMonitorTracker.MonitorState state) {
        mState = state;
        forceRefresh();
    }

    public void forceRefresh() {
        // Do refresh on the main thread
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                mObservers.notifyChanged();
            }
        });
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int i) {
        return true;
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
        if(mService != null && mService.isConnected() && mState != null)
            return LINE_COUNT;
        else
            return 0;
    }

    @Override
    public Object getItem(int i) {
        return i;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup viewGroup) {
        View view;
        TextView primaryLine;
        TextView secondaryLine;

        if(convertView == null) {
            // Need to make the view
            view = mInflater.inflate(android.R.layout.two_line_list_item, null);
        }
        else {
            view = convertView;
        }

        // Get pieces
        primaryLine = (TextView)view.findViewById(android.R.id.text1);
        secondaryLine = (TextView)view.findViewById(android.R.id.text2);

        // If we don't have a service, the only line we should display is
        // the top one, where we'll say we don't have anything to work with
        if(mService == null) {
            primaryLine.setText("Error");
            secondaryLine.setText("Service not running");
            return view;
        }

        switch(pos) {
            case THRESHOLD_LINE:
                primaryLine.setText("Activity Threshold");
                break;
            case USERNAME_LINE:
                primaryLine.setText("User name");
                break;
            case NOISE_DETECTED_LINE:
                primaryLine.setText("Last Noise Heard");
                break;
            default:
                primaryLine.setText("Invalid position");
        }

        if(mState != null) {
            switch (pos) {
                case THRESHOLD_LINE:
                    secondaryLine.setText(Float.toString(mState.getThreshold()));
                    break;

                case USERNAME_LINE:
                    secondaryLine.setText(mState.getUser());
                    break;

                case NOISE_DETECTED_LINE:
                    secondaryLine.setText(NoiseTracker.millisecondsToHuman(mState.getLastNoiseHeard()));
                    break;

                default:
                    secondaryLine.setText("Invalid position");
            }
        }
        else {
            secondaryLine.setText("None selected");
        }

        return view;
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

    /** Watch for changes on the service */
    private JumbleObserver mJumbleObserver = new JumbleObserver() {
        @Override
        public void onConnected() throws RemoteException {
            mObservers.notifyChanged();
        }

        @Override
        public void onDisconnected() throws RemoteException {
            mObservers.notifyChanged();
        }

        @Override
        public void onUserTalkStateUpdated(User user) throws RemoteException {
            mObservers.notifyChanged();
        }
    };
}
