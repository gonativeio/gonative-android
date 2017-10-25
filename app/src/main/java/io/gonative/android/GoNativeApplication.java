package io.gonative.android;

import android.app.Application;
import android.os.Message;
import android.webkit.ValueCallback;

import com.facebook.FacebookSdk;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeModule;
import com.onesignal.OSSubscriptionObserver;
import com.onesignal.OSSubscriptionState;
import com.onesignal.OSSubscriptionStateChanges;
import com.onesignal.OneSignal;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 9/2/15.
 * Copyright 2014 GoNative.io LLC
 */
public class GoNativeApplication extends Application {
    private RegistrationManager registrationManager;
    private Message webviewMessage;
    private ValueCallback webviewValueCallback;
    private boolean oneSignalRegistered = false;
    private int numOneSignalChecks = 0;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

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
                OneSignal.addSubscriptionObserver(new OSSubscriptionObserver() {
                    @Override
                    public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
                        OSSubscriptionState to = stateChanges.getTo();
                        registrationManager.setOneSignalUserId(to.getUserId(), to.getPushToken(), to.getSubscribed());

                        if (to.getSubscribed()) {
                            oneSignalRegistered = true;
                        }
                    }
                });

                // sometimes the subscription observer doesn't get fired, so check a few times on a timer
                final Runnable checkOneSignal = new Runnable() {
                    @Override
                    public void run() {
                        if (oneSignalRegistered) {
                            scheduler.shutdown();
                            return;
                        }

                        OSSubscriptionState state = OneSignal.getPermissionSubscriptionState().getSubscriptionStatus();
                        registrationManager.setOneSignalUserId(state.getUserId(), state.getPushToken(), state.getSubscribed());

                        if (state.getSubscribed()) {
                            scheduler.shutdown();
                            oneSignalRegistered = true;

                        } else if (numOneSignalChecks++ > 10) {
                            scheduler.shutdown();
                        }
                    }
                };
                scheduler.scheduleAtFixedRate(checkOneSignal, 2, 2, TimeUnit.SECONDS);
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
