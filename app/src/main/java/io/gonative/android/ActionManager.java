package io.gonative.android;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.SearchView;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;

import io.gonative.android.icons.Icon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
    private static final String ACTION_REFRESH = "refresh";
    private static final int ACTIONBAR_ITEM_MARGIN = 132;

    private final MainActivity activity;
    private final HashMap<MenuItem, String>itemToUrl;
    private final int action_button_size;
    private final ActionBar actionBar;
    private final AppConfig appConfig;
    private final boolean isRoot;
    private final int colorForeground;
    private final int colorBackground;

    private String currentMenuID;
    private LinearLayout header;
    private RelativeLayout titleContainer;
    private boolean isOnSearchMode = false;
    private SearchView searchView;

    private boolean searchButtonInitialized = false;
    private boolean searchActive = false;

    private int leftItemsCount = 0;
    private int rightItemsCount = 0;

    ActionManager(MainActivity activity, boolean isRoot) {
        this.activity = activity;
        this.appConfig = AppConfig.getInstance(activity);
        this.itemToUrl = new HashMap<>();
        action_button_size = this.activity.getResources().getInteger(R.integer.action_button_size);
        actionBar = activity.getSupportActionBar();
        this.isRoot = isRoot;

        colorForeground = activity.getResources().getColor(R.color.titleTextColor);
        colorBackground = activity.getResources().getColor(R.color.colorPrimary);
    }

    public void setupActionBar() {
        if (actionBar == null) return;
        header = (LinearLayout) activity.getLayoutInflater().inflate(R.layout.actionbar_title, null);
        // why use a custom view and not setDisplayUseLogoEnabled and setLogo?
        // Because logo doesn't work!
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setCustomView(header);
        ActionBar.LayoutParams params = (ActionBar.LayoutParams) header.getLayoutParams();
        params.width = ActionBar.LayoutParams.MATCH_PARENT;

        searchView = header.findViewById(R.id.search_view);
        titleContainer = header.findViewById(R.id.title_container);

        ViewGroup.MarginLayoutParams titleContainerParams = (ViewGroup.MarginLayoutParams) titleContainer.getLayoutParams();
        titleContainerParams.rightMargin = ACTIONBAR_ITEM_MARGIN + 8;

        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(colorBackground);
    }

    public void showTitleView(View titleView) {
        if (actionBar == null) return;
        if (titleView == null) return;

        LinearLayout header = (LinearLayout) actionBar.getCustomView();

        if (header == null) return;

        // Remove Title Container child views if there is any
        titleContainer.removeAllViews();

        // Remove Title View parent if there is any
        if (titleView.getParent() != null) {
            ((ViewGroup) titleView.getParent()).removeView(titleView);
        }

        titleContainer.addView(titleView);
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
            changed = menuID == null || !this.currentMenuID.equals(menuID);
        }

        if (changed) {
            this.currentMenuID = menuID;
            this.activity.invalidateOptionsMenu();
        }
    }

    public void addActions(Menu menu) {
        this.itemToUrl.clear();
        this.rightItemsCount = 0;
        this.leftItemsCount = 0;

        if (this.currentMenuID == null) return;

        AppConfig appConfig = AppConfig.getInstance(this.activity);
        if (appConfig.actions == null) return;

        JSONArray actions = appConfig.actions.get(currentMenuID);
        if (actions == null) return;

        for (int itemID = 0; itemID < actions.length(); itemID++) {
            JSONObject entry = actions.optJSONObject(itemID);
            if (entry != null) {
                String system = AppConfig.optString(entry, "system");

                if (!TextUtils.isEmpty(system)) {
                    if (system.equalsIgnoreCase("share")) {
                        TypedArray a = this.activity.getTheme().obtainStyledAttributes(new int []{R.attr.ic_action_share});
                        Drawable shareIcon = a.getDrawable(0);
                        a.recycle();

                        MenuItem menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, R.string.action_share)
                                .setIcon(shareIcon)
                                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                        itemToUrl.put(menuItem, ACTION_SHARE);
                    } else if (system.equalsIgnoreCase("refresh")) {
                        String icon = AppConfig.optString(entry, "icon");
                        String label = AppConfig.optString(entry, "label");
                        Drawable refreshIcon;

                        if (TextUtils.isEmpty(icon)) {
                            refreshIcon = new Icon(activity, "fa fa-refresh", action_button_size, colorForeground).getDrawable();
                        } else {
                            refreshIcon = new Icon(activity, icon, action_button_size, colorForeground).getDrawable();
                        }

                        String menuLabel = !TextUtils.isEmpty(label) ? label : "Refresh";

                        MenuItem menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, menuLabel)
                                .setIcon(refreshIcon)
                                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

                        itemToUrl.put(menuItem, ACTION_REFRESH);
                        rightItemsCount++;
                    } else if (system.equalsIgnoreCase("search")) {
                        initializeSearchButton(appConfig, entry);
                        showSearchButton();
                        this.searchActive = true;
                        leftItemsCount++;
                    }
                } else {
                    String label = AppConfig.optString(entry, "label");
                    String icon = AppConfig.optString(entry, "icon");
                    String url = AppConfig.optString(entry, "url");
                    
                    Drawable iconDrawable = null;
                    if (icon != null) {
                        iconDrawable = new Icon(activity, icon, action_button_size, colorForeground).getDrawable();
                    }
                    MenuItem menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, label)
                            .setIcon(iconDrawable)
                            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                    if (url != null) {
                        this.itemToUrl.put(menuItem, url);
                    }
                    rightItemsCount++;
                }
            }
        }

        if (!searchActive) {
            hideSearchButton();
        }

        setupActionBarTitleDisplay();
    }

    private void initializeSearchButton(AppConfig appConfig, JSONObject entry) {
        if (searchButtonInitialized) return;
        if (this.searchView == null) return;

        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar == null) return;

        String icon = AppConfig.optString(entry, "icon");
        String url = AppConfig.optString(entry, "url");

        DrawerLayout drawerLayout = activity.getDrawerLayout();
        ActionBarDrawerToggle drawerToggle = activity.getDrawerToggle();

        RelativeLayout titleContainer = header.findViewById(R.id.title_container);

        LinearLayout.LayoutParams searchviewParams = (LinearLayout.LayoutParams) searchView.getLayoutParams();

        // search item in action bar
        SearchView.SearchAutoComplete searchAutoComplete = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchAutoComplete != null) {
            searchAutoComplete.setTextColor(colorForeground);
            int hintColor = colorForeground;
            hintColor = Color.argb(192, Color.red(hintColor), Color.green(hintColor),
                    Color.blue(hintColor));
            searchAutoComplete.setHintTextColor(hintColor);
        }

        searchView.setOnSearchClickListener(view -> {
            activity.setMenuItemsVisible(false);
            titleContainer.setVisibility(View.GONE);

            ActionBar.LayoutParams params = (ActionBar.LayoutParams) header.getLayoutParams();
            params.width = ActionBar.LayoutParams.MATCH_PARENT;

            searchviewParams.width = ActionBar.LayoutParams.MATCH_PARENT;

            // Need to check this otherwise the app will crash
            if (!activity.isNotRoot() && appConfig.showNavigationMenu) {
                drawerToggle.setDrawerIndicatorEnabled(false);
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

                drawerToggle.setDrawerIndicatorEnabled(false);
                actionBar.setDisplayShowHomeEnabled(true);
            } else if (!activity.isNotRoot()) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            isOnSearchMode = true;
        });

        searchView.setOnCloseListener(() -> {
            titleContainer.setVisibility(View.VISIBLE);
            searchviewParams.width = ActionBar.LayoutParams.WRAP_CONTENT;

            activity.setMenuItemsVisible(true);

            if (!activity.isNotRoot() && appConfig.showNavigationMenu) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                actionBar.setDisplayShowHomeEnabled(false);
                drawerToggle.setDrawerIndicatorEnabled(true);
            } else if (!activity.isNotRoot()) {
                actionBar.setDisplayHomeAsUpEnabled(false);
            }

            return false;
        });

        // listener to process query
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (!searchView.isIconified()) {
                    searchView.setIconified(true);
                }
                try {
                    String q = URLEncoder.encode(query, "UTF-8");
                    activity.loadUrl(url + q);
                } catch (UnsupportedEncodingException e) {
                    return true;
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // do nothing
                return true;
            }
        });

        // listener to collapse action view when soft keyboard is closed
        searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    if (!searchView.isIconified()) {
                        searchView.setIconified(true);
                    }
                }
            }
        });

        // Search view button icon and color
        ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_button);
        if (searchIcon != null) {
            icon = !TextUtils.isEmpty(icon) ? icon : "fa fa-search";
            Drawable searchButtonNewIcon = new Icon(activity, icon, action_button_size, colorForeground).getDrawable();
            searchIcon.setImageDrawable(searchButtonNewIcon);
            searchIcon.setColorFilter(colorForeground);
        }

        //Search view close button foreground color
        ImageView closeButtonImage = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (closeButtonImage != null) {
            closeButtonImage.setColorFilter(colorForeground);
        }

        this.searchButtonInitialized = true;
    }

    // Count left and right actionbar buttons to calculate side margins
    public void setupActionBarTitleDisplay() {

        if (leftItemsCount == 0 && rightItemsCount == 0) {
            return;
        }

        // Add to temporary fields so actual items count would not be affected
        int tempLeftItemsCount = leftItemsCount;
        int tempRightItemsCount = rightItemsCount;

        if (appConfig.showNavigationMenu && isRoot)
            tempLeftItemsCount++;

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) titleContainer.getLayoutParams();

        // Reset the margins
        params.rightMargin = 0;
        params.leftMargin = 0;

        if (isRoot) {
            if (tempLeftItemsCount > tempRightItemsCount) {
                int margin = tempLeftItemsCount - tempRightItemsCount;
                params.rightMargin = ACTIONBAR_ITEM_MARGIN * margin;
            } else {
                int margin = tempRightItemsCount - tempLeftItemsCount;
                params.leftMargin = ACTIONBAR_ITEM_MARGIN * margin;
            }
        } else {
            if (titleContainer.getChildAt(0) != null
                    && titleContainer.getChildAt(0) instanceof TextView
                    && searchActive) {
                titleContainer.setGravity(Gravity.CENTER_VERTICAL);
            } else {
                tempLeftItemsCount++;
                if (tempLeftItemsCount > tempRightItemsCount) {
                    int margin = tempLeftItemsCount - tempRightItemsCount;
                    params.rightMargin = ACTIONBAR_ITEM_MARGIN * margin;
                } else {
                    int margin = tempRightItemsCount - tempLeftItemsCount;
                    params.leftMargin = ACTIONBAR_ITEM_MARGIN * margin;
                }
            }
        }

        // Need to add extra margin if no options menu are visible so Title would not move
        if (tempRightItemsCount == 0) {
            params.rightMargin = params.rightMargin + 8;
        }
    }

    private void showSearchButton() {
        if (this.searchView == null) return;
        this.searchView.setVisibility(View.VISIBLE);
    }

    private void hideSearchButton() {
        if (this.searchView == null) return;
        this.searchView.setVisibility(View.GONE);
    }

    public boolean isOnSearchMode() {
        return isOnSearchMode;
    }

    public void setOnSearchMode(boolean onSearchMode) {
        isOnSearchMode = onSearchMode;
    }

    public void closeSearchView() {
        if (!searchButtonInitialized) return;

        if (!searchView.isIconified()) {
            searchView.setIconified(true);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (activity.getCurrentFocus() instanceof SearchView.SearchAutoComplete) {
            activity.getCurrentFocus().clearFocus();
        }
        String url = this.itemToUrl.get(item);
        if (url != null) {
            if (url.equals(ACTION_SHARE)) {
                this.activity.sharePage(null);
                return true;
            } else if (url.equals(ACTION_REFRESH)) {
                this.activity.onRefresh();
                return true;
            }

            this.activity.loadUrl(url);
            return true;
        } else {
            return false;
        }
    }
}
