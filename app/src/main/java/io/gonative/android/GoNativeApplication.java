package io.gonative.android;

import android.app.Application;
import android.os.Message;
import android.webkit.ValueCallback;

import com.facebook.FacebookSdk;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeModule;
import com.onesignal.OneSignal;

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

        if (appConfig.oneSignalEnabled) {
            OneSignal.init(this, "REMOTE", appConfig.oneSignalAppId,
                    new OneSignalNotificationHandler(this));
            OneSignal.setInFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification);
        }

        if (appConfig.facebookEnabled) {
            FacebookSdk.sdkInitialize(getApplicationContext());
        }

        if (appConfig.registrationEndpoints != null) {
            this.registrationManager = new RegistrationManager(this);
            registrationManager.processConfig(appConfig.registrationEndpoints);

            if (appConfig.oneSignalEnabled) {
                OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
                    @Override
                    public void idsAvailable(String userId, String registrationId) {
                        registrationManager.setOneSignalUserId(userId, registrationId);
                    }
                });
            }
        }

        // some global webview setup
        WebViewSetup.setupWebviewGlobals(this);

        Iconify.with(new FontAwesomeModule());
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
