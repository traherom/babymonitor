package com.moreharts.babymonitor.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.service.MonitorService;
import com.moreharts.babymonitor.ui.ServerList;

/**
 * Created by traherom on 11/23/2014.
 */
public class ServerSelectionDialogPreference extends DialogPreference {
    // Settings access
    private Settings mSettings = null;

    // UI
    private EditText mHost;
    private EditText mPort;

    public ServerSelectionDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSettings = Settings.getInstance(context);

        setDialogLayoutResource(R.layout.fragment_manual_server);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mHost = (EditText)view.findViewById(R.id.manual_hostname);
        mPort = (EditText)view.findViewById(R.id.manual_port);

        // Load with given values
        String argHost = mSettings.getMumbleHost();
        int argPort = mSettings.getMumblePort();

        if(argHost != null && !argHost.isEmpty()) {
            mHost.setText(argHost);
        }
        if(argPort > 0 && argPort <= 0xFFFF && argPort != ServerList.DEFAULT_PORT) {
            mPort.setText(Integer.toString(argPort));
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            int port = getPort();

            // Are the ports valid?
            if(port < 1 || port > 0xFFFF)
                return;

            // Non-blank host?
            String host = getHost();
            if(host.isEmpty()) {
                return;
            }

            // Save
            mSettings.setMumblePort(port);
            mSettings.setMumbleHost(host);
        }
    }

    public String getHost() {
        return mHost.getText().toString().trim();
    }

    public int getPort() {
        String port = mPort.getText().toString();
        if(port == null || port.isEmpty())
            return ServerList.DEFAULT_PORT;

        return Integer.parseInt(port);
    }
}
