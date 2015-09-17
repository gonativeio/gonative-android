package io.gonative.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * Created by Weiyin He on 3/13/14.
 * Copyright 2014 GoNative.io LLC
 */
public class AppConfig {
    public static final String PROCESSED_MENU_MESSAGE = "io.gonative.android.AppConfig.processedMenu";
    public static final String PROCESSED_TAB_NAVIGATION_MESSAGE = "io.gonative.android.AppConfig.processedTabNavigation";
    public static final String PROCESSED_WEBVIEW_POOLS_MESSAGE = "io.gonative.android.AppConfig.processedWebViewPools";

    private static final String TAG = AppConfig.class.getName();


    // singleton
    private static AppConfig mInstance = null;

    // instance variables
    private Context context;
    private JSONObject json;
    private String lastConfigUpdate;
    private AppConfigJsBridge appConfigJsBridge;

    // general
    public String initialUrl;
    public String initialHost;
    public String appName;
    public String publicKey;
    public String deviceRegKey;
    public String userAgent;
    public int forceSessionCookieExpiry;
    public ArrayList<Pattern> userAgentRegexes;
    public ArrayList<String> userAgentStrings;
    public boolean disableConfigUpdater;
    public boolean disableEventRecorder;

    // navigation
    public boolean pullToRefresh;
    public HashMap<String,JSONArray> menus;
    public boolean showNavigationMenu;
    public String userIdRegex;
    public String loginDetectionUrl;
    public ArrayList<Pattern> loginDetectRegexes;
    public ArrayList<JSONObject> loginDetectLocations;
    public ArrayList<Pattern> navStructureLevelsRegex;
    public ArrayList<Integer> navStructureLevels;
    public ArrayList<HashMap<String,Object>> navTitles;
    public String profilePickerJS;
    public ArrayList<Pattern> regexInternalExternal;
    public ArrayList<Boolean> regexIsInternal;
    public boolean useWebpageTitle;

    public HashMap<String,JSONArray> tabMenus;
    public ArrayList<Pattern> tabMenuRegexes;
    public ArrayList<String> tabMenuIDs;

    public HashMap<String,JSONArray> actions;
    public ArrayList<Pattern> actionRegexes;
    public ArrayList<String> actionIDs;

    public ArrayList<Pattern> ignorePageFinishedRegexes;

    // styling
    public Integer sidebarBackgroundColor;
    public Integer sidebarForegroundColor;
    public Integer tintColor;
    public Integer tabBarTextColor;
    public Integer tabBarBackgroundColor;
    public Integer tabBarIndicatorColor;
    public String customCSS;
    public double forceViewportWidth;
    public boolean zoomableForceViewport;
    public String androidTheme;
    public Integer actionbarForegroundColor;
    public boolean showActionBar = true;
    public double interactiveDelay;
    public String stringViewport;
    public boolean hideTitleInActionBar;
    public boolean showLogoInActionBar;
    public boolean showRefreshButton;
    public ArrayList<Pattern> navigationTitleImageRegexes;
    public float hideWebviewAlpha;
    public Integer pullToRefreshColor;
    public boolean showSplash;
    public boolean disableAnimations;

    // forms
    public String searchTemplateUrl;
    public String loginUrl;
    public JSONObject loginConfig;
    public boolean loginIsFirstPage;
    public String signupUrl;
    public JSONObject signupConfig;

    // permissions
    public boolean usesGeolocation = false;

    // services
    public boolean pushNotifications = false;
    public boolean analytics = false;
    public int idsite_test = Integer.MIN_VALUE;
    public int idsite_prod = Integer.MIN_VALUE;

    // Parse integration
    public boolean parseEnabled = false;
    public String parseApplicationId;
    public String parseClientKey;
    public boolean parsePushEnabled = false;
    public boolean parseAnalyticsEnabled = false;

    // identity service
    public List<Pattern> checkIdentityUrlRegexes;
    public String identityEndpointUrl;

    // performance
    public JSONArray webviewPools;

    // misc
    public boolean allowZoom = true;
    public boolean interceptHtml = false;
    public String updateConfigJS;


    public File fileForOTAconfig() {
        return new File(context.getFilesDir(), "appConfig.json");
    }

