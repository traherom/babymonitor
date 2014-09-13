/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.moreharts.babymonitor.preferences;

import android.content.Context;

import com.morlunk.jumble.net.JumbleCertificateGenerator;

import org.spongycastle.operator.OperatorCreationException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/*
 * Heavily derived from PlumbleCertificateManager
 */
public class BabyCertificateManager {
	private static final String CERTIFICATE_FORMAT = "babymonitor-%s.p12";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
	
	/**
	 * Generates a new X.509 passwordless certificate in PKCS12 format for connection to a Mumble server.
	 * This certificate is stored in the BabyMonitor folder, in the format {@value #CERTIFICATE_FORMAT} where the timestamp is substituted in.
	 * @return The path of the generated certificate if the operation was a success. Otherwise, null.
	 */
    public static File generateCertificate(Context context) throws NoSuchAlgorithmException, OperatorCreationException, CertificateException, KeyStoreException, NoSuchProviderException, IOException {
        File certificateDirectory = getCertificateDirectory(context);

        String date = DATE_FORMAT.format(new Date());
        String certificateName = String.format(Locale.US, CERTIFICATE_FORMAT, date);
        File certificateFile = new File(certificateDirectory, certificateName);
        FileOutputStream outputStream = new FileOutputStream(certificateFile);
        JumbleCertificateGenerator.generateCertificate(outputStream);
        return certificateFile;
    }

    /**
	 * Returns a list of certificates in the BabyMonitor, ending with pfx or p12.
	 * @return A list of {@link File} objects containing certificates.
	 */
	public static List<File> getAvailableCertificates(Context context) throws IOException {
		File certificateDirectory = getCertificateDirectory(context);
		
		File[] p12Files = certificateDirectory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith("pfx") ||
                       pathname.getName().endsWith("p12");
			}
		});
		
		return Arrays.asList(p12Files);
	}

    /**
	 * Returns the certificate directory
	 * @return The {@link File} object of the directory.
	 */
	public static File getCertificateDirectory(Context context) throws IOException {
        return context.getFilesDir();
	}
}
