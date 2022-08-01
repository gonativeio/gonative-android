package io.gonative.android;

import android.graphics.Color;
import android.view.Gravity;
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import io.gonative.android.library.AppConfig;
import io.gonative.android.widget.GoNativeDrawerLayout;

public class ActionbarManager {
    private static final String TAG = ActionbarManager.class.getName();
    private static final int ACTIONBAR_ITEM_MARGIN = 110;
    
    private final MainActivity mainActivity;
    private final ActionManager actionManager;
    private final ActionBar actionBar;
    private final AppConfig appConfig;
    private final ConfigPreferences configPreferences;
    private final boolean isRoot;
    private final LinearLayout header;
    private final RelativeLayout titleContainer;
    private final SearchView searchView;
    private boolean isOnSearchMode = false;
    
    public ActionbarManager(MainActivity mainActivity, ActionManager actionManager, boolean isRoot) {
        this.mainActivity = mainActivity;
        this.actionManager = actionManager;
        actionBar = mainActivity.getSupportActionBar();
        appConfig = AppConfig.getInstance(mainActivity);
        configPreferences = new ConfigPreferences(mainActivity);
        this.isRoot = isRoot;
        header = (LinearLayout) mainActivity.getLayoutInflater().inflate(R.layout.actionbar_title, null);
        titleContainer = header.findViewById(R.id.title_container);
        searchView = header.findViewById(R.id.search_view);
    }
    
    public void setupActionBar(GoNativeDrawerLayout mDrawerLayout, ActionBarDrawerToggle mDrawerToggle) {
        if (actionBar == null) return;
        // why use a custom view and not setDisplayUseLogoEnabled and setLogo?
        // Because logo doesn't work!
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setCustomView(header);
        ActionBar.LayoutParams params = (ActionBar.LayoutParams) header.getLayoutParams();
        params.width = ActionBar.LayoutParams.MATCH_PARENT;
        showSearchViewMenu(appConfig.searchTemplateUrl != null, mDrawerLayout, mDrawerToggle);
        styleActionBar(mDrawerToggle);
    }
    
