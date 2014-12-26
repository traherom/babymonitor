package com.moreharts.babymonitor.preferences;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.moreharts.babymonitor.service.MonitorService;
import com.moreharts.babymonitor.ui.ServerList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

/**
 * Preference manager
 * Created by traherom on 9/6/14.
 */
public class Settings {
    public static final String PREF_SETUP_COMPLETE = "setupComplete";
    public static final String PREF_START_ON_BOOT = "runOnBoot";
    public static final String PREF_CERT = "certificatePath";
    public static final String PREF_MODE = "isTransmitterMode";
    public static final String PREF_HOST = "mumbleHost";
    public static final String PREF_CHANNEL = "mumbleChannel";
    public static final String PREF_PORT = "mumblePort";
    public static final String PREF_USERNAME = "mumbleUser";

    public static final String PREF_THRESHOLD = "defaultSensitivity";
    public static final String PREF_VIBRATION = "vibrationOn";
    public static final String PREF_LED = "ledOn";
    private static final String PREF_NOTIFICATION_SOUND = "notificationUri";

    public static final String PREF_ENABLE_MOBILE = "mobileAllowed";
    public static final String PREF_MOBILE_FULL_AUDIO = "mobileFullAudioOn";

    public static final String PREF_ENABLE_WIFI = "wifiAllowed";
    public static final String PREF_WIFI_FULL_AUDIO = "wifiFullAudioOn";

    private ArrayList<OnChangeListener> mListeners = new ArrayList<OnChangeListener>();
    private final SharedPreferences mPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener mChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            notifyOnChangeListeners();
        }
    };

    private static Settings mInstance = null;

    public static Settings getInstance(Context context) {
        if(mInstance == null)
            mInstance = new Settings(context);

        return mInstance;
    }

    private Settings(Context ctx) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        mPreferences.registerOnSharedPreferenceChangeListener(mChangeListener);
    }

    public boolean isFirstRun() {
        return getCertificate() == null;
    }

    /**
     * Attempts to read the certificate from the path specified in settings.
     * @return The parsed bytes of the certificate, or null otherwise.
     */
    public byte[] getCertificate() {
        try {
            FileInputStream inputStream = new FileInputStream(mPreferences.getString(PREF_CERT, ""));
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            return buffer;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setCertificate(File cert) {
        setCertificatePath(cert.getAbsolutePath());
    }

    public void setCertificatePath(String path) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(PREF_CERT, path);
        editor.apply();
    }

    public boolean getIsTxMode() {
        return mPreferences.getBoolean(PREF_MODE, true);
    }

    public void setTxMode(boolean isTx) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(PREF_MODE, isTx);
        editor.apply();
    }

    public String getUserName() {
        String user = mPreferences.getString(PREF_USERNAME, null);
        if(user == null) {
            user = MonitorService.MUMBLE_USER_START + Math.abs(new Random().nextInt(100));
            setUserName(user);
        }

        return user;
    }

    public void setUserName(String name) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(PREF_USERNAME, name);
        editor.apply();
    }

    public String getMumbleChannel() {
        return mPreferences.getString(PREF_CHANNEL, MonitorService.PREF_MUMBLE_CHANNEL);
    }

    public void setMumbleChannel(String channel) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(PREF_CHANNEL, channel);
        editor.apply();
    }

    public String getMumbleHost() {
        return mPreferences.getString(PREF_HOST, null);
    }

    public int getMumblePort() {
        return mPreferences.getInt(PREF_PORT, ServerList.DEFAULT_PORT);
    }

    public void setMumbleHost(String host) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(PREF_HOST, host);
        editor.apply();
    }

    public void setMumblePort(int port) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt(PREF_PORT, port);
        editor.apply();
    }

    public boolean getStartOnBoot() {
        return mPreferences.getBoolean(PREF_START_ON_BOOT, true);
    }

    public void setStartOnBoot(boolean shouldStart) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(PREF_START_ON_BOOT, shouldStart);
        editor.apply();
    }

    public float getThreshold() {
        return mPreferences.getFloat(PREF_THRESHOLD, MonitorService.DEFAULT_THRESHOLD);
    }

    public void setThreshold(float thresh) {
        if(thresh < 0 || thresh > 1)
            throw new IllegalArgumentException("Sensitivity must be between 0 and 1");

        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putFloat(PREF_THRESHOLD, thresh);
        editor.apply();
    }

    public boolean getMobileEnabled() {
        return mPreferences.getBoolean(PREF_ENABLE_MOBILE, false);
    }

    public void setMobileEnabled(boolean enabled) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(PREF_ENABLE_MOBILE, enabled);
        editor.apply();
    }

    public boolean getMobileFullAudioOn() {
        return mPreferences.getBoolean(PREF_MOBILE_FULL_AUDIO, false);
    }

    public void setMobileFullAudioOn(boolean on) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(PREF_MOBILE_FULL_AUDIO, on);
        editor.apply();
    }

    public boolean getWifiEnabled() {
        return mPreferences.getBoolean(PREF_ENABLE_WIFI, false);
    }

    public void setWifiEnabled(boolean enabled) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(PREF_ENABLE_WIFI, enabled);
        editor.apply();
    }

    public boolean getWifiFullAudioOn() {
        return mPreferences.getBoolean(PREF_WIFI_FULL_AUDIO, false);
    }

    public void setWifiFullAudioOn(boolean on) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(PREF_WIFI_FULL_AUDIO, on);
        editor.apply();
    }

    public boolean isVibrationOn() {
        return mPreferences.getBoolean(PREF_VIBRATION, false);
    }

    public void setVibrationOn(boolean vibrate) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(PREF_VIBRATION, vibrate);
        editor.apply();
    }

    public boolean isLEDOn() {
        return mPreferences.getBoolean(PREF_LED, true);
    }

    public void setLEDOn(boolean vibrate) {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(PREF_LED, vibrate);
        editor.apply();
    }

    public Uri getNotificationSound() {
        String found = mPreferences.getString(PREF_NOTIFICATION_SOUND, null);
        if(found != null)
            return Uri.parse(found);
        else
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    public interface OnChangeListener {
        public void onChange(Settings settings);
    }

    public void addOnChangeListener(OnChangeListener listener) {
        mListeners.add(listener);
    }

    public void removeOnChangeListener(OnChangeListener listener) {
        mListeners.remove(listener);
    }

    private void notifyOnChangeListeners() {
        for(OnChangeListener listener : mListeners) {
            listener.onChange(this);
        }
    }
}
