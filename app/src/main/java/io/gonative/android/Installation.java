package io.gonative.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 8/8/14.
 */
public class Installation {
    private static final String TAG = Installation.class.getName();

    private static String sID = null;
    private static final String INSTALLATION = "INSTALLATION";

    public synchronized static String id(Context context) {
        if (sID == null) {
            File installation = new File(context.getFilesDir(), INSTALLATION);
            try {
                if (!installation.exists())
                    writeInstallationFile(installation);
                sID = readInstallationFile(installation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return sID;
    }

    public static Map<String,Object> getInfo(Context context) {
        HashMap<String,Object> info = new HashMap<String,Object>();

        info.put("platform", "android");

        String publicKey = AppConfig.getInstance(context).publicKey;
        if (publicKey == null) publicKey = "";
        info.put("publicKey", publicKey);

        String deviceRegKey = AppConfig.getInstance(context).deviceRegKey;
        if (deviceRegKey != null) info.put("deviceRegKey", deviceRegKey);

        String packageName = context.getPackageName();
        info.put("appId", packageName);


        PackageManager manager = context.getPackageManager();
        try {
            PackageInfo packageInfo = manager.getPackageInfo(packageName, 0);
            info.put("appVersion", packageInfo.versionName);
            info.put("appVersionCode", packageInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        String distribution = null;
        boolean isDebuggable =  ( 0 != ( context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE ) );
        if (isDebuggable) {
            distribution = "debug";
        } else {
            String installer = manager.getInstallerPackageName(packageName);
            if (installer == null) {
                distribution = "adhoc";
            } else if (installer.equals("com.android.vending") || installer.equals("com.google.market")) {
                distribution = "playstore";
            } else if (installer.equals("com.amazon.venezia")) {
                distribution = "amazon";
            } else {
                distribution = installer;
            }
        }
        info.put("distribution", distribution);

        info.put("language", Locale.getDefault().getLanguage());
        info.put("os", "Android");
        info.put("osVersion", Build.VERSION.RELEASE);
        info.put("model", new StringBuilder(Build.MANUFACTURER).append(" ").append(Build.MODEL).toString());
        info.put("hardware", Build.FINGERPRINT);
        info.put("timeZone", TimeZone.getDefault().getID());

        info.put("installationId", Installation.id(context));

        return info;
    }

    private static String readInstallationFile(File installation) throws IOException {
        RandomAccessFile f = new RandomAccessFile(installation, "r");
        byte[] bytes = new byte[(int) f.length()];
        f.readFully(bytes);
        f.close();
        return new String(bytes);
    }

    private static void writeInstallationFile(File installation) throws IOException {
        FileOutputStream out = new FileOutputStream(installation);
        String id = UUID.randomUUID().toString();
        out.write(id.getBytes());
        out.close();
    }
}