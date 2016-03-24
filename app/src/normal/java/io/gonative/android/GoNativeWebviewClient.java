package io.gonative.android;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 9/9/15.
 */
public class GoNativeWebviewClient extends WebViewClient{
    private static final String TAG = GoNativeWebviewClient.class.getName();
    private UrlNavigation urlNavigation;
    private Context context;

    public GoNativeWebviewClient(MainActivity mainActivity, UrlNavigation urlNavigation) {
        this.urlNavigation = urlNavigation;
        this.context = mainActivity;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return urlNavigation.shouldOverrideUrlLoading((GoNativeWebviewInterface)view, url);
    }

    public boolean shouldOverrideUrlLoading(WebView view, String url, boolean isReload) {
        return urlNavigation.shouldOverrideUrlLoading((GoNativeWebviewInterface)view, url, isReload);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);

        urlNavigation.onPageStarted(url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);

        urlNavigation.onPageFinished((GoNativeWebviewInterface)view, url);
    }

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        urlNavigation.doUpdateVisitedHistory(view, url, isReload);
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        urlNavigation.onReceivedError((GoNativeWebviewInterface) view, errorCode);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        urlNavigation.onReceivedError((GoNativeWebviewInterface) view, error.getErrorCode());
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        handler.cancel();
        urlNavigation.onReceivedSslError(error);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return urlNavigation.interceptHtml((LeanWebView)view, url);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String method = request.getMethod();
        if (method == null || !method.equalsIgnoreCase("GET")) return null;

        android.net.Uri uri = request.getUrl();
        if (uri == null || !uri.getScheme().startsWith("http")) return null;

        return shouldInterceptRequest(view, uri.toString());
    }
}
