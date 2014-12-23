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
import android.webkit.WebView;
import android.webkit.WebViewClient;

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

/**
 * Created by Weiyin He on 9/3/14.
 * Copyright 2014 GoNative.io LLC
 */

public class WebViewPool {
    private static final String TAG = WebViewPool.class.getName();
    // singleton
    private static WebViewPool instance;
    private Context context;

    private boolean isInitialized;
    private Map<String, LeanWebView> urlToWebview;
    private Map<String, WebViewPoolDisownPolicy> urlToDisownPolicy;
    private List<Set<String>> urlSets;
    private Set<String> urlsToLoad;
    private WebViewClient webviewClient;
    private LeanWebView currentLoadingWebview;
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

        this.urlToWebview = new HashMap<String, LeanWebView>();
        this.urlToDisownPolicy = new HashMap<String, WebViewPoolDisownPolicy>();
        this.urlSets = new ArrayList<Set<String>>();
        this.urlsToLoad = new HashSet<String>();

        // register for broadcast messages
        this.messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;

                if (intent.getAction().equals(LeanWebviewClient.STARTED_LOADING_MESSAGE)) {
                    WebViewPool pool = WebViewPool.this;
                    pool.isMainActivityLoading = true;
                    if (pool.currentLoadingWebview != null) {
                        pool.currentLoadingWebview.stopLoading();
                        pool.isLoading = false;
                    }
                } else if (intent.getAction().equals(LeanWebviewClient.FINISHED_LOADING_MESSAGE)) {
                    WebViewPool pool = WebViewPool.this;
                    pool.isMainActivityLoading = false;
                    pool.resumeLoading();
                } else if (intent.getAction().equals(AppConfig.PROCESSED_WEBVIEW_POOLS_MESSAGE)) {
                    processConfig();
                }
            }
        };
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                this.messageReceiver, new IntentFilter(LeanWebviewClient.STARTED_LOADING_MESSAGE));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                this.messageReceiver, new IntentFilter(LeanWebviewClient.FINISHED_LOADING_MESSAGE));
        LocalBroadcastManager.getInstance(this.context).registerReceiver(
                this.messageReceiver, new IntentFilter(AppConfig.PROCESSED_WEBVIEW_POOLS_MESSAGE));

        this.webviewClient = new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                WebViewPool pool = WebViewPool.this;
                view.setWebViewClient(null);
                pool.urlToWebview.put(pool.currentLoadingUrl, pool.currentLoadingWebview);
                pool.currentLoadingUrl = null;
                pool.currentLoadingWebview = null;
                pool.isLoading = false;

                pool.resumeLoading();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // intercept html
                WebViewPool pool = WebViewPool.this;

                if (AppConfig.getInstance(pool.context).interceptHtml) {
                    try {
                        URL parsedUrl = new URL(url);
                        if (parsedUrl.getProtocol().equals("http") || parsedUrl.getProtocol().equals("https")) {
                            new WebviewInterceptTask(pool.context, null).execute(new WebviewInterceptTask.WebviewInterceptParams(view, parsedUrl, true));
                            return true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }

                return false;
            }
        };

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
            this.currentLoadingWebview.loadUrl(this.currentLoadingUrl);
            this.isLoading = true;
            return;
        }

        if (!this.urlsToLoad.isEmpty()) {
            String urlString = this.urlsToLoad.iterator().next();
            this.currentLoadingUrl = urlString;

            LeanWebView webview = new LeanWebView(this.context);
            LeanUtils.setupWebview(webview, this.context);

            // size it before loading url
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            webview.layout(0, 0, size.x, size.y);

            webview.setWebViewClient(this.webviewClient);
            this.currentLoadingWebview = webview;
            this.urlsToLoad.remove(urlString);
            this.currentLoadingWebview.loadUrl(urlString);
        }
    }

    public void disownWebview(WebView webview) {
        Iterator<String> it = this.urlToWebview.keySet().iterator();
        while(it.hasNext()) {
            String key = it.next();
            if (this.urlToWebview.get(key) == webview) {
                it.remove();
                this.urlsToLoad.add(key);
            }
        }
    }

    public Pair<LeanWebView, WebViewPoolDisownPolicy> webviewForUrl(String url) {
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

        LeanWebView webview = this.urlToWebview.get(url);
        if (webview == null) return new Pair<LeanWebView, WebViewPoolDisownPolicy>(null, null);

        WebViewPoolDisownPolicy policy = this.urlToDisownPolicy.get(url);
        return new Pair<LeanWebView, WebViewPoolDisownPolicy>(webview, policy);
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
