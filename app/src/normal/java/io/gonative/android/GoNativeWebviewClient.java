package io.gonative.android;

import android.graphics.Bitmap;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Created by weiyin on 9/9/15.
 */
public class GoNativeWebviewClient extends WebViewClient{
    private UrlNavigation urlNavigation;

    public GoNativeWebviewClient(MainActivity mainActivity, UrlNavigation urlNavigation) {
        this.urlNavigation = urlNavigation;
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
        urlNavigation.onReceivedError((GoNativeWebviewInterface)view, errorCode, description, failingUrl);
    }
}
