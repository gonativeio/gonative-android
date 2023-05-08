package io.gonative.android;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
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

import io.gonative.gonative_core.AppConfig;

/**
 * Created by weiyin on 11/25/14.
 * Copyright 2014 GoNative.io LLC
 */
public class ActionManager {
    private static final String TAG = ActionManager.class.getName();
    private static final String ACTION_SHARE = "share";
    private static final String ACTION_REFRESH = "refresh";
    private static final String ACTION_SEARCH = "search";
    private static final int ACTIONBAR_ITEM_MARGIN = 132;

    private final MainActivity activity;
    private final HashMap<MenuItem, String>itemToUrl;
    private final int action_button_size;
    private final ActionBar actionBar;
    private final ImageView actionBarImageTitle;
    private final int colorForeground;
    private final int colorBackground;
    private boolean isRoot;
    private String currentMenuID;
    private LinearLayout header;
    private LinearLayoutCompat menuContainer;
    private RelativeLayout titleContainer;
    private boolean isOnSearchMode = false;
    private SearchView searchView;

    private int leftItemsCount = 0;
    private int rightItemsCount = 0;

    private String currentUrl;

    ActionManager(MainActivity activity) {
        this.activity = activity;
        this.itemToUrl = new HashMap<>();
        this.action_button_size = this.activity.getResources().getInteger(R.integer.action_button_size);
        this.actionBar = activity.getSupportActionBar();

        this.actionBarImageTitle = new ImageView(activity);
        this.actionBarImageTitle.setImageResource(R.drawable.ic_actionbar);

        this.colorForeground = activity.getResources().getColor(R.color.titleTextColor);
        this.colorBackground = activity.getResources().getColor(R.color.colorPrimary);
    }

    public void setupActionBar(boolean isRoot) {
        if (actionBar == null) return;

        this.isRoot = isRoot;
        AppConfig appConfig = AppConfig.getInstance(activity);

        // Change hamburger button to back arrow if window is not root
        if (!isRoot) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            Drawable backArrow = ContextCompat.getDrawable(activity, R.drawable.abc_ic_ab_back_material);
            backArrow.setColorFilter(colorForeground, PorterDuff.Mode.SRC_ATOP);
            actionBar.setHomeAsUpIndicator(backArrow);
        }

        header = (LinearLayout) activity.getLayoutInflater().inflate(R.layout.actionbar_title, null);
        // why use a custom view and not setDisplayUseLogoEnabled and setLogo?
        // Because logo doesn't work!
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setCustomView(header);
        ActionBar.LayoutParams params = (ActionBar.LayoutParams) header.getLayoutParams();
        params.width = ActionBar.LayoutParams.MATCH_PARENT;
        titleContainer = header.findViewById(R.id.title_container);

        menuContainer = header.findViewById(R.id.left_menu_container);

        ViewGroup.MarginLayoutParams titleContainerParams = (ViewGroup.MarginLayoutParams) titleContainer.getLayoutParams();
        titleContainerParams.rightMargin = ACTIONBAR_ITEM_MARGIN + 8;

        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(colorBackground);

