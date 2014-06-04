package io.gonative.android;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;

import com.dd.plist.*;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;

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
    private NSDictionary mDict = null;
    private ArrayList<String> mInternalHosts = null;
    private Integer sidebarBackgroundColor = null;
    private Integer sidebarForegroundColor = null;
    private String initialHost = null;
    private boolean allowZoom = true;
    private String userAgent = null;
    private boolean interceptHtml = false;
    private boolean loginIsFirstPage = false;
    private JSONObject json = null;

    private AppConfig(Context context){
        InputStream is = null;
        InputStream jsonIs = null;
        try {
            String phantom = ""; // key
            String sparky = ""; // initilization vector

            if (phantom.length() > 0) {
                SecretKeySpec key = new SecretKeySpec(Base64.decode(phantom), "AES");
                IvParameterSpec iv = new IvParameterSpec(Base64.decode(sparky));
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, key, iv);

                is = context.getApplicationContext().getAssets().open("appConfig.plist.enc");
                CipherInputStream cis = new CipherInputStream(is, cipher);

                mDict = (NSDictionary) PropertyListParser.parse(cis);
            }
            else {
                is = context.getApplicationContext().getAssets().open("appConfig.plist");
                mDict = (NSDictionary) PropertyListParser.parse(is);
            }

            // read json
            jsonIs = context.getAssets().open("appConfig.json");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(jsonIs, baos);
            IOUtils.close(baos);
            this.json = new JSONObject(baos.toString("UTF-8"));

            // preprocess internalHosts
            NSArray hosts = (NSArray) mDict.objectForKey("internalHosts");
            mInternalHosts = new ArrayList<String>(hosts.count());
            for (int i = 0; i < hosts.count(); i++){
                mInternalHosts.add(((NSString)hosts.objectAtIndex(i)).toString());
            }

            // preprocess initialHost
            initialHost = Uri.parse(getString("initialURL")).getHost();
            if (initialHost.startsWith("www.")) {
                initialHost = initialHost.substring("www.".length());
            }

            // preprocess colors
            if (mDict.containsKey("androidSidebarBackgroundColor"))
                sidebarBackgroundColor = Color.parseColor(getString("androidSidebarBackgroundColor"));
            if (mDict.containsKey("androidSidebarForegroundColor"))
                this.sidebarForegroundColor = Color.parseColor(getString("androidSidebarForegroundColor"));

            // preprocess allow zoom
            if (mDict.containsKey("allowZoom"))
                allowZoom = getBoolean("allowZoom");
            else
                allowZoom = true;

            // user agent for everything (webview, httprequest, httpurlconnection)
            WebView wv = new WebView(context);
            this.setUserAgent(wv.getSettings().getUserAgentString() + " "
                    + getString("userAgentAdd"));

            // should intercept html
            this.interceptHtml = containsKey("customCss") || containsKey("stringViewport");

            // login is first page
            this.loginIsFirstPage = containsKey("loginIsFirstPage") && getBoolean("loginIsFirstPage");



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

    public String getString(String key){
        // json first
        if (json != null && json.has(key)) {
            return json.optString(key, null);
        }

        NSString s = (NSString) mDict.objectForKey(key);
        if (s != null)
            return s.toString();
        else
            return null;
    }

    public boolean getBoolean(String key){
        return ((NSNumber) mDict.objectForKey(key)).boolValue();
    }

    public boolean containsKey(String key){
        return mDict.containsKey(key) || json.has(key);
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
}
