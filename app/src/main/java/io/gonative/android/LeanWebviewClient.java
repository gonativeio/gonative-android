package io.gonative.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Pattern;


public class LeanWebviewClient extends WebViewClient{
	private static final String TAG = LeanWebviewClient.class.getName();

    public static final int DEFAULT_HTML_SIZE = 10 * 1024; // 10 kilobytes

	private MainActivity mainActivity;
	private boolean isDialog = false;
    private String profilePickerExec = null;

    private boolean mVisitedLoginOrSignup = false;

	public LeanWebviewClient(MainActivity activity) {
		this(activity, false);
	}
	
	public LeanWebviewClient(MainActivity activity, boolean isDialog) {
		super();
		this.mainActivity = activity;
		this.isDialog = isDialog;

        // profile picker
        String profileJs = AppConfig.getInstance(mainActivity).profilePickerJS;
        if (profileJs != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("javascript: gonative_profile_picker.parseJson(");
            sb.append(profileJs);
            sb.append(")");
            this.profilePickerExec = sb.toString();
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

            // pop this webview if the first page loaded is external
            if (!view.canGoBack() && mainActivity.globalWebViews.size() > 1) {
                mainActivity.popWebView();
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
                mainActivity.startActivityForResult(intent, MainActivity.REQUEST_WEB_ACTIVITY);

                // pop this webview if the first page loaded is higher nav level
                if (!view.canGoBack() && mainActivity.globalWebViews.size() > 1) {
                    mainActivity.popWebView();
                }

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

        // intercept html
        if (AppConfig.getInstance(mainActivity).interceptHtml) {
            try {
                URL parsedUrl = new URL(url);
                if (parsedUrl.getProtocol().equals("http") || parsedUrl.getProtocol().equals("https")) {
                    mainActivity.setProgress(0);
                    new DownloadPageTask().execute(new WebViewAndUrl(view, parsedUrl, isReload));
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

        // profile picker
        if (this.profilePickerExec != null) {
            view.loadUrl(this.profilePickerExec);
        }
		
		mainActivity.clearProgress();
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

    private class DownloadPageTask extends AsyncTask<WebViewAndUrl, Void, String> {
        private WebView webview;
        private URL parsedUrl;
        private URL finalUrl;

        protected String doInBackground(WebViewAndUrl... inputs) {
            AppConfig appConfig = AppConfig.getInstance(mainActivity);

            InputStream is = null;
            ByteArrayOutputStream baos = null;
            try {
                parsedUrl = inputs[0].url;
                webview = inputs[0].webview;
                boolean isReload = inputs[0].isReload;

                // assume Url we got was http or https, since we have already checked in shouldOverrideUrl
                HttpURLConnection.setFollowRedirects(true);
                HttpURLConnection connection = null;
                boolean wasRedirected = false;
                int numRedirects = 0;
                do {
                    connection = (HttpURLConnection) parsedUrl.openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", appConfig.userAgent);
                    if (isReload)
                        connection.setRequestProperty("Cache-Control", "no-cache");

                    connection.connect();
                    int responseCode = connection.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                            responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                        wasRedirected = true;
                        parsedUrl = new URL(connection.getHeaderField("Location"));
                        numRedirects++;
                    } else {
                        wasRedirected = false;
                    }
                } while (wasRedirected && numRedirects < 10);

                finalUrl = connection.getURL();

                String mimetype = connection.getContentType();
                if (mimetype == null) {
                    is = new BufferedInputStream(connection.getInputStream());
                    mimetype = HttpURLConnection.guessContentTypeFromStream(is);
                }

                // if not html, then return null so that webview loads directly.
                if (mimetype == null || !mimetype.startsWith("text/html"))
                    return null;

                // get and intercept the data
                String encoding = connection.getContentEncoding();
                if (encoding == null)
                    encoding = "UTF-8";

                if (is == null)
                    is = new BufferedInputStream(connection.getInputStream());

                int initialLength = connection.getContentLength();
                if (initialLength < 0)
                    initialLength = DEFAULT_HTML_SIZE;

                baos = new ByteArrayOutputStream(initialLength);
                IOUtils.copy(is, baos);
                String origString;
                try {
                    origString = baos.toString(encoding);
                } catch (UnsupportedEncodingException e){
                    // Everything should support UTF-8
                    origString = baos.toString("UTF-8");
                }

                // modify the string!
                String newString = null;
                int insertPoint = origString.indexOf("</head>");
                if (insertPoint >= 0) {
                    StringBuilder builder = new StringBuilder(initialLength);
                    builder.append(origString.substring(0, insertPoint));
                    if (appConfig.customCss != null) {
                        builder.append("<style>");
                        builder.append(appConfig.customCss);
                        builder.append("</style>");
                    }
                    if (appConfig.stringViewport != null) {
                        builder.append("<meta name=\"viewport\" content=\"");
                        builder.append(TextUtils.htmlEncode(appConfig.stringViewport));
                        builder.append("\" />");
                    }
                    if (!Double.isNaN(appConfig.forceViewportWidth)) {
                        // we want to use user-scalable=no, but android has a bug that sets scale to
                        // 1.0 if user-scalable=no. The workaround to is calculate the scale and set
                        // it for initial, minimum, and maximum.
                        // http://stackoverflow.com/questions/12723844/android-viewport-setting-user-scalable-no-breaks-width-zoom-level-of-viewpor
                        double webViewWidth = webview.getWidth() / mainActivity.getResources().getDisplayMetrics().density;
                        double viewportWidth = appConfig.forceViewportWidth;
                        double scale = webViewWidth / viewportWidth;
                        builder.append(String.format("<meta name=\"viewport\" content=\"width=%f,initial-scale=%f,minimum-scale=%f,maximum-scale=%f\" />",
                                viewportWidth, scale, scale, scale));
                    }

                    builder.append(origString.substring(insertPoint));
                    newString = builder.toString();
                }
                else {
                    Log.d(TAG, "could not find closing </head> tag");
                    newString = origString;
                }

                return newString;
            } catch (Exception e) {
                Log.e(TAG, e.toString(), e);
                return null;
            } finally {
                IOUtils.close(is);
                IOUtils.close(baos);
            }
        }

        protected void onPostExecute(String data) {
//            Log.d(TAG, "urlconnection settled on url " + finalUrl.toString());

            if (data == null) {
                // load directly
                if (finalUrl != null)
                    ((LeanWebView)webview).loadUrlDirect(finalUrl.toString());
                else
                    mainActivity.clearProgress();
            } else {
                webview.loadDataWithBaseURL(finalUrl.toString(), data, "text/html", null, finalUrl.toString());
            }
        }
    }

    private class WebViewAndUrl {
        WebView webview;
        URL url;
        boolean isReload;

        private WebViewAndUrl(WebView webview, URL url, boolean isReload) {
            this.webview = webview;
            this.url = url;
            this.isReload = isReload;
        }
    }
}
