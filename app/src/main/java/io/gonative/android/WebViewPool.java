package io.gonative.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Pair;
import android.view.Display;
import android.view.WindowManager;
import android.webkit.WebResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.GoNativeWebviewInterface;

/**
 * Created by Weiyin He on 9/3/14.
 * Copyright 2014 GoNative.io LLC
 */

public class WebViewPool {
    public class WebViewPoolCallback {
        @SuppressWarnings("unused")
        public void onPageFinished(final GoNativeWebviewInterface webview, String url) {
            WebViewPool pool = WebViewPool.this;

            pool.urlToWebview.put(pool.currentLoadingUrl, pool.currentLoadingWebview);
            pool.currentLoadingUrl = null;
            pool.currentLoadingWebview = null;
            pool.isLoading = false;
            pool.htmlIntercept.setInterceptUrl(null);

            pool.resumeLoading();
        }

        public WebResourceResponse interceptHtml(GoNativeWebviewInterface webview, String url) {
            return htmlIntercept.interceptHtml(webview, url, null);
        }
    }

    private Activity context;
    private HtmlIntercept htmlIntercept;

    private boolean isInitialized;
    private Map<String, GoNativeWebviewInterface> urlToWebview;
    private Map<String, WebViewPoolDisownPolicy> urlToDisownPolicy;

    private WebViewPoolCallback webViewPoolCallback = new WebViewPoolCallback();

    private List<Set<String>> urlSets;
    private Set<String> urlsToLoad;
    private GoNativeWebviewInterface currentLoadingWebview;
    private String currentLoadingUrl;
    private boolean isLoading;
    private String lastUrlRequest;
    private boolean isMainActivityLoading;

