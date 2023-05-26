package io.gonative.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
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
import android.webkit.ClientCertRequest;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.GoNativeActivity;
import io.gonative.gonative_core.GoNativeWebviewInterface;
import io.gonative.gonative_core.IOUtils;
import io.gonative.gonative_core.LeanUtils;
import io.gonative.gonative_core.Utils;

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
    private String JSBridgeScript;
    private HtmlIntercept htmlIntercept;
    private Handler startLoadTimeout = new Handler();

    private WebviewLoadState state = WebviewLoadState.STATE_UNKNOWN;
    private boolean mVisitedLoginOrSignup = false;
    private boolean finishOnExternalUrl = false;
    private double connectionOfflineTime;

    private String interceptedRedirectUrl = "";

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
        return shouldOverrideUrlLoading(view, url, false, false);
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
            ((GoNativeApplication) mainActivity.getApplication()).mBridge.handleJSBridgeFunctions(mainActivity, uri);
            return true;
        }

        // check redirects
        if (appConfig.getRedirects() != null) {
            String to = appConfig.getRedirects().get(url);
            if (to == null) to = appConfig.getRedirects().get("*");
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

        if (!mainActivity.isRestoreBrightnessOnNavigation()) {
            mainActivity.setBrightness(-1);
            mainActivity.setRestoreBrightnessOnNavigation(false);
        }

        if (appConfig.maxWindowsEnabled) {

            GoNativeWindowManager windowManager = mainActivity.getGNWindowManager();

            // To prevent consecutive calls and handle MaxWindows correctly
            // Checks for a flag indicating if the Activity was created from CreateNewWindow OR NavLevels
            // and avoid triggering MaxWindows during this initial intercept
            boolean ignoreInterceptMaxWindows = windowManager.isIgnoreInterceptMaxWindows(mainActivity.getActivityId());

            if (ignoreInterceptMaxWindows) {
                windowManager.setIgnoreInterceptMaxWindows(mainActivity.getActivityId(), false);
            } else if (appConfig.numWindows > 0 && windowManager.getWindowCount() >= appConfig.numWindows) {
                if (mainActivity.onMaxWindowsReached(url)) {
                    return true;
                }
            }
        }

        int currentLevel = mainActivity.getUrlLevel();
        int newLevel = mainActivity.urlLevelForUrl(url);
        if (currentLevel >= 0 && newLevel >= 0) {
            if (newLevel > currentLevel) {
                if (noAction) return true;

                // new activity
                Intent intent = new Intent(mainActivity.getBaseContext(), MainActivity.class);
                intent.putExtra("isRoot", false);
                intent.putExtra("url", url);
                intent.putExtra("parentUrlLevel", currentLevel);
                intent.putExtra("postLoadJavascript", mainActivity.postLoadJavascript);

                if (appConfig.maxWindowsEnabled) {
                    intent.putExtra(MainActivity.EXTRA_IGNORE_INTERCEPT_MAXWINDOWS, true);
                }

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
            mainActivity.runOnUiThread(() ->
                    mainActivity.getActionManager().setupTitleDisplayForUrl(url)
            );
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
                                            @SuppressWarnings("unused") boolean isReload, boolean isRedirect) {
        if (url == null) return false;

        boolean shouldOverride = shouldOverrideUrlLoadingNoIntercept(view, url, false);
        if (shouldOverride) {
            if (finishOnExternalUrl) {
                mainActivity.finish();
            }

            // Check if intercepted URL request was a result of a server-side redirect.
            // Redirect URLs triggers redundant onPageFinished()
            if (isRedirect) {
                interceptedRedirectUrl = url;
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
        // Catch intercepted Redirect URL to
        // prevent loading unnecessary components
        if (interceptedRedirectUrl.equals(url)) {
            interceptedRedirectUrl = "";
            return;
        }

        Log.d(TAG, "onpagefinished " + url);
        state = WebviewLoadState.STATE_DONE;
        setCurrentWebviewUrl(url);

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

        injectJSBridgeLibrary(currentWebviewUrl);
        ((GoNativeApplication) mainActivity.getApplication()).mBridge.onPageFinish(mainActivity, doNativeBridge);
    }

    private void injectJSBridgeLibrary(String currentWebviewUrl) {
        if(!LeanUtils.checkNativeBridgeUrls(currentWebviewUrl, mainActivity)) return;

        try {
            if(JSBridgeScript == null) {
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
        ((GoNativeApplication) mainActivity.getApplication()).mBridge.setCurrentWebviewUrl(currentWebviewUrl);
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

        if (creator.videosAllowed() || creator.imagesAllowed()) {
            mainActivity.getPermission(new String[]{Manifest.permission.CAMERA}, (permissions, grantResults) -> launchChooserIntent(creator));
            return true;
        }

        return launchChooserIntent(creator);
    }

    private boolean launchChooserIntent(FileUploadIntentsCreator creator) {
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
            if (!Utils.isPermissionGranted(mainActivity, Manifest.permission.CAMERA)) {
                mainActivity.getPermission(new String[]{Manifest.permission.CAMERA}, (permissions, grantResults) -> openDirectCameraAfterPermission(mimetypespec, multiple));
            } else {
                openDirectCameraAfterPermission(mimetypespec, multiple);
            }
        } else {

            ArrayList<String> permissionRequests = new ArrayList<>();
            permissionRequests.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissionRequests.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

            if (!Utils.isPermissionGranted(mainActivity, Manifest.permission.CAMERA)) {
                permissionRequests.add(Manifest.permission.CAMERA);
            }

            mainActivity.getPermission(permissionRequests.toArray(new String[0]), (permissions, grantResults) -> openDirectCameraAfterPermission(mimetypespec, multiple));
        }
        return true;
    }

    /*
        Directly opens camera if the mime types are images. If not, run existing default process
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean openDirectCameraAfterPermission(String[] mimetypespec, boolean multiple) {

        // Check and verify CAMERA permission so app will not crash when using cam
        if (!Utils.isPermissionGranted(mainActivity, Manifest.permission.CAMERA)) {
            Toast.makeText(mainActivity, R.string.upload_camera_permission_denied, Toast.LENGTH_SHORT).show();
            return false;
        }

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

    @SuppressLint("SetJavaScriptEnabled")
    public void createNewWindow(WebView webView, Message resultMsg) {
        AppConfig appConfig = AppConfig.getInstance(mainActivity);
        if (appConfig.maxWindowsEnabled && appConfig.numWindows > 0 && mainActivity.getGNWindowManager().getWindowCount() >= appConfig.numWindows) {
            // All of these just to get new url
            WebView newWebView = new WebView(webView.getContext());
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(newWebView);
            resultMsg.sendToTarget();
            newWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (!mainActivity.onMaxWindowsReached(url)) {
                        Intent intent = new Intent(mainActivity.getBaseContext(), MainActivity.class);
                        intent.putExtra("isRoot", false);
                        intent.putExtra("url", url);
                        intent.putExtra(MainActivity.EXTRA_IGNORE_INTERCEPT_MAXWINDOWS, true);
                        mainActivity.startActivityForResult(intent, MainActivity.REQUEST_WEB_ACTIVITY);
                    }
                }
            });
            return;
        }
        createNewWindow(resultMsg, appConfig.maxWindowsEnabled);
    }

    private void createNewWindow(Message resultMsg, boolean maxWindowsEnabled) {
        ((GoNativeApplication) mainActivity.getApplication()).setWebviewMessage(resultMsg);
        Intent intent = new Intent(mainActivity.getBaseContext(), MainActivity.class);
        intent.putExtra("isRoot", false);
        intent.putExtra(MainActivity.EXTRA_WEBVIEW_WINDOW_OPEN, true);

        if (maxWindowsEnabled) {
            intent.putExtra(MainActivity.EXTRA_IGNORE_INTERCEPT_MAXWINDOWS, true);
        }

        // need to use startActivityForResult instead of startActivity because of singleTop launch mode
        mainActivity.startActivityForResult(intent, MainActivity.REQUEST_WEB_ACTIVITY);
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
