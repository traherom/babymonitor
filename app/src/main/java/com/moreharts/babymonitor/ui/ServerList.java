package com.moreharts.babymonitor.ui;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

import com.morlunk.jumble.model.Server;

import java.util.ArrayList;

/**
 * Maintains a current list of all known Mumble servers that we might use.
 * Servers are found via bonjour and any manually configured IPs.
 *
 * Created by traherom on 9/20/14.
 */
public class ServerList implements ListAdapter {
    private static final String TAG = "ServerList";

    public static final String DEFAULT_SERVICE_NAME = "BabyMonitor";
    public static final String DEFAULT_SERVICE_TYPE = "._mumble._tcp";
    public static final int DEFAULT_PORT = 64738;

    private Context mContext;
    private DataSetObservable mObservers = new DataSetObservable();

    private Server mSelectedServer = null;
    private ArrayList<NsdServiceInfo> mLocalServers = new ArrayList<NsdServiceInfo>();
    private ArrayList<Server> mManualServers = new ArrayList<Server>();

    NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener;

    public ServerList(Context context) {
        mContext = context;
    }

    protected void finalize() throws Throwable {
        stopServiceDiscovery();
    }

    /**
     * Returns the best server we have found so far. Priority:
     * - Current selected
     * - Manually entered
     * - NSD with BabyMonitor name
     * - NSD for any mumble server
     * @return
     */
    public Server getPreferredServer() {
        if(mSelectedServer != null) {
            return mSelectedServer;
        }

        synchronized (mManualServers) {
            if(!mManualServers.isEmpty()) {
                return mManualServers.get(0);
            }
        }

        NsdServiceInfo bestNsd = null;
        synchronized (mLocalServers) {
            for(NsdServiceInfo check : mLocalServers) {
                if(check.getServiceType().equals(DEFAULT_SERVICE_TYPE) &&
                        check.getServiceName().startsWith(DEFAULT_SERVICE_NAME)) {
                    bestNsd = check;
                }
                else if(bestNsd == null && check.getServiceType().equals(DEFAULT_SERVICE_TYPE)) {
                    bestNsd = check;
                }
            }
        }

        if(bestNsd != null) {
            return new Server(1000, bestNsd.getServiceName(), bestNsd.getHost().toString(), bestNsd.getPort(), "babymonitor", "");
        }
        else
            return null;
    }

    public void setPreferredServer(Server pref) {
        mSelectedServer = pref;
    }

    public void startServiceDiscovery() {
        Log.i(TAG, "Starting network discovery");
        mNsdManager = (NsdManager)mContext.getSystemService(Context.NSD_SERVICE);

        mResolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Called when the resolve fails.  Use the error code to debug.
                Log.e(TAG, "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.i(TAG, "Found BabyMonitor at " + serviceInfo.getHost().toString() + ":" + serviceInfo.getPort());
                synchronized (mLocalServers) {
                    mLocalServers.add(serviceInfo);
                }
            }
        };

        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String s, int i) {
                Log.e(TAG, "Discovery failed to start, error " + i);
                //mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String s, int i) {
                Log.e(TAG, "Discovery failed to stop, error " + i);
                //mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onDiscoveryStarted(String s) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d(TAG, "Service discovery success " + service);

                // TODO also only look for BabyMonitor servers
                if (service.getServiceType().equals(DEFAULT_SERVICE_TYPE)) {
                    mNsdManager.resolveService(service, mResolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e(TAG, "service lost" + service);
            }
        };

        mNsdManager.discoverServices(DEFAULT_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void stopServiceDiscovery() {
        if(mNsdManager == null)
            return;

        try {
            Log.i(TAG, "Stopping network discovery");
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
        catch(IllegalArgumentException e) {
            // Likely not running
            Log.d(TAG, "Stop service discovery threw IllegalArgumentException. Likely fine: " + e);
        }
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

    @Override
    public int getCount() {
        return mLocalServers.size() + mManualServers.size() + (mSelectedServer == null ? 0 : 1);
    }

    @Override
    public Object getItem(int i) {
        synchronized (mLocalServers) {
            // Select into our two lists+selected server to item i amount
            int pos = i;
            if (mSelectedServer != null) {
                if (i == 0)
                    return mSelectedServer;
                else
                    pos -= 1;
            }

            // Manual list?
            if (pos < mManualServers.size()) {
                return mManualServers.get(pos);
            } else {
                pos -= mManualServers.size();
            }

            // Dynamic
            return mLocalServers.get(pos);
        }
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        Log.d(TAG, "List item");

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
}
