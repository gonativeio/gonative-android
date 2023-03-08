package io.gonative.android;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;

import io.gonative.android.library.AppConfig;
import io.gonative.gonative_core.GoNativeWebviewInterface;
import io.gonative.gonative_core.LeanUtils;

/**
 * Created by weiyin on 1/29/16.
 */

public class HtmlIntercept {
    private static final String TAG = HtmlIntercept.class.getName();

    private Context context;
    private String interceptUrl;
    private String JSBridgeScript;
    private String redirectedUrl;

    // track whether we have intercepted a page at all. We will always try to intercept the first time,
    // because interceptUrl may not have been set if restoring from a bundle.
    private boolean hasIntercepted = false;

    HtmlIntercept(Context context) {
        this.context = context;
    }

    public void setInterceptUrl(String interceptUrl) {
        this.interceptUrl = interceptUrl;
    }

    public WebResourceResponse interceptHtml(GoNativeWebviewInterface view, String url, String referer) {

        AppConfig appConfig = AppConfig.getInstance(context);
        if (!appConfig.interceptHtml && (appConfig.customHeaders == null || appConfig.customHeaders.isEmpty())) return null;

        if (!hasIntercepted) {
            interceptUrl = url;
            hasIntercepted = true;
        }
        if (!urlMatches(interceptUrl, url)) return null;

        InputStream is = null;
        ByteArrayOutputStream baos = null;

        try {
            URL parsedUrl = new URL(url);
            String protocol = parsedUrl.getProtocol();
            if (!protocol.equalsIgnoreCase("http") && !protocol.equalsIgnoreCase("https")) return null;

            HttpURLConnection connection = (HttpURLConnection)parsedUrl.openConnection();
            connection.setInstanceFollowRedirects(true);
            String customUserAgent = appConfig.userAgentForUrl(parsedUrl.toString());
            if (customUserAgent != null) {
                connection.setRequestProperty("User-Agent", customUserAgent);
            } else {
                connection.setRequestProperty("User-Agent", appConfig.userAgent);
            }
            connection.setRequestProperty("Cache-Control", "no-cache");

            if (referer != null) {
                connection.setRequestProperty("Referer", referer);
            }

            Map<String, String> customHeaders = CustomHeaders.getCustomHeaders(context);
            if (customHeaders != null) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            connection.connect();
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307) {
                // Get redirect URL to be loaded directly to webview, return blank resource which we cancel on UrlNavigation.onPageStart()
                // We cannot pass headers in webresourceresponse until Android API 21, and we cannot return null
                // or else the webview will handle the request entirely without intercept
                String location = connection.getHeaderField("Location");
                if (!TextUtils.isEmpty(location)) {
                    this.redirectedUrl = url;
                    MainActivity mainActivity = (MainActivity) context;
                    WebView webView = (WebView) mainActivity.getWebView();
                    webView.post(() -> webView.loadUrl(location));
                }
                return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream("".getBytes()));
            }

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
            String characterEncoding = getCharset(mimetype);
            if (characterEncoding == null) {
                characterEncoding = "UTF-8";
            } else if (characterEncoding.toLowerCase().equals("iso-8859-1")) {
                // windows-1252 is a superset of ios-8859-1 that supports the euro symbol €.
                // The html5 spec actually maps "iso-8859-1" to windows-1252 encoding
                characterEncoding = "windows-1252";
            }

            if (is == null) {
                try {
                    is = new BufferedInputStream(connection.getInputStream());
                } catch (IOException e) {
                    is = new BufferedInputStream(connection.getErrorStream());
                }
            }

            int initialLength = connection.getContentLength();
            if (initialLength < 0)
                initialLength = UrlNavigation.DEFAULT_HTML_SIZE;

            baos = new ByteArrayOutputStream(initialLength);
            IOUtils.copy(is, baos);
            String origString;
            try {
                origString = baos.toString(characterEncoding);
            } catch (UnsupportedEncodingException e){
                // Everything should support UTF-8
                origString = baos.toString("UTF-8");
            }

