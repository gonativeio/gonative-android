package io.gonative.android;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Pass calls WebViewClient.shouldOverrideUrlLoading when loadUrl, reload, or goBack are called.
 */
public class LeanWebView extends WebView{
    private WebViewClient mClient = null;
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
    public void loadUrl(String url) {
        if (url == null) return;

        if (url.startsWith("javascript:"))
            LeanUtils.runJavascriptOnWebView(this, url.substring("javascript:".length()));
        else if (mClient == null || !mClient.shouldOverrideUrlLoading(this, url)) {
            super.loadUrl(url);
        }
    }

    @Override
    public void reload() {
        if (mClient == null || !(mClient instanceof LeanWebviewClient)) super.reload();
        else if(!((LeanWebviewClient)mClient).shouldOverrideUrlLoading(this, getUrl(), true))
            super.reload();
    }

    @Override
    public void goBack() {
        WebBackForwardList list = copyBackForwardList();
        int currentIndex = list.getCurrentIndex();
        if (currentIndex > 0){
            WebHistoryItem item = list.getItemAtIndex(currentIndex - 1);
            loadUrl(item.getUrl());
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
}
