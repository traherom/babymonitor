package com.moreharts.babymonitor.ui;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.service.MonitorService;
import com.moreharts.babymonitor.service.RemoteMonitorTracker;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link RemoteStatusFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RemoteStatusFragment extends Fragment implements ClientStatus.OnMonitorServiceBound {
    private static final String TAG = "RemoteStatusFragment";

    private MonitorService mService = null;

    private FragmentManager mFragmentMgr = null;
    private RemoteStatusAdapter mStatusAdapter = null;
    private StatusListFragment mStatusList = null;
    private Spinner mMonitorSpinner = null;

    private AdapterView.OnItemSelectedListener mItemSelectedListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            RemoteMonitorTracker.MonitorState state = (RemoteMonitorTracker.MonitorState)mMonitorSpinner.getAdapter().getItem(position);
            mStatusAdapter.setMonitorState(state);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            mStatusAdapter.setMonitorState(null);
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameter
     *
     * @return A new instance of fragment RemoteStatusFragment.
     */
    public static RemoteStatusFragment newInstance() {
        RemoteStatusFragment fragment = new RemoteStatusFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public RemoteStatusFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFragmentMgr = getChildFragmentManager();
        mStatusAdapter = new RemoteStatusAdapter(getActivity());

        ((ClientStatus)getActivity()).addOnMonitorServiceBoundListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_remote_status, container, false);

        mMonitorSpinner = (Spinner)view.findViewById(R.id.monitor_spinner);
        if(mService != null) {
            mMonitorSpinner.setAdapter(mService.getRemoteMonitorTracker());
            mMonitorSpinner.setOnItemSelectedListener(mItemSelectedListener);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        mStatusList = (StatusListFragment)mFragmentMgr.findFragmentById(R.id.status_list);
        mStatusList.setListAdapter(mStatusAdapter);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onMonitorServiceBound(MonitorService service) {
        mService = service;
        mService.getRemoteMonitorTracker().addOnRemoteMonitorUpdateListener(mUpdateListener);
        mStatusAdapter.setService(service);
        mMonitorSpinner.setAdapter(mService.getRemoteMonitorTracker());
        mMonitorSpinner.setOnItemSelectedListener(mItemSelectedListener);
    }

    @Override
    public void onMonitorServiceUnbound(MonitorService service) {
        service.getRemoteMonitorTracker().removeOnRemoteMonitorUpdateListener(mUpdateListener);
        mStatusAdapter.setService(null);
        mService = null;
    }

    private RemoteMonitorTracker.OnRemoteMonitorUpdateListener mUpdateListener = new RemoteMonitorTracker.OnRemoteMonitorUpdateListener() {
        @Override
        public void onRemoteMonitorAdded(RemoteMonitorTracker.MonitorState monitor) {

        }

        @Override
        public void onRemoteMonitorRemoved(RemoteMonitorTracker.MonitorState monitor) {

        }

        @Override
        public void onRemoteMonitorUpdated(RemoteMonitorTracker.MonitorState monitor) {

        }
    };
}
