package io.gonative.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.LocationManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;

import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.webkit.ClientCertRequest;
import android.webkit.CookieSyncManager;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.facebook.appevents.AppEventsLogger;
import com.onesignal.OneSignal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.math.BigDecimal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.gonative.android.library.AppConfig;

enum WebviewLoadState {
    STATE_UNKNOWN,
    STATE_START_LOAD, // we have decided to load the url in this webview in shouldOverrideUrlLoading
    STATE_PAGE_STARTED, // onPageStarted has been called
    STATE_DONE // onPageFinished has been called
}

public class UrlNavigation {
    public static final String STARTED_LOADING_MESSAGE = "io.gonative.android.webview.started";
    public static final String FINISHED_LOADING_MESSAGE = "io.gonative.android.webview.finished";
    public static final String CLEAR_POOLS_MESSAGE = "io.gonative.android.webview.clearPools";

    private static final String TAG = UrlNavigation.class.getName();

    private static final String ASSET_URL = "file:///android_asset/";
    public static final String OFFLINE_PAGE_URL = "file:///android_asset/offline.html";
    public static final int DEFAULT_HTML_SIZE = 10 * 1024; // 10 kilobytes

	private MainActivity mainActivity;
    private String profilePickerExec;
    private String currentWebviewUrl;
    private HtmlIntercept htmlIntercept;
    private Handler startLoadTimeout = new Handler();

    private WebviewLoadState state = WebviewLoadState.STATE_UNKNOWN;
    private boolean mVisitedLoginOrSignup = false;
    private boolean finishOnExternalUrl = false;
    private boolean restoreBrightnessOnNavigation = false;
    private double connectionOfflineTime;

    UrlNavigation(MainActivity activity) {
		this.mainActivity = activity;
        this.htmlIntercept = new HtmlIntercept(activity);

        AppConfig appConfig = AppConfig.getInstance(mainActivity);

        // profile picker
        if (appConfig.profilePickerJS != null) {
            this.profilePickerExec = "gonative_profile_picker.parseJson(eval("
                    + LeanUtils.jsWrapString(appConfig.profilePickerJS)
                    + "))";
        }

        if (mainActivity.getIntent().getBooleanExtra(MainActivity.EXTRA_WEBVIEW_WINDOW_OPEN, false)) {
            finishOnExternalUrl = true;
        }

        connectionOfflineTime = appConfig.androidConnectionOfflineTime;
	}
	
	private boolean isInternalUri(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return false;
        }

        AppConfig appConfig = AppConfig.getInstance(mainActivity);
        String urlString = uri.toString();

        // first check regexes
        ArrayList<Pattern> regexes = appConfig.regexInternalExternal;
        ArrayList<Boolean> isInternal = appConfig.regexIsInternal;
        if (regexes != null) {
            for (int i = 0; i < regexes.size(); i++) {
                Pattern regex = regexes.get(i);
                if (regex.matcher(urlString).matches()) {
                    return isInternal.get(i);
                }
            }
        }

        String host = uri.getHost();
        String initialHost = appConfig.initialHost;

