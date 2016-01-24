package io.gonative.android;

import android.content.Context;
import android.os.Handler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Created by weiyin on 9/9/15.
 */
public class PoolWebViewClient extends WebViewClient {
    private WebViewPool.WebViewPoolCallback webViewPoolCallback;

    public PoolWebViewClient(WebViewPool.WebViewPoolCallback webViewPoolCallback, LeanWebView view) {
        this.webViewPoolCallback = webViewPoolCallback;
        view.setWebViewClient(this);
    }

    @Override
    public void onPageFinished(final WebView view, String url) {
        super.onPageFinished(view, url);

        // remove self as webviewclient
        new Handler(view.getContext().getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                view.setWebViewClient(null);
            }
        });

        webViewPoolCallback.onPageFinished((GoNativeWebviewInterface)view, url);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return webViewPoolCallback.shouldOverrideUrlLoading((GoNativeWebviewInterface)view, url);
    }
}
