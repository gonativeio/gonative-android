package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.aurelhubert.ahbottomnavigation.AHBottomNavigation;
import com.aurelhubert.ahbottomnavigation.AHBottomNavigationItem;
import com.mikepenz.fontawesome_typeface_library.FontAwesome;
import com.mikepenz.iconics.IconicsDrawable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.gonative.android.library.AppConfig;

/**
 * Created by Weiyin He on 9/22/14.
 * Copyright 2014 GoNative.io LLC
 */
public class TabManager implements AHBottomNavigation.OnTabSelectedListener {
    private static final String TAG = TabManager.class.getName();
    private MainActivity mainActivity;
    private AHBottomNavigation bottomNavigationView;
    private String currentMenuId;
    private String currentUrl;
    private JSONArray tabs;
    private Map<JSONObject, List<Pattern>> tabRegexCache = new HashMap<>(); // regex for each tab to auto-select
    private boolean useJavascript; // do not use tabs from config
    AppConfig appConfig;


    @SuppressWarnings("unused")
    private TabManager(){
        // disable instantiation without mainActivity
    }

    TabManager(MainActivity mainActivity, AHBottomNavigation bottomNavigationView) {
        this.mainActivity = mainActivity;
        this.bottomNavigationView = bottomNavigationView;
        this.bottomNavigationView.setOnTabSelectedListener(this);
        this.appConfig = AppConfig.getInstance(this.mainActivity);

        this.bottomNavigationView.setTitleState(AHBottomNavigation.TitleState.ALWAYS_SHOW);
        if (appConfig.tabBarBackgroundColor != null){
            this.bottomNavigationView.setDefaultBackgroundColor(appConfig.tabBarBackgroundColor);
        }
        if (appConfig.tabBarIndicatorColor != null) {
            this.bottomNavigationView.setAccentColor(appConfig.tabBarIndicatorColor);
        } else {
            this.bottomNavigationView.setAccentColor(Color.parseColor("#2f79fe"));
        }
        if (appConfig.tabBarTextColor != null) {
            this.bottomNavigationView.setInactiveColor(appConfig.tabBarTextColor);
        }

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && intent.getAction().equals(AppConfig.PROCESSED_TAB_NAVIGATION_MESSAGE)) {
                    currentMenuId = null;
                    checkTabs(currentUrl);
                }
            }
        };
        LocalBroadcastManager.getInstance(this.mainActivity)
                .registerReceiver(broadcastReceiver,
                        new IntentFilter(AppConfig.PROCESSED_TAB_NAVIGATION_MESSAGE));
    }

    public void checkTabs(String url) {
        this.currentUrl = url;

        if (this.mainActivity == null || url == null) {
            return;
        }

        if (this.useJavascript) {
            autoSelectTab(url);
            return;
        }

        ArrayList<Pattern> regexes = appConfig.tabMenuRegexes;
        ArrayList<String> ids = appConfig.tabMenuIDs;
        if (regexes == null || ids == null) {
            hideTabs();
            return;
        }

        String menuId = null;

        for (int i = 0; i < regexes.size(); i++) {
            Pattern regex = regexes.get(i);
            if (regex.matcher(url).matches()) {
                menuId = ids.get(i);
                break;
            }
        }

        setMenuID(menuId);

        if (menuId != null) autoSelectTab(url);
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
        this.tabs = tabs;

        int selectedNumber = -1;
        bottomNavigationView.removeAllItems();
        for (int i = 0; i < tabs.length(); i++) {
            JSONObject item = tabs.optJSONObject(i);
            if (item == null) continue;

            String label = item.optString("label");
            String icon = item.optString("icon");

            Drawable iconDrawable = new ColorDrawable(Color.TRANSPARENT);
            if (icon != null) {
                icon = "faw_" + icon.substring(icon.indexOf("-")+1).replaceAll("-", "_");
                try {
                    iconDrawable = new IconicsDrawable(this.mainActivity, FontAwesome.Icon.valueOf(icon)).color(appConfig.actionbarForegroundColor).sizeDp(24);
                } catch (IllegalArgumentException e) {
                    // icon was not found in IconValue enum
                    Log.e(TAG, e.getMessage(), e);
                }
            }

            AHBottomNavigationItem navigationItem = new AHBottomNavigationItem(label, iconDrawable);
            bottomNavigationView.addItem(navigationItem);

            if (item.optBoolean("selected")) {
                selectedNumber = i;
            }
        }

        if (selectedNumber > -1) {
            selectTabNumber(selectedNumber);
        }
    }

    private void showTabs() {
        this.mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.showTabs();
            }
        });
    }

    private void hideTabs() {
        this.mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.hideTabs();
            }
        });
    }

    // regex used for auto tab selection
    private List<Pattern> getRegexForTab(JSONObject tabConfig) {
        if (tabConfig == null) return null;

        Object regex = tabConfig.opt("regex");
        if (regex == null) return null;

        return LeanUtils.createRegexArrayFromStrings(regex);
    }

    private List<Pattern> getCachedRegexForTab(int position) {
        if (tabs == null || position < 0 || position >= tabs.length()) return null;

        JSONObject tabConfig = tabs.optJSONObject(position);
        if (tabConfig == null) return null;

        if (tabRegexCache.containsKey(tabConfig)) {
            return tabRegexCache.get(tabConfig);
        } else {
            List<Pattern> regex = getRegexForTab(tabConfig);
            tabRegexCache.put(tabConfig, regex);
            return regex;
        }
    }

    public void autoSelectTab(String url) {
        if (tabs == null) return;

        for (int i = 0; i < tabs.length(); i++) {
            List<Pattern> patternList = getCachedRegexForTab(i);
            if (patternList == null) continue;

            for(Pattern regex : patternList) {
                if (regex.matcher(url).matches()) {
                    bottomNavigationView.setCurrentItem(i, false);
                    return;
                }
            }
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean selectTab(String url, String javascript) {
        if (url == null) return false;

        if (javascript == null) javascript = "";

        if (this.tabs != null) {
            for (int i = 0; i < this.tabs.length(); i++) {
                JSONObject entry = this.tabs.optJSONObject(i);
                if (entry != null) {
                    String entryUrl = entry.optString("url");
                    String entryJs = entry.optString("javascript");

                    if (entryUrl == null) continue;
                    if (entryJs == null) entryJs = "";

                    if (url.equals(entryUrl) && javascript.equals(entryJs)) {
                        if (this.bottomNavigationView != null) {
                            this.bottomNavigationView.setCurrentItem(i, false);
                            return true;
                        }
                    }

                }
            }
        }

        return false;
    }

    public void setTabsWithJson(String tabsJson) {
        JSONObject tabsConfig;
        try {
            tabsConfig = new JSONObject(tabsJson);
        } catch (JSONException e) {
            return;
        }

        this.useJavascript = true;

        JSONArray tabs = tabsConfig.optJSONArray("items");
        if (tabs != null) setTabs(tabs);

        Object enabled = tabsConfig.opt("enabled");
        if (enabled instanceof Boolean) {
            if ((Boolean)enabled) {
                this.showTabs();
            } else {
                this.hideTabs();
            }
        }
    }

    public void selectTabNumber(int tabNumber) {
        if (tabNumber < 0 || tabNumber >= bottomNavigationView.getItemsCount()) {
            return;
        }

        this.bottomNavigationView.setCurrentItem(tabNumber);
    }

    @Override
    public boolean onTabSelected(int position, boolean wasSelected) {
        if (this.tabs != null && position < this.tabs.length()) {
            JSONObject entry = this.tabs.optJSONObject(position);

            String url = entry.optString("url");
            String javascript = entry.optString("javascript");

            if (url != null && !url.isEmpty()) {
                if (javascript != null) mainActivity.loadUrlAndJavascript(url, javascript, true);
                else mainActivity.loadUrl(url, true);
            }
        }
        return true;
    }
}
