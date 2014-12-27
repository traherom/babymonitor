package com.moreharts.babymonitor.ui;

import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.preferences.GlobalSettingsActivity;
import com.moreharts.babymonitor.preferences.Settings;
import com.moreharts.babymonitor.service.MonitorService;
import com.morlunk.jumble.util.JumbleObserver;

import java.util.ArrayList;


public class ClientStatus extends FragmentActivity implements
        AdapterView.OnItemClickListener,
        ManualServerFragment.ManualServerEntryListener,
        ClientControlFragment.OnFragmentInteractionListener
{
    private static final String TAG = "ClientStatus";

    public interface OnMonitorServiceBound {
        public void onMonitorServiceBound(MonitorService service);
        public void onMonitorServiceUnbound(MonitorService service);
    }

    // People listening for us binding to the service
    private ArrayList<OnMonitorServiceBound> mServiceBoundListeners = new ArrayList<OnMonitorServiceBound>();

    public void addOnMonitorServiceBoundListener(OnMonitorServiceBound listener) {
        mServiceBoundListeners.add(listener);
    }

    public void removeOnMonitorServiceBoundListener(OnMonitorServiceBound listener) {
        mServiceBoundListeners.remove(listener);
    }

    private void notifyOnMonitorServiceBoundListeners(MonitorService service) {
        for(OnMonitorServiceBound listener : mServiceBoundListeners) {
            listener.onMonitorServiceBound(service);
        }
    }

    private void notifyOnMonitorServiceUnboundListeners(MonitorService service) {
        for(OnMonitorServiceBound listener : mServiceBoundListeners) {
            listener.onMonitorServiceUnbound(service);
        }
    }

    // Connection to service
    private MonitorService mService = null;
    private ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "Bound to client background service");
            MonitorService.LocalBinder binder = (MonitorService.LocalBinder) iBinder;
            mService = binder.getService();

            // Watch for non-Jumble info updates
            notifyOnMonitorServiceBoundListeners(mService);

            // Tie UI in to service
            try{
                mService.getBinder().registerObserver(mJumbleObserver);
            }
            catch(RemoteException e) {
                e.printStackTrace();
            }

            // Set up UI appropriately at the beginning
            refreshOptionsMenuVisibility();
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

            notifyOnMonitorServiceUnboundListeners(mService);
            mService = null;
        }
    };

    /** Watch for changes on the service */
    private JumbleObserver mJumbleObserver = new JumbleObserver() {
        @Override
        public void onConnected() throws RemoteException {
            // UI updates?
            refreshOptionsMenuVisibility();

            // Ensure the mute status matches our button
            //mService.setDeafen(mClientControlFragment.getMute());
        }

        @Override
        public void onDisconnected() throws RemoteException {
            // UI updates?
            refreshOptionsMenuVisibility();
        }
    };

    // UI elements
    private PagerAdapter mPagerAdapter;
    private FragmentManager mFragmentMgr;
    private RemoteStatusFragment mRemoteStatusFrag = null;
    private LocalStatusFragment mLocalStatusFrag = null;
    private ActionBar mActionBar;
    private ViewPager mPager;

    private MenuItem connectMenuItem = null;
    private MenuItem changeServerMenuItem = null;
    private MenuItem disconnectMenuItem = null;
    private MenuItem switchToRxMenuItem = null;
    private MenuItem switchToTxMenuItem = null;

    // Dynamic server list and settings
    private Settings mSettings;
    private ServerList mServerList;

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

    public class PagerAdapter extends FragmentPagerAdapter {
        public static final int LOCAL_STATUS_TAB = 0;
        public static final int REMOTE_STATUS_TAB = 1;
        public static final int TAB_COUNT = REMOTE_STATUS_TAB + 1;

        private Context mContext = null;

        public PagerAdapter(Context context, FragmentManager fm) {
            super(fm);
            mContext = context;
        }

        @Override
        public Fragment getItem(int position) {
            // Give everyone access to our service as we create the fragment

            Fragment frag = null;
            switch(position) {
                case LOCAL_STATUS_TAB:
                    if(mLocalStatusFrag == null)
                        mLocalStatusFrag = new LocalStatusFragment();
                    frag = mLocalStatusFrag;
                    break;
                case REMOTE_STATUS_TAB:
                    if(mRemoteStatusFrag == null)
                        mRemoteStatusFrag = new RemoteStatusFragment();
                    frag = mRemoteStatusFrag;
                    break;
            }

            return frag;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch(position) {
                case LOCAL_STATUS_TAB:
                    return mContext.getString(R.string.local_status_tab_name);
                case REMOTE_STATUS_TAB:
                    return mContext.getString(R.string.remote_status_tab_name);
                default:
                    return "unknown";
            }
        }

        @Override
        public int getCount() {
            return TAB_COUNT;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_status);

        // UI stuff
        mFragmentMgr = getSupportFragmentManager();

        mPagerAdapter = new PagerAdapter(this, mFragmentMgr);
        mPager = (ViewPager)findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);

        mActionBar = getActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        ActionBar.TabListener tabListener = new ActionBar.TabListener() {
            @Override
            public void onTabSelected(ActionBar.Tab tab, android.app.FragmentTransaction ft) {
                mPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(ActionBar.Tab tab, android.app.FragmentTransaction ft) {

            }

            @Override
            public void onTabReselected(ActionBar.Tab tab, android.app.FragmentTransaction ft) {
                // Nobody cares
            }
        };

        mPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                mActionBar.setSelectedNavigationItem(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        mActionBar.addTab(mActionBar.newTab()
                .setText(mPagerAdapter.getPageTitle(0))
                .setTabListener(tabListener));
        mActionBar.addTab(mActionBar.newTab()
                .setText(mPagerAdapter.getPageTitle(1))
                .setTabListener(tabListener));

        // Audio controls enabled
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Settings
        mSettings = Settings.getInstance(this);

        // Start the discoverer
        mServerList = new ServerList(getApplicationContext());
        //mServerList.startServiceDiscovery();

        // Start background service
        MonitorService.startMonitor(this, null);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Bind to background service
        bindService(MonitorService.getIntent(this), mServiceConn, Context.BIND_AUTO_CREATE);
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

        refreshOptionsMenuVisibility();

        return true;
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
                mSettings.setTxMode(false);
                refreshOptionsMenuVisibility();
                return true;
            case R.id.switch_to_tx:
                // Make connection a transmitter
                mService.setTransmitterMode(true);
                mSettings.setTxMode(true);
                refreshOptionsMenuVisibility();
                return true;
            case R.id.connect: // Fall through
            case R.id.change_server:
                // Show host selection dialog
                showManualServerDialog();
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
        mService.connect(host, port, mSettings.getUserName());
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
        mService.setVADThreshold(threshold);
    }

    @Override
    public void onMuteRequested(boolean mute) {
        mService.setDeafen(mute);
    }
}
