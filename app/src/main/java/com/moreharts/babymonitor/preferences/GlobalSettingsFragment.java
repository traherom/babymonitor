package com.moreharts.babymonitor.preferences;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.app.Fragment;
import android.util.Log;
import android.preference.PreferenceFragment;

import com.moreharts.babymonitor.R;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link GlobalSettingsFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link GlobalSettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class GlobalSettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = "GlobalSettingsFragment";

    private Settings mSettings;

    private OnFragmentInteractionListener mListener;

    public static GlobalSettingsFragment newInstance() {
        GlobalSettingsFragment fragment = new GlobalSettingsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }
    public GlobalSettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        mSettings = Settings.getInstance(getActivity());

        fixPreferenceSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        fixPreferenceSummaries();
    }

    /**
     * Ensures all preference summaries reflect their current values
     */
    public void fixPreferenceSummaries() {
        // Server
        DialogPreference fullHostPref = (DialogPreference)findPreference("mumbleHostSettings");
        String host = mSettings.getMumbleHost();
        int port = mSettings.getMumblePort();
        if(host != null) {
            fullHostPref.setSummary(host + ":" + port);
        }
        else {
            fullHostPref.setSummary("Unset");
        }

        // User name
        EditTextPreference userPref = (EditTextPreference)findPreference(Settings.PREF_USERNAME);
        userPref.setSummary(mSettings.getUserName());

        // Channel
        EditTextPreference channelPref = (EditTextPreference)findPreference(Settings.PREF_CHANNEL);
        channelPref.setSummary(mSettings.getMumbleChannel());
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

    }

}
