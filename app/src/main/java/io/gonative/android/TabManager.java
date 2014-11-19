package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Created by Weiyin He on 9/22/14.
 * Copyright 2014 GoNative.io LLC
 */
public class TabManager implements ActionBar.TabListener {
    private MainActivity mainActivity;
    private boolean isFirstSelection;
    private String currentMenuId;
    private String currentUrl;
    private BroadcastReceiver broadcastReceiver;

    private TabManager(){
        // disable instantiation without mainActivity
    }

    public TabManager(MainActivity mainActivity) {
        this.mainActivity = mainActivity;

        this.broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && intent.getAction().equals(AppConfig.PROCESSED_TAB_NAVIGATION_MESSAGE)) {
                    currentMenuId = null;
                    checkTabs(currentUrl);
                }
            }
        };
        LocalBroadcastManager.getInstance(this.mainActivity)
                .registerReceiver(this.broadcastReceiver,
                        new IntentFilter(AppConfig.PROCESSED_TAB_NAVIGATION_MESSAGE));
    }

    public void checkTabs(String url) {
        this.currentUrl = url;

        if (this.mainActivity == null || this.mainActivity.getSupportActionBar() == null || url == null) {
            return;
        }

        AppConfig appConfig = AppConfig.getInstance(this.mainActivity);

        ArrayList<Pattern> regexes = appConfig.tabMenuRegexes;
        ArrayList<String> ids = appConfig.tabMenuIDs;
        if (regexes == null || ids == null) {
            hideTabs();
            return;
        }

        for (int i = 0; i < regexes.size(); i++) {
            Pattern regex = regexes.get(i);
            if (regex.matcher(url).matches()) {
                setMenuID(ids.get(i));
                return;
            }
        }

        setMenuID(null);
    }

    private void setMenuID(String id){
        if (id == null) {
            this.currentMenuId = null;
            hideTabs();
        }
        else if (this.currentMenuId == null || !this.currentMenuId.equals(id)) {
            this.currentMenuId = id;
            JSONArray tabs = AppConfig.getInstance(this.mainActivity).tabMenus.get(id);
            setTabs(tabs);
            showTabs();
        }
    }

    private void setTabs(JSONArray tabs) {
        ActionBar actionBar = this.mainActivity.getSupportActionBar();
        if (actionBar == null) {
            return;
        }

        actionBar.removeAllTabs();
        this.isFirstSelection = true;

        for (int i = 0; i < tabs.length(); i++) {
            JSONObject entry = tabs.optJSONObject(i);
            if (entry != null) {
                String label = entry.optString("label");
                String url = entry.optString("url");
                String javascript = entry.optString("javascript");

                if (label != null) {
                    ActionBar.Tab tab = actionBar.newTab().setText(label).setTabListener(this);
                    actionBar.addTab(tab);

                    tab.setTag(new Pair<String,String>(url, javascript));
                }
            }
        }
    }

    private void showTabs() {
        if (this.mainActivity.getSupportActionBar() != null
                && this.mainActivity.getSupportActionBar().getNavigationMode() != ActionBar.NAVIGATION_MODE_TABS) {
            this.mainActivity.getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        }
    }

    private void hideTabs() {
        if (this.mainActivity.getSupportActionBar() != null) {
            this.mainActivity.getSupportActionBar().removeAllTabs();
            this.mainActivity.getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        }
    }

    private void selectedTab(ActionBar.Tab tab) {
        Object tag = tab.getTag();
        if (tag instanceof Pair) {
            Pair<String,String> urlJavascript = (Pair<String,String>)tag;
            String url = urlJavascript.first;
            String javascript = urlJavascript.second;

            if (url != null && !url.isEmpty()) {
                if (javascript != null) mainActivity.loadUrlAndJavascript(url, javascript);
                else mainActivity.loadUrl(url);
            }
        }
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        if (isFirstSelection) {
            isFirstSelection = false;
        } else {
            selectedTab(tab);
        }
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
        // do nothing
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
        selectedTab(tab);
    }
}
