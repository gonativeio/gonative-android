package io.gonative.android;

import android.os.Message;
import android.util.Log;
import android.webkit.ValueCallback;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import java.util.List;
import java.util.Map;

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.Bridge;
import io.gonative.gonative_core.BridgeModule;

/**
 * Created by weiyin on 9/2/15.
 * Copyright 2014 GoNative.io LLC
 */
public class GoNativeApplication extends MultiDexApplication {

    private LoginManager loginManager;
    private RegistrationManager registrationManager;
    private WebViewPool webViewPool;
    private Message webviewMessage;
    private ValueCallback webviewValueCallback;
    private GoNativeWindowManager goNativeWindowManager;
    private List<BridgeModule> plugins;
    private final static String TAG = GoNativeApplication.class.getSimpleName();
    public final Bridge mBridge = new Bridge(this) {
        @Override
        protected List<BridgeModule> getPlugins() {
            if (GoNativeApplication.this.plugins == null) {
                GoNativeApplication.this.plugins = new PackageList(GoNativeApplication.this).getPackages();
            }

            return  GoNativeApplication.this.plugins;
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

        this.loginManager = new LoginManager(this);

        if (appConfig.registrationEndpoints != null) {
            this.registrationManager = new RegistrationManager(this);
            registrationManager.processConfig(appConfig.registrationEndpoints);
        }

        // some global webview setup
        WebViewSetup.setupWebviewGlobals(this);

        webViewPool = new WebViewPool();

        goNativeWindowManager = new GoNativeWindowManager();
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

    public Map<String, Object> getAnalyticsProviderInfo() {
        return mBridge.getAnalyticsProviderInfo();
    }

    // Needed for Crosswalk
    @SuppressWarnings("unused")
    public ValueCallback getWebviewValueCallback() {
        return webviewValueCallback;
    }

    public void setWebviewValueCallback(ValueCallback webviewValueCallback) {
        this.webviewValueCallback = webviewValueCallback;
    }

    public GoNativeWindowManager getWindowManager() {
        return goNativeWindowManager;
    }
}
