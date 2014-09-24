package io.gonative.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.webkit.CookieManager;
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
	private boolean isDialog = false;
    private String profilePickerExec;
    private String analyticsExec;
    private String dynamicUpdateExec;

    private boolean mVisitedLoginOrSignup = false;

	public LeanWebviewClient(MainActivity activity) {
		this(activity, false);
	}
	
	public LeanWebviewClient(MainActivity activity, boolean isDialog) {
		super();
		this.mainActivity = activity;
		this.isDialog = isDialog;

        AppConfig appConfig = AppConfig.getInstance(mainActivity);

        // profile picker
        String profileJs = appConfig.profilePickerJS;
        if (profileJs != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("javascript: gonative_profile_picker.parseJson(");
            sb.append(profileJs);
            sb.append(")");
            this.profilePickerExec = sb.toString();
        }

        // analytics
        if (appConfig.analytics) {
            String distribution = (String)Installation.getInfo(mainActivity).get("distribution");
            int idsite;
            if (distribution != null && (distribution.equals("playstore") || distribution.equals("amazon")))
                idsite = appConfig.idsite_prod;
            else idsite = appConfig.idsite_test;

            this.analyticsExec = String.format("javascript:var _paq = _paq || [];\n" +
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
            sb.append("javascript: gonative_dynamic_update.parseJson(");
            sb.append(appConfig.updateConfigJS);
            sb.append(")");
            this.dynamicUpdateExec = sb.toString();
        }
	}
	
	
	private boolean isInternalUri(Uri uri) {
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

	public boolean shouldOverrideUrlLoading(WebView view, String url, boolean isReload) {
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
            view.getContext().startActivity(intent);

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
                mainActivity.startActivityForResult(intent, MainActivity.REQUEST_WEB_ACTIVITY);

                return true;
            }
            else if (newLevel < currentLevel) {
                // pop activity
                Intent returnIntent = new Intent();
                returnIntent.putExtra("url", url);
                returnIntent.putExtra("urlLevel", newLevel);
                mainActivity.setResult(Activity.RESULT_OK, returnIntent);
                mainActivity.finish();
                return true;
            }
        }

        // Starting here, the request will be loaded in this activity.
        if (newLevel >= 0) {
            mainActivity.setUrlLevel(newLevel);
        }

        String newTitle = mainActivity.titleForUrl(url);
        if (newTitle != null) {
            mainActivity.setTitle(newTitle);
        }

        // check to see if the webview exists in pool.
        Pair<LeanWebView, WebViewPoolDisownPolicy> pair = WebViewPool.getInstance().webviewForUrl(url);
        LeanWebView poolWebview = pair.first;
        WebViewPoolDisownPolicy poolDisownPolicy = pair.second;
        if (poolWebview != null && poolDisownPolicy == WebViewPoolDisownPolicy.Always) {
            this.mainActivity.switchToWebview(poolWebview, true);
            WebViewPool.getInstance().disownWebview(poolWebview);
            LocalBroadcastManager.getInstance(mainActivity).sendBroadcast(new Intent(this.FINISHED_LOADING_MESSAGE));
            return true;
        }

        if (poolWebview != null && poolDisownPolicy == WebViewPoolDisownPolicy.Never) {
            this.mainActivity.switchToWebview(poolWebview, true);
            return true;
        }

        if (poolWebview != null && poolDisownPolicy == WebViewPoolDisownPolicy.Reload &&
                !LeanUtils.urlsMatchOnPath(url, view.getUrl())) {
            this.mainActivity.switchToWebview(poolWebview, true);
            return true;
        }

        if (this.mainActivity.isPoolWebview) {
            // if we are here, either the policy is reload and we are reloading the page, or policy is never but we are going to a different page. So take ownership of the webview.
            WebViewPool.getInstance().disownWebview(view);
            this.mainActivity.isPoolWebview = false;
        }

        // intercept html
        if (AppConfig.getInstance(mainActivity).interceptHtml) {
            try {
                URL parsedUrl = new URL(url);
                if (parsedUrl.getProtocol().equals("http") || parsedUrl.getProtocol().equals("https")) {
                    mainActivity.setProgress(0);
                    new WebviewInterceptTask(this.mainActivity).execute(new WebviewInterceptTask.WebviewInterceptParams(view, parsedUrl, isReload));
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

		// clear all cookies (including facebook) if at logout page
		Uri uri = Uri.parse(url);
		if (isInternalUri(uri) && uri.getPath().equals("/user/logout")){
			Log.d(TAG, "clearing cookies");
			CookieManager cookieManager = CookieManager.getInstance();
			cookieManager.removeAllCookie();
		}

		String path = uri.getPath();

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

	@Override
	public void onPageFinished(WebView view, String url) {
//        Log.d(TAG, "onpagefinished " + url);

        mainActivity.showWebview();

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
            view.loadUrl(this.dynamicUpdateExec);
        }

        // profile picker
        if (this.profilePickerExec != null) {
            view.loadUrl(this.profilePickerExec);
        }

        // analytics
        if (this.analyticsExec != null) {
            view.loadUrl(this.analyticsExec);
        }

        // tabs
        mainActivity.checkTabs(url);
		
		mainActivity.clearProgress();

        // send broadcast message
        LocalBroadcastManager.getInstance(mainActivity).sendBroadcast(new Intent(this.FINISHED_LOADING_MESSAGE));

	}

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        if (!isReload) {
            mainActivity.addToHistory(url);
        }
    }
	
	@Override
	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl){
        mainActivity.showWebview();

		// first check connectivity
		if (!mainActivity.isConnected()){			
			view.loadData(mainActivity.getString(R.string.not_connected), "text/html", "utf-8");
		}
	}


}
