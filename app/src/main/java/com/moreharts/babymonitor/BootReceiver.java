package com.moreharts.babymonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.preference.PreferenceManager;

import com.moreharts.babymonitor.service.MonitorService;
import com.moreharts.babymonitor.server.ServerStatus;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Boot complete received");

        // Should we start on boot and, if so, which service?
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if(sharedPreferences.getBoolean("pref_startOnStartup", false)) {
            if (sharedPreferences.getString("pref_monitorMode", "client") == "client") {
                MonitorService.startMonitor(context);
            }
            else {
                ServerStatus.startMonitor(context);
            }
        }
    }
}
