package com.moreharts.babymonitor.ui;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.service.MonitorService;
import com.moreharts.babymonitor.service.LocalStatusAdapter;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link LocalStatusFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LocalStatusFragment extends Fragment implements ClientStatus.OnMonitorServiceBound {
    private MonitorService mService = null;

    private FragmentManager mFragmentMgr = null;
    private LocalStatusAdapter mStatusAdapter = null;
    private StatusListFragment mStatusList = null;

    private ClientControlFragment mClientControlFragment = null;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment LocalStatusFragment.
     */
    public static LocalStatusFragment newInstance() {
        LocalStatusFragment fragment = new LocalStatusFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public LocalStatusFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFragmentMgr = getChildFragmentManager();
        mStatusAdapter = new LocalStatusAdapter(getActivity());

        ((ClientStatus)getActivity()).addOnMonitorServiceBoundListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_local_status, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        mStatusList = (StatusListFragment)mFragmentMgr.findFragmentById(R.id.status_list);
        mStatusList.setListAdapter(mStatusAdapter);

        mClientControlFragment = (ClientControlFragment)mFragmentMgr.findFragmentById(R.id.client_controls);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public void setService(MonitorService service) {
        mService = service;
        mStatusAdapter.setService(service);
    }

    @Override
    public void onMonitorServiceBound(MonitorService service) {
        mService = service;

        // Listen for important stuff
        mService.addOnTxModeChangedListener(mOnTxModeChangedListener);
        mService.addOnVADThresholdChangedListener(mOnVADThresholdChangedListener);

        mStatusAdapter.setService(mService);
    }

    @Override
    public void onMonitorServiceUnbound(MonitorService service) {
        mService.removeOnTxModeChangedListener(mOnTxModeChangedListener);
        mService.removeOnVADThresholdChangedListener(mOnVADThresholdChangedListener);

        mService = null;
    }

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

    public void showClientControlFragment() {
        //FragmentTransaction trans = mFragmentMgr.beginTransaction();
        //trans.show(mClientControlFragment);
        //trans.commitAllowingStateLoss();
    }

    public void hideClientControlFragment() {
        //FragmentTransaction trans = mFragmentMgr.beginTransaction();
        //trans.hide(mClientControlFragment);
        //trans.commitAllowingStateLoss();
    }

    public void refreshControlFragmentVisibility() {
        if(mService == null || !mService.isConnected() || mService.isTransmitterMode()) {
            hideClientControlFragment();
        }
        else {
            showClientControlFragment();
        }
    }
}
