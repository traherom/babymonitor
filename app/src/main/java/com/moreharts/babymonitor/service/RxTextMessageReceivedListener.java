package com.moreharts.babymonitor.service;

import android.os.RemoteException;
import android.util.Log;

import com.morlunk.jumble.model.Message;

import java.util.IllegalFormatException;

/**
 * Created by traherom on 11/22/2014.
 */
public class RxTextMessageReceivedListener implements MonitorService.OnMessageReceivedListener {
    public static String TAG = "RxTextMessageHandler";

    public void onMessageReceived(MonitorService service, Message msg) {
        // Only handle command if this service is in RX mode
        if(service.isTransmitterMode()) {
            return;
        }

        Log.i(TAG, "Message: " + msg.getMessage());

        // Parse message
        String[] parts = msg.getMessage().trim().split(" ");
        String cmd = parts[0].trim().toLowerCase();

        if(cmd.equals(MonitorService.RESP_THRESHOLD)) {
            try {
                service.setVADThreshold(Float.parseFloat(parts[1]));
            }
            catch(RemoteException e) {
                Log.e(TAG, "Unable to set threshold");
                e.printStackTrace();
            }
            catch(IllegalFormatException e) {
                Log.i(TAG, "Ignoring threshold message: " + e);
            }
        }
    }
}
