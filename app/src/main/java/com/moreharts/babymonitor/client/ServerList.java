package com.moreharts.babymonitor.client;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.moreharts.babymonitor.server.ServerBackgroundService;
import com.morlunk.jumble.model.Server;

import java.util.ArrayList;

/**
 * Maintains a current list of all known Mumble servers that we might use.
 * Servers are found via bonjour and any manually configured IPs.
 *
 * Created by traherom on 9/20/14.
 */
public class ServerList {
    private static final String TAG = "ServerList";

    Context mContext;

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
     * - Manually entered
     * - NSD with BabyMonitor name
     * - NSD for any mumble server
     * @return
     */
    public Server getPreferredServer() {
        NsdServiceInfo bestNsd = null;

        synchronized (mManualServers) {
            if(!mManualServers.isEmpty()) {
                return mManualServers.get(0);
            }
        }

        synchronized (mLocalServers) {
            for(NsdServiceInfo check : mLocalServers) {
                if(check.getServiceType().equals(ServerBackgroundService.SERVICE_TYPE) &&
                        check.getServiceName().startsWith(ServerBackgroundService.DEFAULT_SERVICE_NAME)) {
                    bestNsd = check;
                }
                else if(bestNsd == null && check.getServiceType().equals(ServerBackgroundService.SERVICE_TYPE)) {
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
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String s, int i) {
                Log.e(TAG, "Discovery failed to stop, error " + i);
                mNsdManager.stopServiceDiscovery(this);
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
                if (service.getServiceType().equals(ServerBackgroundService.SERVICE_TYPE)) {
                    mNsdManager.resolveService(service, mResolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e(TAG, "service lost" + service);
            }
        };

        mNsdManager.discoverServices(ServerBackgroundService.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void stopServiceDiscovery() {
        try {
            Log.i(TAG, "Stopping network discovery");
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
        catch(IllegalArgumentException e) {
            // Likely not running
            Log.d(TAG, "Stop service discovery threw IllegalArgumentException. Likely fine: " + e);
        }
    }
}
