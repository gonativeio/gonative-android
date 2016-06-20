package io.gonative.android;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.webkit.ValueCallback;

import org.xwalk.core.JavascriptInterface;
import org.xwalk.core.XWalkDownloadListener;
import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;

import java.util.Map;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 9/8/15.
 */
public class WebViewSetup {
    private static final String TAG = WebViewSetup.class.getName();

    static private class XWalkProfileBridge {
        ProfilePicker.ProfileJsBridge bridge;
        public XWalkProfileBridge(ProfilePicker.ProfileJsBridge bridge) {
            this.bridge = bridge;
        }
        @JavascriptInterface
        public void parseJson(String s) {
            bridge.parseJson(s);
        }
    }

    static private class XWalkStatusBridge {
        MainActivity.StatusCheckerBridge bridge;
        public XWalkStatusBridge(MainActivity.StatusCheckerBridge bridge) {
            this.bridge = bridge;
        }
        @JavascriptInterface
        public void onReadyState(String state) {
            bridge.onReadyState(state);
        }
    }

    static private class XWalkDynamicAppConfigBridge {
        AppConfig.AppConfigJsBridge bridge;
        public XWalkDynamicAppConfigBridge(AppConfig.AppConfigJsBridge bridge) {
            this.bridge = bridge;
        }
        @JavascriptInterface
        public void parseJson(String s) {
            bridge.parseJson(s);
        }
    }

    public static void setupWebviewForActivity(GoNativeWebviewInterface webview, MainActivity activity) {
        if (!(webview instanceof XWalkView)) {
            Log.e(TAG, "Expected webview to be of class XWalkView and not " + webview.getClass().getName());
            return;
        }

        AppConfig appConfig = AppConfig.getInstance(activity);

        LeanWebView wv = (LeanWebView)webview;

        // we need to tell appconfig what the default crosswalk user agent is. Crosswalk 14 does not
        // have an API to get it, and rather than use async javascript, fake it here.
        String userAgent = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE +
                "; " + Build.MODEL + " Build/" + Build.DISPLAY +
                ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.130 Crosswalk/14.43.343.24 Mobile Safari/537.36";
        appConfig.setWebviewDefaultUserAgent(userAgent);

        // setupwebview will actually set the user agent for crosswalk. Important to do it there
        // rather than this function so that webview pools will also get the right user agent.
        setupWebview(wv, activity);

        UrlNavigation urlNavigation = new UrlNavigation(activity);
        urlNavigation.setCurrentWebviewUrl(wv.getUrl());

        wv.setUIClient(new GoNativeXWalkUIClient(wv, urlNavigation, activity));
        wv.setResourceClient(new GoNativeXWalkResourceClient(wv, urlNavigation, activity));

        final FileDownloader fileDownloader = activity.getFileDownloader();
        if (fileDownloader != null) {
            wv.setDownloadListener(new XWalkDownloadListener(activity) {
                @Override
                public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                            String mimetype, long contentLength) {
                    fileDownloader.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength);
                }
            });
        }

        // what follows are some javascript interface classes. We create temporary classes here
        // because we cannot put the org.xwalk.core.JavascriptInterface annotations in the main code.
        ProfilePicker profilePicker = activity.getProfilePicker();
        if (profilePicker != null) {
            final ProfilePicker.ProfileJsBridge profileJsBridge = profilePicker.getProfileJsBridge();
            wv.addJavascriptInterface(new XWalkProfileBridge(profileJsBridge), "gonative_profile_picker");
        }

        if (appConfig.updateConfigJS != null) {
            final AppConfig.AppConfigJsBridge appConfigJsBridge = appConfig.getJsBridge();
            wv.addJavascriptInterface(new XWalkDynamicAppConfigBridge(appConfigJsBridge), "gonative_dynamic_update");
        }

        final MainActivity.StatusCheckerBridge statusCheckerBridge = activity.getStatusCheckerBridge();
        wv.addJavascriptInterface(new XWalkStatusBridge(statusCheckerBridge), "gonative_status_checker");

        // pass back to webview that created it
        if (activity.getIntent().getBooleanExtra(MainActivity.EXTRA_WEBVIEW_WINDOW_OPEN, false)) {
            ValueCallback callback = ((GoNativeApplication) activity.getApplication()).getWebviewValueCallback();
            if (callback != null) {
                ((GoNativeApplication)activity.getApplication()).getWebviewValueCallback().onReceiveValue(wv);
            }
        }
    }

    public static void setupWebview(GoNativeWebviewInterface webview, Context context) {
        if (!(webview instanceof XWalkView)) {
            Log.e(TAG, "Expected webview to be of class XWalkView and not " + webview.getClass().getName());
            return;
        }

        AppConfig appConfig = AppConfig.getInstance(context);
        ((LeanWebView)webview).setUserAgentString(appConfig.userAgent);
    }

    public static void setupWebviewGlobals(Context context) {
        // XWalkView debugging
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Map<String,Object> installation = Installation.getInfo(context);
            String dist = (String)installation.get("distribution");
            if (dist != null && (dist.equals("debug") || dist.equals("adhoc"))) {
                try {
                    XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting XWalkPreferences.REMOTE_DEBUGGING", e);
                }

            }
        }

        // Fixes issues with setting alpha != 1.0
        try {
            XWalkPreferences.setValue(XWalkPreferences.ANIMATABLE_XWALK_VIEW, true);
        } catch (Exception e) {
            Log.e(TAG, "Error setting XWalkPreferences.ANIMATABLE_XWALK_VIEW", e);
        }

        XWalkPreferences.setValue(XWalkPreferences.SUPPORT_MULTIPLE_WINDOWS,
                AppConfig.getInstance(context).enableWindowOpen);
        XWalkPreferences.setValue(XWalkPreferences.JAVASCRIPT_CAN_OPEN_WINDOW, true);
    }

    public static void removeCallbacks(LeanWebView webview) {
        //cannot call setUIClient or setResourceClient with nulls, so we have to create these dummys
        webview.setUIClient(new XWalkUIClient(webview));
        webview.setResourceClient(new XWalkResourceClient(webview));
    }
}
