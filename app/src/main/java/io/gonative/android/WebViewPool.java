package io.gonative.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.WindowManager;
import android.webkit.WebResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.gonative.android.library.AppConfig;

/**
 * Created by Weiyin He on 9/3/14.
 * Copyright 2014 GoNative.io LLC
 */

public class WebViewPool {
    public class WebViewPoolCallback {
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

    private static final String TAG = WebViewPool.class.getName();
    // singleton
    private static WebViewPool instance;
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

    private BroadcastReceiver messageReceiver;

    public static synchronized WebViewPool getInstance(){
        if (instance == null) {
            instance = new WebViewPool();
        }
        return instance;
    }

    protected WebViewPool() {
        // prevent external instantiation
    }

    public void init(Activity activity) {
        if (this.isInitialized) return;
        this.isInitialized = true;

        // webviews must be instantiated from activity context
        this.context = activity;
        this.htmlIntercept = new HtmlIntercept(activity);

        this.urlToWebview = new HashMap<String, GoNativeWebviewInterface>();
        this.urlToDisownPolicy = new HashMap<String, WebViewPoolDisownPolicy>();
        this.urlSets = new ArrayList<Set<String>>();
        this.urlsToLoad = new HashSet<String>();

        // register for broadcast messages
        this.messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;

                if (intent.getAction().equals(UrlNavigation.STARTED_LOADING_MESSAGE)) {
                    WebViewPool pool = WebViewPool.this;
                    pool.isMainActivityLoading = true;
                    if (pool.currentLoadingWebview != null) {
                        // onReceive is always called on the main thread, so this is safe.
                        pool.currentLoadingWebview.stopLoading();
                        pool.isLoading = false;
                    }
                } else if (intent.getAction().equals(UrlNavigation.FINISHED_LOADING_MESSAGE)) {
                    WebViewPool pool = WebViewPool.this;
                    pool.isMainActivityLoading = false;
                    pool.resumeLoading();
                } else if (intent.getAction().equals(AppConfig.PROCESSED_WEBVIEW_POOLS_MESSAGE)) {
                    processConfig();
                } else if (intent.getAction().equals(UrlNavigation.CLEAR_POOLS_MESSAGE)) {
                    WebViewPool.this.flushAll();
                }
            }
        };
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                this.messageReceiver, new IntentFilter(UrlNavigation.STARTED_LOADING_MESSAGE));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                this.messageReceiver, new IntentFilter(UrlNavigation.FINISHED_LOADING_MESSAGE));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                this.messageReceiver, new IntentFilter(UrlNavigation.CLEAR_POOLS_MESSAGE));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                this.messageReceiver, new IntentFilter(AppConfig.PROCESSED_WEBVIEW_POOLS_MESSAGE));

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
                    HashSet<String> urlSet = new HashSet<String>();
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
                    Display display = wm.getDefaultDisplay();
                    Point size = new Point();
                    display.getSize(size);
                    webview.layout(0, 0, size.x, size.y);

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
            HashSet<String> newUrls = (HashSet<String>)urlSet.clone();
            if (this.currentLoadingUrl != null) {
                newUrls.remove(this.currentLoadingUrl);
            }
            newUrls.removeAll(this.urlToWebview.keySet());

            this.urlsToLoad.addAll(newUrls);
        }

        GoNativeWebviewInterface webview = this.urlToWebview.get(url);
        if (webview == null) return new Pair<GoNativeWebviewInterface, WebViewPoolDisownPolicy>(null, null);

        WebViewPoolDisownPolicy policy = this.urlToDisownPolicy.get(url);
        return new Pair<GoNativeWebviewInterface, WebViewPoolDisownPolicy>(webview, policy);
    }

    private HashSet<String> urlSetForUrl(String url){
        HashSet<String> result = new HashSet<String>();
        for (Set<String> set : this.urlSets) {
            if (set.contains(url)) {
                result.addAll(set);
            }
        }
        return result;
    }

}
