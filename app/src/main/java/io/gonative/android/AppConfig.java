package io.gonative.android;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * Created by weiyin on 3/13/14.
 */
public class AppConfig {
    private static final String TAG = AppConfig.class.getName();


    // singleton
    private static AppConfig mInstance = null;

    // instance variables
    private Context context;
    private JSONObject json;

    // general
    public String initialUrl;
    public String initialHost;
    public String appName;
    public String publicKey;
    public String deviceRegKey;
    public String userAgent;

    // navigation
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

    // styling
    public Integer sidebarBackgroundColor;
    public Integer sidebarForegroundColor;
    public String customCSS;
    public double forceViewportWidth;
    public String androidTheme;
    public boolean showActionBar = true;
    public double interactiveDelay;
    public String stringViewport;

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

    // misc
    public boolean allowZoom = true;
    public boolean interceptHtml = false;


    public File fileForOTAconfig() {
        return new File(context.getFilesDir(), "appConfig.json");
    }

    private AppConfig(Context context){
        this.context = context;

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
            this.menus = new HashMap<String, JSONArray>();
            this.loginDetectRegexes = new ArrayList<Pattern>();
            this.loginDetectLocations = new ArrayList<JSONObject>();
            this.navStructureLevelsRegex = new ArrayList<Pattern>();
            this.navStructureLevels = new ArrayList<Integer>();
            this.navTitles = new ArrayList<HashMap<String, Object>>();
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
                String userAgentAdd = optString(general, "userAgentAdd");
                if (userAgentAdd == null) userAgentAdd = "gonative";
                WebView wv = new WebView(context);
                StringBuilder sb = new StringBuilder(wv.getSettings().getUserAgentString());
                sb.append(" ");
                sb.append(userAgentAdd);
                this.userAgent = sb.toString();

                this.publicKey = optString(general, "publicKey");
                this.deviceRegKey = optString(general, "deviceRegKey");
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
                int numActiveMenus = 0;

                JSONObject sidebarNav = navigation.optJSONObject("sidebarNavigation");
                if (sidebarNav != null) {
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

                // navigation levels
                JSONObject navigationLevels = navigation.optJSONObject("navigationLevels");
                if (navigationLevels != null && navigationLevels.optBoolean("active", false)) {
                    JSONArray urlLevels = navigationLevels.optJSONArray("levels");
                    if (urlLevels != null) {
                        for (int i = 0; i < urlLevels.length(); i++){
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

                // navigation titles
                JSONObject navigationTitles = navigation.optJSONObject("navigationTitles");
                if (navigationTitles != null && navigationTitles.optBoolean("active", false)) {
                    JSONArray titles = navigationTitles.optJSONArray("titles");
                    if (titles != null) {
                        for (int i = 0; i < titles.length(); i++) {
                            JSONObject entry = titles.optJSONObject(i);
                            if (entry != null) {
                                String regex = optString(entry, "regex");
                                if (regex != null) {
                                    try {
                                        HashMap<String, Object> toAdd = new HashMap<String, Object>();
                                        Pattern pattern = Pattern.compile(entry.getString("regex"));
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
                                        if (urlChompWords != -1) {
                                            toAdd.put("urlChompWords", urlChompWords);
                                        }

                                        this.navTitles.add(toAdd);
                                    } catch (PatternSyntaxException e) {
                                        Log.e(TAG, e.getMessage(), e);
                                    }
                                }
                            }
                        }
                    }
                }

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
            }

            ////////////////////////////////////////////////////////////
            // Styling
            ////////////////////////////////////////////////////////////
            JSONObject styling = this.json.optJSONObject("styling");

            this.customCSS = optString(styling, "customCSS");

            this.forceViewportWidth = styling.optDouble("forceViewportWidth", Double.NaN);

            this.interceptHtml = this.customCSS != null || !Double.isNaN(this.forceViewportWidth);
            this.showActionBar = styling.optBoolean("showActionBar", true);

            this.androidTheme = optString(styling, "androidTheme");

            // preprocess colors
            String background = AppConfig.optString(styling, "androidSidebarBackgroundColor");
            if (background != null){
                try {
                    this.sidebarBackgroundColor = Color.parseColor(background);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Bad androidSidebarBackgroundColor");
                }
            }

            String foreground = AppConfig.optString(styling, "androidSidebarForegroundColor");
            if (foreground != null){
                try {
                    this.sidebarForegroundColor = Color.parseColor(foreground);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Bad androidSidebarForegroundColor");
                }
            }

            this.interactiveDelay = styling.optDouble("transitionInteractiveDelayMax", Double.NaN);


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
            }

            ////////////////////////////////////////////////////////////
            // Miscellaneous stuff
            ////////////////////////////////////////////////////////////
            this.allowZoom = this.json.optBoolean("allowZoom", true);


        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        finally {
            IOUtils.close(is);
            IOUtils.close(jsonIs);
        }
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
}
