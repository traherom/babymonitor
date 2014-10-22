package com.moreharts.babymonitor.client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.preferences.BabyTrustStore;
import com.moreharts.babymonitor.preferences.GlobalSettingsActivity;
import com.moreharts.babymonitor.preferences.Settings;
import com.moreharts.babymonitor.server.ServerStatus;
import com.moreharts.babymonitor.service.MonitorService;
import com.morlunk.jumble.Constants;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.model.Server;

import java.util.ArrayList;


public class ClientStatus extends Activity {
    private static final String TAG = "ClientStatus";

    // Connection to service
    private MonitorService mService = null;
    private ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Bound to client background service");
            MonitorService.LocalBinder binder = (MonitorService.LocalBinder) iBinder;
            mService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "Unbound from client background service");
            mService = null;
        }
    };

    // Dynamic server list and settings
    Settings mSettings;
    private ServerList mServerList;

    private String mMumbleUserName = "babymonitor_client_";

    private class BackgroundConnectTask extends AsyncTask<Server, Void, Void> {
        @Override
        protected Void doInBackground(Server... params) {
            Server info = params[0];

            // Resolve name if needed
            /*if(info.inetAddress == null) {
                try {
                    info.inetAddress = InetAddress.getByName(info.host);
                }
                catch(UnknownHostException e) {
                    Log.e(TAG, "Unable to resolve host name " + info.host);
                    return null;
                }
            }*/

            // Start Jumble
            Intent connectIntent = new Intent(getApplicationContext(), MonitorService.class);

            connectIntent.putExtra(JumbleService.EXTRAS_SERVER, info);
            connectIntent.putExtra(JumbleService.EXTRAS_CLIENT_NAME, getString(R.string.app_name));
            connectIntent.putExtra(JumbleService.EXTRAS_TRANSMIT_MODE, Constants.TRANSMIT_PUSH_TO_TALK);

            connectIntent.putExtra(JumbleService.EXTRAS_CERTIFICATE, mSettings.getCertificate());
            connectIntent.putExtra(JumbleService.EXTRAS_CERTIFICATE_PASSWORD, "");

            connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT, true);
            connectIntent.putExtra(JumbleService.EXTRAS_AUTO_RECONNECT_DELAY, 10);
            //connectIntent.putExtra(JumbleService.EXTRAS_INPUT_RATE, mSettings.getInputSampleRate());
            //connectIntent.putExtra(JumbleService.EXTRAS_INPUT_QUALITY, mSettings.getInputQuality());

            connectIntent.putStringArrayListExtra(JumbleService.EXTRAS_ACCESS_TOKENS, new ArrayList<String>(){{
                add(MonitorService.MUMBLE_TOKEN);
            }});
            //connectIntent.putExtra(JumbleService.EXTRAS_AUDIO_SOURCE, audioSource);
            //connectIntent.putExtra(JumbleService.EXTRAS_AUDIO_STREAM, audioStream);
            //connectIntent.putExtra(JumbleService.EXTRAS_FRAMES_PER_PACKET, mSettings.getFramesPerPacket());

            connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE, BabyTrustStore.getTrustStorePath(getApplicationContext()));
            connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_PASSWORD, BabyTrustStore.getTrustStorePassword());
            connectIntent.putExtra(JumbleService.EXTRAS_TRUST_STORE_FORMAT, BabyTrustStore.getTrustStoreFormat());

            //connectIntent.putExtra(JumbleService.EXTRAS_HALF_DUPLEX, mSettings.isHalfDuplex());
            //connectIntent.putExtra(JumbleService.EXTRAS_ENABLE_PREPROCESSOR, mSettings.isPreprocessorEnabled());*/

            connectIntent.setAction(JumbleService.ACTION_CONNECT);

            startService(connectIntent);
            bindService(connectIntent, mServiceConn, 0);

            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_status);

        // Settings
        mSettings = Settings.getInstance(this);

        // Start the discoverer
        mServerList = new ServerList(this);
        mServerList.startServiceDiscovery();

        // Start background service
        MonitorService.startMonitor(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Start background service
        bindService(new Intent(this, MonitorService.class), mServiceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService(mServiceConn);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop searching on network (if we haven't already)
        mServerList.stopServiceDiscovery();
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
                startActivity(new Intent(this, ServerStatus.class).addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY));
                return true;
            case R.id.action_quit:
                MonitorService.killMonitor(this);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onListenModeToggle(View view) {
        Log.d(TAG, "Listen mode toggled");
    }

    public void onConnectBtnClick(View view) {
        Log.d(TAG, "Connect button clicked");

        if(!mService.isConnected()) {
            // Connect to preferred server
            Log.d(TAG, "Connect pressed");
            Server connectTo = mServerList.getPreferredServer();
            if(connectTo != null) {
                Log.i(TAG, "Attempting to connect to " + connectTo);
                new BackgroundConnectTask().execute(connectTo);
            }
            else {
                Log.i(TAG, "No preferred host found. Just going with home server");
                new BackgroundConnectTask().execute(new Server(2000, "home", "mumble.moreharts.com", 64738, mMumbleUserName, ""));
            }
        }
        else {
            // Disconnect
            Log.d(TAG, "Disconnect pressed");

            mService.disconnect();
        }
    }

    public void onJoinBtnClick(View view) {
        Log.d(TAG, "Join button clicked");

        if(mService.isConnected()) {
            try {
                mService.joinBabyMonitorChannel();
            }
            catch(RemoteException e) {
                Log.d(TAG, "Failed to join room: " + e);
            }
        }
    }
}
