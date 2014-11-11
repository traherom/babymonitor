package com.moreharts.babymonitor.ui;



import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.moreharts.babymonitor.R;
import com.moreharts.babymonitor.service.MonitorService;

/**
 * A simple {@link Fragment} subclass.
 *
 */
public class ServerSelectionDialog extends DialogFragment {
    public static final String TAG = "ServerSelectionDialog";

    private ClientStatus mClientStatus = null;

    public void setClientStatus(ClientStatus status) {
        mClientStatus = status;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstance) {
        MonitorService service = null;
        ServerList list = null;
        if(mClientStatus != null) {
            service = mClientStatus.getMonitorService();
            list = mClientStatus.getServerList();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.d(TAG, "Ok");
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.d(TAG, "Cancel");
            }
        });

        if(service == null || service.isConnected()) {
            builder.setNeutralButton("Disconnect", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Log.d(TAG, "Disconnect");
                }
            });
        }

        builder.setSingleChoiceItems(list, 0, null);

        return builder.create();
    }
}
