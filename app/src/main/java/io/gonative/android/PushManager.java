package io.gonative.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 8/10/14.
 */
public class PushManager {
    private static final String GONATIVE_GOOGLE_PROJECT_ID = "132633329658";
    private static final String GONATIVE_REG_URL = "https://push.gonative.io/api/register";

    private static final String TAG = PushManager.class.getName();
    private static final String PROPERTY_PUSHREG_APPVERSION = "push_app_version";
    private static final String PROPERTY_PUSHREG_GOOGLE_PROJECT_ID = "push_google_project_id";
    private static final String PROPERTY_PUSHREG_ID = "push_registration_id";
    private static final String SHARED_PREF_FILE = "gcm_registration";

    private Context context;
    private MainActivity mainActivity;
    private GoogleCloudMessaging gcm;
    private String regid;
    private String googleProjectId;

    public PushManager(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        this.context = mainActivity.getApplicationContext();

        AppConfig appConfig = AppConfig.getInstance(this.context);
        if (appConfig.googleProjectId != null) {
            // using custom push service
            this.googleProjectId = appConfig.googleProjectId;
        } else {
            // gonative push service
            this.googleProjectId = GONATIVE_GOOGLE_PROJECT_ID;
        }
    }

    public void register() {
        AppConfig appConfig = AppConfig.getInstance(this.context);
        if (!appConfig.pushNotifications) return;

        if (googleProjectId.equals(GONATIVE_GOOGLE_PROJECT_ID) && appConfig.publicKey == null) {
            Log.w(TAG, "publicKey is required for push");
            return;
        }

        if (checkPlayServices()) {
            this.gcm = GoogleCloudMessaging.getInstance(this.context);

            regid = getRegistrationId();
            if (regid.isEmpty()) {
                registerInBackground();
            } else {
                sendRegistrationIdToBackend(regid);
            }

        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
    }

    private void registerInBackground() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(googleProjectId);

                    sendRegistrationIdToBackend(regid);
                    storeRegistrationId(regid);
                } catch (IOException e) {
                    Log.e(TAG, "Error registering for push notifications", e);
                }

                return null;
            }
        }.execute(null, null, null);
    }

    private void sendRegistrationIdToBackend(final String regid) {
        // registration service
        RegistrationManager registrationManager = ((GoNativeApplication)mainActivity.getApplication()).getRegistrationManager();
        if (registrationManager != null) {
            registrationManager.setPushRegistrationToken(regid);
        }

        if (this.googleProjectId.equals(GONATIVE_GOOGLE_PROJECT_ID)) {
            new AsyncTask<Void,Void,Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    JSONObject json = new JSONObject(Installation.getInfo(context));

                    try {
                        json.put("registrationId", regid);

                        URL url = new URL(GONATIVE_REG_URL);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("POST");
                        connection.setRequestProperty("Content-Type", "application/json");
                        connection.setDoOutput(true);
                        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
                        writer.write(json.toString());
                        writer.close();
                        connection.connect();
                        int result = connection.getResponseCode();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }

                    return null;
                }
            }.execute();
        }
    }

    private void storeRegistrationId(String regId) {
        final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_FILE, Context.MODE_PRIVATE);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_PUSHREG_ID, regId);
        editor.putInt(PROPERTY_PUSHREG_APPVERSION, appVersion);
        editor.putString(PROPERTY_PUSHREG_GOOGLE_PROJECT_ID, this.googleProjectId);
        editor.commit();
    }

    private String getRegistrationId() {
        final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREF_FILE, Context.MODE_PRIVATE);
        String registrationId = prefs.getString(PROPERTY_PUSHREG_ID, "");

        if (registrationId.isEmpty()) {
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_PUSHREG_APPVERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed. Regenerating registration id");
            return "";
        }

        if (!prefs.getString(PROPERTY_PUSHREG_GOOGLE_PROJECT_ID, "").equals(this.googleProjectId)) {
            Log.i(TAG, "Google project id changed. Regenerating registration id");
            return "";
        }
        return registrationId;
    }

    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (Exception e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    // Check for Google Play Services APK.
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
        if (resultCode == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED) {
            GooglePlayServicesUtil.getErrorDialog(resultCode, mainActivity,
                    MainActivity.REQUEST_PLAY_SERVICES_RESOLUTION)
                    .show();
            return false;
        }
        else if (resultCode != ConnectionResult.SUCCESS) {
            Log.i(TAG, "This device is not supported for Google Play Services");
            return false;
        } else {
            return true;
        }
    }
}
