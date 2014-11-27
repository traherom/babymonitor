package com.moreharts.babymonitor.service;

import android.nfc.FormatException;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Message;

import java.util.ArrayList;
import java.util.IllegalFormatException;

/**
 * Manages all incoming and outgoing text messages within Mumble
 * Created by traherom on 11/26/2014.
 */
public class TextMessageManager {
    public static final String TAG = "TextMessageManager";

    public static final int BROADCAST_STATE_DELAY = 5000;

    public static final String MSG_STATE = "state";
    public static final String CMD_THRESHOLD = "threshold";

    private MonitorService mService = null;

    private TxTextMessageReceivedListener mTxReceiver = new TxTextMessageReceivedListener();
    private RxTextMessageReceivedListener mRxReceiver = new RxTextMessageReceivedListener();

    private ArrayList<OnStateMessageListener> mStateListeners = new ArrayList<OnStateMessageListener>();

    private Handler mHandler = new Handler();

    private Runnable mBroadcaster = new Runnable() {
        @Override
        public void run() {
            broadcastState();
            mHandler.postDelayed(this, BROADCAST_STATE_DELAY);
        }
    };

    public TextMessageManager(MonitorService service) {
        mService = service;

        // Add message handlers. If ignore messages if not in the correct mode, so no need to swap
        // them in and out (TODO: do it later for memory)
        mService.addOnMessageHandler(mTxReceiver);
        mService.addOnMessageHandler(mRxReceiver);

        // Watch for changes to mode so we can switch in the correct handlers
        mService.addOnTxModeChangedListener(new MonitorService.OnTxModeChangedListener() {
            @Override
            public void onTXModeChanged(MonitorService service, boolean isTxMode) {
                if(isTxMode) {
                    // Start periodic broadcaster of our state
                    startBroadcaster();
                }
                else {
                    // Stop broadcaster
                    stopBroadcaster();
                }
            }
        });
    }

    private void startBroadcaster() {
        // Periodically (every few seconds) send out the state of this monitor
        mHandler.post(mBroadcaster);
    }

    private void stopBroadcaster() {
        mHandler.removeCallbacks(mBroadcaster);
    }

    /**
     * Sends a message to the current channel
     * @param msg Message to send
     * @throws android.os.RemoteException
     */
    public void sendChannelMessage(String msg) throws RemoteException {
        if(mService.isConnected()) {
            try {
                IJumbleService binder = mService.getBinder();

                int channelId = mService.getBinder().getSessionChannel().getId();
                binder.sendChannelTextMessage(channelId, msg, false);
            }
            catch(NullPointerException e) {
                Log.d(TAG, "Unable to send command '" + msg + "': " + e);
            }
        }
    }

    /**
     * Sends state of the attached service in the format below. Spaces separate each value
     *  "state"
     *  r|t:     r if in RX mode, t if in TX mode
     *  00-1.0:   Activity threshold
     *  0-999:   Number of seconds ago sound was last heard
     */
    public void broadcastState()  {
        StringBuilder sb = new StringBuilder();
        sb.append(MSG_STATE);

        if(mService.isTransmitterMode()) {
            sb.append(" t ");
        }
        else {
            sb.append(" r ");
        }

        sb.append(mService.getVADThreshold());
        sb.append(" ");

        sb.append(mService.getNoiseTracker().getLastNoiseHeard() / 1000);

        try {
            sendChannelMessage(sb.toString());
        }
        catch(RemoteException e) {
            e.printStackTrace();
        }
    }

    public void handleStateMessage(Message msg) {
        try {
            // Parse message
            String[] parts = msg.getMessage().trim().toLowerCase().split(" ");
            String cmd = parts[0];
            if (!cmd.equals(MSG_STATE))
                throw new IllegalArgumentException("Unable to handle message, not a state message: " + msg.getMessage());

            boolean isTx = false;
            if(parts[1].equals("t"))
                isTx = true;

            float threshold = Float.parseFloat(parts[2]);
            int seconds = Integer.parseInt(parts[3]);

            notifyOnStateMessageListeners(mService, msg.getActorName(), isTx, threshold, seconds * 1000);
        }
        catch(ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Message given for state parsing does not contain enough parts");
            e.printStackTrace();
        }
    }

    // Listeners
    public void addOnStateMessageListener(OnStateMessageListener listener) {
        mStateListeners.add(listener);
    }

    public void removeOnStateMessageListener(OnStateMessageListener listener) {
        mStateListeners.remove(listener);
    }

    protected void notifyOnStateMessageListeners(MonitorService service, String user, boolean isTxMode, float threshold, long lastNoiseHeard) {
        for(OnStateMessageListener listener : mStateListeners) {
            listener.onStateMessageReceived(service, user, isTxMode, threshold, lastNoiseHeard);
        }
    }

    public interface OnStateMessageListener {
        public void onStateMessageReceived(MonitorService service, String user, boolean isTxMode, float threshold, long lastNoiseHeard);
    }
}