    public void init(Activity activity) {
        if (this.isInitialized) return;
        this.isInitialized = true;

        // webviews must be instantiated from activity context
        this.context = activity;
        this.htmlIntercept = new HtmlIntercept(activity);

        this.urlToWebview = new HashMap<>();
        this.urlToDisownPolicy = new HashMap<>();
        this.urlSets = new ArrayList<>();
        this.urlsToLoad = new HashSet<>();

        // register for broadcast messages
        BroadcastReceiver messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;

                switch (intent.getAction()) {
                    case UrlNavigation.STARTED_LOADING_MESSAGE: {
                        WebViewPool pool = WebViewPool.this;
                        pool.isMainActivityLoading = true;
                        if (pool.currentLoadingWebview != null) {
                            // onReceive is always called on the main thread, so this is safe.
                            pool.currentLoadingWebview.stopLoading();
                            pool.isLoading = false;
                        }
                        break;
                    }
                    case UrlNavigation.FINISHED_LOADING_MESSAGE: {
                        WebViewPool pool = WebViewPool.this;
                        pool.isMainActivityLoading = false;
                        pool.resumeLoading();
                        break;
                    }
                    case AppConfig.PROCESSED_WEBVIEW_POOLS_MESSAGE:
                        processConfig();
                        break;
                    case UrlNavigation.CLEAR_POOLS_MESSAGE:
                        WebViewPool.this.flushAll();
                        break;
                }
            }
        };
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                messageReceiver, new IntentFilter(UrlNavigation.STARTED_LOADING_MESSAGE));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                messageReceiver, new IntentFilter(UrlNavigation.FINISHED_LOADING_MESSAGE));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                messageReceiver, new IntentFilter(UrlNavigation.CLEAR_POOLS_MESSAGE));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                messageReceiver, new IntentFilter(AppConfig.PROCESSED_WEBVIEW_POOLS_MESSAGE));

        processConfig();
    }

    private void processConfig() {
        JSONArray config = AppConfig.getInstance(this.context).webviewPools;
        if (config == null) {
            return;
        }

        for (int i = 0; i < config.length(); i++) {
            JSONObject entry = config.optJSONObject(i);
            if (entry != null) {
                JSONArray urls = entry.optJSONArray("urls");
                if (urls != null) {
                    HashSet<String> urlSet = new HashSet<>();
                    for (int j = 0; j < urls.length(); j++) {
                        if (urls.isNull(j)) continue;
                        String urlString = null;
                        WebViewPoolDisownPolicy policy = WebViewPoolDisownPolicy.defaultPolicy;

                        Object urlEntry = urls.opt(j);
                        if (urlEntry instanceof String) urlString = (String)urlEntry;

                        if (urlString == null && urlEntry instanceof JSONObject) {
                            urlString = ((JSONObject)urlEntry).optString("url");
                            String policyString = AppConfig.optString((JSONObject)urlEntry, "disown");
                            if (policyString != null) {
                                if (policyString.equalsIgnoreCase("reload"))
                                    policy = WebViewPoolDisownPolicy.Reload;
                                else if (policyString.equalsIgnoreCase("never"))
                                    policy = WebViewPoolDisownPolicy.Never;
                                else if (policyString.equalsIgnoreCase("always"))
                                    policy = WebViewPoolDisownPolicy.Always;
                            }
                        }

                        if (urlString != null) {
                            urlSet.add(urlString);
                            this.urlToDisownPolicy.put(urlString, policy);
                        }
                    }

                    this.urlSets.add(urlSet);
                }

            }
        }

        // if config changed, we may have to load webviews corresponding to the previously requested url
        if (this.lastUrlRequest != null) {
            webviewForUrl(this.lastUrlRequest);
        }

        resumeLoading();
    }

    private void resumeLoading() {
        if (this.isMainActivityLoading || this.isLoading) return;

        if (this.currentLoadingWebview != null && this.currentLoadingUrl != null) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentLoadingWebview.loadUrl(currentLoadingUrl);
                }
            });
            this.isLoading = true;
            return;
        }

        if (!this.urlsToLoad.isEmpty()) {
            final String urlString = this.urlsToLoad.iterator().next();
            this.currentLoadingUrl = urlString;
            this.htmlIntercept.setInterceptUrl(urlString);

            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    LeanWebView webview = new LeanWebView(context);
                    currentLoadingWebview = webview;
                    urlsToLoad.remove(urlString);
                    WebViewSetup.setupWebview(webview, context);

                    // size it before loading url
                    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) {
                        Display display = wm.getDefaultDisplay();
                        Point size = new Point();
                        display.getSize(size);
                        webview.layout(0, 0, size.x, size.y);
                    }

                    new PoolWebViewClient(webViewPoolCallback, webview);

                    currentLoadingWebview = webview;
                    urlsToLoad.remove(urlString);

                    currentLoadingWebview.loadUrl(urlString);
                }
            });
        }
    }

    private void flushAll() {
        if (this.currentLoadingWebview != null) this.currentLoadingWebview.stopLoading();
        this.isLoading = false;
        this.currentLoadingWebview = null;
        this.currentLoadingUrl = null;
        this.lastUrlRequest = null;
        this.urlToWebview.clear();
    }

    public void disownWebview(GoNativeWebviewInterface webview) {
        Iterator<String> it = this.urlToWebview.keySet().iterator();
        while(it.hasNext()) {
            String key = it.next();
            if (this.urlToWebview.get(key) == webview) {
                it.remove();
                this.urlsToLoad.add(key);
            }
        }
    }

    public Pair<GoNativeWebviewInterface, WebViewPoolDisownPolicy> webviewForUrl(String url) {
        this.lastUrlRequest = url;
        HashSet<String> urlSet = urlSetForUrl(url);
        if (urlSet.size() > 0) {
            HashSet<String> newUrls = new HashSet<> (urlSet);
            if (this.currentLoadingUrl != null) {
                newUrls.remove(this.currentLoadingUrl);
            }
            newUrls.removeAll(this.urlToWebview.keySet());

            this.urlsToLoad.addAll(newUrls);
        }

        GoNativeWebviewInterface webview = this.urlToWebview.get(url);
        if (webview == null) return new Pair<>(null, null);

        WebViewPoolDisownPolicy policy = this.urlToDisownPolicy.get(url);
        return new Pair<>(webview, policy);
    }

    private HashSet<String> urlSetForUrl(String url){
        HashSet<String> result = new HashSet<>();
        for (Set<String> set : this.urlSets) {
            if (set.contains(url)) {
                result.addAll(set);
            }
        }
        return result;
    }

}
