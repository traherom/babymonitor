package com.moreharts.babymonitor.server;

import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;

import com.moreharts.babymonitor.client.ClientStatus;
import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.preferences.GlobalSettingsActivity;


public class ServerStatus extends ListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_status);

        // Start the monitoring service
        //ServerStatus.startMonitor(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.server_status, menu);
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
                startActivity(new Intent(this, ClientStatus.class));
                return true;
            case R.id.action_quit:
                // Kill server and quit
                killBackgroundMonitor(this);
                ClientStatus.killBackgroundMonitor(this);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static void killBackgroundMonitor(Context context) {
        // If running, ends the background monitor service
        context.stopService(new Intent(context, ServerBackgroundService.class));
    }

    public static void startMonitor(Context context) {
        context.startService(new Intent(context, ServerBackgroundService.class));
    }
}
