package io.gonative.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Pattern;


public class LeanWebviewClient extends WebViewClient{
    public static final String STARTED_LOADING_MESSAGE = "io.gonative.android.webview.started";
    public static final String FINISHED_LOADING_MESSAGE = "io.gonative.android.webview.finished";

    private static final String TAG = LeanWebviewClient.class.getName();

    public static final int DEFAULT_HTML_SIZE = 10 * 1024; // 10 kilobytes

	private MainActivity mainActivity;
    private String profilePickerExec;
    private String analyticsExec;
    private String dynamicUpdateExec;

    private boolean mVisitedLoginOrSignup = false;

	public LeanWebviewClient(MainActivity activity) {
		super();
		this.mainActivity = activity;

        AppConfig appConfig = AppConfig.getInstance(mainActivity);

        // profile picker
        if (appConfig.profilePickerJS != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("gonative_profile_picker.parseJson(eval(");
            sb.append(LeanUtils.jsWrapString(appConfig.profilePickerJS));
            sb.append("))");

            this.profilePickerExec = sb.toString();
        }

        // analytics
        if (appConfig.analytics) {
            String distribution = (String)Installation.getInfo(mainActivity).get("distribution");
            int idsite;
            if (distribution != null && (distribution.equals("playstore") || distribution.equals("amazon")))
                idsite = appConfig.idsite_prod;
            else idsite = appConfig.idsite_test;

            this.analyticsExec = String.format("var _paq = _paq || [];\n" +
                    "  _paq.push(['trackPageView']);\n" +
                    "  _paq.push(['enableLinkTracking']);\n" +
                    "  (function() {\n" +
                    "    var u = 'https://analytics.gonative.io/';\n" +
                    "    _paq.push(['setTrackerUrl', u+'piwik.php']);\n" +
                    "    _paq.push(['setSiteId', %d]);\n" +
                    "    var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0]; g.type='text/javascript';\n" +
                    "    g.defer=true; g.async=true; g.src=u+'piwik.js'; s.parentNode.insertBefore(g,s);\n" +
                    "  })();", idsite);
        }

        // dynamic config updates
        if (appConfig.updateConfigJS != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("gonative_dynamic_update.parseJson(eval(");
            sb.append(LeanUtils.jsWrapString(appConfig.updateConfigJS));
            sb.append("))");

            this.dynamicUpdateExec = sb.toString();
        }
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

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return shouldOverrideUrlLoading(view, url, false);
    }

    public boolean shouldOverrideUrlLoadingNoIntercept(WebView view, String url) {
//		Log.d(TAG, "shouldOverrideUrl: " + url);

        // return if url is null (can happen if clicking refresh when there is no page loaded)
        if (url == null)
            return false;

        // checkLoginSignup might be false when returning from login screen with loginIsFirstPage
        boolean checkLoginSignup = ((LeanWebView)view).checkLoginSignup();
        ((LeanWebView)view).setCheckLoginSignup(true);

        Uri uri = Uri.parse(url);

        AppConfig appConfig = AppConfig.getInstance(mainActivity);

        if(checkLoginSignup &&
                appConfig.loginUrl != null &&
                LeanUtils.urlsMatchOnPath(url, appConfig.loginUrl)){

            mainActivity.launchWebForm("login", "Log In");
            return true;
        }
        else if(checkLoginSignup &&
                appConfig.signupUrl != null &&
                LeanUtils.urlsMatchOnPath(url, appConfig.signupUrl)) {

            mainActivity.launchWebForm("signup", "Sign Up");
            return true;
        }

        if (!isInternalUri(uri)){
            // launch browser
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            try {
                view.getContext().startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, e.getMessage(), e);
            }

            return true;
        }

        // Starting here, we are going to load the request, but possibly in a
        // different activity depending on the structured nav level

