package io.gonative.android;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.facebook.FacebookSdk;
import com.onesignal.OneSignal;
import com.parse.Parse;
import com.parse.ParseInstallation;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.Signature;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 9/2/15.
 * Copyright 2014 GoNative.io LLC
 */
public class GoNativeApplication extends Application {
    private RegistrationManager registrationManager;

    @Override
    public void onCreate() {
        super.onCreate();

        AppConfig appConfig = AppConfig.getInstance(this);
        String parseInstallationId = null;
        if (appConfig.parseEnabled) {
            Parse.initialize(this, appConfig.parseApplicationId, appConfig.parseClientKey);
            ParseInstallation.getCurrentInstallation().saveInBackground();
            parseInstallationId = ParseInstallation.getCurrentInstallation().getInstallationId();
        }

        if (appConfig.oneSignalEnabled) {
            OneSignal.init(this, appConfig.oneSignalGoogleProjectId, appConfig.oneSignalAppId,
                    new OneSignalReceiver(this));
            OneSignal.enableNotificationsWhenActive(true);
        }

        if (appConfig.facebookEnabled) {
            FacebookSdk.sdkInitialize(getApplicationContext());
        }

        if (appConfig.registrationEndpoints != null) {
            this.registrationManager = new RegistrationManager(this);
            registrationManager.processConfig(appConfig.registrationEndpoints);

            if (parseInstallationId != null) registrationManager.setParseInstallationId(parseInstallationId);

            if (appConfig.oneSignalEnabled) {
                OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
                    @Override
                    public void idsAvailable(String userId, String registrationId) {
                        registrationManager.setOneSignalUserId(userId);
                    }
                });
            }
        }
    }

    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }
}
