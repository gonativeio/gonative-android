package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import com.astuetz.PagerSlidingTabStrip;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Created by Weiyin He on 9/22/14.
 * Copyright 2014 GoNative.io LLC
 */
public class TabManager extends PagerAdapter implements PagerSlidingTabStrip.OnTabClickListener {
    private MainActivity mainActivity;
    private String currentMenuId;
    private String currentUrl;
    private BroadcastReceiver broadcastReceiver;
    private JSONArray tabs;

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
                if (javascript != null) mainActivity.loadUrlAndJavascript(url, javascript);
                else mainActivity.loadUrl(url);
            }
        }
    }
}