        int currentLevel = mainActivity.getUrlLevel();
        int newLevel = mainActivity.urlLevelForUrl(url);
        if (currentLevel >= 0 && newLevel >= 0) {
            if (newLevel > currentLevel) {
                // new activity
                Intent intent = new Intent(mainActivity.getBaseContext(), MainActivity.class);
                intent.putExtra("isRoot", false);
                intent.putExtra("url", url);
                intent.putExtra("parentUrlLevel", currentLevel);
                intent.putExtra("postLoadJavascript", mainActivity.postLoadJavascript);
                mainActivity.startActivityForResult(intent, MainActivity.REQUEST_WEB_ACTIVITY);

                return true;
            }
            else if (newLevel < currentLevel && newLevel <= mainActivity.getParentUrlLevel()) {
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
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.setTitle(newTitle);
                }
            });
        }

        // nav title image
        mainActivity.showLogoInActionBar(appConfig.shouldShowNavigationTitleImageForUrl(url));

        // check to see if the webview exists in pool.
        Pair<LeanWebView, WebViewPoolDisownPolicy> pair = WebViewPool.getInstance().webviewForUrl(url);
        LeanWebView poolWebview = pair.first;
        WebViewPoolDisownPolicy poolDisownPolicy = pair.second;
        if (poolWebview != null && poolDisownPolicy == WebViewPoolDisownPolicy.Always) {
            this.mainActivity.switchToWebview(poolWebview, true);
            this.mainActivity.checkNavigationForPage(url);
            WebViewPool.getInstance().disownWebview(poolWebview);
            LocalBroadcastManager.getInstance(mainActivity).sendBroadcast(new Intent(this.FINISHED_LOADING_MESSAGE));
            return true;
        }

        if (poolWebview != null && poolDisownPolicy == WebViewPoolDisownPolicy.Never) {
            this.mainActivity.switchToWebview(poolWebview, true);
            this.mainActivity.checkNavigationForPage(url);
            return true;
        }

        if (poolWebview != null && poolDisownPolicy == WebViewPoolDisownPolicy.Reload &&
                !LeanUtils.urlsMatchOnPath(url, view.getUrl())) {
            this.mainActivity.switchToWebview(poolWebview, true);
            this.mainActivity.checkNavigationForPage(url);
            return true;
        }

        if (this.mainActivity.isPoolWebview) {
            // if we are here, either the policy is reload and we are reloading the page, or policy is never but we are going to a different page. So take ownership of the webview.
            WebViewPool.getInstance().disownWebview(view);
            this.mainActivity.isPoolWebview = false;
        }

        return false;
    }

	public boolean shouldOverrideUrlLoading(WebView view, String url, boolean isReload) {
        boolean shouldOverride = shouldOverrideUrlLoadingNoIntercept(view, url);
        if (shouldOverride) return shouldOverride;

        // intercept html
        if (AppConfig.getInstance(mainActivity).interceptHtml) {
            try {
                URL parsedUrl = new URL(url);
                if (parsedUrl.getProtocol().equals("http") || parsedUrl.getProtocol().equals("https")) {
                    mainActivity.setProgress(0);
                    new WebviewInterceptTask(this.mainActivity, this).execute(new WebviewInterceptTask.WebviewInterceptParams(view, parsedUrl, isReload));
                    mainActivity.hideWebview();
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        mainActivity.hideWebview();
        return false;
    }

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
//        Log.d(TAG, "onpagestarted " + url);
        UrlInspector.getInstance().inspectUrl(url);
		Uri uri = Uri.parse(url);

        // reload menu if internal url
        if (AppConfig.getInstance(mainActivity).loginDetectionUrl != null && isInternalUri(uri)) {
            mainActivity.updateMenu();
        }

		super.onPageStarted(view, url, favicon);

        // check ready status
        mainActivity.startCheckingReadyStatus();

        // send broadcast message
        LocalBroadcastManager.getInstance(mainActivity).sendBroadcast(new Intent(this.STARTED_LOADING_MESSAGE));
    }

    public void showWebViewImmediately() {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.showWebviewImmediately();
            }
        });
    }

	@Override
	public void onPageFinished(WebView view, String url) {
//        Log.d(TAG, "onpagefinished " + url);

        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.showWebview();
            }
        });

        UrlInspector.getInstance().inspectUrl(url);
		super.onPageFinished(view, url);

		Uri uri = Uri.parse(url);		
		if (isInternalUri(uri)){
			CookieSyncManager.getInstance().sync();
		}

        AppConfig appConfig = AppConfig.getInstance(mainActivity);

        if (appConfig.loginDetectionUrl != null) {
            if (mVisitedLoginOrSignup){
                mainActivity.updateMenu();
            }

            mVisitedLoginOrSignup = LeanUtils.urlsMatchOnPath(url, appConfig.loginUrl) ||
                    LeanUtils.urlsMatchOnPath(url, appConfig.signupUrl);
        }

        // dynamic config updater
        if (this.dynamicUpdateExec != null) {
            LeanUtils.runJavascriptOnWebView(view, this.dynamicUpdateExec);
        }

        // profile picker
        if (this.profilePickerExec != null) {
            LeanUtils.runJavascriptOnWebView(view, this.profilePickerExec);
        }

        // analytics
        if (this.analyticsExec != null) {
            LeanUtils.runJavascriptOnWebView(view, this.analyticsExec);
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
        LocalBroadcastManager.getInstance(mainActivity).sendBroadcast(new Intent(this.FINISHED_LOADING_MESSAGE));

	}

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        if (!isReload && !url.equals("file:///android_asset/offline.html")) {
            mainActivity.addToHistory(url);
        }
    }
	
	@Override
	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl){
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.showWebview();
            }
        });

		// first check connectivity
		if (!mainActivity.isConnected()){
            ((LeanWebView)view).loadUrlDirect("file:///android_asset/offline.html");
		}
	}


}
