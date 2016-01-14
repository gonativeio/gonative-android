package io.gonative.android;

import android.app.Application;

import com.parse.Parse;
import com.parse.ParseInstallation;

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

        if (appConfig.registrationEndpoints != null) {
            this.registrationManager = new RegistrationManager(this);
            registrationManager.processConfig(appConfig.registrationEndpoints);

            if (parseInstallationId != null) registrationManager.setParseInstallationId(parseInstallationId);
        }
    }

    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }
}
