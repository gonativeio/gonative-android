package io.gonative.android;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Pass calls WebViewClient.shouldOverrideUrlLoading when loadUrl, reload, or goBack are called.
 */
public class LeanWebView extends WebView implements GoNativeWebviewInterface {
    private WebViewClient mClient = null;
    private WebChromeClient mChromeClient = null;
    private boolean checkLoginSignup = true;

    public LeanWebView(Context context) {
        super(context);
    }

    public LeanWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LeanWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        mClient = client;
        super.setWebViewClient(client);
    }

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        mChromeClient = client;
        super.setWebChromeClient(client);
    }

    @Override
    public void loadUrl(String url) {
        if (url == null) return;

        if (url.startsWith("javascript:"))
            runJavascript(url.substring("javascript:".length()));
        else if (mClient == null || !mClient.shouldOverrideUrlLoading(this, url)) {
            super.loadUrl(url);
        }
    }

    @Override
    public void reload() {
        if (mClient == null || !(mClient instanceof GoNativeWebviewClient)) super.reload();
        else if(!((GoNativeWebviewClient)mClient).shouldOverrideUrlLoading(this, getUrl(), true))
            super.reload();
    }

    @Override
    public void goBack() {
        try {
            WebBackForwardList history = copyBackForwardList();
            WebHistoryItem item =  history.getItemAtIndex(history.getCurrentIndex() - 1);
            // this shouldn't be necessary, but sometimes we are not able to get an updated
            // intercept url from onPageStarted, so this call to shouldOverrideUrlLoading ensures
            // that our html interceptor knows about this url.
            if (mClient.shouldOverrideUrlLoading(this, item.getUrl())) {
                return;
            }

            super.goBack();
            return;
        } catch (Exception ignored) {
            super.goBack();
            return;
        }
    }

    // skip shouldOverrideUrlLoading, including its html override logic.
    public void loadUrlDirect(String url) {
        super.loadUrl(url);
    }

    public boolean checkLoginSignup() {
        return checkLoginSignup;
    }

    public void setCheckLoginSignup(boolean checkLoginSignup) {
        this.checkLoginSignup = checkLoginSignup;
    }

    public void runJavascript(String js) {
        // before Kitkat, the only way to run javascript was to load a url that starts with "javascript:".
        // Starting in Kitkat, the "javascript:" method still works, but it expects the rest of the string
        // to be URL encoded, unlike previous versions. Rather than URL encode for Kitkat and above,
        // use the new evaluateJavascript method.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            loadUrlDirect("javascript:" + js);
        } else {
            evaluateJavascript(js, null);
        }
    }

    public boolean exitFullScreen() {
        if (mChromeClient != null && mChromeClient instanceof GoNativeWebChromeClient) {
            return ((GoNativeWebChromeClient) mChromeClient).exitFullScreen();
        } else {
            return false;
        }
    }

    @Override
    public boolean isCrosswalk() {
        return false;
    }

    @Override
    public void saveStateToBundle(Bundle outBundle) {
        saveState(outBundle);
    }

    @Override
    public void restoreStateFromBundle(Bundle inBundle) {
        restoreState(inBundle);
    }
}
