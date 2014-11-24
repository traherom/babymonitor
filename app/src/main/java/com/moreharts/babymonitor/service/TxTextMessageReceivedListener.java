package com.moreharts.babymonitor.service;

import android.os.RemoteException;
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

        if(cmd.equals(MonitorService.CMD_THRESHOLD)) {
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
                catch(RemoteException e) {
                    Log.e(TAG, "Unable to change threshold: " + e);
                    e.printStackTrace();
                }
                catch(IllegalFormatException e) {
                    Log.e(TAG, "Ignoring threshold change request: " + e);
                }
            }

            // No matter what, return the current threshold setting
            try {
                service.sendThresholdResponse();
            }
            catch(RemoteException e) {
                Log.e(TAG, "Unable to send threshold response command");
                e.printStackTrace();
            }
        }
        else if(cmd.equals(MonitorService.CMD_TX_PING)) {
            try {
                Log.i(TAG, "Ping");
                service.sendChannelMessage(MonitorService.RESP_PING);
            }
            catch(RemoteException e) {
                Log.e(TAG, "Unable to send pong");
                e.printStackTrace();
            }
        }
        else if(cmd.equals(MonitorService.CMD_NOISE_STATE)) {
            try {
                Log.i(TAG, "Noise state request");
                if(service.isThereNoise())
                    service.sendChannelMessage(MonitorService.RESP_NOISE_ON);
                else
                    service.sendChannelMessage(MonitorService.RESP_NOISE_OFF);
            }
            catch(RemoteException e) {
                Log.e(TAG, "Unable to send noise state response");
                e.printStackTrace();
            }
        }
    }
}
