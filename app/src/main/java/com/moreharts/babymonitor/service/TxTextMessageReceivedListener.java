package com.moreharts.babymonitor.service;

import android.util.Log;

import com.morlunk.jumble.model.Message;

import java.util.IllegalFormatException;

/**
 * Created by traherom on 11/22/2014.
 */
public class TxTextMessageReceivedListener implements MonitorService.OnMessageReceivedListener {
    public static String TAG = "TxTextMessageHandler";

    public void onMessageReceived(MonitorService service, Message msg) {
        // Only handle command if this service is in TX mode
        if(!service.isTransmitterMode()) {
            return;
        }

        Log.i(TAG, "Message: " + msg.getMessage());

        // Parse message
        String[] parts = msg.getMessage().trim().split(" ");
        String cmd = parts[0].trim().toLowerCase();

        if(cmd.equals(TextMessageManager.MSG_STATE)) {
            service.getTextMessageManager().handleStateMessage(msg);
        }
        else if(cmd.equals(TextMessageManager.CMD_THRESHOLD)) {
            // Threshold changes
            if(parts.length == 2) {
                try {
                    float newThresh = Float.parseFloat(parts[1]);
                    if (newThresh < 0)
                        newThresh = 0f;
                    if (newThresh > 1)
                        newThresh = 1f;

                    Log.i(TAG, "TX threshold set to " + newThresh);
                    service.setVADThreshold(newThresh);
                }
                catch(IllegalFormatException e) {
                    Log.e(TAG, "Ignoring threshold change request: " + e);
                }
            }

            // No matter what, return the current threshold setting
            service.getTextMessageManager().broadcastState();
        }
    }
}
