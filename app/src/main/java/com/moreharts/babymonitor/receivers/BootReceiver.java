package com.moreharts.babymonitor.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.moreharts.babymonitor.preferences.Settings;
import com.moreharts.babymonitor.service.MonitorService;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Boot complete received");

        // Should we start on boot and, if so, which service?
        Settings settings = Settings.getInstance(context);
        if(settings.getStartOnBoot()) {
            MonitorService.startMonitor(context, null);
        }
    }
}
