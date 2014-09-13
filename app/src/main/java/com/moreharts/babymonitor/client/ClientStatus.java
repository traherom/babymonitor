package com.moreharts.babymonitor.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.preferences.GlobalSettingsActivity;
import com.moreharts.babymonitor.server.ServerStatus;


public class ClientStatus extends Activity {
    private static final String TAG = "ClientStatus";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_status);

        ClientStatus.startMonitor(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.client_status, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch(id) {
            case R.id.action_settings:
                startActivity(new Intent(this, GlobalSettingsActivity.class));
                return true;
            case R.id.switch_mode:
                startActivity(new Intent(this, ServerStatus.class));
                return true;
            case R.id.action_quit:
                killBackgroundMonitor(this);
                ServerStatus.killBackgroundMonitor(this);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static void killBackgroundMonitor(Context context) {
        // If running, ends the background monitor service
        context.stopService(new Intent(context, ClientBackgroundService.class));
    }

    public static void startMonitor(Context context) {
        context.startService(new Intent(context, ClientBackgroundService.class));
    }
}
