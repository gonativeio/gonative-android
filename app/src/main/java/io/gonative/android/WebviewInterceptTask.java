package io.gonative.android;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
* Created by weiyin on 9/5/14.
*/
class WebviewInterceptTask extends AsyncTask<WebviewInterceptTask.WebviewInterceptParams, Void, String> {
    private static final String TAG = WebviewInterceptTask.class.getName();

    private Context context;
    private LeanWebviewClient leanWebviewClient;
    private WebView webview;
    private URL parsedUrl;
    private URL finalUrl;

    public WebviewInterceptTask(Context context, LeanWebviewClient leanWebviewClient) {
        this.context = context;
        this.leanWebviewClient = leanWebviewClient;
    }

    private WebviewInterceptTask(){
    }

    protected String doInBackground(WebviewInterceptParams... inputs) {
        AppConfig appConfig = AppConfig.getInstance(this.context);

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
                if (isCancelled()) return null;

                connection = (HttpURLConnection) parsedUrl.openConnection();
                connection.setInstanceFollowRedirects(false);
                String customUserAgent = appConfig.userAgentForUrl(parsedUrl.toString());
                if (customUserAgent != null) {
                    connection.setRequestProperty("User-Agent", customUserAgent);
                } else {
                    connection.setRequestProperty("User-Agent", appConfig.userAgent);
                }
                if (isReload)
                    connection.setRequestProperty("Cache-Control", "no-cache");

                connection.connect();
                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                    wasRedirected = true;
                    parsedUrl = new URL(parsedUrl, connection.getHeaderField("Location"));

                    // check if this page should be intercepted
                    if (this.leanWebviewClient != null
                            && this.leanWebviewClient.shouldOverrideUrlLoadingNoIntercept(this.webview, parsedUrl.toString())) {

                        leanWebviewClient.showWebViewImmediately();
                        connection.disconnect();
                        this.cancel(true);

                        return null;
                    }

                    numRedirects++;
                } else {
                    wasRedirected = false;
                }
            } while (wasRedirected && numRedirects < 10);

            finalUrl = connection.getURL();

            String mimetype = connection.getContentType();
            if (mimetype == null) {
                try {
                    is = new BufferedInputStream(connection.getInputStream());
                } catch (IOException e) {
                    is = new BufferedInputStream(connection.getErrorStream());
                }
                mimetype = HttpURLConnection.guessContentTypeFromStream(is);
            }

            // if not html, then return null so that webview loads directly.
            if (mimetype == null || !mimetype.startsWith("text/html"))
                return null;

            // get and intercept the data
            String encoding = connection.getContentEncoding();
            if (encoding == null)
                encoding = "UTF-8";

            if (is == null) {
                try {
                    is = new BufferedInputStream(connection.getInputStream());
                } catch (IOException e) {
                    is = new BufferedInputStream(connection.getErrorStream());
                }

                if (is == null) return null;
            }

            int initialLength = connection.getContentLength();
            if (initialLength < 0)
                initialLength = LeanWebviewClient.DEFAULT_HTML_SIZE;

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
                if (appConfig.customCSS != null) {
                    builder.append("<style>");
                    builder.append(appConfig.customCSS);
                    builder.append("</style>");
                }
                if (appConfig.stringViewport != null) {
                    builder.append("<meta name=\"viewport\" content=\"");
                    builder.append(TextUtils.htmlEncode(appConfig.stringViewport));
                    builder.append("\" />");
                }
                if (!Double.isNaN(appConfig.forceViewportWidth)) {
                    if (appConfig.zoomableForceViewport) {
                        builder.append(String.format("<meta name=\"viewport\" content=\"width=%f,maximum-scale=1.0\" />",
                                appConfig.forceViewportWidth));
                    }
                    else {
                        // we want to use user-scalable=no, but android has a bug that sets scale to
                        // 1.0 if user-scalable=no. The workaround to is calculate the scale and set
                        // it for initial, minimum, and maximum.
                        // http://stackoverflow.com/questions/12723844/android-viewport-setting-user-scalable-no-breaks-width-zoom-level-of-viewpor
                        double webViewWidth = webview.getWidth() / this.context.getResources().getDisplayMetrics().density;
                        double viewportWidth = appConfig.forceViewportWidth;
                        double scale = webViewWidth / viewportWidth;
                        builder.append(String.format("<meta name=\"viewport\" content=\"width=%f,initial-scale=%f,minimum-scale=%f,maximum-scale=%f\" />",
                                viewportWidth, scale, scale, scale));
                    }
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
        } else {
            webview.loadDataWithBaseURL(finalUrl.toString(), data, "text/html", null, finalUrl.toString());
        }
    }

    public static class WebviewInterceptParams {
        WebView webview;
        URL url;
        boolean isReload;

        public WebviewInterceptParams(WebView webview, URL url, boolean isReload) {
            this.webview = webview;
            this.url = url;
            this.isReload = isReload;
        }
    }
}
