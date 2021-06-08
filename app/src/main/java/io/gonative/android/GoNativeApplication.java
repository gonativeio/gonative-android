package io.gonative.android;

import android.content.Intent;
import android.os.Message;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.multidex.MultiDexApplication;

import android.util.Log;
import android.webkit.ValueCallback;
import android.widget.Toast;

import com.facebook.FacebookSdk;
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
public class GoNativeApplication extends MultiDexApplication {
    public static final String ONESIGNAL_STATUS_CHANGED_MESSAGE = "io.gonative.android.onesignal.statuschanged";

    private LoginManager loginManager;
    private RegistrationManager registrationManager;
    private WebViewPool webViewPool;
    private Message webviewMessage;
    private ValueCallback webviewValueCallback;
    private boolean oneSignalRegistered = false;
    private int numOneSignalChecks = 0;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final static String TAG = GoNativeApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        AppConfig appConfig = AppConfig.getInstance(this);
        if (appConfig.configError != null) {
            Toast.makeText(this, "Invalid appConfig json", Toast.LENGTH_LONG).show();
            Log.e(TAG, "AppConfig error", appConfig.configError);
        }

        if (appConfig.oneSignalEnabled) {
            LeanUtils.initOneSignal(this, appConfig);
        }

        if (appConfig.facebookEnabled) {
            Log.d(TAG, "Facebook is enabled with  App ID: " + FacebookSdk.getApplicationId());
            FacebookSdk.setAutoLogAppEventsEnabled(appConfig.facebookAutoLogging);
            FacebookSdk.setAdvertiserIDCollectionEnabled(true);
        }

        this.loginManager = new LoginManager(this);

        if (appConfig.registrationEndpoints != null) {
            this.registrationManager = new RegistrationManager(this);
            registrationManager.processConfig(appConfig.registrationEndpoints);
        }


        if (appConfig.oneSignalEnabled) {
            OneSignal.addSubscriptionObserver(new OSSubscriptionObserver() {
                @Override
                public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
                    OSSubscriptionState to = stateChanges.getTo();
                    if (registrationManager != null) {
                        registrationManager.setOneSignalUserId(to.getUserId(), to.getPushToken(), to.getSubscribed());
                    }

                    if (to.getSubscribed()) {
                        oneSignalRegistered = true;
                    }

                    LocalBroadcastManager.getInstance(GoNativeApplication.this)
                            .sendBroadcast(new Intent(ONESIGNAL_STATUS_CHANGED_MESSAGE));
                }
            });

            // sometimes the subscription observer doesn't get fired, so check a few times on a timer
            final Runnable checkOneSignal = new Runnable() {
                @Override
                public void run() {
                    // the subscription observer fired, so stop checking
                    if (oneSignalRegistered) {
                        scheduler.shutdown();
                        return;
                    }

                    OSSubscriptionState state = OneSignal.getPermissionSubscriptionState().getSubscriptionStatus();
                    if (registrationManager != null) {
                        registrationManager.setOneSignalUserId(state.getUserId(), state.getPushToken(), state.getSubscribed());
                    }

                    if (state.getSubscribed()) {
                        scheduler.shutdown();
                        oneSignalRegistered = true;

                        LocalBroadcastManager.getInstance(GoNativeApplication.this)
                                .sendBroadcast(new Intent(ONESIGNAL_STATUS_CHANGED_MESSAGE));
                    } else if (numOneSignalChecks++ > 10) {
                        scheduler.shutdown();
                    }
                }
            };
            scheduler.scheduleAtFixedRate(checkOneSignal, 2, 2, TimeUnit.SECONDS);
        }

        // some global webview setup
        WebViewSetup.setupWebviewGlobals(this);

        webViewPool = new WebViewPool();

    }

    public LoginManager getLoginManager() {
        return loginManager;
    }

    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    public WebViewPool getWebViewPool() {
        return webViewPool;
    }

    public Message getWebviewMessage() {
        return webviewMessage;
    }

    public void setWebviewMessage(Message webviewMessage) {
        this.webviewMessage = webviewMessage;
    }

    // Needed for Crosswalk
    @SuppressWarnings("unused")
    public ValueCallback getWebviewValueCallback() {
        return webviewValueCallback;
    }

    public void setWebviewValueCallback(ValueCallback webviewValueCallback) {
        this.webviewValueCallback = webviewValueCallback;
    }
}
