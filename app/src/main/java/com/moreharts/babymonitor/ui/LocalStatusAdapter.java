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

import com.moreharts.babymonitor.preferences.Settings;
import com.moreharts.babymonitor.service.MonitorService;
import com.moreharts.babymonitor.service.NoiseTracker;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;

/**
 * Created by traherom on 10/26/2014.
 */
public class LocalStatusAdapter implements ListAdapter {
    public static final String TAG = "LocalStatusAdapter";

    public static final int REFRESH_TIME = 5000;

    private DataSetObservable mObservers = new DataSetObservable();
    private Handler mHandler = new Handler();
    private Settings mSettings = null;
    private MonitorService mService = null;
    private LayoutInflater inflater = null;

    public LocalStatusAdapter(Context context) {
        inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Watch for changes we care about
        mSettings = Settings.getInstance(context);
        mSettings.addOnChangeListener(new Settings.OnChangeListener() {
            @Override
            public void onChange(Settings settings) {
                forceRefresh();
            }
        });
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
        }
        else {
            // We already had a service, so unregister from them first
            try {
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
        mObservers.notifyChanged();
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

    private int getCountTransmitter() {
        return 4;
    }

    private int getCountReceiver() {
        return 3;
    }

    @Override
    public int getCount() {
        if(mService == null) {
            return 1;
        }
        else if(mService.isTransmitterMode()) {
            return getCountTransmitter();
        }
        else {
            return getCountReceiver();
        }
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
        return true;
    }

    private View createTwoLineListItemView(View convertView, String line1, String line2) {
        View view;
        TextView primaryLine;
        TextView secondaryLine;

        if(convertView == null) {
            // Need to make the view
            view = inflater.inflate(android.R.layout.two_line_list_item, null);
        }
        else {
            view = convertView;
        }

        // Get pieces
        primaryLine = (TextView)view.findViewById(android.R.id.text1);
        secondaryLine = (TextView)view.findViewById(android.R.id.text2);

        primaryLine.setText(line1);
        secondaryLine.setText(line2);

        return view;
    }

    private View getViewForMode(View convertView) {
        String line1 = null;
        String line2 = null;

        if(mService.isTransmitterMode()) {
            line1 = "Transmitter Mode";
        }
        else  {
            line1 = "Receiver Mode";
        }

        if(mService.isConnected()) {
            StringBuilder sb = new StringBuilder();

            sb.append(mService.getUser());
            sb.append(" on ");
            sb.append(mService.getHost());

            line2 = sb.toString();
        }
        else if(mService.getPendingConnectionInfo() == null) {
            line2 = "No connection info";
        }
        else if(!mService.connectionAllowed()) {
            line2 = "Waiting for allowed network";
        }
        else if(mService.isReconnecting()) {
            line2 = "Reconnecting";
        }
        else {
            line2 = "Not connected";
        }

        return createTwoLineListItemView(convertView, line1, line2);
    }

    private View getViewForConnectionTypes(View convertView) {
        String line1 = null;
        String line2 = null;

        line1 = "Connection Types";

        boolean mobile = mSettings.getMobileEnabled();
        boolean wifi = mSettings.getWifiEnabled();

        if(mobile && wifi)
            line2 = "All";
        else if(wifi)
            line2 = "Wifi only";
        else if(mobile)
            line2 = "Mobile only";
        else
            line2 = "None";

        return createTwoLineListItemView(convertView, line1, line2);
    }

    private View getViewTransmitter(int pos, View convertView, ViewGroup viewGroup) {
        String line1 = null;
        String line2 = null;

        switch(pos) {
            case 0:
                return getViewForMode(convertView);

            case 1:
                return getViewForConnectionTypes(convertView);

            case 2:
                line1 = "Audio Threshold";
                line2 = Float.toString(mService.getVADThreshold());
                break;

            case 3:
                line1 = "Last Noise";
                line2 = NoiseTracker.millisecondsToHuman(mService.getNoiseTracker().getLastNoiseHeard());
                break;

            default:
                line1 = "Invalid position";
                line2 = Integer.toString(pos);
        }

        return createTwoLineListItemView(convertView, line1, line2);
    }

    private View getViewReceiver(int pos, View convertView, ViewGroup viewGroup) {
        String line1 = null;
        String line2 = null;

        switch(pos) {
            case 0:
                return getViewForMode(convertView);

            case 1:
                return getViewForConnectionTypes(convertView);

            case 2:
                line1 = "Audio Mode";

                boolean isWifi = mService.isWifiNetwork();

                if((mService.isWifiNetwork() && mSettings.getWifiFullAudioOn()) || (!mService.isMobileNetwork() && mSettings.getMobileFullAudioOn())) {
                    line2 = "Full audio";
                }
                else {
                    line2 = "Notification sound only";
                }

                break;

            default:
                line1 = "Invalid position";
                line2 = Integer.toString(pos);
        }

        return createTwoLineListItemView(convertView, line1, line2);
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup viewGroup) {
        if(mService == null) {
            return createTwoLineListItemView(convertView, "Mode", "Service not running");
        }
        else if(mService.isTransmitterMode()) {
            return getViewTransmitter(pos, convertView, viewGroup);
        }
        else {
            return getViewReceiver(pos, convertView, viewGroup);
        }
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

        @Override
        public void onUserJoinedChannel(User user, Channel newChannel, Channel oldChannel) throws RemoteException {
            mObservers.notifyChanged();
        }

        @Override
        public void onUserRemoved(User user, String reason) throws RemoteException {
            mObservers.notifyChanged();
        }
    };
}
