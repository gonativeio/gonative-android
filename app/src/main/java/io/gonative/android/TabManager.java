package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;

import com.astuetz.PagerSlidingTabStrip;

import org.json.JSONArray;
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
public class TabManager extends PagerAdapter implements PagerSlidingTabStrip.OnTabClickListener {
    private MainActivity mainActivity;
    private ViewPager viewPager;
    private String currentMenuId;
    private String currentUrl;
    private BroadcastReceiver broadcastReceiver;
    private JSONArray tabs;
    private Map<JSONObject, List<Pattern>> tabRegexCache = new HashMap<>(); // regex for each tab to auto-select

    private TabManager(){
        // disable instantiation without mainActivity
    }

    public TabManager(MainActivity mainActivity, ViewPager viewPager) {
        this.mainActivity = mainActivity;
        this.viewPager = viewPager;

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

        if (this.mainActivity == null || url == null) {
            return;
        }

        AppConfig appConfig = AppConfig.getInstance(this.mainActivity);

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
        notifyDataSetChanged();
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
                    viewPager.setCurrentItem(i);
                    return;
                }
            }
        }
    }

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
                        if (this.viewPager != null) {
                            this.viewPager.setCurrentItem(i);
                            return true;
                        }
                    }

                }
            }
        }

        return false;
    }

    @Override
    public int getCount() {
        if (this.tabs != null) {
            return this.tabs.length();
        } else {
            return 0;
        }
    }

    // the following three methods are there for our dummy viewpager.
    @Override
    public boolean isViewFromObject(View view, Object o) {
        return view == o;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        return mainActivity.getLayoutInflater().inflate(R.layout.empty, container);
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        if (object instanceof View) {
            container.removeView((View)object);
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        String title = "";
        if (this.tabs != null && position < this.tabs.length()) {
            JSONObject entry = this.tabs.optJSONObject(position);
            if (entry != null) {
                title = entry.optString("label", "");
            }
        }

        return title;
    }

    @Override
    public void onTabClick(int position) {
        if (this.tabs != null && position < this.tabs.length()) {
            JSONObject entry = this.tabs.optJSONObject(position);

            String url = entry.optString("url");
            String javascript = entry.optString("javascript");

            if (url != null && !url.isEmpty()) {
                if (javascript != null) mainActivity.loadUrlAndJavascript(url, javascript, true);
                else mainActivity.loadUrl(url, true);
            }
        }
    }
}