        // fix title offset when side menu hamburger icon is not yet available
        if (appConfig.showNavigationMenu) {
            menuContainer.getLayoutParams().width = 140;
        }
    }

    public void setupTitleDisplayForUrl(String url) {
        if (actionBar == null || url == null) return;
        AppConfig appConfig = AppConfig.getInstance(activity);
        this.currentUrl = url;

        boolean urlHasNavTitle = false;
        boolean urlHasActionMenu = false;

        // Check for Nav title
        HashMap<String, Object> urlNavTitle = appConfig.getNavigationTitleForUrl(url);
        if (urlNavTitle != null) {
            urlHasNavTitle = true;
        }

        // Check for Action Menus
        ArrayList<Pattern> regexes = appConfig.actionRegexes;
        ArrayList<String> ids = appConfig.actionIDs;
        if (regexes != null && ids != null) {
            for (int i = 0; i < regexes.size(); i++) {
                Pattern regex = regexes.get(i);
                if (regex.matcher(url).matches()) {
                    urlHasActionMenu = true;
                    break;
                }
            }
        }

        if (!appConfig.showActionBar && !appConfig.showNavigationMenu && !urlHasNavTitle && !urlHasActionMenu) {
            actionBar.hide();
        } else {
            if (urlHasNavTitle) {
                boolean showImage = true;
                if (urlNavTitle.containsKey("showImage")) showImage = (boolean) urlNavTitle.get("showImage");

                if (showImage) {
                    // Show image title
                    actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
                    showTitleView(actionBarImageTitle);
                } else {
                    // Show text title
                    String title = activity.getTitle().toString();;
                    if (urlNavTitle.containsKey("title")) title = (String) urlNavTitle.get("title");
                    showTextActionBarTitle(title);
                }
            } else {
                showLogoInActionBar(appConfig.shouldShowNavigationTitleImageForUrl(currentUrl));
            }
            setupActionBarDisplay();
            actionBar.show();
        }
    }

    private void showLogoInActionBar(boolean show) {
        if (actionBar == null) return;
        if (show) {
            showTitleView(actionBarImageTitle);
        } else {
            // Show Text
            showTextActionBarTitle(activity.getTitle());
        }
    }

    public void showTextActionBarTitle(CharSequence title) {
        TextView textView = new TextView(activity);
        textView.setText(TextUtils.isEmpty(title) ? activity.getTitle() : title);
        textView.setTextSize(18);
        textView.setTypeface(null, Typeface.BOLD);
        textView.setMaxLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(colorForeground);
        showTitleView(textView);
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

    // Remove title offset once sidebar hamburger menu setup is complete
    public void cleanSidebarMenuTitleOffset() {
        if (menuContainer == null) return;
        menuContainer.getLayoutParams().width = 0;
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

        AppConfig appConfig = AppConfig.getInstance(this.activity);
        if (appConfig.actions == null) return;
        menuContainer.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;

        JSONArray actions = appConfig.actions.get(currentMenuID);
        if (actions == null || actions.length() == 0) {
            replaceLeftIcon(null);
        } else {
            if (actions.length() <= 2) {
                for (int itemID = 0; itemID < actions.length(); itemID++) {
                    JSONObject entry = actions.optJSONObject(itemID);
                    addRightButton(appConfig, menu, itemID, entry);
                }
            } else {
                for (int itemID = 0; itemID < actions.length(); itemID++) {
                    JSONObject entry = actions.optJSONObject(itemID);
                    if (itemID == 0) {
                        addLeftButton(appConfig, entry);
                    } else {
                        addRightButton(appConfig, menu, itemID, entry);
                    }
                }
            }
        }
        setupActionBarDisplay();
    }

    private void addLeftButton(AppConfig appConfig, JSONObject entry) {
        if (entry == null) return;

        String system = AppConfig.optString(entry, "system");
        String icon = AppConfig.optString(entry, "icon");
        String url = AppConfig.optString(entry, "url");

        if (!TextUtils.isEmpty(system)) {
            if (system.equalsIgnoreCase("refresh")) {
                if (TextUtils.isEmpty(icon)) {
                    icon = "fa-rotate-right";
                }
                Button refresh = createButtonMenu(icon);
                refresh.setOnClickListener(v -> this.activity.onRefresh());
                replaceLeftIcon(refresh);
            } else if (system.equalsIgnoreCase("share")) {
                if (TextUtils.isEmpty(icon)) {
                    icon = "fa-share";
                }
                Button share = createButtonMenu(icon);
                share.setOnClickListener(v -> this.activity.sharePage(null, null));
                replaceLeftIcon(share);
            } else if (system.equalsIgnoreCase("search")) {
                if (TextUtils.isEmpty(icon)) {
                    icon = "fa fa-search";
                }
                this.searchView = createSearchView(appConfig, icon, url, null, true);
                replaceLeftIcon(this.searchView);
            } else {
                addLeftCustomButton(icon, url);
            }
        } else {
            addLeftCustomButton(icon, url);
        }

        if (!appConfig.showNavigationMenu) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) menuContainer.getLayoutParams();
            params.leftMargin = 35;
        }

        leftItemsCount++;
    }

    private void addLeftCustomButton(String icon, String url) {
        Button userButton = createButtonMenu(icon);
        userButton.setOnClickListener(v -> this.activity.loadUrl(url));
        replaceLeftIcon(userButton);
    }

    private void addRightButton(AppConfig appConfig, Menu menu, int itemID, JSONObject entry) {
        if (entry == null) return;

        String system = AppConfig.optString(entry, "system");
        String label = AppConfig.optString(entry, "label");
        String icon = AppConfig.optString(entry, "icon");
        String url = AppConfig.optString(entry, "url");

        if (!TextUtils.isEmpty(system)) {
            if (system.equalsIgnoreCase("refresh")) {

                if (TextUtils.isEmpty(icon)) {
                    icon = "fa-rotate-right";
                }

                Drawable refreshIcon = new Icon(activity, icon, action_button_size, colorForeground).getDrawable();

                String menuLabel = !TextUtils.isEmpty(label) ? label : "Refresh";

                MenuItem menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, menuLabel)
                        .setIcon(refreshIcon)
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

                itemToUrl.put(menuItem, ACTION_REFRESH);
            } else if (system.equalsIgnoreCase("share")) {

                if (TextUtils.isEmpty(icon)) {
                    icon = "fa-share";
                }

                Drawable refreshIcon = new Icon(activity, icon, action_button_size, colorForeground).getDrawable();;

                String menuLabel = !TextUtils.isEmpty(label) ? label : "Share";

                MenuItem menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, menuLabel)
                        .setIcon(refreshIcon)
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

                itemToUrl.put(menuItem, ACTION_SHARE);

            } else if (system.equalsIgnoreCase("search")) {

                if (TextUtils.isEmpty(icon)) {
                    icon = "fa fa-search";
                }

                String menuLabel = !TextUtils.isEmpty(label) ? label : "Search";

                MenuItem menuItem = menu.add(Menu.NONE, itemID, Menu.NONE, menuLabel)
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

                this.searchView = createSearchView(appConfig, icon, url, menuItem, false);
                menuItem.setActionView(searchView);

                itemToUrl.put(menuItem, ACTION_SEARCH);
            } else {
                addRightCustomButton(menu, itemID, label, icon, url);
            }
        } else {
            addRightCustomButton(menu, itemID, label, icon, url);
        }

        rightItemsCount++;
    }

    private void addRightCustomButton(Menu menu, int itemID, String label, String icon, String url) {
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
    }

    private void replaceLeftIcon(View view) {
        if (menuContainer == null) return;
        menuContainer.removeAllViews();
        if (view != null) {
            menuContainer.addView(view);
            menuContainer.setVisibility(View.VISIBLE);
        } else {
            menuContainer.setVisibility(View.GONE);
        }
    }

    private Button createButtonMenu(String iconString) {
        Drawable icon = new Icon(activity, iconString, action_button_size, colorForeground).getDrawable();
        icon.setBounds(0, 0, 50, 50);
        LinearLayout tempView = (LinearLayout) LayoutInflater.from(activity).inflate(R.layout.button_menu, null);
        Button button = tempView.findViewById(R.id.menu_button);
        tempView.removeView(button);
        button.setCompoundDrawables(icon, null, null, null);
        return button;
    }

    private SearchView createSearchView(AppConfig appConfig, String icon, String url, MenuItem menuItem, boolean forLeftSide) {
        SearchView searchView = new SearchView(activity);

        // Set layout Params to WRAP_CONTENT
        ViewGroup.LayoutParams searchViewParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchView.setLayoutParams(searchViewParams);

        // Left Drawer Instance
        DrawerLayout drawerLayout = activity.getDrawerLayout();
        ActionBarDrawerToggle drawerToggle = activity.getDrawerToggle();

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
            searchViewParams.width = ActionBar.LayoutParams.MATCH_PARENT;

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
            if (forLeftSide) {
                titleContainer.setVisibility(View.VISIBLE);
            } else {
                header.setVisibility(View.VISIBLE);
                activity.invalidateOptionsMenu();
            }
            searchViewParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;

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

            // Handling a bug when SearchView is expanded and setting other menu
            // visibility to false, SearchView would trigger unnecessary onClose event
            // Solution is to hide other menus first before expanding
            searchIcon.setOnClickListener(v -> {
                if (forLeftSide) {
                    activity.setMenuItemsVisible(false);
                    titleContainer.setVisibility(View.GONE);
                } else {
                    header.setVisibility(View.GONE);
                    activity.setMenuItemsVisible(false, menuItem);
                }
                // Expand SearchView, simulates onSearchViewClicked event
                searchView.setIconified(false);
            });
        }

        //Search view close button foreground color
        ImageView closeButtonImage = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (closeButtonImage != null) {
            closeButtonImage.setColorFilter(colorForeground);
        }

        return searchView;
    }

    // Count left and right actionbar buttons to calculate side margins
    public void setupActionBarDisplay() {

        if (actionBar == null) return;

        AppConfig appConfig = AppConfig.getInstance(activity);

        // Add to temporary fields so actual items count would not be affected
        int tempLeftItemsCount = leftItemsCount;

        // Limit right menu count to three for margin
        int tempRightItemsCount = Math.min(rightItemsCount, 3);

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) titleContainer.getLayoutParams();

        // Reset the margins
        params.rightMargin = 0;
        params.leftMargin = 0;

        if (isRoot) {
            if (appConfig.showNavigationMenu) {
                tempLeftItemsCount++;
            }

            if (tempLeftItemsCount > tempRightItemsCount) {
                int margin = tempLeftItemsCount - tempRightItemsCount;
                params.rightMargin = ACTIONBAR_ITEM_MARGIN * margin;
            } else {
                int margin = tempRightItemsCount - tempLeftItemsCount;
                params.leftMargin = ACTIONBAR_ITEM_MARGIN * margin;
            }
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

    public boolean isOnSearchMode() {
        return isOnSearchMode;
    }

    public void setOnSearchMode(boolean onSearchMode) {
        isOnSearchMode = onSearchMode;
    }

    public void closeSearchView() {
        if (searchView == null) return;

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
            switch (url) {
                case ACTION_SHARE:
                    this.activity.sharePage(null, null);
                    return true;
                case ACTION_REFRESH:
                    this.activity.onRefresh();
                    return true;
                case ACTION_SEARCH:
                    // Ignore
                    return true;
            }

            this.activity.loadUrl(url);
            return true;
        } else {
            return false;
        }
    }
}
