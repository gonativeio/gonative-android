package io.gonative.android;

import android.content.Intent;
import android.os.Message;
import android.util.Log;
import android.webkit.ValueCallback;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.multidex.MultiDexApplication;

import com.onesignal.OSDeviceState;
import com.onesignal.OSSubscriptionObserver;
import com.onesignal.OSSubscriptionState;
import com.onesignal.OSSubscriptionStateChanges;
import com.onesignal.OneSignal;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.gonative.android.library.AppConfig;
import io.gonative.gonative_core.Bridge;
import io.gonative.gonative_core.BridgeModule;

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
    public final Bridge mBridge = new Bridge(this) {
        @Override
        protected List<BridgeModule> getPlugins() {
            return new PackageList(GoNativeApplication.this).getPackages();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        mBridge.onApplicationCreate(this);

        AppConfig appConfig = AppConfig.getInstance(this);
        if (appConfig.configError != null) {
            Toast.makeText(this, "Invalid appConfig json", Toast.LENGTH_LONG).show();
            Log.e(TAG, "AppConfig error", appConfig.configError);
        }

        if (appConfig.oneSignalEnabled) {
            OneSignal.initWithContext(this);
            OneSignal.setRequiresUserPrivacyConsent(appConfig.oneSignalRequiresUserPrivacyConsent);
            OneSignal.setAppId(appConfig.oneSignalAppId);
            OneSignal.setNotificationOpenedHandler(new OneSignalNotificationHandler(this));
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
                        registrationManager.setOneSignalUserId(to.getUserId(), to.getPushToken(), to.isSubscribed());
                    }

                    if (to.isSubscribed()) {
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

                    OSDeviceState state = OneSignal.getDeviceState();
                    if (state == null) {
                        Log.w(TAG, "OSDeviceState is null. OneSignal.initWithContext not called");
                        return;
                    }
                    if (registrationManager != null) {
                        registrationManager.setOneSignalUserId(state.getUserId(), state.getPushToken(), state.isSubscribed());
                    }

                    if (state.isSubscribed()) {
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
