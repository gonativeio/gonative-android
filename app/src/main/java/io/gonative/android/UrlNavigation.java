package io.gonative.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;
import android.webkit.ClientCertRequest;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.gonative.android.library.AppConfig;
import io.gonative.gonative_core.GoNativeWebviewInterface;
import io.gonative.gonative_core.LeanUtils;

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
    public static final String OFFLINE_PAGE_URL_RAW = "file:///offline.html";

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
    private String JSBridgeScript;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private String deviceInfoCallback = "";

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
        requestPermissionLauncher = mainActivity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            runGonativeDeviceInfo(deviceInfoCallback);
        });
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

    public void handleJSBridgeFunctions(Object jsData) {
        Uri uri;
        JSONObject jsonData;
        AppConfig appConfig = AppConfig.getInstance(mainActivity);

        if (jsData instanceof Uri) {
            try {
                uri = (Uri) jsData;
                jsonData = LeanUtils.parseQueryParamsWithUri(uri); // can be null if only command is needed to be executed
            } catch (Exception exception) {
                Log.d(TAG, "GoNative Handle JS Bridge Functions Error:- " + exception.getMessage());
                return;
            }
        } else {
            try {
                JSONObject jsonObject = (JSONObject) jsData;
                jsonData = jsonObject.optJSONObject("data"); // can be null if only command is needed to be executed
                uri = Uri.parse(jsonObject.optString("gonativeCommand"));
            } catch (Exception exception) {
                Log.d(TAG, "GoNative Handle JS Bridge Functions Error:- " + exception.getMessage());
                return;
            }
        }

        if (((GoNativeApplication) mainActivity.getApplication()).mBridge.shouldOverrideUrlLoading(mainActivity, uri, jsonData, currentWebviewUrl)) {
            return;
        }

        if ("registration".equals(uri.getHost()) && "/send".equals(uri.getPath())) {
            RegistrationManager registrationManager = ((GoNativeApplication) mainActivity.getApplication()).getRegistrationManager();
            if (jsonData != null) {
                JSONObject customData = jsonData.optJSONObject("customData");
                if (customData == null) {
                    try { // try converting json string from url to json object
                        customData = new JSONObject(jsonData.optString("customData"));
                    } catch (JSONException e) {
                        Log.e(TAG, "GoNative Registration JSONException:- " + e.getMessage());
                    }
                }
                if (customData != null) {
                    registrationManager.setCustomData(customData);
                }
            }
            registrationManager.sendToAllEndpoints();
        }

        if ("nativebridge".equals(uri.getHost())) {
            if ("/multi".equals(uri.getPath())) {
                if (jsonData == null) return;
                String data = jsonData.optString("data");
                if (data.isEmpty()) return;
                try {
                    JSONObject json = new JSONObject(data);
                    JSONArray urls = json.getJSONArray("urls");
                    for (int i = 0; i < urls.length(); i++) {
                        String s = urls.getString(i);
                        Uri u = Uri.parse(s);
                        if (!"gonative".equals(u.getScheme())) continue;
                        handleJSBridgeFunctions(u);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error calling gonative://nativebridge/multi", e);
                }
            } else if ("/custom".equals(uri.getPath())) {
                if (jsonData == null) return;
                Map<String, String> params = new HashMap<String, String>();
                // map to json
                for (Iterator<String> it = jsonData.keys(); it.hasNext(); ) {
                    String parameterName = it.next();
                    String parameter = jsonData.optString(parameterName);
                    params.put(parameterName, parameter);
                }

                // execute code defined by the CustomCodeHandler
                // call JsCustomCodeExecutor#setHandler to override this default handler
                JSONObject data = JsCustomCodeExecutor.execute(params);

                String callback = params.get("callback");
                if (callback != null && !callback.isEmpty()) {
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
            return;
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
            return;
        }

        if ("webview".equals(uri.getHost())) {
            if ("/clearCache".equals(uri.getPath())) {
                Log.d(TAG, "Clearing webview cache");
                mainActivity.clearWebviewCache();
            } else if ("/reload".equals(uri.getPath())) {
                Log.d(TAG, "Reloading webview");
                mainActivity.refreshPage();
            }
            return;
        }

        if ("run".equals(uri.getHost())) {
            if ("/gonative_device_info".equals(uri.getPath())) {
                String callback = "gonative_device_info";
                boolean includeCarrierNames = false;
                if (jsonData != null) {
                    callback = jsonData.optString("callback", "gonative_device_info");
                    includeCarrierNames = jsonData.optBoolean("includeCarrierNames", false);
                }

                if (includeCarrierNames) {
                    deviceInfoCallback = callback;
                    requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
                } else {
                    runGonativeDeviceInfo(callback);
                }
            }
        }

        if ("geoLocation".equals(uri.getHost())) {
            if ("/promptAndroidLocationServices".equals(uri.getPath())) {
                mainActivity.getRuntimeGeolocationPermission(granted -> {
                    if (!granted) return;
                    if (!isLocationServiceEnabled()) {
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
                });
            }
            return;
        }

        if ("config".equals(uri.getHost()) && jsonData != null) {
            if ("/set".equals(uri.getPath())) {
                String initialUrl = jsonData.optString("initialUrl");
                if (!initialUrl.isEmpty()) {
                    appConfig.setInitialUrl(initialUrl, true);
                }
            }
            return;
        }

        if ("screen".equals(uri.getHost())) {
            if ("/setBrightness".equals(uri.getPath())) {
                if (jsonData == null) return;
                String brightnessString = jsonData.optString("brightness");
                if (brightnessString.isEmpty()) {
                    Log.e(TAG, "Brightness not specified in " + uri.toString());
                    return;
                }

                if (brightnessString.equals("default")) {
                    mainActivity.setBrightness(-1);
                    restoreBrightnessOnNavigation = false;
                    return;
                }

                try {
                    float newBrightness = Float.parseFloat(brightnessString);
                    if (newBrightness < 0 || newBrightness > 1.0) {
                        Log.e(TAG, "Invalid brightness value in " + uri.toString());
                        return;
                    }
                    mainActivity.setBrightness(newBrightness);
                    String restoreString = jsonData.optString("restoreOnNavigation");
                    if ("true".equals(restoreString) || "1".equals(restoreString)) {
                        this.restoreBrightnessOnNavigation = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing brightness", e);
                }
            } else if ("/fullscreen".equals(uri.getPath())) {
                mainActivity.toggleFullscreen(true);
            } else if ("/normal".equals(uri.getPath())) {
                mainActivity.toggleFullscreen(false);
            } else if ("/keepScreenOn".equals(uri.getPath())) {
                mainActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else if ("/keepScreenNormal".equals(uri.getPath())) {
                mainActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else if ("/setMode".equals(uri.getPath())) {
                String mode = jsonData.optString("mode", "auto");
                mainActivity.setupAppTheme(mode);
            }
            return;
        }

        if ("navigationMaxWindows".equals(uri.getHost())) {
            if ("/set".equals(uri.getPath()) && jsonData != null) {
                int maxWindow = jsonData.optInt("data");
                boolean persist = jsonData.optBoolean("persist");
                appConfig.setMaxWindows(maxWindow, persist);
            }
            return;
        }

        if ("navigationTitles".equals(uri.getHost()) && jsonData != null) {
            if ("/set".equals(uri.getPath())) {
                boolean persist = jsonData.optBoolean("persist");
                JSONObject data = jsonData.optJSONObject("data");
                try {
                    if (data == null) { // convert string to json from url
                        data = new JSONObject(jsonData.optString("data"));
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "GoNative Navigation Titles JSONException:- " + e.getMessage());
                    return;
                }
                appConfig.setNavigationTitles(data, persist);
            } else if ("/setCurrent".equals(uri.getPath())) {
                String title = jsonData.optString("title");
                if (!title.isEmpty()) {
                    mainActivity.setTitle(title);
                } else {
                    mainActivity.setTitle(R.string.app_name);
                }
            }
            return;
        }

        if ("navigationLevels".equals(uri.getHost()) && jsonData != null) {
            if ("/set".equals(uri.getPath())) {
                boolean persist = jsonData.optBoolean("persist");
                JSONObject data = jsonData.optJSONObject("data");
                try {
                    if (data == null) { // convert string to json from url
                        data = new JSONObject(jsonData.optString("data"));
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "GoNative Navigation Levels JSONException:- " + e.getMessage());
                    return;
                }
                appConfig.setNavigationLevels(data, persist);
            }
            return;
        }

        if ("sidebar".equals(uri.getHost())) {
            if ("/setItems".equals(uri.getPath()) && jsonData != null) {
                Object items = jsonData.optJSONArray("items");
                if (items == null) {
                    String itemsString = jsonData.optString("items");
                    if (!itemsString.isEmpty()) {
                        try {
                            items = new JSONTokener(itemsString).nextValue();
                        } catch (JSONException e) {
                            Log.d(TAG, "GoNative sidebar error: items is not JSON object");
                            return;
                        }
                    }
                }
                boolean enabled = jsonData.optBoolean("enabled", true);
                if (jsonData.has("persist")) {
                    boolean persist = jsonData.optBoolean("persist", false);
                    AppConfig.getInstance(this.mainActivity).setSidebarNavigation(items, enabled, persist);
                } else {
                    AppConfig.getInstance(this.mainActivity).setSidebarNavigation(items);
                }
                this.mainActivity.setSidebarNavigationEnabled(enabled);
            } else if ("/getItems".equals(uri.getPath()) && jsonData != null && !jsonData.optString("callback").isEmpty()) {
                String callback = jsonData.optString("callback");
                JSONObject menus = AppConfig.getInstance(this.mainActivity).getSidebarNavigation();
                if (menus != null) {
                    mainActivity.runJavascript(LeanUtils.createJsForCallback(callback, menus));
                }
            }
            return;
        }

        if ("share".equals(uri.getHost()) && jsonData != null) {
            String urlString = jsonData.optString("url");
            if ("/sharePage".equals(uri.getPath())) {
                this.mainActivity.sharePage(urlString);
            } else if ("/downloadFile".equals(uri.getPath()) && !urlString.isEmpty()) {
                this.mainActivity.getFileDownloader().downloadFile(urlString, false);
            } else if ("/downloadImage".equals(uri.getPath()) && !urlString.isEmpty()) {
                this.mainActivity.getFileDownloader().downloadFile(urlString, true);
            }

            return;
        }

        if ("tabs".equals(uri.getHost())) {
            TabManager tabManager = this.mainActivity.getTabManager();
            if (tabManager == null) return;

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
                        return;
                    }

                }
            } else if ("/deselect".equals(uri.getPath())) {
                mainActivity.deselectTabs();
            } else if ("/setTabs".equals(uri.getPath()) && jsonData != null) {
                JSONObject tabsConfig = jsonData.optJSONObject("tabs");
                int tabMenuId = tabsConfig.optInt("tabMenu", -1);
                if (tabsConfig == null) {
                    try {
                        tabsConfig = new JSONObject(jsonData.optString("tabs"));
                    } catch (JSONException e) {
                        Log.e(TAG, "GoNative Tabs JSONException", e);
                        return;
                    }
                }
                tabManager.setTabsWithJson(tabsConfig, tabMenuId);
            }

            return;
        }

        if ("connectivity".equals(uri.getHost())) {
            if ("/get".equals(uri.getPath())) {
                if (jsonData != null && !jsonData.optString("callback").isEmpty()) {
                    this.mainActivity.sendConnectivityOnce(jsonData.optString("callback"));
                }
            } else if ("/subscribe".equals(uri.getPath())) {
                if (jsonData != null && !jsonData.optString("callback").isEmpty()) {
                    this.mainActivity.subscribeConnectivity(jsonData.optString("callback"));
                }
            } else if ("/unsubscribe".equals(uri.getPath())) {
                this.mainActivity.unsubscribeConnectivity();
            }
        }

        if ("audio".equals(uri.getHost())) {
            if ("/requestFocus".equals(uri.getPath()) && appConfig.enableWebRTCBluetoothAudio && jsonData != null) {
                boolean requestFocusEnabled = jsonData.optBoolean("enabled", true);
                if (requestFocusEnabled) {
                    AudioUtils.requestAudioFocus(mainActivity);
                } else {
                    AudioUtils.abandonFocusRequest(mainActivity);
                }
            }
            return;
        }

        if ("statusbar".equals(uri.getHost())) {
            if ("/set".equals(uri.getPath()) && jsonData != null) {
                String style = jsonData.optString("style");
                this.mainActivity.updateStatusBarStyle(style);

                String color = jsonData.optString("color");
                Integer parsedColor = LeanUtils.parseColor(color);
                if (parsedColor != null && Build.VERSION.SDK_INT >= 21) {
                    this.mainActivity.getWindow().setStatusBarColor(parsedColor);
                }

                boolean overlay = jsonData.optBoolean("overlay");
                this.mainActivity.updateStatusBarOverlay(overlay);
            }
            return;
        }

        if ("internalExternal".equals(uri.getHost())) {
            if ("/set".equals(uri.getPath())) {
                if (jsonData == null || jsonData.length() == 0) {
                    // Reset
                    appConfig.setRegexInternalExternal(null);
                } else {
                    try {
                        JSONArray rules = jsonData.optJSONArray("rules");
                        if (rules == null || rules.length() == 0) {
                            appConfig.setRegexInternalExternal(null);
                            return;
                        }
                        // Validate rules JSON structure
                        for (int i = 0; i < rules.length(); i++) {
                            JSONObject obj = rules.getJSONObject(i);
                            // Check if object has "regex" field, return error if not
                            if (TextUtils.isEmpty(obj.optString("regex"))) {
                                Log.e(TAG, "handleJSBridgeFunctions: internalExternal/set format error, missing field");
                                return;
                            }
                            // Check if object has "internal" field, will cause JSONException if field is missing or if value is not boolean
                            obj.getBoolean("internal");
                        }
                        appConfig.setRegexInternalExternal(rules);
                    } catch (JSONException e) {
                        Log.e(TAG, "handleJSBridgeFunctions: internalExternal/set parse error", e);
                        return;
                    }
                }
            }
            return;
        }

        if ("clipboard".equals(uri.getHost()) && jsonData != null) {
            if ("/set".equals(uri.getPath())) {
                String clipboardContent = jsonData.optString("data");
                if (!clipboardContent.isEmpty()) {
                    ClipboardManager clipboard = (ClipboardManager) mainActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("copy", clipboardContent);
                    clipboard.setPrimaryClip(clip);
                }
            } else if ("/get".equals(uri.getPath())) {
                String callback = jsonData.optString("callback");
                if (callback != null && !callback.isEmpty()) {
                    Map<String, String> params = new HashMap<String, String>();
                    ClipboardManager clipboard = (ClipboardManager) mainActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                    CharSequence pasteData = "";
                    if (clipboard.hasPrimaryClip()) {
                        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                        pasteData = item.getText();
                        if (pasteData != null)
                            params.put("data", pasteData.toString());
                        else
                            params.put("error", "Clipboard item is not a string.");
                    } else {
                        params.put("error", "No Clipboard item available.");
                    }
                    JSONObject jsonObject = new JSONObject(params);
                    mainActivity.runJavascript(LeanUtils.createJsForCallback(callback, jsonObject));
                }
            }
        }
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
                    if (command.isEmpty()) continue;

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

        if ("gonative".equals(uri.getScheme())) {
            handleJSBridgeFunctions(uri);
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

        if (!isInternalUri(uri)) {
            if (noAction) return true;

            Log.d(TAG, "processing dynamic link: " + uri);
            Intent intent = null;
            // launch browser
            try {
                if (uri.getScheme().equals("intent")) {
                    intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                } else {
                    intent = new Intent(Intent.ACTION_VIEW, uri);
                }
                mainActivity.startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                Log.e(TAG, ex.getMessage(), ex);
                // Try loading fallback url if available
                if (intent != null) {
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (!TextUtils.isEmpty(fallbackUrl)) {
                        mainActivity.loadUrl(fallbackUrl);
                    } else {
                        Toast.makeText(mainActivity, R.string.app_not_installed, Toast.LENGTH_LONG).show();
                    }
                }
            } catch (URISyntaxException e) {
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
            } else if (newLevel < currentLevel && newLevel <= mainActivity.getParentUrlLevel()) {
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
        WebViewPool webViewPool = ((GoNativeApplication) mainActivity.getApplication()).getWebViewPool();
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
            }, (long) (connectionOfflineTime * 1000));
        }

        return false;
    }

    public void onPageStarted(String url) {
        // catch blank pages from htmlIntercept and cancel loading
        if (url.equals(htmlIntercept.getRedirectedUrl())) {
            mainActivity.goBack();
            htmlIntercept.setRedirectedUrl(null);
            return;
        }

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
            for (Pattern pattern : appConfig.ignorePageFinishedRegexes) {
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
        if (isInternalUri(uri)) {
            AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    CookieManager.getInstance().flush();
                }
            });
        }

        if (appConfig.loginDetectionUrl != null) {
            if (mVisitedLoginOrSignup) {
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
            runGonativeDeviceInfo("gonative_device_info");
        }
        injectJSBridgeLibrary();

        ((GoNativeApplication) mainActivity.getApplication()).mBridge.onPageFinish(mainActivity, doNativeBridge);
    }

    private void injectJSBridgeLibrary() {
        if (!LeanUtils.checkNativeBridgeUrls(currentWebviewUrl, mainActivity)) return;
        try {
            if (JSBridgeScript == null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream is = new BufferedInputStream(mainActivity.getAssets().open("GoNativeJSBridgeLibrary.js"));
                IOUtils.copy(is, baos);
                JSBridgeScript = baos.toString();
            }
            mainActivity.runJavascript(JSBridgeScript);
            ((GoNativeApplication) mainActivity.getApplication()).mBridge.injectJSLibraries(mainActivity);
            // call the user created function that needs library access on page finished.
            mainActivity.runJavascript(LeanUtils.createJsForCallback("gonative_library_ready", null));
        } catch (Exception e) {
            Log.d(TAG, "GoNative JSBridgeLibrary Injection Error:- " + e.getMessage());
        }
    }

    public void onFormResubmission(GoNativeWebviewInterface view, Message dontResend, Message resend) {
        resend.sendToTarget();
    }

    private void runGonativeDeviceInfo(String callback) {
        Map<String, Object> installationInfo = Installation.getInfo(mainActivity);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
        if (!sharedPreferences.getBoolean("hasLaunched", false)) {
            sharedPreferences.edit().putBoolean("hasLaunched", true).commit();
            installationInfo.put("isFirstLaunch", true);
        } else {
            installationInfo.put("isFirstLaunch", false);
        }

        JSONObject jsonObject = new JSONObject(installationInfo);
        String js = LeanUtils.createJsForCallback(callback, jsonObject);
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
                                String errorDescription, String failingUrl) {
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
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            chooseFileUploadAfterPermission(mimetypespec, multiple);
        } else {
            mainActivity.getPermission(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, (permissions, grantResults) -> chooseFileUploadAfterPermission(mimetypespec, multiple));
        }
        return true;
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean chooseFileUploadAfterPermission(String[] mimetypespec, boolean multiple) {
        mainActivity.setDirectUploadImageUri(null);

        FileUploadIntentsCreator creator = new FileUploadIntentsCreator(mainActivity, mimetypespec, multiple);
        Intent intentToSend = creator.chooserIntent();
        mainActivity.setDirectUploadImageUri(creator.getCurrentCaptureUri());

        try {
            mainActivity.startActivityForResult(intentToSend, MainActivity.REQUEST_SELECT_FILE);
            return true;
        } catch (ActivityNotFoundException e) {
            mainActivity.cancelFileUpload();
            Toast.makeText(mainActivity, R.string.cannot_open_file_chooser, Toast.LENGTH_LONG).show();
            return false;
        }
    }


    public boolean openDirectCamera(final String[] mimetypespec, final boolean multiple) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            openDirectCameraAfterPermission(mimetypespec, multiple);
        } else {
            mainActivity.getPermission(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, (permissions, grantResults) -> openDirectCameraAfterPermission(mimetypespec, multiple));
        }
        return true;
    }

    /*
        Directly opens camera if the mime types are images. If not, run existing default process
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean openDirectCameraAfterPermission(String[] mimetypespec, boolean multiple) {
        mainActivity.setDirectUploadImageUri(null);

        FileUploadIntentsCreator creator = new FileUploadIntentsCreator(mainActivity, mimetypespec, multiple);
        Intent intentToSend = creator.cameraIntent();
        mainActivity.setDirectUploadImageUri(creator.getCurrentCaptureUri());

        try {
            // Directly open the camera intent with the same Request Result value value
            mainActivity.startActivityForResult(intentToSend, MainActivity.REQUEST_SELECT_FILE);
            return true;
        } catch (ActivityNotFoundException e) {
            mainActivity.cancelFileUpload();
            Toast.makeText(mainActivity, R.string.cannot_open_file_chooser, Toast.LENGTH_LONG).show();
        }

        return false;
    }

    public boolean createNewWindow(Message resultMsg) {
        ((GoNativeApplication) mainActivity.getApplication()).setWebviewMessage(resultMsg);
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

    public boolean isLocationServiceEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager lm = mainActivity.getSystemService(LocationManager.class);
            return lm.isLocationEnabled();
        } else {
            // This is Deprecated in API 28
            int mode = Settings.Secure.getInt(mainActivity.getContentResolver(), Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF);
            return (mode != Settings.Secure.LOCATION_MODE_OFF);
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
                request.ignore();
                ;
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

    // Cancels scheduled display of offline page after timeout
    public void cancelLoadTimeout() {
        if (startLoadTimeout == null && state != WebviewLoadState.STATE_START_LOAD) return;
        startLoadTimeout.removeCallbacksAndMessages(null);
        showWebViewImmediately();
    }
}
