package com.moreharts.babymonitor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.preference.PreferenceManager;

import com.moreharts.babymonitor.ui.ClientStatus;
import com.moreharts.babymonitor.preferences.BabyCertificateManager;
import com.moreharts.babymonitor.preferences.Settings;

import org.spongycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.io.File;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;


public class BabyMonitor extends Activity {
    public static final String TAG = "BabyMonitor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure preferences are set up correctly
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        Settings settings = Settings.getInstance(this);

        // Is this the first run? Start activity to set everything up correctly TODO
        if(settings.isFirstRun()) {
            Log.i(TAG, "Running first-time setup");

            try {
                // Generate cert for client to connect with (and the "client" portion of the server)
                File clientCert = BabyCertificateManager.generateCertificate(getApplicationContext());
                Log.d(TAG, "Client certificate generated at " + clientCert.getAbsolutePath());
                settings.setCertificate(clientCert);
            }
            catch(NoSuchAlgorithmException e) {
                Log.e(TAG, "NoSuchAlgorithmException while generating certificate: " + e);
            }
            catch(OperatorCreationException e) {
                Log.e(TAG, "OperatorCreationException while generating certificate: " + e);
            }
            catch(CertificateException e) {
                Log.e(TAG, "CertificateException while generating certificate: " + e);
            }
            catch(KeyStoreException e) {
                Log.e(TAG, "KeyStoreException while generating certificate: " + e);
            }
            catch(NoSuchProviderException e) {
                Log.e(TAG, "NoSuchProvider while generating certificate: " + e);
            }
            catch(IOException e) {
                Log.e(TAG, "IOException while generating certificate: " + e);
            }
        }

        // Start the status screen
        startActivity(new Intent(this, ClientStatus.class));
    }
}
