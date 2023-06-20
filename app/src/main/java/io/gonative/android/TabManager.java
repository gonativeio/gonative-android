package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.aurelhubert.ahbottomnavigation.AHBottomNavigation;
import com.aurelhubert.ahbottomnavigation.AHBottomNavigationItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.LeanUtils;
import io.gonative.android.icons.Icon;

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
    private Map<String, TabMenu> tabMenus;
    private final int maxTabs = 5;
    private int tabbar_icon_size;
    private int tabbar_icon_padding;
    private Map<JSONObject, List<Pattern>> tabRegexCache = new HashMap<>(); // regex for each tab to auto-select
    private boolean useJavascript; // do not use tabs from config
    AppConfig appConfig;
    private boolean performAction = true;


    @SuppressWarnings("unused")
    private TabManager(){
        // disable instantiation without mainActivity
    }

    TabManager(MainActivity mainActivity, AHBottomNavigation bottomNavigationView) {
        this.mainActivity = mainActivity;
        tabbar_icon_size = this.mainActivity.getResources().getInteger(R.integer.tabbar_icon_size);
        tabbar_icon_padding = this.mainActivity.getResources().getInteger(R.integer.tabbar_icon_padding);
        this.bottomNavigationView = bottomNavigationView;
        this.bottomNavigationView.setOnTabSelectedListener(this);
        this.appConfig = AppConfig.getInstance(this.mainActivity);

        this.bottomNavigationView.setTitleState(AHBottomNavigation.TitleState.ALWAYS_SHOW);
        this.bottomNavigationView.setDefaultBackgroundColor(mainActivity.getResources().getColor(R.color.tabBarBackground));
        this.bottomNavigationView.setAccentColor(mainActivity.getResources().getColor(R.color.tabBarIndicator));
        this.bottomNavigationView.setInactiveColor(mainActivity.getResources().getColor(R.color.tabBarTextColor));

        this.bottomNavigationView.setTitleTextSizeInSp(12, 12);

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && intent.getAction().equals(AppConfig.PROCESSED_TAB_NAVIGATION_MESSAGE)) {
                    currentMenuId = null;
                    initializeTabMenus();
                    checkTabs(currentUrl);
                }
            }
        };
        LocalBroadcastManager.getInstance(this.mainActivity)
                .registerReceiver(broadcastReceiver,
                        new IntentFilter(AppConfig.PROCESSED_TAB_NAVIGATION_MESSAGE));

        initializeTabMenus();
    }

    private void initializeTabMenus(){
        ArrayList<Pattern> regexes = appConfig.tabMenuRegexes;
        ArrayList<String> ids = appConfig.tabMenuIDs;

        if (regexes == null || ids == null) {
            return;
        }

        tabMenus = new HashMap<>();
        Map<String, Pattern> tabSelectionConfig = new HashMap<>();

        for (int i = 0; i < ids.size(); i++) {
            tabSelectionConfig.put(ids.get(i), regexes.get(i));
        }

        for (Map.Entry<String, JSONArray> tabMenu : appConfig.tabMenus.entrySet()) {
            TabMenu item = new TabMenu();
            item.tabs = tabMenu.getValue();
            item.urlRegex = tabSelectionConfig.get(tabMenu.getKey());
            tabMenus.put(tabMenu.getKey(), item);
        }
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
            if(bottomNavigationView.getItemsCount() == 0) {
                hideTabs();
            } else {
                showTabs();
            }
        }
    }

    private void setTabs(JSONArray tabs) {
        this.tabs = tabs;

        int selectedNumber = -1;
        bottomNavigationView.removeAllItems();
        if(tabs == null) return;
    
        for (int i = 0; i < tabs.length(); i++) {
            if(i > (maxTabs-1)){
                Log.e(TAG, "Tab menu items list should not have more than 5 items");
                break;
            }

            JSONObject item = tabs.optJSONObject(i);
            if (item == null) continue;

            String label = item.optString("label");
            String icon = item.optString("icon");

            // if no label, icon and url is provided, do not include
            if(label.isEmpty() && icon.isEmpty() && item.optString("url").isEmpty()){
                continue;
            }

            // set default drawable "Question Mark" when no icon provided
            if (icon.isEmpty()) {
                icon = "faw_question";
                Log.e(TAG, "All tabs must have icons.");
            }
            
            AHBottomNavigationItem navigationItem = new AHBottomNavigationItem(label, new Icon(mainActivity.getApplicationContext(), icon, tabbar_icon_size, mainActivity.getResources().getColor(R.color.tabBarTextColor)).getDrawable());
            bottomNavigationView.addItem(navigationItem);

            if (item.optBoolean("selected")) {
                selectedNumber = i;
            }
        }

        if (selectedNumber > -1) {
            selectTabNumber(selectedNumber, true);
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

    public void setTabsWithJson(JSONObject tabsJson, int tabMenuId) {
        if(tabsJson == null) return;

        this.useJavascript = true;

        JSONArray tabs = tabsJson.optJSONArray("items");
        if (tabs != null) setTabs(tabs);

        if(tabMenuId != -1){
            TabMenu tabMenu = tabMenus.get(Integer.toString(tabMenuId));
            if(tabMenu == null || tabs != null) return;
            setTabs(tabMenu.tabs);
        }

        Object enabled = tabsJson.opt("enabled");
        if (enabled instanceof Boolean) {
            if ((Boolean)enabled) {
                this.showTabs();
            } else {
                this.hideTabs();
            }
        }
    }

    public void selectTabNumber(int tabNumber, boolean performAction) {
        if (tabNumber < 0 || tabNumber >= bottomNavigationView.getItemsCount()) {
            return;
        }
        this.performAction = performAction;
        this.bottomNavigationView.setCurrentItem(tabNumber);
    }

    @Override
    public boolean onTabSelected(int position, boolean wasSelected) {
        if (this.tabs != null && position < this.tabs.length() && position != -1) {
            JSONObject entry = this.tabs.optJSONObject(position);

            String url = entry.optString("url");
            String javascript = entry.optString("javascript");

            if (!performAction) {
                performAction = true;
                return true;
            }

            if (!TextUtils.isEmpty(url)) {
                if (!TextUtils.isEmpty(javascript)) mainActivity.loadUrlAndJavascript(url, javascript, true);
                else mainActivity.loadUrl(url, true);
            }
        }
        return true;
    }

    private class TabMenu {
        Pattern urlRegex;
        JSONArray tabs;
    }
}
