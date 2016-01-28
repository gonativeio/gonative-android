package io.gonative.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;
import java.util.Map;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 9/8/15.
 */
public class WebViewSetup {
    private static final String TAG = WebViewSetup.class.getName();

    public static void setupWebviewForActivity(GoNativeWebviewInterface webview, MainActivity activity) {
        if (!(webview instanceof LeanWebView)) {
            Log.e(TAG, "Expected webview to be of class LeanWebView and not " + webview.getClass().getName());
            return;
        }

        LeanWebView wv = (LeanWebView)webview;

        setupWebview(wv, activity);

        UrlNavigation urlNavigation = new UrlNavigation(activity);
        urlNavigation.setCurrentWebviewUrl(webview.getUrl());

        wv.setWebChromeClient(new GoNativeWebChromeClient(activity, urlNavigation));
        wv.setWebViewClient(new GoNativeWebviewClient(activity, urlNavigation));

        FileDownloader fileDownloader = activity.getFileDownloader();
        if (fileDownloader != null) {
            wv.setDownloadListener(fileDownloader);
        }

        ProfilePicker profilePicker = activity.getProfilePicker();
        wv.removeJavascriptInterface("gonative_profile_picker");
        if (profilePicker != null) {
            wv.addJavascriptInterface(profilePicker.getProfileJsBridge(), "gonative_profile_picker");
        }

        AppConfig appConfig = AppConfig.getInstance(activity);

        wv.removeJavascriptInterface("gonative_dynamic_update");
        if (appConfig.updateConfigJS != null) {
            wv.addJavascriptInterface(appConfig.getJsBridge(), "gonative_dynamic_update");
        }

        wv.removeJavascriptInterface("gonative_status_checker");
        wv.addJavascriptInterface(activity.getStatusCheckerBridge(), "gonative_status_checker");

    }

    @SuppressWarnings("deprecation")
    @SuppressLint("SetJavaScriptEnabled")
    public static void setupWebview(GoNativeWebviewInterface webview, Context context) {
        if (!(webview instanceof LeanWebView)) {
            Log.e(TAG, "Expected webview to be of class LeanWebView and not " + webview.getClass().getName());
            return;
        }

        LeanWebView wv = (LeanWebView)webview;

        WebSettings webSettings = wv.getSettings();

        if (AppConfig.getInstance(context).allowZoom) {
            webSettings.setBuiltInZoomControls(true);
        }
        else {
            webSettings.setBuiltInZoomControls(false);
        }

        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        webSettings.setDomStorageEnabled(true);
        File cachePath = new File(context.getCacheDir(), MainActivity.webviewCacheSubdir);
        webSettings.setAppCachePath(cachePath.getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
        webSettings.setDatabaseEnabled(true);

        webSettings.setSaveFormData(false);
        webSettings.setSavePassword(false);
        webSettings.setUserAgentString(AppConfig.getInstance(context).userAgent);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setGeolocationEnabled(AppConfig.getInstance(context).usesGeolocation);
    }

    public static void setupWebviewGlobals(Context context) {
        // WebView debugging
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Map<String,Object> installation = Installation.getInfo(context);
            String dist = (String)installation.get("distribution");
            if (dist != null && (dist.equals("debug") || dist.equals("adhoc"))) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }
    }

    public static void removeCallbacks(LeanWebView webview) {
        webview.setWebViewClient(null);
        webview.setWebChromeClient(null);
    }
}
