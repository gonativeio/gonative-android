package io.gonative.android;

import android.app.ActionBar;
import android.app.FragmentTransaction;

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

    public TabManager(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void checkTabs(String url) {
        if (this.mainActivity == null || this.mainActivity.getActionBar() == null) {
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
        ActionBar actionBar = this.mainActivity.getActionBar();
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

                if (label != null) {
                    ActionBar.Tab tab = actionBar.newTab().setText(label).setTabListener(this);
                    actionBar.addTab(tab);
                    if (url != null) {
                        tab.setTag(url);
                    }
                }
            }
        }
    }

    private void showTabs() {
        if (this.mainActivity.getActionBar() != null
                && this.mainActivity.getActionBar().getNavigationMode() != ActionBar.NAVIGATION_MODE_TABS) {
            this.mainActivity.getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        }
    }

    private void hideTabs() {
        if (this.mainActivity.getActionBar() != null) {
            this.mainActivity.getActionBar().removeAllTabs();
            this.mainActivity.getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        }
    }

    private void selectedTab(ActionBar.Tab tab) {
        Object url = tab.getTag();
        if (url instanceof String) {
            String urlString = (String) url;
            if (!urlString.isEmpty()) {
                mainActivity.loadUrl(urlString);
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
