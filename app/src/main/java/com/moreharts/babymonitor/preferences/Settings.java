package com.moreharts.babymonitor.preferences;

import android.content.SharedPreferences;
import android.content.Context;
import android.preference.PreferenceManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

/**
 * Preference manager
 * Created by traherom on 9/6/14.
 */
public class Settings {
    public static final String PREF_SETUP_COMPLETE = "setupComplete";
    public static final String PREF_CERT = "certificatePath";
    public static final String PREF_MODE = "isClientMode";

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

    public boolean isClientMode() {
        return preferences.getBoolean(PREF_MODE, true);
    }

    public boolean isServerMode() {
        return !isClientMode();
    }
}
