package io.gonative.android;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebResourceResponse;

import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkView;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 9/9/15.
 */
public class GoNativeXWalkResourceClient extends XWalkResourceClient {
    private static final String TAG = GoNativeXWalkResourceClient.class.getName();
    private UrlNavigation urlNavigation;
    private Context context;

    public GoNativeXWalkResourceClient(XWalkView view, UrlNavigation urlNavigation, Context context) {
        super(view);
        this.urlNavigation = urlNavigation;
        this.context = context;
    }

    @Override
    public boolean shouldOverrideUrlLoading(XWalkView view, String url) {
        return urlNavigation.shouldOverrideUrlLoading((GoNativeWebviewInterface)view, url);
    }

    @Override
    public void onReceivedLoadError(XWalkView view, int errorCode, String description, String failingUrl) {
        // crosswalk has this weird load error: "A network change was detected"
        if (description != null &&  description.startsWith("A network change was detected")) {
            view.reload(XWalkView.RELOAD_NORMAL);
        } else {
            urlNavigation.onReceivedError((GoNativeWebviewInterface)view, errorCode, description, failingUrl);
        }
    }

    @Override
    public WebResourceResponse shouldInterceptLoadRequest(XWalkView view, String url) {
        if (!url.equals(urlNavigation.getInterceptHtmlUrl())) return null;

        InputStream is = null;
        ByteArrayOutputStream baos = null;

        try {
            AppConfig appConfig = AppConfig.getInstance(context);

            URL parsedUrl = new URL(url);
            if (!parsedUrl.getProtocol().toLowerCase().startsWith("http")) return null;

            HttpURLConnection connection;
            boolean wasRedirected = false;
            int numRedirects = 0;
            do {
                connection = (HttpURLConnection)parsedUrl.openConnection();
                connection.setInstanceFollowRedirects(false);
                String customUserAgent = appConfig.userAgentForUrl(parsedUrl.toString());
                if (customUserAgent != null) {
                    connection.setRequestProperty("User-Agent", customUserAgent);
                } else {
                    connection.setRequestProperty("User-Agent", appConfig.userAgent);
                }
                connection.setRequestProperty("Cache-Control", "no-cache");

                connection.connect();
                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {

                    wasRedirected = true;
                    numRedirects++;
                    parsedUrl = new URL(parsedUrl, connection.getHeaderField("Location"));

                    // We may have been redirected to a page that should be intercepted.
                    // check if this page should be intercepted
                    if (this.urlNavigation != null
                            && this.urlNavigation.shouldOverrideUrlLoadingNoIntercept((GoNativeWebviewInterface)view,
                                parsedUrl.toString(), true)) {

                        urlNavigation.showWebViewImmediately();
                        connection.disconnect();
                        return null;
                    }
                } else {
                    wasRedirected = false;
                }
            } while (wasRedirected && numRedirects < 10);

            // done with redirects
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
                initialLength = UrlNavigation.DEFAULT_HTML_SIZE;

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
                        double webViewWidth = view.getWidth() / this.context.getResources().getDisplayMetrics().density;
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

            return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(newString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            Log.e(TAG, e.toString(), e);
            return null;
        } finally {
            IOUtils.close(is);
            IOUtils.close(baos);
        }

    }
}