        return host != null &&
                (host.equals(initialHost) || host.endsWith("." + initialHost));
    }

    public boolean shouldOverrideUrlLoading(GoNativeWebviewInterface view, String url) {
        return shouldOverrideUrlLoading(view, url, false);
    }

    // noAction to skip stuff like opening url in external browser, higher nav levels, etc.
    private boolean shouldOverrideUrlLoadingNoIntercept(final GoNativeWebviewInterface view, final String url,
                                                        @SuppressWarnings("SameParameterValue") final boolean noAction) {
//		Log.d(TAG, "shouldOverrideUrl: " + url);

        // return if url is null (can happen if clicking refresh when there is no page loaded)
        if (url == null)
            return false;

        // return if loading from local assets
        if (url.startsWith(ASSET_URL)) return false;

        if (url.startsWith("blob:")) return false;

        view.setCheckLoginSignup(true);

        Uri uri = Uri.parse(url);

        if (uri.getScheme() != null && uri.getScheme().equals("gonative-bridge")) {
            if (noAction) return true;

            try {
                String json = uri.getQueryParameter("json");

                JSONArray parsedJson = new JSONArray(json);
                for (int i = 0; i < parsedJson.length(); i++) {
                    JSONObject entry = parsedJson.optJSONObject(i);
                    if (entry == null) continue;

                    String command = entry.optString("command");
                    if (command == null) continue;

                    if (command.equals("pop")) {
                        if (mainActivity.isNotRoot()) mainActivity.finish();
                    } else if (command.equals("clearPools")) {
                        LocalBroadcastManager.getInstance(mainActivity).sendBroadcast(
                                new Intent(UrlNavigation.CLEAR_POOLS_MESSAGE));
                    }
                }
            } catch (Exception e) {
                // do nothing
            }

            return true;
        }

        final AppConfig appConfig = AppConfig.getInstance(mainActivity);

        // Check native bridge urls
        if ("gonative".equals(uri.getScheme()) && currentWebviewUrl != null &&
                !LeanUtils.checkNativeBridgeUrls(currentWebviewUrl, mainActivity)) {
            Log.e(TAG, "URL not authorized for native bridge: " + currentWebviewUrl);
            return true;
        }

        if ("gonative".equals(uri.getScheme()) && "registration".equals(uri.getHost()) &&
                "/send".equals(uri.getPath())) {

            RegistrationManager registrationManager = ((GoNativeApplication) mainActivity.getApplication()).getRegistrationManager();
            String customDataString = uri.getQueryParameter("customData");
            if (customDataString != null) {
                try {
                    JSONObject customData = new JSONObject(customDataString);
                    registrationManager.setCustomData(customData);
                    registrationManager.sendToAllEndpoints();
                } catch (JSONException e) {
                    Log.d(TAG, "Gonative registration error: customData is not JSON object");
                }
            } else {
                registrationManager.sendToAllEndpoints();
            }

            return true;
        }

        if ("gonative".equals(uri.getScheme())) {
            if ("nativebridge".equals(uri.getHost())) {
                if ("/multi".equals(uri.getPath())) {
                    String data = uri.getQueryParameter("data");
                    if (data == null) return true;
                    try {
                        JSONObject json = new JSONObject(data);
                        JSONArray urls = json.getJSONArray("urls");
                        for (int i = 0; i < urls.length(); i++) {
                            String s = urls.getString(i);
                            Uri u = Uri.parse(s);
                            if (!"gonative".equals(u.getScheme())) continue;
                            shouldOverrideUrlLoadingNoIntercept(view, s, noAction);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error calling gonative://nativebridge/multi", e);
                    }
                } else if("/custom".equals(uri.getPath())) {
                    Map<String, String> params = new HashMap<String, String>();
                    for(String parameterName : uri.getQueryParameterNames()) {
                        String parameter = uri.getQueryParameter(parameterName);
                        params.put(parameterName, parameter);
                    }

                    // execute code defined by the CustomCodeHandler
                    // call JsCustomCodeExecutor#setHandler to override this default handler
                    JSONObject data = JsCustomCodeExecutor.execute(params);

                    String callback = params.get("callback");
                    if(callback != null && !callback.isEmpty()) {
                        final String js = LeanUtils.createJsForCallback(callback, data);
                        // run on main thread
                        Handler mainHandler = new Handler(mainActivity.getMainLooper());
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mainActivity.runJavascript(js);
                            }
                        });
                    }
                }
                return true;
            }

            // settings
            if ("open".equals(uri.getHost())) {
                if ("/app-settings".equals(uri.getPath())) {
                    try {
                        Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri appUri = Uri.fromParts("package", mainActivity.getPackageName(), null);
                        settingsIntent.setData(appUri);
                        mainActivity.startActivity(settingsIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening app settings", e);
                    }
                }
                return true;
            }

            if ("webview".equals(uri.getHost())) {
                if ("/clearCache".equals(uri.getPath())) {
                    Log.d(TAG, "Clearing webview cache");
                    mainActivity.clearWebviewCache();
                }
                return true;
            }

            if ("run".equals(uri.getHost())) {
                if ("/gonative_device_info".equals(uri.getPath())) {
                    runGonativeDeviceInfo();
                } else if ("/gonative_onesignal_info".equals(uri.getPath())) {
                    mainActivity.sendOneSignalInfo();
                }
            }

            if ("geoLocation".equals(uri.getHost())) {
                if ("/promptAndroidLocationServices".equals(uri.getPath())) {
                    if (isLocationServiceEnabled()) return true;

                    new AlertDialog.Builder(mainActivity)
                            .setMessage(R.string.location_services_not_enabled)
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    mainActivity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                                }
                            })
                            .setNegativeButton(R.string.no_thanks, null)
                            .show();
                }
                return true;
            }

            if ("config".equals(uri.getHost())) {
                ConfigPreferences configPreferences = new ConfigPreferences(this.mainActivity);
                configPreferences.handleUrl(uri);
                return true;
            }

            if ("screen".equals(uri.getHost())) {
                if ("/setBrightness".equals(uri.getPath())) {
                    String brightnessString = uri.getQueryParameter("brightness");
                    if (brightnessString == null) {
                        Log.e(TAG, "Brightness not specified in " + uri.toString());
                        return true;
                    }

                    if (brightnessString.equals("default")) {
                        mainActivity.setBrightness(-1);
                        restoreBrightnessOnNavigation = false;
                        return true;
                    }

                    try {
                        float newBrightness = Float.parseFloat(brightnessString);
                        if (newBrightness < 0 || newBrightness > 1.0) {
                            Log.e(TAG, "Invalid brightness value in " + uri.toString());
                            return true;
                        }
                        mainActivity.setBrightness(newBrightness);
                        String restoreString = uri.getQueryParameter("restoreOnNavigation");
                        if ("true".equals(restoreString) || "1".equals(restoreString)) {
                            this.restoreBrightnessOnNavigation = true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing brightness", e);

                    }
                }
                return true;
            }

            if ("navigationMaxWindows".equals(uri.getHost())) {
                if ("/set".equals(uri.getPath())) {
                    String dataString = uri.getQueryParameter("data");
                    boolean persist = uri.getBooleanQueryParameter("persist", false);

                    if (!TextUtils.isEmpty(dataString)) {
                        try {
                            int maxWindow = Integer.parseInt(dataString);
                            appConfig.setMaxWindows(maxWindow, persist);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Invalid navigationMaxWindows: " + dataString, e);
                            return true;
                        }
                    } else {
                        appConfig.setMaxWindows(0, persist);
                    }
                }
                return true;
            }

            if ("navigationTitles".equals(uri.getHost())) {
                if ("/set".equals(uri.getPath())) {
                    String dataString = uri.getQueryParameter("data");
                    String persistString = uri.getQueryParameter("persist");
                    boolean persist = "1".equals(persistString) || "true".equals(persistString);

                    if (dataString != null && !dataString.isEmpty()) {
                        try {
                            JSONObject data = new JSONObject(dataString);
                            appConfig.setNavigationTitles(data, persist);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing navigationTitles", e);
                        }

                    } else {
                        appConfig.setNavigationTitles(null, persist);
                    }
                } else if ("/setCurrent".equals(uri.getPath())) {
                    String title = uri.getQueryParameter("title");
                    if (title != null) {
                        mainActivity.setTitle(title);
                    } else {
                        mainActivity.setTitle(R.string.app_name);
                    }
                }
                return true;
            }

            if ("navigationLevels".equals(uri.getHost())) {
                if ("/set".equals(uri.getPath())) {
                    String dataString = uri.getQueryParameter("data");
                    String persistString = uri.getQueryParameter("persist");
                    boolean persist = "1".equals(persistString) || "true".equals(persistString);

                    if (dataString != null && !dataString.isEmpty()) {
                        try {
                            JSONObject data = new JSONObject(dataString);
                            appConfig.setNavigationLevels(data, persist);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing navigationLevels", e);
                        }
                    } else {
                        appConfig.setNavigationLevels(null, persist);
                    }
                }
                return true;
            }

            if ("sidebar".equals(uri.getHost())) {
                if ("/setItems".equals(uri.getPath())) {
                    String itemsString = uri.getQueryParameter("items");
                    if (itemsString != null) {
                        try {
                            Object items = new JSONTokener(itemsString).nextValue();
                            AppConfig.getInstance(this.mainActivity).setSidebarNavigation(items);
                        } catch (JSONException e) {
                            Log.d(TAG, "Gonative registration error: customData is not JSON object");
                        }
                    }
                }
                return true;
            }

            if ("share".equals(uri.getHost())) {
                String urlString = uri.getQueryParameter("url");
                if ("/sharePage".equals(uri.getPath())) {
                    this.mainActivity.sharePage(urlString);
                } else if ("/downloadFile".equals(uri.getPath()) && urlString != null) {
                    this.mainActivity.getFileDownloader().onDownloadStart(urlString, null, null, null, -1);
                }

                return true;
            }

            if ("tabs".equals(uri.getHost())) {
                TabManager tabManager = this.mainActivity.getTabManager();
                if (tabManager == null) return true;

                if (uri.getPath().startsWith("/select/")) {
                    List<String> segments = uri.getPathSegments();
                    if (segments.size() == 2) {
                        String tabNumberString = segments.get(1);
                        try {
                            int tabNumber = Integer.parseInt(tabNumberString);
                            if (tabNumber >= 0) {
                                tabManager.selectTabNumber(tabNumber);
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Invalid tab number " + tabNumberString, e);
                            return true;
                        }

                    }
                } else if ("/deselect".equals(uri.getPath())) {
                    mainActivity.deselectTabs();
                } else if ("/setTabs".equals(uri.getPath())) {
                    String tabsJson = uri.getQueryParameter("tabs");
                    if (tabsJson != null && !tabsJson.isEmpty()) {
                        tabManager.setTabsWithJson(tabsJson);
                    }
                }

                return true;
            }

            if ("facebook".equals(uri.getHost())) {
                if (!appConfig.facebookEnabled) return true;

                boolean isPurchase = "/events/sendPurchase".equals(uri.getPath());
                if (isPurchase || "/events/send".equals(uri.getPath())) {
                    String dataString = uri.getQueryParameter("data");
                    if (dataString == null || dataString.isEmpty()) return true;

                    try {
                        JSONObject data = new JSONObject(dataString);

                        Bundle params = null;
                        JSONObject parameters = data.optJSONObject("parameters");
                        if (parameters != null) {
                            params = LeanUtils.jsonObjectToBundle(parameters);
                        }

                        if (!isPurchase) {
                            String eventName = LeanUtils.optString(data, "event");
                            if (eventName == null) return true;

                            double valueToSum = data.optDouble("valueToSum");

                            AppEventsLogger logger = AppEventsLogger.newLogger(mainActivity);
                            if (Double.isNaN(valueToSum)) {
                                logger.logEvent(eventName, params);
                            } else {
                                logger.logEvent(eventName, valueToSum, params);
                            }
                        } else {
                            // isPurchase
                            double purchaseAmount = data.optDouble("purchaseAmount");
                            if (Double.isNaN(purchaseAmount)) return true;
                            String currencyString = LeanUtils.optString(data, "currency");
                            if (currencyString == null) return true;
                            Currency currency = Currency.getInstance(currencyString);

                            AppEventsLogger logger = AppEventsLogger.newLogger(mainActivity);
                            logger.logPurchase(BigDecimal.valueOf(purchaseAmount), currency, params);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing json for facebook event", e);
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Error creating facebook app event", e);
                    }
                }

                return true;
            }

            if ("onesignal".equals(uri.getHost())) {
                if ("/tags/get".equals(uri.getPath())) {
                    final String callback = uri.getQueryParameter("callback");
                    if (callback == null || callback.isEmpty()) return true;

                    OneSignal.getTags(new OneSignal.GetTagsHandler() {
                        @Override
                        public void tagsAvailable(JSONObject tags) {
                            JSONObject results = new JSONObject();
                            try {
                                results.put("success", true);
                                if (tags != null) {
                                    results.put("tags", tags);
                                }
                                final String js = LeanUtils.createJsForCallback(callback, results);
                                // run on main thread
                                Handler mainHandler = new Handler(mainActivity.getMainLooper());
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mainActivity.runJavascript(js);
                                    }
                                });
                            } catch (JSONException e) {
                                Log.e(TAG, "Error json encoding tags", e);
                            }
                        }
                    });

                    return true;
                }

                if ("/tags/set".equals(uri.getPath())) {
                    String tagsString = uri.getQueryParameter("tags");
                    if (tagsString == null || tagsString.isEmpty()) return true;

                    try {
                        JSONObject tags = new JSONObject(tagsString);
                        OneSignal.sendTags(tags);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing tags JSON", e);
                    }
                    return true;
                }

                if ("/promptLocation".equals(uri.getPath())) {
                    OneSignal.promptLocation();
                    return true;
                }

                if ("/userPrivacyConsent/grant".equals(uri.getPath())) {
                    OneSignal.provideUserConsent(true);
                    return true;
                }

                if ("/userPrivacyConsent/revoke".equals(uri.getPath())) {
                    OneSignal.provideUserConsent(false);
                    return true;
                }

                if ("/showTagsUI".equals(uri.getPath())) {
                    Intent intent = new Intent(mainActivity, SubscriptionsActivity.class);
                    mainActivity.startActivity(intent);
                }

                if ("/iam/addTrigger".equals(uri.getPath())) {
                    String key = uri.getQueryParameter("key");
                    if (TextUtils.isEmpty(key)) return true;
                    String value = uri.getQueryParameter("value");
                    if (TextUtils.isEmpty(value)) return true;

                    OneSignal.addTrigger(key, value);
                    return true;
                }

                if ("/iam/addTriggers".equals(uri.getPath())) {
                    String map = uri.getQueryParameter("map");
                    if (TextUtils.isEmpty(map)) return true;

                    String jsonString = Uri.decode(map);
                    OneSignal.addTriggersFromJsonString(jsonString);
                    return true;
                }

                if ("/iam/removeTriggerForKey".equals(uri.getPath())) {
                    String key = uri.getQueryParameter("key");
                    if (TextUtils.isEmpty(key)) return true;

                    OneSignal.removeTriggerForKey(key);
                    return true;
                }

                if ("/iam/getTriggerValueForKey".equals(uri.getPath())) {
                    String key = uri.getQueryParameter("key");
                    if (TextUtils.isEmpty(key)) return true;

                    String value = (String) OneSignal.getTriggerValueForKey(key);
                    HashMap<String, Object> params = new HashMap<>();
                    params.put("key", key);
                    params.put("value", value);

                    JSONObject jsonObject = new JSONObject(params);
                    String js = LeanUtils.createJsForCallback("gonative_iam_trigger_value", jsonObject);
                    mainActivity.runJavascript(js);
                    return true;
                }

                if ("/iam/pauseInAppMessages".equals(uri.getPath())) {
                    boolean value = uri.getBooleanQueryParameter("pause", false);
                    OneSignal.pauseInAppMessages(value);
                    return true;
                }

                if ("/iam/setInAppMessageClickHandler".equals(uri.getPath())) {
                    String handler = uri.getQueryParameter("handler");
                    if (TextUtils.isEmpty(handler)) return true;

                    OneSignal.startInit(mainActivity)
                            .setInAppMessageClickHandler(action -> {
                                HashMap<String, Object> params = new HashMap<>();
                                params.put("clickName", action.clickName);
                                params.put("clickUrl", action.clickUrl);
                                params.put("firstClick", action.firstClick);
                                params.put("closesMessage", action.closesMessage);

                                JSONObject jsonObject = new JSONObject(params);
                                String js = LeanUtils.createJsForCallback(handler, jsonObject);
                                mainActivity.runJavascript(js);
                            }).init(); // fails if onesignal_app_id is not in build.gradle

                    // have to re-initialize after init with builder to restore the other configurations
                    LeanUtils.initOneSignal(mainActivity, appConfig);
                    return true;
                }
            }

            if ("connectivity".equals(uri.getHost())) {
                String callback = uri.getQueryParameter("callback");
                if ("/get".equals(uri.getPath())) {
                    if (callback != null && !callback.isEmpty()) {
                        this.mainActivity.sendConnectivityOnce(callback);
                    }
                } else if ("/subscribe".equals(uri.getPath())) {
                    if (callback != null && !callback.isEmpty()) {
                        this.mainActivity.subscribeConnectivity(callback);
                    }
                } else if ("/unsubscribe".equals(uri.getPath())) {
                    this.mainActivity.unsubscribeConnectivity();
                }
            }

            if ("statusbar".equals(uri.getHost())) {
                if ("/set".equals(uri.getPath())) {
                    String style = uri.getQueryParameter("style");
                    if (style != null && !style.isEmpty() && Build.VERSION.SDK_INT >= 23) {
                        if (style.equals("light")) {
                            // light icons and text
                            View decor = this.mainActivity.getWindow().getDecorView();
                            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~ View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                        } else if (style.equals("dark")) {
                            // dark icons and text
                            View decor = this.mainActivity.getWindow().getDecorView();
                            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                        }
                    }

                    String color = uri.getQueryParameter("color");
                    Integer parsedColor = LeanUtils.parseColor(color);
                    if (parsedColor != null && Build.VERSION.SDK_INT >= 21) {
                        this.mainActivity.getWindow().setStatusBarColor(parsedColor);
                    }

                    String overlay = uri.getQueryParameter("overlay");
                    if (overlay != null) {
                        if (overlay.equals("true") || overlay.equals("1")) {
                            View decor = this.mainActivity.getWindow().getDecorView();
                            decor.setSystemUiVisibility(decor.getSystemUiVisibility() |
                                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                        } else {
                            View decor = this.mainActivity.getWindow().getDecorView();
                            decor.setSystemUiVisibility(decor.getSystemUiVisibility() &
                                    ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN &
                                    ~View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                        }
                    }
                }
            }

            return true;
        }

        // check redirects
        if (appConfig.redirects != null) {
            String to = appConfig.redirects.get(url);
            if (to == null) to = appConfig.redirects.get("*");
            if (to != null && !to.equals(url)) {
                if (noAction) return true;

                final String destination = to;
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mainActivity.loadUrl(destination);
                    }
                });
                return true;
            }
        }

        if (!isInternalUri(uri)){
            if (noAction) return true;

            // launch browser
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            try {
                mainActivity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, e.getMessage(), e);
            }

            return true;
        }

        // Starting here, we are going to load the request, but possibly in a
        // different activity depending on the structured nav level

        if (restoreBrightnessOnNavigation) {
            mainActivity.setBrightness(-1);
            restoreBrightnessOnNavigation = false;
        }

        int currentLevel = mainActivity.getUrlLevel();
        int newLevel = mainActivity.urlLevelForUrl(url);
        if (currentLevel >= 0 && newLevel >= 0) {
            if (newLevel > currentLevel) {
                if (noAction) return true;

                if (appConfig.maxWindows > 0 && mainActivity.getWebViewCount() > appConfig.maxWindows) {
                    mainActivity.setRemoveExcessWebView(true);
                    LocalBroadcastManager.getInstance(mainActivity)
                            .sendBroadcast(new Intent(MainActivity.BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED));
                }

                // new activity
                Intent intent = new Intent(mainActivity.getBaseContext(), MainActivity.class);
                intent.putExtra("isRoot", false);
                intent.putExtra("url", url);
                intent.putExtra("parentUrlLevel", currentLevel);
                intent.putExtra("postLoadJavascript", mainActivity.postLoadJavascript);
                mainActivity.startActivityForResult(intent, MainActivity.REQUEST_WEB_ACTIVITY);

                mainActivity.postLoadJavascript = null;
                mainActivity.postLoadJavascriptForRefresh = null;

                return true;
            }
            else if (newLevel < currentLevel && newLevel <= mainActivity.getParentUrlLevel()) {
                if (noAction) return true;

                // pop activity
                Intent returnIntent = new Intent();
                returnIntent.putExtra("url", url);
                returnIntent.putExtra("urlLevel", newLevel);
                returnIntent.putExtra("postLoadJavascript", mainActivity.postLoadJavascript);
                mainActivity.setResult(Activity.RESULT_OK, returnIntent);
                mainActivity.finish();
                return true;
            }
        }

        // Starting here, the request will be loaded in this activity.
        if (newLevel >= 0) {
            mainActivity.setUrlLevel(newLevel);
        }

        final String newTitle = mainActivity.titleForUrl(url);
        if (newTitle != null) {
            if (!noAction) {
                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mainActivity.setTitle(newTitle);
                    }
                });
            }
        }

        // nav title image
        if (!noAction) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.showLogoInActionBar(appConfig.shouldShowNavigationTitleImageForUrl(url));
                }
            });
        }

        // check to see if the webview exists in pool.
        WebViewPool webViewPool = ((GoNativeApplication)mainActivity.getApplication()).getWebViewPool();
        Pair<GoNativeWebviewInterface, WebViewPoolDisownPolicy> pair = webViewPool.webviewForUrl(url);
        final GoNativeWebviewInterface poolWebview = pair.first;
        WebViewPoolDisownPolicy poolDisownPolicy = pair.second;

        if (noAction && poolWebview != null) return true;

        if (poolWebview != null && poolDisownPolicy == WebViewPoolDisownPolicy.Always) {
            this.mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.switchToWebview(poolWebview, true, false);
                    mainActivity.checkNavigationForPage(url);
                }
            });
            webViewPool.disownWebview(poolWebview);
            LocalBroadcastManager.getInstance(mainActivity).sendBroadcast(new Intent(UrlNavigation.FINISHED_LOADING_MESSAGE));
            return true;
        }

        if (poolWebview != null && poolDisownPolicy == WebViewPoolDisownPolicy.Never) {
            this.mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.switchToWebview(poolWebview, true, false);
                    mainActivity.checkNavigationForPage(url);
                }
            });
            return true;
        }

        if (poolWebview != null && poolDisownPolicy == WebViewPoolDisownPolicy.Reload &&
                !LeanUtils.urlsMatchOnPath(url, this.currentWebviewUrl)) {
            this.mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.switchToWebview(poolWebview, true, false);
                    mainActivity.checkNavigationForPage(url);
                }
            });
            return true;
        }

        if (this.mainActivity.isPoolWebview) {
            // if we are here, either the policy is reload and we are reloading the page, or policy is never but we are going to a different page. So take ownership of the webview.
            webViewPool.disownWebview(view);
            this.mainActivity.isPoolWebview = false;
        }

        return false;
    }

	public boolean shouldOverrideUrlLoading(final GoNativeWebviewInterface view, String url,
                                            @SuppressWarnings("unused") boolean isReload) {
        if (url == null) return false;

        boolean shouldOverride = shouldOverrideUrlLoadingNoIntercept(view, url, false);
        if (shouldOverride) {
            if (finishOnExternalUrl) {
                mainActivity.finish();
            }
            return true;
        } else {
            finishOnExternalUrl = false;
        }

        // intercept html
        this.htmlIntercept.setInterceptUrl(url);
        mainActivity.hideWebview();
        state = WebviewLoadState.STATE_START_LOAD;
        // 10 second (default) delay to get to onPageStarted or doUpdateVisitedHistory
        if (!Double.isNaN(connectionOfflineTime) && !Double.isInfinite(connectionOfflineTime) &&
                connectionOfflineTime > 0) {
            startLoadTimeout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    AppConfig appConfig = AppConfig.getInstance(mainActivity);
                    String url = view.getUrl();
                    if (appConfig.showOfflinePage && !OFFLINE_PAGE_URL.equals(url)) {
                        view.loadUrlDirect(OFFLINE_PAGE_URL);
                    }
                }
            }, (long)(connectionOfflineTime * 1000));
        }

        return false;
    }

	public void onPageStarted(String url) {
//        Log.d(TAG, "onpagestarted " + url);
        state = WebviewLoadState.STATE_PAGE_STARTED;
        startLoadTimeout.removeCallbacksAndMessages(null);
        htmlIntercept.setInterceptUrl(url);

        UrlInspector.getInstance().inspectUrl(url);
		Uri uri = Uri.parse(url);

        // reload menu if internal url
        if (AppConfig.getInstance(mainActivity).loginDetectionUrl != null && isInternalUri(uri)) {
            mainActivity.updateMenu();
        }

        // check ready status
        mainActivity.startCheckingReadyStatus();

        mainActivity.checkPreNavigationForPage(url);

        // send broadcast message
        LocalBroadcastManager.getInstance(mainActivity).sendBroadcast(new Intent(UrlNavigation.STARTED_LOADING_MESSAGE));


        // enable swipe refresh controller if offline page
        if (OFFLINE_PAGE_URL.equals(url)) {
            mainActivity.enableSwipeRefresh();
        } else {
            mainActivity.restoreSwipRefreshDefault();
        }
    }

    @SuppressWarnings("unused")
    public void showWebViewImmediately() {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.showWebviewImmediately();
            }
        });
    }

	@SuppressLint("ApplySharedPref")
    public void onPageFinished(GoNativeWebviewInterface view, String url) {
//        Log.d(TAG, "onpagefinished " + url);
        state = WebviewLoadState.STATE_DONE;
        this.currentWebviewUrl = url;

        AppConfig appConfig = AppConfig.getInstance(mainActivity);
        if (url != null && appConfig.ignorePageFinishedRegexes != null) {
            for (Pattern pattern: appConfig.ignorePageFinishedRegexes) {
                if (pattern.matcher(url).matches()) return;
            }
        }

        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.showWebview();
            }
        });

        UrlInspector.getInstance().inspectUrl(url);

		Uri uri = Uri.parse(url);		
		if (isInternalUri(uri)){
            CookieSyncManager.getInstance().sync();
		}

        if (appConfig.loginDetectionUrl != null) {
            if (mVisitedLoginOrSignup){
                mainActivity.updateMenu();
            }

            mVisitedLoginOrSignup = LeanUtils.urlsMatchOnPath(url, appConfig.loginUrl) ||
                    LeanUtils.urlsMatchOnPath(url, appConfig.signupUrl);
        }

        // post-load javascript
        if (appConfig.postLoadJavascript != null) {
		    view.runJavascript(appConfig.postLoadJavascript);
        }

        // profile picker
        if (this.profilePickerExec != null) {
            view.runJavascript(this.profilePickerExec);
        }

        // tabs
        mainActivity.checkNavigationForPage(url);

        // post-load javascript
        if (mainActivity.postLoadJavascript != null) {
            String js = mainActivity.postLoadJavascript;
            mainActivity.postLoadJavascript = null;
            mainActivity.runJavascript(js);
        }
		
        // send broadcast message
        LocalBroadcastManager.getInstance(mainActivity).sendBroadcast(new Intent(UrlNavigation.FINISHED_LOADING_MESSAGE));

        boolean doNativeBridge = true;
        if (currentWebviewUrl != null) {
            doNativeBridge = LeanUtils.checkNativeBridgeUrls(currentWebviewUrl, mainActivity);
        }

        // send installation info
        if (doNativeBridge) {
            runGonativeDeviceInfo();
        }
	}

    public void onFormResubmission(GoNativeWebviewInterface view, Message dontResend, Message resend) {
        resend.sendToTarget();
    }

	private void runGonativeDeviceInfo() {
        Map<String, Object> installationInfo = Installation.getInfo(mainActivity);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
        if (!sharedPreferences.getBoolean("hasLaunched", false)) {
            sharedPreferences.edit().putBoolean("hasLaunched", true).commit();
            installationInfo.put("isFirstLaunch", true);
        } else {
            installationInfo.put("isFirstLaunch", false);
        }

        JSONObject jsonObject = new JSONObject(installationInfo);
        String js = LeanUtils.createJsForCallback("gonative_device_info", jsonObject);
        mainActivity.runJavascript(js);
    }

    public void doUpdateVisitedHistory(@SuppressWarnings("unused") GoNativeWebviewInterface view, String url, boolean isReload) {
        if (state == WebviewLoadState.STATE_START_LOAD) {
            state = WebviewLoadState.STATE_PAGE_STARTED;
            startLoadTimeout.removeCallbacksAndMessages(null);
        }

        if (!isReload && !url.equals(OFFLINE_PAGE_URL)) {
            mainActivity.addToHistory(url);
        }
    }
	
	public void onReceivedError(final GoNativeWebviewInterface view,
                                @SuppressWarnings("unused") int errorCode,
                                String errorDescription, String failingUrl){
        if (errorDescription != null && errorDescription.contains("net::ERR_CACHE_MISS")) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    view.reload();
                }
            });
            return;
        }

        boolean showingOfflinePage = false;

        // show offline page if not connected to internet
        AppConfig appConfig = AppConfig.getInstance(this.mainActivity);
        if (appConfig.showOfflinePage &&
                (state == WebviewLoadState.STATE_PAGE_STARTED || state == WebviewLoadState.STATE_START_LOAD)) {

            if (mainActivity.isDisconnected() ||
                    (errorCode == WebViewClient.ERROR_HOST_LOOKUP &&
                            failingUrl != null &&
                            view.getUrl() != null &&
                            failingUrl.equals(view.getUrl()))) {

                showingOfflinePage = true;

                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        view.stopLoading();
                        view.loadUrlDirect(OFFLINE_PAGE_URL);

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                view.loadUrlDirect(OFFLINE_PAGE_URL);
                            }
                        }, 100);
                    }
                });
            }
        }

        if (!showingOfflinePage) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.showWebview();
                }
            });
        }
	}

    public void onReceivedSslError(SslError error) {
        int errorMessage;
        switch (error.getPrimaryError()) {
            case SslError.SSL_EXPIRED:
                errorMessage = R.string.ssl_error_expired;
                break;
            case SslError.SSL_DATE_INVALID:
            case SslError.SSL_IDMISMATCH:
            case SslError.SSL_NOTYETVALID:
            case SslError.SSL_UNTRUSTED:
                errorMessage = R.string.ssl_error_cert;
                break;
            case SslError.SSL_INVALID:
            default:
                errorMessage = R.string.ssl_error_generic;
                break;
        }

        Toast.makeText(mainActivity, errorMessage, Toast.LENGTH_LONG).show();
    }

    @SuppressWarnings("unused")
    public String getCurrentWebviewUrl() {
        return currentWebviewUrl;
    }

    public void setCurrentWebviewUrl(String currentWebviewUrl) {
        this.currentWebviewUrl = currentWebviewUrl;
    }

    public WebResourceResponse interceptHtml(LeanWebView view, String url) {
//        Log.d(TAG, "intercept " + url);
        return htmlIntercept.interceptHtml(view, url, this.currentWebviewUrl);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean chooseFileUpload(String[] mimetypespec) {
        return chooseFileUpload(mimetypespec, false);
    }

    public boolean chooseFileUpload(final String[] mimetypespec, final boolean multiple) {
        mainActivity.getPermission(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, (permissions, grantResults) -> chooseFileUploadAfterPermission(mimetypespec, multiple));
        return true;
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean chooseFileUploadAfterPermission(String[] mimetypespec, boolean multiple) {
        mainActivity.setDirectUploadImageUri(null);

        Set<String> mimeTypes = new HashSet<>();
        for (String spec : mimetypespec) {
            String[] splitSpec = spec.split("[,;\\s]");
            for (String s : splitSpec) {
                if (s.startsWith(".")) {
                    String t = MimeTypeMap.getSingleton().getMimeTypeFromExtension(s.substring(1));
                    if (t != null) mimeTypes.add(t);
                } else if (s.contains("/")) {
                    mimeTypes.add(s);
                }
            }
        }

        if (mimeTypes.isEmpty()) mimeTypes.add("*/*");

        boolean useCamera = false;
        boolean useVideo = false;

        if (AppConfig.getInstance(mainActivity).directCameraUploads) {
            for (String type : mimeTypes) {
                if (type.equals("*/*")) {
                    useCamera = true;
                    useVideo = true;
                } else if (type.equals("image/*") || type.equals("image/jpeg") || type.equals("image/jpg")) {
                    useCamera = true;
                } else if (type.startsWith("video/")) {
                    useVideo = true;
                }
            }
        }

        List<Intent> directCaptureIntents = new ArrayList<>();

        PackageManager packageManger = mainActivity.getPackageManager();
        if (useCamera) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "IMG_" + timeStamp + ".jpg";

            Uri captureUrl = null;
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                ContentResolver resolver = mainActivity.getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/*");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                captureUrl = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            }else {
                File storageDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
                File captureFile = new File(storageDir, imageFileName);
                captureUrl = Uri.fromFile(captureFile);
            }

            if (captureUrl != null) {
                Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                List<ResolveInfo> resolveList = packageManger.queryIntentActivities(captureIntent, 0);
                for (ResolveInfo resolve : resolveList) {
                    String packageName = resolve.activityInfo.packageName;
                    Intent intent = new Intent(captureIntent);
                    intent.setComponent(new ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name));
                    intent.setPackage(packageName);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, captureUrl);
                    mainActivity.setDirectUploadImageUri(captureUrl);
                    directCaptureIntents.add(intent);
                }
            }
        }

        if (useVideo) {
            Intent captureIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            List<ResolveInfo> resolveList = packageManger.queryIntentActivities(captureIntent, 0);
            for (ResolveInfo resolve : resolveList) {
                String packageName = resolve.activityInfo.packageName;
                Intent intent = new Intent(captureIntent);
                intent.setComponent(new ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name));
                intent.setPackage(packageName);
                directCaptureIntents.add(intent);
            }
        }

        Intent documentIntent = new Intent();
        documentIntent.setAction(Intent.ACTION_GET_CONTENT);
        documentIntent.addCategory(Intent.CATEGORY_OPENABLE);

        if (mimeTypes.size() == 1) {
            documentIntent.setType(mimeTypes.iterator().next());
        } else {
            documentIntent.setType("*/*");

            // If running kitkat or later, then we can specify multiple mime types
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                documentIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toArray(new String[mimeTypes.size()]));
            }
        }

        // INTENT_ALLOW_MULTIPLE can be used starting API 18. But we should only get multiple=true
        // starting in Lollipop anyway.
        if (multiple && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            documentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        Intent intentToSend;

        if (directCaptureIntents.isEmpty()) {
            intentToSend = documentIntent;
        } else {
            Intent chooserIntent = Intent.createChooser(documentIntent, "Choose an action");
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, directCaptureIntents.toArray(new Parcelable[0]));
            intentToSend = chooserIntent;
        }

        try {
            mainActivity.startActivityForResult(intentToSend, MainActivity.REQUEST_SELECT_FILE);
            return true;
        } catch (ActivityNotFoundException e) {
            mainActivity.cancelFileUpload();
            Toast.makeText(mainActivity, R.string.cannot_open_file_chooser, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public boolean createNewWindow(Message resultMsg) {
        ((GoNativeApplication)mainActivity.getApplication()).setWebviewMessage(resultMsg);
        return createNewWindow();
    }

    @SuppressWarnings("unused")
    public boolean createNewWindow(ValueCallback callback) {
        ((GoNativeApplication) mainActivity.getApplication()).setWebviewValueCallback(callback);
        return createNewWindow();
    }

    private boolean createNewWindow() {
        Intent intent = new Intent(mainActivity.getBaseContext(), MainActivity.class);
        intent.putExtra("isRoot", false);
        intent.putExtra(MainActivity.EXTRA_WEBVIEW_WINDOW_OPEN, true);
        // need to use startActivityForResult instead of startActivity because of singleTop launch mode
        mainActivity.startActivityForResult(intent, MainActivity.REQUEST_WEB_ACTIVITY);

        return true;
    }

    public boolean isLocationServiceEnabled()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager lm = mainActivity.getSystemService(LocationManager.class);
            return lm.isLocationEnabled();
        } else {
            // This is Deprecated in API 28
            int mode = Settings.Secure.getInt(mainActivity.getContentResolver(), Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF);
            return  (mode != Settings.Secure.LOCATION_MODE_OFF);
        }
    }

    protected void onDownloadStart() {
        startLoadTimeout.removeCallbacksAndMessages(null);
        state = WebviewLoadState.STATE_DONE;
    }


    private static class GetKeyTask extends AsyncTask<String, Void, Pair<PrivateKey, X509Certificate[]>> {
        private Activity activity;
        private ClientCertRequest request;

        public GetKeyTask(Activity activity, ClientCertRequest request) {
            this.activity = activity;
            this.request = request;
        }

        @Override
        protected Pair<PrivateKey, X509Certificate[]> doInBackground(String... strings) {
            String alias = strings[0];

            try {
                PrivateKey privateKey = KeyChain.getPrivateKey(activity, alias);
                X509Certificate[] certificates = KeyChain.getCertificateChain(activity, alias);
                return new Pair<>(privateKey, certificates);
            } catch (Exception e) {
                Log.e(TAG, "Erorr getting private key for alias " + alias, e);
                return null;
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        protected void onPostExecute(Pair<PrivateKey, X509Certificate[]> result) {
            if (result != null && result.first != null & result.second != null) {
                request.proceed(result.first, result.second);
            } else {
                request.ignore();;
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onReceivedClientCertRequest(String url, ClientCertRequest request) {
        Uri uri = Uri.parse(url);
        KeyChainAliasCallback callback = alias -> {
            if (alias == null) {
                request.ignore();
                return;
            }

            new GetKeyTask(mainActivity, request).execute(alias);
        };

        KeyChain.choosePrivateKeyAlias(mainActivity, callback, request.getKeyTypes(), request.getPrincipals(), request.getHost(),
                request.getPort(), null);
    }
}