    private void showSearchViewMenu(boolean show, GoNativeDrawerLayout mDrawerLayout, ActionBarDrawerToggle mDrawerToggle) {
        if (actionBar == null) return;
        
        LinearLayout.LayoutParams searchviewParams = (LinearLayout.LayoutParams) searchView.getLayoutParams();
        
        if (show) {
            RelativeLayout titleContainer = header.findViewById(R.id.title_container);
            
            // search item in action bar
            SearchView.SearchAutoComplete searchAutoComplete = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (searchAutoComplete != null) {
                String currentAppTheme = configPreferences.getAppTheme();
                searchAutoComplete.setTextColor(appConfig.getActionbarForegroundColor(currentAppTheme));
                int hintColor = appConfig.getActionbarForegroundColor(currentAppTheme);
                hintColor = Color.argb(192, Color.red(hintColor), Color.green(hintColor),
                        Color.blue(hintColor));
                searchAutoComplete.setHintTextColor(hintColor);
            }
            
            searchView.setOnSearchClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    
                    mainActivity.setMenuItemsVisible(false);
                    titleContainer.setVisibility(View.GONE);
                    
                    ActionBar.LayoutParams params = (ActionBar.LayoutParams) header.getLayoutParams();
                    params.width = ActionBar.LayoutParams.MATCH_PARENT;
                    
                    searchviewParams.width = ActionBar.LayoutParams.MATCH_PARENT;
                    
                    // Need to check this otherwise the app will crash
                    if (isRoot && appConfig.showNavigationMenu) {
                        mDrawerToggle.setDrawerIndicatorEnabled(false);
                        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                        
                        mDrawerToggle.setDrawerIndicatorEnabled(false);
                        actionBar.setDisplayShowHomeEnabled(true);
                    } else if (isRoot) {
                        actionBar.setDisplayHomeAsUpEnabled(true);
                    }
                    isOnSearchMode = true;
                }
            });
            
            searchView.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    //closeSearchView();
                    titleContainer.setVisibility(View.VISIBLE);
                    searchviewParams.width = ActionBar.LayoutParams.WRAP_CONTENT;
                    
                    mainActivity.setMenuItemsVisible(true);
                    
                    if (isRoot && appConfig.showNavigationMenu) {
                        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                        actionBar.setDisplayShowHomeEnabled(false);
                        mDrawerToggle.setDrawerIndicatorEnabled(true);
                    } else if (isRoot) {
                        actionBar.setDisplayHomeAsUpEnabled(false);
                    }
                    
                    return false;
                }
            });
            
            // listener to process query
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    //searchItem.collapseActionView();
                    
                    if (!searchView.isIconified()) {
                        searchView.setIconified(true);
                    }
                    try {
                        String q = URLEncoder.encode(query, "UTF-8");
                        mainActivity.loadUrl(AppConfig.getInstance(mainActivity.getApplicationContext()).searchTemplateUrl + q);
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
        } else {
            
            // Hide Search View
            searchView.setVisibility(View.GONE);
        }
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
        setupActionBarTitleDisplay();
    }
    
    // Count left and right actionbar buttons to calculate side margins
    public void setupActionBarTitleDisplay() {
        int leftItemsCount = 0;
        int rightItemsCount = 0;
        
        if (appConfig.showNavigationMenu && isRoot)
            leftItemsCount++;
        if (appConfig.searchTemplateUrl != null)
            leftItemsCount++;
        if (mainActivity.getOptionsMenu() != null) {
            if (actionManager.getItemToUrl().size() > 0) rightItemsCount++;
        } else {
            if (appConfig.actions != null && appConfig.actions.size() > 0) rightItemsCount++;
        }
        if (appConfig.showRefreshButton)
            rightItemsCount++;
        
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) titleContainer.getLayoutParams();
        
        // Reset the margins
        params.rightMargin = 0;
        params.leftMargin = 0;
        
        if (isRoot) {
            if (leftItemsCount > rightItemsCount) {
                int margin = leftItemsCount - rightItemsCount;
                params.rightMargin = ACTIONBAR_ITEM_MARGIN * margin;
            } else {
                int margin = rightItemsCount - leftItemsCount;
                params.leftMargin = ACTIONBAR_ITEM_MARGIN * margin;
            }
        } else {
            if (titleContainer.getChildAt(0) != null
                    && titleContainer.getChildAt(0) instanceof TextView
                    && appConfig.searchTemplateUrl == null) {
                titleContainer.setGravity(Gravity.CENTER_VERTICAL);
            } else {
                leftItemsCount++;
                if (leftItemsCount > rightItemsCount) {
                    int margin = leftItemsCount - rightItemsCount;
                    params.rightMargin = ACTIONBAR_ITEM_MARGIN * margin;
                } else {
                    int margin = rightItemsCount - leftItemsCount;
                    params.leftMargin = ACTIONBAR_ITEM_MARGIN * margin;
                }
            }
        }
    }
    
    private void styleActionBar(ActionBarDrawerToggle mDrawerToggle) {
        String currentAppTheme = configPreferences.getAppTheme();
        
        MaterialToolbar toolbar = mainActivity.findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(appConfig.getActionbarBackgroundColor(currentAppTheme));
        
        // Toggle Button
        if (mDrawerToggle != null) {
            mDrawerToggle.getDrawerArrowDrawable().setColor(appConfig.getActionbarForegroundColor(currentAppTheme));
        }
        // Search view button foreground color
        ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_button);
        if (searchIcon != null) {
            searchIcon.setColorFilter(appConfig.getActionbarForegroundColor(currentAppTheme));
        }
        
        //Search view close button foreground color
        ImageView closeButtonImage = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (closeButtonImage != null) {
            closeButtonImage.setColorFilter(appConfig.getActionbarForegroundColor(currentAppTheme));
        }
    }
    
    public void closeSearchView() {
        if (actionBar == null) return;
        if (header == null) return;
        
        if (!searchView.isIconified()) {
            searchView.setIconified(true);
        }
    }
    
    public boolean isOnSearchMode() {
        return isOnSearchMode;
    }
    
    public void setOnSearchMode(boolean onSearchMode) {
        isOnSearchMode = onSearchMode;
    }
}