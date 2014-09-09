package io.gonative.android;


import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.net.Uri;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class LeanUtils {
    private static final String TAG = LeanUtils.class.getName();

    public static boolean urlsMatchOnPath(String url1, String url2) {
        try {
            Uri uri1 = Uri.parse(url1);
            Uri uri2 = Uri.parse(url2);
            String path1 = uri1.getPath();
            String path2 = uri2.getPath();

            if (path1.length() >= 2 && path1.substring(0, 2).equals("//"))
                path1 = path1.substring(1, path1.length());
            if (path2.length() >= 2 && path2.substring(0, 2).equals("//"))
                path2 = path2.substring(1, path2.length());

            if (path1.isEmpty()) path1 = "/";
            if (path2.isEmpty()) path2 = "/";

            String host1 = uri1.getHost();
            String host2 = uri2.getHost();
            if (host1.startsWith("www."))
                host1 = host1.substring(4);
            if (host2.startsWith("www."))
                host2 = host2.substring(4);

            return host1.equals(host2) && path1.equals(path2);
        } catch (Exception e) {
            return false;
        }
    }


    public static boolean isValidEmail(String email) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\S+@\\S+\\.\\S+");
        java.util.regex.Matcher m = p.matcher(email);
        return m.matches();
    }

    public static String jsWrapString(String s) {
        try {
            // urlencoder replaces spaces with pluses. Need to revert it.
            String encoded = URLEncoder.encode(s, "UTF-8");
            encoded = encoded.replace("+", " ");

            return "decodeURIComponent(\"" + encoded + "\")";
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString(), e);
        }

        return null;
    }

    public static String capitalizeWords(String s) {
        StringBuilder sb = new StringBuilder();
        String[] words = s.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.length() > 0) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1));
                }
            }

            if (i < words.length - 1) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("SetJavaScriptEnabled")
    public static void setupWebview(WebView wv, Context context) {
        WebSettings webSettings = wv.getSettings();

        if (AppConfig.getInstance(context).allowZoom) {
            webSettings.setBuiltInZoomControls(true);
        }
        else {
            webSettings.setBuiltInZoomControls(false);
        }

        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        webSettings.setDomStorageEnabled(true);
        File cachePath = new File(context.getCacheDir(), MainActivity.webviewCacheSubdir);
        webSettings.setAppCachePath(cachePath.getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
        webSettings.setDatabaseEnabled(true);

        webSettings.setSaveFormData(false);
        webSettings.setSavePassword(false);
        webSettings.setUserAgentString(AppConfig.getInstance(context).userAgent);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setGeolocationEnabled(AppConfig.getInstance(context).usesGeolocation);
    }
}
