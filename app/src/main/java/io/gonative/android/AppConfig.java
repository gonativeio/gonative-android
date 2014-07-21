package io.gonative.android;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by weiyin on 3/13/14.
 */
public class AppConfig {
    private static final String TAG = AppConfig.class.getName();


    // singleton
    private static AppConfig mInstance = null;

    // instance variables
    private JSONObject json = null;

    private ArrayList<String> mInternalHosts = null;
    private Integer sidebarBackgroundColor = null;
    private Integer sidebarForegroundColor = null;
    private String initialHost = null;
    private boolean allowZoom = true;
    private String userAgent = null;
    private boolean interceptHtml = false;
    private boolean loginIsFirstPage = false;
    private boolean usesGeolocation = false;
    private boolean showActionBar = true;
    private ArrayList<Pattern> regexInternalExternal = null;
    private ArrayList<Boolean> regexIsInternal = null;
    private ArrayList<Pattern> navStructureLevelsRegex = null;
    private ArrayList<Integer> navStructureLevels = null;
    private ArrayList<HashMap<String,Object>> navTitles = null;
    private HashMap<String,JSONArray> menus = null;


    private AppConfig(Context context){
        InputStream is = null;
        InputStream jsonIs = null;
        try {
            // read json
            jsonIs = context.getAssets().open("appConfig.json");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(jsonIs, baos);
            IOUtils.close(baos);
            this.json = new JSONObject(baos.toString("UTF-8"));

            // preprocess internalHosts
            JSONArray hosts = json.getJSONArray("internalHosts");
            mInternalHosts = new ArrayList<String>(hosts.length());
            for (int i = 0; i < hosts.length(); i++){
                mInternalHosts.add(hosts.getString(i));
            }

            // regexInternalExternal
            if (json.has("regexInternalExternal")) {
                JSONArray array = json.getJSONArray("regexInternalExternal");
                int len = array.length();
                this.regexInternalExternal = new ArrayList<Pattern>(len);
                this.regexIsInternal = new ArrayList<Boolean>(len);

                for (int i = 0; i < len; i++) {
                    if (array.isNull(i)) continue;
                    JSONObject entry = array.getJSONObject(i);
                    if (entry != null && entry.has("regex") && entry.has("internal")) {
                        this.regexInternalExternal.add(i, Pattern.compile(entry.getString("regex")));
                        this.regexIsInternal.add(i, entry.getBoolean("internal"));
                    }
                }

            }
            else {
                this.regexInternalExternal = new ArrayList<Pattern>();
                this.regexIsInternal = new ArrayList<Boolean>();
            }

            // preprocess initialHost
            initialHost = Uri.parse(getString("initialURL")).getHost();
            if (initialHost.startsWith("www.")) {
                initialHost = initialHost.substring("www.".length());
            }

            // preprocess colors
            if (getString("androidSidebarBackgroundColor") != null)
                sidebarBackgroundColor = Color.parseColor(getString("androidSidebarBackgroundColor"));
            if (getString("androidSidebarForegroundColor") != null)
                this.sidebarForegroundColor = Color.parseColor(getString("androidSidebarForegroundColor"));

            // preprocess allow zoom
            allowZoom = getBoolean("allowZoom", true);

            // user agent for everything (webview, httprequest, httpurlconnection)
            WebView wv = new WebView(context);
            this.setUserAgent(wv.getSettings().getUserAgentString() + " "
                    + getString("userAgentAdd"));

            // should intercept html
            String css = getString("customCss");
            this.interceptHtml = (getString("customCss") != null && getString("customCss").length() > 0)
                    || (getString("stringViewport") != null && getString("stringViewport").length() > 0)
                    || containsKey("viewportWidth");

            // login is first page
            this.loginIsFirstPage = getBoolean("loginIsFirstPage", false);

            // geolocation. Also need to allow permissions in AndroidManifest.xml
            this.usesGeolocation = getBoolean("usesGeolocation", false);

            this.showActionBar = getBoolean("showActionBar", true);

            // navStructure
            if (json.has("navStructure") && json.getJSONObject("navStructure").has("urlLevels")) {
                JSONArray urlLevels = json.getJSONObject("navStructure").getJSONArray("urlLevels");
                this.navStructureLevelsRegex = new ArrayList<Pattern>(urlLevels.length());
                this.navStructureLevels = new ArrayList<Integer>(urlLevels.length());
                for (int i = 0; i < urlLevels.length(); i++) {
                    if (urlLevels.isNull(i)) continue;
                    JSONObject entry = urlLevels.getJSONObject(i);
                    if (entry.has("regex") && entry.has("level")) {
                        try {
                            this.navStructureLevelsRegex.add(Pattern.compile(entry.getString("regex")));
                            this.navStructureLevels.add(entry.getInt("level"));
                        } catch (PatternSyntaxException e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
                }
            }

            if (json.has("navStructure") && json.getJSONObject("navStructure").has("titles")) {
                JSONArray titles = json.getJSONObject("navStructure").getJSONArray("titles");
                this.navTitles = new ArrayList<HashMap<String,Object>>(titles.length());
                for (int i = 0; i < titles.length(); i++) {
                    if (titles.isNull(i)) continue;
                    JSONObject entry = titles.getJSONObject(i);
                    if (entry != null && entry.has("regex")) {
                        try {
                            HashMap<String, Object> toAdd = new HashMap<String, Object>();
                            Pattern regex = Pattern.compile(entry.getString("regex"));
                            toAdd.put("regex", regex);
                            if (entry.has("title")) {
                                toAdd.put("title", entry.getString("title"));
                            }
                            if (entry.has("urlRegex")) {
                                Pattern urlRegex = Pattern.compile(entry.getString("urlRegex"));
                                toAdd.put("urlRegex", urlRegex);
                            }
                            if (entry.has("urlChompWords")) {
                                toAdd.put("urlChompWords", entry.getInt("urlChompWords"));
                            }
                            this.navTitles.add(toAdd);
                        } catch (PatternSyntaxException e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
                }
            }

            // menus
            JSONObject menus = json.optJSONObject("menus");
            if (menus != null) {
                this.menus = new HashMap<String, JSONArray>();
                Iterator keys = menus.keys();
                while (keys.hasNext()) {
                    String key = (String)keys.next();
                    if (menus.optJSONObject(key) != null &&
                            menus.optJSONObject(key).optJSONArray("items") != null) {
                        this.menus.put(key, menus.optJSONObject(key).optJSONArray("items"));
                    }
                }
            }

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

    public String getString(String key) {
        if (json.isNull(key)) return null;
        else return json.optString(key, null);
    }

    public double getDouble(String key) {
        return json.optDouble(key, Double.NaN);
    }

    public boolean getBoolean(String key){
        return json.optBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return json.optBoolean(key, fallback);
    }

    public JSONObject getJSONObject(String key) {
        return json.optJSONObject(key);
    }

    public boolean containsKey(String key){
        return json.has(key);
    }

    public ArrayList<String> getInternalHosts(){
        return mInternalHosts;
    }

    public Integer getSidebarBackgroundColor() {
        return sidebarBackgroundColor;
    }

    public void setSidebarBackgroundColor(Integer sidebarBackgroundColor) {
        this.sidebarBackgroundColor = sidebarBackgroundColor;
    }

    public Integer getSidebarForegroundColor() {
        return sidebarForegroundColor;
    }

    public void setSidebarForegroundColor(Integer sidebarForegroundColor) {
        this.sidebarForegroundColor = sidebarForegroundColor;
    }

    public String getInitialHost() {
        return initialHost;
    }

    public void setInitialHost(String initialHost) {
        this.initialHost = initialHost;
    }

    public boolean getAllowZoom() {
        return allowZoom;
    }

    public void setAllowZoom(boolean allowZoom) {
        this.allowZoom = allowZoom;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean getInterceptHtml() {
        return interceptHtml;
    }

    public void setInterceptHtml(boolean interceptHtml) {
        this.interceptHtml = interceptHtml;
    }

    public boolean loginIsFirstPage() {
        return loginIsFirstPage;
    }

    public void setLoginIsFirstPage(boolean loginIsFirstPage) {
        this.loginIsFirstPage = loginIsFirstPage;
    }

    public boolean usesGeolocation() {
        return usesGeolocation;
    }

    public void setUsesGeolocation(boolean usesGeolocation) {
        this.usesGeolocation = usesGeolocation;
    }

    public boolean showActionBar() {
        return showActionBar;
    }

    public void setShowActionBar(boolean showActionBar) {
        this.showActionBar = showActionBar;
    }

    public ArrayList<Pattern> getRegexInternalExternal() {
        return regexInternalExternal;
    }

    public ArrayList<Boolean> getRegexIsInternal() {
        return regexIsInternal;
    }

    public ArrayList<Pattern> getNavStructureLevelsRegex() {
        return navStructureLevelsRegex;
    }

    public ArrayList<Integer> getNavStructureLevels() {
        return navStructureLevels;
    }

    public ArrayList<HashMap<String, Object>> getNavTitles() {
        return navTitles;
    }

    public HashMap<String, JSONArray> getMenus() {
        return menus;
    }
}