            // modify the string!
            String newString;
            int insertPoint = origString.indexOf("</head>");
            if (insertPoint >= 0) {
                StringBuilder builder = new StringBuilder(initialLength);
                builder.append(origString.substring(0, insertPoint));
                if (appConfig.customCSS != null || appConfig.androidCustomCSS != null) {
                    builder.append("<style>");
                    if(appConfig.customCSS != null)
                        builder.append(appConfig.customCSS).append(" ");
                    if(appConfig.androidCustomCSS != null)
                        builder.append(appConfig.androidCustomCSS);
                    builder.append("</style>");
                }

                if (appConfig.customJS != null || appConfig.androidCustomJS != null) {
                    builder.append("<script>");
                    if(appConfig.customJS != null)
                        builder.append(appConfig.customJS).append(" ");
                    if(appConfig.androidCustomJS != null)
                        builder.append(appConfig.androidCustomJS);
                    builder.append("</script>");
                }

                if (appConfig.stringViewport != null) {
                    builder.append("<meta name=\"viewport\" content=\"");
                    builder.append(TextUtils.htmlEncode(appConfig.stringViewport));
                    builder.append("\" />");
                }
                if (!Double.isNaN(appConfig.forceViewportWidth)) {
                    if (appConfig.zoomableForceViewport) {
                        builder.append(String.format(Locale.US, "<meta name=\"viewport\" content=\"width=%f,maximum-scale=1.0\" />",
                                appConfig.forceViewportWidth));
                    }
                    else {
                        // we want to use user-scalable=no, but android has a bug that sets scale to
                        // 1.0 if user-scalable=no. The workaround to is calculate the scale and set
                        // it for initial, minimum, and maximum.
                        // http://stackoverflow.com/questions/12723844/android-viewport-setting-user-scalable-no-breaks-width-zoom-level-of-viewpor
                        double webViewWidth = view.getWidth() / context.getResources().getDisplayMetrics().density;
                        double viewportWidth = appConfig.forceViewportWidth;
                        double scale = webViewWidth / viewportWidth;
                        builder.append(String.format(Locale.US, "<meta name=\"viewport\" content=\"width=%f,initial-scale=%f,minimum-scale=%f,maximum-scale=%f\" />",
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

            return new WebResourceResponse("text/html", "UTF-8",
                    new ByteArrayInputStream(newString.getBytes("UTF-8")));
        } catch (Exception e) {
            Log.e(TAG, e.toString(), e);
            return null;
        } finally {
            IOUtils.close(is);
            IOUtils.close(baos);
        }
    }

    // Do these urls match, ignoring trailing slash in path
    private static boolean urlMatches(String url1, String url2) {
        if (url1 == null || url2 == null) return false;

        try {
            URL parsed1 = new URL(url1);
            URL parsed2 = new URL(url2);

            if (stringsNotEqual(parsed1.getProtocol(), parsed2.getProtocol())) return false;

            if (stringsNotEqual(parsed1.getAuthority(), parsed2.getAuthority())) return false;

            if (stringsNotEqual(parsed1.getQuery(), parsed2.getQuery())) return false;

            String path1 = parsed1.getPath();
            String path2 = parsed2.getPath();
            if (path1 == null) path1 = "";
            if (path2 == null) path2 = "";

            int lengthDiff = path2.length() - path2.length();
            if (lengthDiff > 1 || lengthDiff < -1) return false;
            if (lengthDiff == 0) return path1.equals(path2);
            if (lengthDiff == 1) {
                return path2.equals(path1 + "/");
            }

            // lengthDiff == -1
            return path1.equals(path2 + "/");
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private static boolean stringsNotEqual(String s1, String s2) {
        return !(s1 == null ? s2 == null : s1.equals(s2));
    }

    private static String getCharset(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }

        String[] tokens = contentType.split("; *");
        for (String s : tokens) {
            if (s.startsWith("charset=")) {
                return s.substring("charset=".length());
            }
        }

        return null;
    }

    public String getRedirectedUrl() {
        return redirectedUrl;
    }

    public void setRedirectedUrl(String redirectUrl) {
        this.redirectedUrl = redirectUrl;
    }
}
