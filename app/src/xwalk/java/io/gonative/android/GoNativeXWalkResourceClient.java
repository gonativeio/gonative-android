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
            urlNavigation.onReceivedError((GoNativeWebviewInterface)view, errorCode);
        }
    }

    @Override
    public WebResourceResponse shouldInterceptLoadRequest(XWalkView view, String url) {
        return urlNavigation.interceptHtml((LeanWebView)view, url);
    }
}
