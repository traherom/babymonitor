package com.moreharts.babymonitor.ui;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.ToggleButton;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.service.MonitorService;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link ClientControlFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link ClientControlFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class ClientControlFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_SERVICE = "monitor_service";

    private MonitorService mService = null;

    private SeekBar mThresholdBar = null;
    private ToggleButton mMuteButton = null;
    private ToggleButton mVoiceButton = null;
    private ToggleButton mContinuousButton = null;

    private OnFragmentInteractionListener mListener;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     */
    public static ClientControlFragment newInstance() {
        ClientControlFragment fragment = new ClientControlFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }
    public ClientControlFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setService(MonitorService service) {
        mService = service;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_client_control, container, false);

        mThresholdBar = (SeekBar)view.findViewById(R.id.thresholdSlider);
        mThresholdBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float thresh = (float)seekBar.getProgress() / 100f;
                mListener.onThresholdAdjusted(thresh);
            }
        });

//        mMuteButton = (ToggleButton)view.findViewById(R.id.mute_button);
//        mMuteButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if(mListener != null) {
//                    mListener.onMuteRequested(mMuteButton.isChecked());
//                }
//            }
//        });

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public void setMute(boolean mute) {
        mMuteButton.setChecked(mute);
    }

    public boolean getMute() {
        return mMuteButton.isChecked();
    }

    public void setThreshold(float threshold) {
        mThresholdBar.setProgress((int)(threshold * 100f));
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        public void onThresholdAdjusted(float threshold);
        public void onMuteRequested(boolean mute);
    }
}
