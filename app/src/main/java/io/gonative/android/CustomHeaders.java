package io.gonative.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 5/1/17.
 */

public class CustomHeaders {
    public static Map<String, String> getCustomHeaders(Context context) {
        AppConfig appConfig = AppConfig.getInstance(context);
        if (appConfig.customHeaders == null) return null;

        HashMap<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : appConfig.customHeaders.entrySet()) {
            String key = entry.getKey();
            String val;
            try {
                val = interpolateValues(context, entry.getValue());
            } catch (UnsupportedEncodingException e) {
                val = null;
            }

            if (key != null & val != null) {
                result.put(key, val);
            }
        }

        return result;
    }

    private static String interpolateValues(Context context, String value) throws UnsupportedEncodingException {
        if (value == null) return null;

        if (value.contains("%DEVICEID%")) {
            @SuppressLint("HardwareIds")
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null) androidId = "";
            value = value.replace("%DEVICEID%", androidId);
        }

        if (value.contains("%DEVICENAME64%")) {
            // base 64 encoded name
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;
            String name;
            if (model.startsWith(manufacturer)) {
                name = model;
            } else {
                name = manufacturer + " " + model;
            }

            String name64 = Base64.encodeToString(name.getBytes("UTF-8"), Base64.NO_WRAP);
            value = value.replace("%DEVICENAME64%", name64);
        }

        return value;
    }
}