    private AppConfig(Context context){
        this.context = context;
        this.appConfigJsBridge = new AppConfigJsBridge();

        InputStream is = null;
        InputStream jsonIs = null;
        try {
            // read json

            if (fileForOTAconfig().exists()){
                InputStream otaIS = null;
                try {
                    otaIS = new BufferedInputStream(new FileInputStream(fileForOTAconfig()));
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    IOUtils.copy(otaIS, baos);
                    baos.close();
                    this.json = new JSONObject(baos.toString("UTF-8"));
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    IOUtils.close(otaIS);
                }
            }

            if (this.json == null) {
                jsonIs = context.getAssets().open("appConfig.json");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IOUtils.copy(jsonIs, baos);
                IOUtils.close(baos);
                this.json = new JSONObject(baos.toString("UTF-8"));
            }

            // initialize some stuff
            this.regexInternalExternal = new ArrayList<Pattern>();
            this.regexIsInternal = new ArrayList<Boolean>();


            ////////////////////////////////////////////////////////////
            // General
            ////////////////////////////////////////////////////////////
            JSONObject general = this.json.optJSONObject("general");
            if (general != null) {
                this.initialUrl = optString(general, "initialUrl");

                // preprocess initialHost
                initialHost = Uri.parse(this.initialUrl).getHost();
                if (initialHost.startsWith("www.")) {
                    initialHost = initialHost.substring("www.".length());
                }

                this.appName = optString(general, "appName");

                // user agent for everything (webview, httprequest, httpurlconnection)
                String forceUserAgent = optString(general, "forceUserAgent");
                if (forceUserAgent != null && !forceUserAgent.isEmpty()) {
                    this.userAgent = forceUserAgent;
                } else {
                    String userAgentAdd = optString(general, "userAgentAdd");
                    if (userAgentAdd == null) userAgentAdd = "gonative";
                    WebView wv = new WebView(context);
                    StringBuilder sb = new StringBuilder(wv.getSettings().getUserAgentString());
                    sb.append(" ");
                    sb.append(userAgentAdd);
                    this.userAgent = sb.toString();
                }

                this.publicKey = optString(general, "publicKey");
                this.deviceRegKey = optString(general, "deviceRegKey");
                this.forceSessionCookieExpiry = general.optInt("forceSessionCookieExpiry", 0);
                // forceSessionCookieExpiry requires direct parsing of http headers, which webview
                // does not allow.
                if (this.forceSessionCookieExpiry > 0) this.interceptHtml = true;

                processUserAgentRegexes(general.optJSONArray("userAgentRegexes"));

                this.disableConfigUpdater = general.optBoolean("disableConfigUpdater", false);
                this.disableEventRecorder = general.optBoolean("disableEventRecorder", false);
            }


            ////////////////////////////////////////////////////////////
            // Forms
            ////////////////////////////////////////////////////////////
            JSONObject forms = this.json.optJSONObject("forms");
            if (forms != null) {
                // search
                JSONObject search = forms.optJSONObject("search");
                if (search != null && search.optBoolean("active", false)) {
                    this.searchTemplateUrl = optString(search, "searchTemplateURL");
                }

                // login
                JSONObject loginConfig = forms.optJSONObject("loginConfig");
                if (loginConfig != null && loginConfig.optBoolean("active", false)) {
                    this.loginConfig = loginConfig;
                    this.loginUrl = optString(loginConfig, "interceptUrl");
                    this.loginIsFirstPage = loginConfig.optBoolean("loginIsFirstPage", false);
                }

                // signup
                JSONObject signupConfig = forms.optJSONObject("signupConfig");
                if (signupConfig != null && signupConfig.optBoolean("active", false)) {
                    this.signupConfig = signupConfig;
                    this.signupUrl = optString(signupConfig, "interceptUrl");
                }
            }



            ////////////////////////////////////////////////////////////
            // Navigation
            ////////////////////////////////////////////////////////////
            JSONObject navigation = this.json.optJSONObject("navigation");
            if (navigation != null) {
                this.pullToRefresh = navigation.optBoolean("androidPullToRefresh");

                // sidebar
                JSONObject sidebarNav = navigation.optJSONObject("sidebarNavigation");
                processSidebarNavigation(sidebarNav);

                // navigation levels
                if (navigation.optJSONObject("androidNavigationLevels") != null) {
                    processNavigationLevels(navigation.optJSONObject("androidNavigationLevels"));
                } else {
                    processNavigationLevels(navigation.optJSONObject("navigationLevels"));
                }

                // navigation titles
                JSONObject navigationTitles = navigation.optJSONObject("navigationTitles");
                processNavigationTitles(navigationTitles);

                this.profilePickerJS = optString(navigation, "profilePickerJS");

                // regex for internal vs external links
                // note that we ignore "active" here
                JSONObject regexInternalExternal = navigation.optJSONObject("regexInternalExternal");
                if (regexInternalExternal != null) {
                    JSONArray rules = regexInternalExternal.optJSONArray("rules");
                    if (rules != null) {
                        for (int i = 0; i < rules.length(); i++) {
                            JSONObject entry = rules.optJSONObject(i);
                            if (entry != null && entry.has("regex") && entry.has("internal")) {
                                String regex = optString(entry, "regex");
                                boolean internal = entry.optBoolean("internal", true);

                                if (regex != null) {
                                    this.regexInternalExternal.add(Pattern.compile(regex));
                                    this.regexIsInternal.add(internal);
                                }
                            }
                        }
                    }
                }

                // tab menus
                JSONObject tabNavigation = navigation.optJSONObject("tabNavigation");
                processTabNavigation(tabNavigation);

                // actions
                JSONObject actionConfig = navigation.optJSONObject("actionConfig");
                processActions(actionConfig);

                // refresh button
                this.showRefreshButton = navigation.optBoolean("androidShowRefreshButton", true);

                // ignore the "onPageFinished" event for certain URLs
                JSONArray ignorePageFinished = navigation.optJSONArray("ignorePageFinishedRegexes");
                this.ignorePageFinishedRegexes = new ArrayList<Pattern>();
                if (ignorePageFinished != null) {
                    for (int i = 0; i < ignorePageFinished.length(); i++) {
                        if (!ignorePageFinished.isNull(i)) {
                            String entry = ignorePageFinished.optString(i);
                            if (entry != null) {
                                try {
                                    Pattern pattern = Pattern.compile(entry);
                                    this.ignorePageFinishedRegexes.add(pattern);
                                } catch (PatternSyntaxException e) {
                                    Log.e(TAG, "Error parsing regex " + entry, e);
                                }
                            }
                        }
                    }
                }
            }

            ////////////////////////////////////////////////////////////
            // Styling
            ////////////////////////////////////////////////////////////
            JSONObject styling = this.json.optJSONObject("styling");

            this.customCSS = optString(styling, "customCSS");
            // css and viewport require manipulation of html before it is sent to the webview
            if (this.customCSS != null) this.interceptHtml = true;

            this.forceViewportWidth = styling.optDouble("forceViewportWidth", Double.NaN);
            if (!Double.isNaN(this.forceViewportWidth)) this.interceptHtml = true;

            this.zoomableForceViewport = styling.optBoolean("zoomableForceViewport", false);

            this.showActionBar = styling.optBoolean("showActionBar", true);

            this.androidTheme = optString(styling, "androidTheme");

            // preprocess colors

            String sideBackColor = AppConfig.optString(styling, "androidSidebarBackgroundColor");
            this.sidebarBackgroundColor = LeanUtils.parseColor(sideBackColor);
            String sideForeColor = AppConfig.optString(styling, "androidSidebarForegroundColor");
            this.sidebarForegroundColor = LeanUtils.parseColor(sideForeColor);
            String tintColor = AppConfig.optString(styling, "androidTintColor");
            this.tintColor = LeanUtils.parseColor(tintColor);

            this.tabBarBackgroundColor = LeanUtils.parseColor(AppConfig.optString(styling, "androidTabBarBackgroundColor"));
            this.tabBarTextColor = LeanUtils.parseColor(AppConfig.optString(styling, "androidTabBarTextColor"));
            this.tabBarIndicatorColor = LeanUtils.parseColor(AppConfig.optString(styling, "androidTabBarIndicatorColor"));

            this.interactiveDelay = styling.optDouble("transitionInteractiveDelayMax", Double.NaN);
            this.hideTitleInActionBar = styling.optBoolean("androidHideTitleInActionBar", false);
            this.showLogoInActionBar = styling.optBoolean("androidShowLogoInActionBar", this.hideTitleInActionBar);

            String actionBarForegroundColor = AppConfig.optString(styling, "androidActionBarForegroundColor");
            this.actionbarForegroundColor = LeanUtils.parseColor(actionBarForegroundColor);
            if (this.actionbarForegroundColor == null) {
                if (this.androidTheme == null) this.actionbarForegroundColor = Color.WHITE;
                else if (this.androidTheme.equalsIgnoreCase("light")) this.actionbarForegroundColor = Color.BLACK;
                else this.actionbarForegroundColor = Color.WHITE;
            }

            processNavigationTitleImage(styling.opt("navigationTitleImage"));

            this.hideWebviewAlpha = (float)styling.optDouble("hideWebviewAlpha", 0.0);

            this.pullToRefreshColor = LeanUtils.parseColor(AppConfig.optString(styling, "androidPullToRefreshColor"));

            this.showSplash = styling.optBoolean("androidShowSplash", false);

            this.disableAnimations = styling.optBoolean("disableAnimations", false);

            ////////////////////////////////////////////////////////////
            // Permissions
            ////////////////////////////////////////////////////////////
            JSONObject permissions = this.json.optJSONObject("permissions");
            if (permissions != null) {
                // geolocation. Also need to allow permissions in AndroidManifest.xml
                this.usesGeolocation = permissions.optBoolean("usesGeolocation", false);
            }

            ////////////////////////////////////////////////////////////
            // Services
            ////////////////////////////////////////////////////////////
            JSONObject services = this.json.optJSONObject("services");
            if (services != null) {
                JSONObject push = services.optJSONObject("push");
                this.pushNotifications = push != null && push.optBoolean("active", false);

                JSONObject analytics = services.optJSONObject("analytics");
                if (analytics != null && analytics.optBoolean("active", false)) {
                    this.idsite_test = analytics.optInt("idsite_test", Integer.MIN_VALUE);
                    this.idsite_prod = analytics.optInt("idsite_prod", Integer.MIN_VALUE);
                    if (this.idsite_test == Integer.MIN_VALUE ||
                            this.idsite_prod == Integer.MIN_VALUE) {
                        Log.w(TAG, "Analytics requires idsite_test and idsite_prod");
                        this.analytics = false;
                    } else {
                        this.analytics = true;
                    }
                }

                JSONObject parse = services.optJSONObject("parse");
                if (parse != null && parse.optBoolean("active")) {
                    String applicationId = optString(parse, "applicationId");
                    String clientKey = optString(parse, "clientKey");
                    if (applicationId != null && !applicationId.isEmpty() &&
                            clientKey != null && !clientKey.isEmpty()) {

                        this.parseEnabled = true;
                        this.parseApplicationId = applicationId;
                        this.parseClientKey = clientKey;
                        this.parsePushEnabled = parse.optBoolean("pushEnabled");
                        this.parseAnalyticsEnabled = parse.optBoolean("analyticsEnabled");
                    } else {
                        Log.e(TAG, "Config is missing Parse applicationId or clientKey");
                        this.parseEnabled = false;
                    }
                } else {
                    this.parseEnabled = false;
                }

                JSONObject identityService = services.optJSONObject("identity");
                if (identityService != null && identityService.optBoolean("active")) {
                    // string or array of strings
                    LinkedList<Pattern> regexes = new LinkedList<Pattern>();
                    this.checkIdentityUrlRegexes = regexes;

                    Object identityUrls = identityService.opt("checkIdentityUrl");
                    if (identityUrls != null && identityUrls instanceof String) {
                        Pattern regex = Pattern.compile((String) identityUrls);
                        regexes.add(regex);
                    } else if (identityUrls != null && identityUrls instanceof JSONArray) {
                        JSONArray identityUrlArray = (JSONArray)identityUrls;
                        for (int i = 0; i < identityUrlArray.length(); i++) {
                            String regexString = identityUrlArray.optString(i);
                            if (regexString != null && !regexString.isEmpty()) {
                                Pattern regex = Pattern.compile(regexString);
                                regexes.add(regex);
                            }
                        }
                    }

                    // identityEndpointUrl
                    this.identityEndpointUrl = optString(identityService, "identityEndpointUrl");
                }
            }

            ////////////////////////////////////////////////////////////
            // Performance
            ////////////////////////////////////////////////////////////
            JSONObject performance = this.json.optJSONObject("performance");
            if (performance != null) {
                processWebViewPools(performance.optJSONArray("webviewPools"));
            }

            ////////////////////////////////////////////////////////////
            // Miscellaneous stuff
            ////////////////////////////////////////////////////////////
            this.allowZoom = this.json.optBoolean("allowZoom", true);
            this.updateConfigJS = optString(this.json, "updateConfigJS");

        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        finally {
            IOUtils.close(is);
            IOUtils.close(jsonIs);
        }
    }

    public void processDynamicUpdate(String json) {
        if (json == null || json.isEmpty() || json.equals("null") || json.equals(this.lastConfigUpdate)) {
            return;
        }

        this.lastConfigUpdate = json;

        JSONObject parsedJson = null;
        try {
            parsedJson = new JSONObject(json);
        } catch (JSONException e) {
            Log.e(TAG, "Error processing config update:"+e.getMessage(), e);
            return;
        }

        if (parsedJson != null) {
            processTabNavigation(parsedJson.optJSONObject("tabNavigation"));
            processSidebarNavigation(parsedJson.optJSONObject("sidebarNavigation"));
            if (parsedJson.optJSONObject("androidNavigationLevels") != null) {
                processNavigationLevels(parsedJson.optJSONObject("androidNavigationLevels"));
            } else {
                processNavigationLevels(parsedJson.optJSONObject("navigationLevels"));
            }
            processNavigationTitles(parsedJson.optJSONObject("navigationTitles"));
            processWebViewPools(parsedJson.optJSONArray("webviewPools"));
            processNavigationTitleImage(parsedJson.opt("navigationTitleImage"));
        }
    }

    private void processWebViewPools(JSONArray webviewPools) {
        if (webviewPools == null) return;

        this.webviewPools = webviewPools;
        LocalBroadcastManager.getInstance(this.context).sendBroadcast(new Intent(PROCESSED_WEBVIEW_POOLS_MESSAGE));
    }

    private void processNavigationTitles(JSONObject navigationTitles) {
        if (navigationTitles == null) return;

        this.navTitles = new ArrayList<HashMap<String, Object>>();

        if (!navigationTitles.optBoolean("active")) return;

        JSONArray titles = navigationTitles.optJSONArray("titles");
        if (titles != null) {
            for (int i = 0; i < titles.length(); i++) {
                JSONObject entry = titles.optJSONObject(i);

                if (entry == null) continue;
                String regex = optString(entry, "regex");
                if (regex == null) continue;

                try {
                    HashMap<String, Object> toAdd = new HashMap<String, Object>();
                    Pattern pattern = Pattern.compile(regex);
                    toAdd.put("regex", pattern);

                    String title = optString(entry, "title");
                    String urlRegex = optString(entry, "urlRegex");
                    int urlChompWords = entry.optInt("urlChompWords", -1);

                    if (title != null) {
                        toAdd.put("title", title);
                    }
                    if (urlRegex != null) {
                        Pattern urlRegexPattern = Pattern.compile(urlRegex);
                        toAdd.put("urlRegex", urlRegexPattern);
                    }
                    if (urlChompWords > -1) {
                        toAdd.put("urlChompWords", urlChompWords);
                    }

                    this.navTitles.add(toAdd);
                } catch (PatternSyntaxException e) {
                    Log.e(TAG, e.getMessage(), e);
                }

            }
        }

    }

    private void processNavigationLevels(JSONObject navigationLevels) {
        if (navigationLevels == null) return;

        this.navStructureLevelsRegex = new ArrayList<Pattern>();
        this.navStructureLevels = new ArrayList<Integer>();

        if (!navigationLevels.optBoolean("active")) return;

        JSONArray urlLevels = navigationLevels.optJSONArray("levels");
        if (urlLevels != null) {
            for (int i = 0; i < urlLevels.length(); i++) {
                JSONObject entry = urlLevels.optJSONObject(i);
                if (entry != null) {
                    String regex = optString(entry, "regex");
                    int level = entry.optInt("level", -1);

                    if (regex != null && level != -1) {
                        this.navStructureLevelsRegex.add(Pattern.compile(regex));
                        this.navStructureLevels.add(level);
                    }

                }
            }
        }
    }

    private void processSidebarNavigation(JSONObject sidebarNav){
        if (sidebarNav == null) return;

        this.menus = new HashMap<String, JSONArray>();
        this.loginDetectRegexes = new ArrayList<Pattern>();
        this.loginDetectLocations = new ArrayList<JSONObject>();

        int numActiveMenus = 0;

        // menus
        JSONArray menus = sidebarNav.optJSONArray("menus");
        if (menus != null) {
            for(int i = 0; i < menus.length(); i++){
                JSONObject menu = menus.optJSONObject(i);
                if (menu != null) {
                    if (!menu.optBoolean("active", false)){
                        continue;
                    }

                    numActiveMenus++;

                    String name = optString(menu, "name");
                    JSONArray items = menu.optJSONArray("items");
                    if (name != null && items != null) {
                        this.menus.put(name, items);

                        // show menu if the menu named "default" is active
                        if (name.equals("default")) {
                            this.showNavigationMenu = true;
                        }
                    }
                }
            }
        }

        LocalBroadcastManager.getInstance(this.context).sendBroadcast(new Intent(PROCESSED_MENU_MESSAGE));

        this.userIdRegex = optString(sidebarNav, "userIdRegex");

        // menu selection config
        JSONObject menuSelectionConfig = sidebarNav.optJSONObject("menuSelectionConfig");
        if ((numActiveMenus > 1 || this.loginIsFirstPage) && menuSelectionConfig != null) {
            this.loginDetectionUrl = optString(menuSelectionConfig, "testURL");

            JSONArray redirectLocations = menuSelectionConfig.optJSONArray("redirectLocations");
            if (redirectLocations != null) {
                for(int i = 0; i < redirectLocations.length(); i++) {
                    JSONObject entry = redirectLocations.optJSONObject(i);
                    if (entry != null) {
                        String regex = optString(entry, "regex");
                        if (regex != null) {
                            this.loginDetectRegexes.add(Pattern.compile(regex));
                            this.loginDetectLocations.add(entry);
                        }
                    }
                }
            }
        }

    }

    private void processTabNavigation(JSONObject tabNavigation) {
        if (tabNavigation == null) return;

        this.tabMenus = new HashMap<String, JSONArray>();
        this.tabMenuIDs = new ArrayList<String>();
        this.tabMenuRegexes = new ArrayList<Pattern>();

        if (!tabNavigation.optBoolean("active")) return;

        JSONArray tabMenus = tabNavigation.optJSONArray("tabMenus");
        if (tabMenus != null) {
            for (int i = 0; i < tabMenus.length(); i++) {
                JSONObject entry = tabMenus.optJSONObject(i);
                if (entry != null) {
                    String id = optString(entry, "id");
                    JSONArray items = entry.optJSONArray("items");
                    if (id != null && items != null) {
                        this.tabMenus.put(id, items);
                    }
                }
            }
        }

        JSONArray tabSelection = tabNavigation.optJSONArray("tabSelectionConfig");
        if (tabSelection != null) {
            for (int i = 0; i < tabSelection.length(); i++) {
                JSONObject entry = tabSelection.optJSONObject(i);
                if (entry != null) {
                    String regex = optString(entry, "regex");
                    String id = optString(entry, "id");

                    if (regex != null && id != null) {
                        try {
                            Pattern pattern = Pattern.compile(regex);
                            this.tabMenuRegexes.add(pattern);
                            this.tabMenuIDs.add(id);
                        } catch (PatternSyntaxException e) {
                            Log.w(TAG, "Problem with tabSelectionConfig pattern. " + e.getMessage());
                        }
                    }
                }
            }
        }

        LocalBroadcastManager.getInstance(this.context).sendBroadcast(new Intent(PROCESSED_TAB_NAVIGATION_MESSAGE));
    }

    private void processActions(JSONObject actionConfig) {
        if (actionConfig == null) return;

        this.actions = new HashMap<String, JSONArray>();
        this.actionIDs = new ArrayList<String>();
        this.actionRegexes = new ArrayList<Pattern>();

        if (!actionConfig.optBoolean("active")) return;

        JSONArray actions = actionConfig.optJSONArray("actions");
        if (actions != null) {
            for (int i = 0; i < actions.length(); i++) {
                JSONObject entry = actions.optJSONObject(i);
                if (entry != null) {
                    String id = optString(entry, "id");
                    JSONArray items = entry.optJSONArray("items");
                    if (id != null && items != null) {
                        this.actions.put(id, items);
                    }
                }
            }
        }

        JSONArray actionSelection = actionConfig.optJSONArray("actionSelection");
        if (actionSelection != null) {
            for (int i = 0; i < actionSelection.length(); i++) {
                JSONObject entry = actionSelection.optJSONObject(i);
                if (entry != null) {
                    String regex = optString(entry, "regex");
                    String id = optString(entry, "id");

                    if (regex != null && id != null) {
                        try {
                            Pattern pattern = Pattern.compile(regex);
                            this.actionRegexes.add(pattern);
                            this.actionIDs.add(id);
                        } catch (PatternSyntaxException e) {
                            Log.w(TAG, "Problem with actionSelection pattern. " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private void processUserAgentRegexes(JSONArray config) {
        if (config == null) return;

        this.userAgentRegexes = new ArrayList<Pattern>(config.length());
        this.userAgentStrings = new ArrayList<String>(config.length());

        for (int i = 0; i < config.length(); i++) {
            JSONObject entry = config.optJSONObject(i);
            if (entry != null) {
                String regex = optString(entry, "regex");
                String agent = optString(entry, "userAgent");

                if (regex != null && agent != null) {
                    try {
                        Pattern pattern = Pattern.compile(regex);
                        this.userAgentRegexes.add(pattern);
                        this.userAgentStrings.add(agent);
                        this.interceptHtml = true;
                    } catch (PatternSyntaxException e) {
                        Log.e(TAG, "Syntax error with user agent regex", e);
                    }
                }
            }
        }
    }

    private void processNavigationTitleImage(Object navTitleImage) {
        if (navTitleImage == null) return;

        this.navigationTitleImageRegexes = new ArrayList<Pattern>();

        if (navTitleImage instanceof  Boolean) {
            if ((Boolean)navTitleImage) {
                // match everything
                this.navigationTitleImageRegexes.add(Pattern.compile(".*"));
            } else {
                // match nothing
                this.navigationTitleImageRegexes.add(Pattern.compile("a^"));
            }

            return;
        }

        if (navTitleImage instanceof JSONArray) {
            JSONArray array = (JSONArray)navTitleImage;
            for (int i = 0; i < array.length(); i++) {
                String regex = array.optString(i);
                if (regex != null) {
                    try {
                        this.navigationTitleImageRegexes.add(Pattern.compile(regex));
                    } catch (PatternSyntaxException e) {
                        Log.e(TAG, "Problem with navigation title image regex: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    public String userAgentForUrl(String url) {
        if (url == null) url = "";

        if (this.userAgentRegexes != null) {
            for (int i = 0; i < this.userAgentRegexes.size(); i++) {
                if (this.userAgentRegexes.get(i).matcher(url).matches()) {
                    return this.userAgentStrings.get(i);
                }
            }
        }

        return this.userAgent;
    }

    public boolean shouldShowNavigationTitleImageForUrl(String url) {
        if (url == null || this.navigationTitleImageRegexes == null)
            return this.showLogoInActionBar;

        for (Pattern regex : this.navigationTitleImageRegexes) {
            if (regex.matcher(url).matches()) return true;
        }
        return false;
    }

    public synchronized static AppConfig getInstance(Context context){
        if (mInstance == null){
            mInstance = new AppConfig(context.getApplicationContext());
        }
        return mInstance;
    }

    /** Return the value mapped by the given key, or null if not present or null. */
    public static String optString(JSONObject json, String key)
    {
        // http://code.google.com/p/android/issues/detail?id=13830
        if (json.isNull(key))
            return null;
        else
            return json.optString(key, null);
    }

    // bridge for dynamic update
    public class AppConfigJsBridge {
        @JavascriptInterface
        public void parseJson(String s) {
            processDynamicUpdate(s);
        }
    }

    public AppConfigJsBridge getJsBridge(){
        return this.appConfigJsBridge;
    }
}
