package com.moreharts.babymonitor.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.util.Log;

import com.moreharts.babymonitor.preferences.Settings;
import com.moreharts.babymonitor.service.MonitorService;

/**
 * Created by traherom on 12/22/2014.
 */
public class NetworkChangeReceiver  extends BroadcastReceiver {
    private static final String TAG = "NetworkChangeReceiver";

    public NetworkChangeReceiver() {
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(TAG, "Network state changed");

        MonitorService service = ((MonitorService.LocalBinder)peekService(context, MonitorService.getIntent(context))).getService();
        if(service != null) {
            service.onNetworkStateChanged();
        }
    }
}
