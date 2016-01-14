package io.gonative.android;

import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 11/25/14.
 * Copyright 2014 GoNative.io LLC
 */
public class ActionManager {
    private static final String TAG = ActionManager.class.getName();
    private static final String ACTION_SHARE = "share";
    private MainActivity activity;
    private String currentMenuID;
    private HashMap<MenuItem, String>itemToUrl;

    public ActionManager(MainActivity activity) {
        this.activity = activity;
        this.itemToUrl = new HashMap<MenuItem, String>();
    }

    public void checkActions(String url) {
        if (this.activity == null || url == null) return;

        AppConfig appConfig = AppConfig.getInstance(this.activity);

        ArrayList<Pattern> regexes = appConfig.actionRegexes;
        ArrayList<String> ids = appConfig.actionIDs;
        if (regexes == null || ids == null) {
            setMenuID(null);
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

    private void setMenuID(String menuID) {
        boolean changed;

        if (this.currentMenuID == null) {
            changed = menuID != null;
        } else {
            if (menuID == null) {
                changed = true;
            } else {
                changed = !this.currentMenuID.equals(menuID);
            }
        }

        if (changed) {
            this.currentMenuID = menuID;
            this.activity.invalidateOptionsMenu();
        }
    }

    public void addActions(Menu menu) {
        this.itemToUrl.clear();

        if (this.currentMenuID == null) return;

        AppConfig appConfig = AppConfig.getInstance(this.activity);
        if (appConfig.actions == null) return;

        JSONArray actions = appConfig.actions.get(currentMenuID);
        if (actions == null) return;

        for (int i = 0; i < actions.length(); i++) {
            int itemID = i;
            JSONObject entry = actions.optJSONObject(i);
            if (entry != null) {
                String system = AppConfig.optString(entry, "system");
                if (system != null && system.equals("share")) {
                    TypedArray a = this.activity.getTheme().obtainStyledAttributes(new int []{R.attr.ic_action_share});
                    Drawable shareIcon = a.getDrawable(0);
                    a.recycle();

                    MenuItem menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, R.string.action_share)
                            .setIcon(shareIcon)
                            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                    itemToUrl.put(menuItem, ACTION_SHARE);
                }
                else {
                    String label = AppConfig.optString(entry, "label");
                    String icon = AppConfig.optString(entry, "icon");
                    String url = AppConfig.optString(entry, "url");

                    Drawable iconDrawable = null;
                    if (icon != null) {
                        icon = icon.replaceAll("-", "_");
                        try {
                            iconDrawable = new IconDrawable(this.activity, Iconify.IconValue.valueOf(icon))
                                    .actionBarSize().color(appConfig.actionbarForegroundColor);
                        } catch (IllegalArgumentException e) {
                            // icon was not found in IconValue enum
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }

                    MenuItem menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, label)
                            .setIcon(iconDrawable)
                            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                    if (url != null) {
                        this.itemToUrl.put(menuItem, url);
                    }
                }
            }
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        String url = this.itemToUrl.get(item);
        if (url != null) {
            if (url.equals(ACTION_SHARE)) {
                this.activity.sharePage();
                return true;
            }

            this.activity.loadUrl(url);
            return true;
        } else {
            return false;
        }
    }
}
