package com.moreharts.babymonitor.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.support.v4.app.FragmentTransaction;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.preferences.GlobalSettingsActivity;
import com.moreharts.babymonitor.preferences.Settings;
import com.moreharts.babymonitor.service.MonitorService;
import com.moreharts.babymonitor.service.ServiceStatusAdapter;
import com.morlunk.jumble.util.JumbleObserver;

import java.util.Random;


public class ClientStatus extends FragmentActivity implements
        AdapterView.OnItemClickListener,
        ManualServerFragment.ManualServerEntryListener,
        ClientControlFragment.OnFragmentInteractionListener
{
    private static final String TAG = "ClientStatus";

    // Connection to service
    private MonitorService mService = null;
    private ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Bound to client background service");
            MonitorService.LocalBinder binder = (MonitorService.LocalBinder) iBinder;
            mService = binder.getService();

            // Watch for non-Jumble info updates
            mService.addOnTxModeChangedListener(mOnTxModeChangedListener);
            mService.addOnVADThresholdChangedListener(mOnVADThresholdChangedListener);

            // Set up UI appropriately at the beginning
            refreshOptionsMenuVisibility();
            refreshControlFragmentVisibility();

            // Tie info list to service
            mStatusAdapter.setService(mService);
            try{
                mService.getBinder().registerObserver(mJumbleObserver);
            }
            catch(RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "Unbound from client background service");

            try {
                mService.getBinder().unregisterObserver(mJumbleObserver);
            }
            catch(RemoteException e) {
                e.printStackTrace();
            }

            mService.removeOnTxModeChangedListener(mOnTxModeChangedListener);
            mService.removeOnVADThresholdChangedListener(mOnVADThresholdChangedListener);

            mStatusAdapter.setService(null);
            mService = null;
        }
    };

    private MonitorService.OnTxModeChangedListener mOnTxModeChangedListener = new MonitorService.OnTxModeChangedListener() {
        @Override
        public void onTXModeChanged(MonitorService service, boolean isTxMode) {
            refreshControlFragmentVisibility();
        }
    };

    private MonitorService.OnVADThresholdChangedListener mOnVADThresholdChangedListener = new MonitorService.OnVADThresholdChangedListener() {
        @Override
        public void onVADThresholdChanged(MonitorService service, float newThreshold) {
            mClientControlFragment.setThreshold(newThreshold);
            mStatusList.forceRefresh();
        }
    };

    /** Watch for changes on the service */
    private JumbleObserver mJumbleObserver = new JumbleObserver() {
        @Override
        public void onConnected() throws RemoteException {
            // UI updates?
            refreshOptionsMenuVisibility();
            refreshControlFragmentVisibility();

            // Save the host and port for future use
            mSettings.setMumbleHost(mService.getHost());
            mSettings.setMumblePort(mService.getPort());
        }

        @Override
        public void onDisconnected() throws RemoteException {
            // UI updates?
            refreshOptionsMenuVisibility();
            refreshControlFragmentVisibility();
        }
    };

    // UI elements
    FragmentManager mFragmentMgr;
    StatusListFragment mStatusList;
    ClientControlFragment mClientControlFragment;

    ServiceStatusAdapter mStatusAdapter;
    MenuItem connectMenuItem = null;
    MenuItem changeServerMenuItem = null;
    MenuItem disconnectMenuItem = null;
    MenuItem switchToRxMenuItem = null;
    MenuItem switchToTxMenuItem = null;
    MenuItem startOnBootMenuItem = null;

    // Dynamic server list and settings
    Settings mSettings;
    private ServerList mServerList;

    private String mMumbleUserName = null;

    public void showClientControlFragment() {
        FragmentTransaction trans = mFragmentMgr.beginTransaction();
        trans.show(mClientControlFragment);
        trans.commit();
    }

    public void hideClientControlFragment() {
        FragmentTransaction trans = mFragmentMgr.beginTransaction();
        trans.hide(mClientControlFragment);
        trans.commit();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {
        Log.d(TAG, "User clicked on status list item " + id);

        /*switch(pos) {
            case ServiceStatusAdapter.CONNECTED_LINE:
                // Display the server selection dialog
                //ServerSelectionDialog d = new ServerSelectionDialog();
                //d.setClientStatus(this);
                //d.show(mFragmentMgr, null);

                ManualServerFragment dialog = null;
                if(mService.isConnected()) {
                    dialog = ManualServerFragment.newInstance(mService.getHost(), mService.getPort());
                }
                else {
                    dialog = new ManualServerFragment();
                }

                dialog.show(mFragmentMgr, null);
                break;
            default:
                Log.d(TAG, "Ignoring item click");
        }*/
    }

    @Override
    public void onManualServerAccept(ManualServerFragment dialog) {
        // Are the new settings different than our current connection?
        String host = dialog.getHost();
        int port = dialog.getPort();
        if(mService.isConnected()) {
            if(mService.getHost().equals(host) && mService.getPort() == port) {
                Log.d(TAG, "No change to manual server settings");
                return;
            }
        }

        // Connect to new host
        Log.d(TAG, "User changed manual server parameters");
        connect(host, port);
    }

    @Override
    public void onManualServerCancel(ManualServerFragment dialog) {
        Log.d(TAG, "User cancelled changing server parameters, ignoring");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_status);

        // UI stuff
        mStatusAdapter = new ServiceStatusAdapter(this);

        mFragmentMgr = getSupportFragmentManager();
        mStatusList = (StatusListFragment)mFragmentMgr.findFragmentById(R.id.status_list);
        mStatusList.setListAdapter(mStatusAdapter);
        mStatusList.getListView().setOnItemClickListener(this);

        mClientControlFragment = (ClientControlFragment)mFragmentMgr.findFragmentById(R.id.client_controls);

        // Audio controls enabled
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Settings
        mSettings = Settings.getInstance(this);

        if(mMumbleUserName == null) {
            // TODO get client user name from settings
            mMumbleUserName = mSettings.getUserName();
            if(mMumbleUserName == null) {
                mMumbleUserName = MonitorService.MUMBLE_USER_START + Math.abs(new Random().nextInt());
                mSettings.setUserName(mMumbleUserName);
            }
        }

        // Start the discoverer
        mServerList = new ServerList(getApplicationContext());
        //mServerList.startServiceDiscovery();

        // Start background service
        MonitorService.startMonitor(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Bind to background service
        bindService(new Intent(this, MonitorService.class), mServiceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        unbindService(mServiceConn);

        super.onStop();
    }

    @Override
    public void onDestroy() {
        // Stop searching on network (if we haven't already)
        mServerList.stopServiceDiscovery();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.client_status, menu);

        // Save items
        disconnectMenuItem = menu.findItem(R.id.disconnect);
        connectMenuItem = menu.findItem(R.id.connect);
        changeServerMenuItem = menu.findItem(R.id.change_server);
        switchToRxMenuItem = menu.findItem(R.id.switch_to_rx);
        switchToTxMenuItem = menu.findItem(R.id.switch_to_tx);
        startOnBootMenuItem = menu.findItem(R.id.start_on_boot);

        refreshOptionsMenuVisibility();

        return true;
    }

    public void refreshControlFragmentVisibility() {
        if(mService == null || !mService.isConnected() || mService.isTransmitterMode()) {
            hideClientControlFragment();
        }
        else {
            showClientControlFragment();
        }
    }

    public void refreshOptionsMenuVisibility() {
        // Ensure menu is already created/saved
        if(disconnectMenuItem == null)
            return;

        // Set menu appropriately
        if(mService != null && mService.isConnected()) {
            disconnectMenuItem.setVisible(true);
            changeServerMenuItem.setVisible(true);
            connectMenuItem.setVisible(false);
        }
        else {
            disconnectMenuItem.setVisible(false);
            changeServerMenuItem.setVisible(false);
            connectMenuItem.setVisible(true);
        }

        // Rx/Tx?
        if(mService != null && mService.isTransmitterMode()) {
            switchToRxMenuItem.setVisible(true);
            switchToTxMenuItem.setVisible(false);
        }
        else {
            switchToRxMenuItem.setVisible(false);
            switchToTxMenuItem.setVisible(true);
        }

        // Boot mode?
        startOnBootMenuItem.setChecked(mSettings.getStartOnBoot());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "Menu bar clicked: " + item);

        int id = item.getItemId();
        switch(id) {
            case R.id.disconnect:
                disconnect();
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this, GlobalSettingsActivity.class));
                return true;
            case R.id.switch_to_rx:
                // Make connection a receiver
                mService.setTransmitterMode(false);
                mSettings.setRxMode(true);
                mStatusList.forceRefresh();
                refreshOptionsMenuVisibility();
                return true;
            case R.id.switch_to_tx:
                // Make connection a transmitter
                mService.setTransmitterMode(true);
                mSettings.setRxMode(false);
                mStatusList.forceRefresh();
                refreshOptionsMenuVisibility();
                return true;
            case R.id.connect: // Fall through
            case R.id.change_server:
                // Show host selection dialog
                showManualServerDialog();
                return true;
            case R.id.start_on_boot:
                // Toggle boot setting
                if(startOnBootMenuItem.isChecked()) {
                    // Disable
                    startOnBootMenuItem.setChecked(false);
                    mSettings.setStartOnBoot(false);
                }
                else {
                    // Enable
                    startOnBootMenuItem.setChecked(true);
                    mSettings.setStartOnBoot(true);
                }
                return true;
            case R.id.action_quit:
                disconnect();
                MonitorService.killMonitor(this);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void disconnect() {
        if(mService != null) {
            mService.disconnect();
        }
    }

    public void connect(String host, int port) {
        mService.connect(host, port, mMumbleUserName);
    }

    public void showManualServerDialog() {
        ManualServerFragment dialog = null;
        if(mService.isConnected()) {
            dialog = ManualServerFragment.newInstance(mService.getHost(), mService.getPort());
        }
        else {
            dialog = ManualServerFragment.newInstance(mSettings.getMumbleHost(), mSettings.getMumblePort());
        }

        dialog.show(mFragmentMgr, null);
    }

    public MonitorService getMonitorService() {
        return mService;
    }

    public ServerList getServerList() {
        return mServerList;
    }

    public void onListenModeToggle(View view) {
        Log.d(TAG, "Listen mode toggled");
    }

    @Override
    public void onThresholdAdjusted(float threshold) {
        Log.d(TAG, "Requesting threshold adjust to " + threshold);
        try {
            mService.setVADThreshold(threshold);
        }
        catch(RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMuteRequested(boolean mute) {
        try {
            if (mService.isConnected()) {
                if (mute)
                    mService.setDeafen();
                else
                    mService.setDefaultMuteDeafenStatus();
            }
        }
        catch(RemoteException e) {
            e.printStackTrace();
        }
    }
}
