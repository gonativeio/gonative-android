package io.gonative.android;

import android.app.Application;
import android.os.Message;
import android.webkit.ValueCallback;

import com.facebook.FacebookSdk;
import com.onesignal.OneSignal;
import com.parse.Parse;
import com.parse.ParseInstallation;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 9/2/15.
 * Copyright 2014 GoNative.io LLC
 */
public class GoNativeApplication extends Application {
    private RegistrationManager registrationManager;
    private Message webviewMessage;
    private ValueCallback webviewValueCallback;

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

        // some global webview setup
        WebViewSetup.setupWebviewGlobals(this);
    }

    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    public Message getWebviewMessage() {
        return webviewMessage;
    }

    public void setWebviewMessage(Message webviewMessage) {
        this.webviewMessage = webviewMessage;
    }

    public ValueCallback getWebviewValueCallback() {
        return webviewValueCallback;
    }

    public void setWebviewValueCallback(ValueCallback webviewValueCallback) {
        this.webviewValueCallback = webviewValueCallback;
    }
}
