package com.moreharts.babymonitor.service;

import android.util.Log;

import com.morlunk.jumble.model.Message;

/**
 * Created by traherom on 11/22/2014.
 */
public class RxTextMessageReceivedListener implements MonitorService.OnMessageReceivedListener {
    public static String TAG = "RxTextMessageHandler";

    public void onMessageReceived(MonitorService service, Message msg) {
        // Only handle message if this service is in RX mode
        if(service.isTransmitterMode()) {
            return;
        }

        Log.i(TAG, "Message: " + msg.getMessage());
        if(msg.getMessage().startsWith(TextMessageManager.MSG_STATE)) {
            service.getTextMessageManager().handleStateMessage(msg);
        }
    }
}
