package io.gonative.android;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;


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
        String profileJs = AppConfig.getInstance(mainActivity).getString("profilePickerJS");
        if (profileJs != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("javascript: gonative_profile_picker.parseJson(");
            sb.append(profileJs);
            sb.append(")");
            this.profilePickerExec = sb.toString();
        }
	}
	
	
	private boolean isInternalUri(Uri uri) {
        String host = uri.getHost();
        String initialHost = AppConfig.getInstance(mainActivity).getInitialHost();

        return host != null &&
                (host.equals(initialHost) || host.endsWith("." + initialHost) ||
                        AppConfig.getInstance(mainActivity).getInternalHosts().contains(host));
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

		boolean override = false;

		Uri uri = Uri.parse(url);

        if(checkLoginSignup &&
                AppConfig.getInstance(mainActivity).getBoolean("checkNativeLogin") &&
                LeanUtils.urlsMatchOnPath(url, AppConfig.getInstance(mainActivity).getString("loginURL"))){

            mainActivity.launchWebForm(R.raw.login_config, AppConfig.getInstance(mainActivity).getString("loginURL"),
                    AppConfig.getInstance(mainActivity).getString("loginURLfail"), "Log In", true);
            return true;
        }
        else if(checkLoginSignup &&
                AppConfig.getInstance(mainActivity).getBoolean("checkNativeSignup") &&
                LeanUtils.urlsMatchOnPath(url, AppConfig.getInstance(mainActivity).getString("signupURL"))) {

            mainActivity.launchWebForm(R.raw.signup_config, AppConfig.getInstance(mainActivity).getString("signupURL"),
                    AppConfig.getInstance(mainActivity).getString("signupURLfail"), "Sign Up", false);
            return true;
        }
		
		if (!isInternalUri(uri)){
            override = true;

		}

		if (override){
			// launch browser
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);
			view.getContext().startActivity(intent);
        }
        // intercept html
        else if (AppConfig.getInstance(mainActivity).getInterceptHtml()) {
            try {
                URL parsedUrl = new URL(url);
                if (parsedUrl.getProtocol().equals("http") || parsedUrl.getProtocol().equals("https")) {
                    mainActivity.setProgress(0);
                    new DownloadPageTask().execute(new WebViewAndUrl(view, parsedUrl, isReload));
                    override = true;
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        return override;
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

		// reload menu if at /user/logout or /products (first redirection from /user/login), or / (facebook)
        /*
		if (isInternalUri(uri) && (path.equals("/user/logout") || path.equals("/products") || path.equals("/"))){
			mainActivity.updateMenu();
		}*/

        // reload menu if internal url
        if (AppConfig.getInstance(mainActivity).getBoolean("checkUserAuth") && isInternalUri(uri)) {
//            Log.d(TAG, "onpagestarted refreshing menu");
            mainActivity.updateMenu();
        }

		super.onPageStarted(view, url, favicon);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
//        Log.d(TAG, "onpagefinished " + url);
        UrlInspector.getInstance().inspectUrl(url);
		super.onPageFinished(view, url);

		Uri uri = Uri.parse(url);		
		if (isInternalUri(uri)){
			CookieSyncManager.getInstance().sync();
		}

        AppConfig appConfig = AppConfig.getInstance(mainActivity);

        if (appConfig.getBoolean("checkUserAuth")) {
            if (mVisitedLoginOrSignup){
                mainActivity.updateMenu();
            }

            mVisitedLoginOrSignup = LeanUtils.urlsMatchOnPath(url, appConfig.getString("loginURL")) ||
                    LeanUtils.urlsMatchOnPath(url, appConfig.getString("signupURL"));
        }

        // profile picker
        if (this.profilePickerExec != null) {
//            Log.d(TAG, "running " + profilePickerExec);
            view.loadUrl(this.profilePickerExec);
        }
		
		mainActivity.clearProgress();
	}
	
	@Override
	public void onReceivedError(WebView view, int errorCode, String description, String failingUrl){
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
                    connection.setRequestProperty("User-Agent", appConfig.getUserAgent());
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
                    if (appConfig.containsKey("customCss")) {
                        builder.append("<style>");
                        builder.append(appConfig.getString("customCss"));
                        builder.append("</style>");
                    }
                    if (appConfig.containsKey("stringViewport")) {
                        builder.append("<meta name=\"viewport\" content=\"");
                        builder.append(TextUtils.htmlEncode(appConfig.getString("stringViewport")));
                        builder.append("\">");
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
