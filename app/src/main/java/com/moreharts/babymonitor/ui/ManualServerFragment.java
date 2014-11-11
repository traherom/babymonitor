package com.moreharts.babymonitor.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.moreharts.babymonitor.R;

public class ManualServerFragment extends DialogFragment {
    public static final String TAG = "ManualServerFragment";

    private static final String ARG_HOST = "host";
    private static final String ARG_PORT = "port";

    private EditText mHost;
    private EditText mPort;

    private ManualServerEntryListener mListener = null;

    public interface ManualServerEntryListener {
        public void onManualServerAccept(ManualServerFragment dialog);
        public void onManualServerCancel(ManualServerFragment dialog);
    }

    public String getHost() {
        return mHost.getText().toString();
    }

    public int getPort() {
        String port = mPort.getText().toString();
        if(port == null || port.isEmpty())
            return ServerList.DEFAULT_PORT;

        return Integer.parseInt(port);
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param host Parameter 1.
     * @param port Parameter 2.
     * @return A new instance of fragment ManualServerFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ManualServerFragment newInstance(String host, int port) {
        ManualServerFragment fragment = new ManualServerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_HOST, host);
        args.putInt(ARG_PORT, port);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (ManualServerEntryListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement ManualServerEntryListener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstance) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.fragment_manual_server, null);

        mHost = (EditText)view.findViewById(R.id.manual_hostname);
        mPort = (EditText)view.findViewById(R.id.manual_port);
        mPort.setHint("Port (Default " + ServerList.DEFAULT_PORT + ")");

        // Load with given values
        Bundle args = getArguments();
        String argHost = args.getString(ARG_HOST, "");
        int argPort = args.getInt(ARG_PORT, -1);
        if(argHost != null && !argHost.isEmpty()) {
            mHost.setText(argHost);
        }
        if(argPort > 0) {
            mPort.setText(Integer.toString(argPort));
        }

        builder.setView(view);

        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                try {
                    Log.d(TAG, "Ok clicked");

                    int port = getPort();

                    // Are these values valid?
                    if(port < 1 || port > 65535)
                        return;

                    mListener.onManualServerAccept(ManualServerFragment.this);
                }
                catch(NumberFormatException e) {
                    return;
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mListener.onManualServerCancel(ManualServerFragment.this);
            }
        });

        return builder.create();
    }
}
