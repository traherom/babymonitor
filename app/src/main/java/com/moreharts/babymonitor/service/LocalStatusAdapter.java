package com.moreharts.babymonitor.service;

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

import com.moreharts.babymonitor.R;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.model.Channel;
import com.morlunk.jumble.model.User;
import com.morlunk.jumble.util.JumbleObserver;

/**
 * Created by traherom on 10/26/2014.
 */
public class LocalStatusAdapter implements ListAdapter {
    public static final String TAG = "ServiceStatusAdapter";

    public static final int MODE_LINE = 0;
    public static final int CONNECTED_LINE = 1;
    public static final int CHANNEL_LINE = 2;
    public static final int TALK_LINE = 3;
    public static final int THRESHOLD_LINE = 4;
    public static final int USERNAME_LINE = 5;

    private DataSetObservable mObservers = new DataSetObservable();
    private MonitorService mService = null;
    private LayoutInflater inflater = null;

    public LocalStatusAdapter(Context context) {
        inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
        Log.d(TAG, "Registering dataset observer");
        mObservers.registerObserver(dataSetObserver);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
        Log.d(TAG, "Unregister dataset observer");
        mObservers.unregisterObserver(dataSetObserver);
    }

    @Override
    public int getCount() {
        return 6;
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

    @Override
    public View getView(int pos, View convertView, ViewGroup viewGroup) {
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

        // If we don't have a service, the only line we should display is
        // the top one, where we'll say we don't have anything to work with
        if(mService == null) {
            primaryLine.setText("Error");
            secondaryLine.setText("Service not running");
            return view;
        }

        // Fill as appropriate
        if(pos == CONNECTED_LINE) {
            primaryLine.setText("Host");

            if(mService.isConnected()) {
                secondaryLine.setText(mService.getHost() + ":" + mService.getPort());
            }
            else {
                secondaryLine.setText("Disconnected");
            }
        }
        else if(pos == CHANNEL_LINE) {
            primaryLine.setText("Channel");

            if(mService.isConnected()) {
                try {
                    Channel ch = mService.getBinder().getSessionChannel();
                    StringBuilder sb = new StringBuilder();
                    sb.append(ch.getName());
                    sb.append(": ");
                    sb.append(ch.getUsers().size());
                    sb.append(" users");

                    secondaryLine.setText(sb);
                }
                catch(RemoteException e) {
                    e.printStackTrace();
                    secondaryLine.setText("Remote Exception Error");
                }
                catch(NullPointerException e) {
                    e.printStackTrace();
                    secondaryLine.setText(e.getMessage());
                }
            }
            else {
                secondaryLine.setText("Disconnected");
                view.setEnabled(false);
            }
        }
        else if(pos == TALK_LINE) {
            primaryLine.setText("Talk");

            if(mService.isConnected()) {
                try {
                    User user = mService.getBinder().getSessionUser();
                    boolean canHear = !user.isDeafened() && !user.isSuppressed();
                    boolean canSpeak = !user.isMuted() && !user.isSuppressed();

                    int txMode = mService.getBinder().getTransmitMode();

                    StringBuilder sb = new StringBuilder();
                    if(txMode == Constants.TRANSMIT_PUSH_TO_TALK) {
                        sb.append("PTT");
                    }
                    else if(txMode == Constants.TRANSMIT_CONTINUOUS) {
                        sb.append("Continuous TX");
                    }
                    else if(txMode == Constants.TRANSMIT_VOICE_ACTIVITY) {
                        sb.append("TX on activity");
                    }

                    sb.append(", ");
                    sb.append(canHear ? "can hear" : "can't hear");
                    sb.append(", ");
                    sb.append(canSpeak ? "can speak" : "can't speak");

                    secondaryLine.setText(sb);
                }
                catch(RemoteException e) {
                    e.printStackTrace();
                    secondaryLine.setText("Remote Exception Error");
                }
                catch(NullPointerException e) {
                    e.printStackTrace();
                    secondaryLine.setText("Null pointer exception");
                }
            }
            else {
                secondaryLine.setText("Disconnected");
                view.setEnabled(false);
            }
        }
        else if(pos == MODE_LINE) {
            primaryLine.setText("Monitor Mode");

            if(mService.isTransmitterMode()) {
                secondaryLine.setText("Transmitter");
            }
            else {
                secondaryLine.setText("Receiver");
            }
        }
        else if(pos == THRESHOLD_LINE) {
            primaryLine.setText("Activity Threshold");

            secondaryLine.setText(Float.toString(mService.getVADThreshold()));
        }
        else if(pos == USERNAME_LINE) {
            primaryLine.setText("User name");

            try {
                if(mService.isConnected()) {
                    secondaryLine.setText(mService.getBinder().getSessionUser().getName());
                }
                else {
                    secondaryLine.setText("Disconnected");
                }
            }
            catch(RemoteException e) {
                e.printStackTrace();
                secondaryLine.setText("Remote exception error");
            }
            catch(NullPointerException e) {
                e.printStackTrace();
                secondaryLine.setText("Null pointer exception");
            }
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
