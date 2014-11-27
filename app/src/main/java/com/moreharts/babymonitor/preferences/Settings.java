package com.moreharts.babymonitor.preferences;

import android.content.SharedPreferences;
import android.content.Context;
import android.preference.PreferenceManager;

import com.moreharts.babymonitor.service.MonitorService;
import com.moreharts.babymonitor.ui.ServerList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
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

    private final SharedPreferences preferences;

    public static Settings getInstance(Context context) {
        return new Settings(context);
    }

    private Settings(Context ctx) {
        preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
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
            FileInputStream inputStream = new FileInputStream(preferences.getString(PREF_CERT, ""));
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
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREF_CERT, path);
        editor.apply();
    }

    public boolean getIsTxMode() {
        return preferences.getBoolean(PREF_MODE, true);
    }

    public void setTxMode(boolean isTx) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREF_MODE, isTx);
        editor.apply();
    }

    public String getUserName() {
        String user = preferences.getString(PREF_USERNAME, null);
        if(user == null) {
            user = MonitorService.MUMBLE_USER_START + Math.abs(new Random().nextInt());
            setUserName(user);
        }

        return user;
    }

    public void setUserName(String name) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREF_USERNAME, name);
        editor.apply();
    }

    public String getMumbleChannel() {
        return preferences.getString(PREF_CHANNEL, MonitorService.PREF_MUMBLE_CHANNEL);
    }

    public void setMumbleChannel(String channel) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREF_CHANNEL, channel);
        editor.apply();
    }

    public String getMumbleHost() {
        return preferences.getString(PREF_HOST, null);
    }

    public int getMumblePort() {
        return preferences.getInt(PREF_PORT, ServerList.DEFAULT_PORT);
    }

    public void setMumbleHost(String host) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREF_HOST, host);
        editor.apply();
    }

    public void setMumblePort(int port) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREF_PORT, port);
        editor.apply();
    }

    public boolean getStartOnBoot() {
        return preferences.getBoolean(PREF_START_ON_BOOT, true);
    }

    public void setStartOnBoot(boolean shouldStart) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREF_START_ON_BOOT, shouldStart);
        editor.apply();
    }

    public float getThreshold() {
        return preferences.getFloat(PREF_THRESHOLD, MonitorService.DEFAULT_THRESHOLD);
    }

    public void setThreshold(float thresh) {
        if(thresh < 0 || thresh > 1)
            throw new IllegalArgumentException("Sensitivity must be between 0 and 1");

        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat(PREF_THRESHOLD, thresh);
        editor.apply();
    }

    public boolean isVibrationOn() {
        return preferences.getBoolean(PREF_VIBRATION, false);
    }

    public void setVibrationOn(boolean vibrate) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREF_VIBRATION, vibrate);
        editor.apply();
    }

    public boolean isLEDOn() {
        return preferences.getBoolean(PREF_LED, true);
    }

    public void setLEDOn(boolean vibrate) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(PREF_LED, vibrate);
        editor.apply();
    }
}
