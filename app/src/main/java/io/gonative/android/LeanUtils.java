package io.gonative.android;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.gonative.android.library.AppConfig;

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

    public static String urlEncode(String s) {
        try {
            // urlencoder replaces spaces with pluses. Need to revert it.
            String encoded = URLEncoder.encode(s, "UTF-8");
            return encoded.replace("+", " ");
        } catch (UnsupportedEncodingException e){
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    public static String jsWrapString(String s) {
        return new StringBuilder("decodeURIComponent(\"")
                .append(urlEncode(s))
                .append("\")")
                .toString();
    }

    // only used by WebFormActivity. For real webviews, not our subclasses.
    public static void runJavascriptOnWebView(WebView webview, String js) {
        // before Kitkat, the only way to run javascript was to load a url that starts with "javascript:".
        // Starting in Kitkat, the "javascript:" method still works, but it expects the rest of the string
        // to be URL encoded, unlike previous versions. Rather than URL encode for Kitkat and above,
        // use the new evaluateJavascript method.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                webview.loadUrl("javascript:" + js);
        } else {
            webview.evaluateJavascript(js, null);
        }
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

    public static Integer parseColor(String colorString) {
        if (colorString == null) return null;

        try {
            return Color.parseColor(colorString);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Bad color string:" + colorString, e);
            return null;
        }
    }

    public static String colorString(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    public static List<Pattern> createRegexArrayFromStrings(Object json) {
        List<Pattern> result = new LinkedList<Pattern>();

        if (json instanceof JSONArray) {
            JSONArray array = (JSONArray)json;
            for (int i = 0; i < array.length(); i++) {
                String regexString = array.isNull(i) ? null : array.optString(i, null);
                if (regexString != null) {
                    try {
                        Pattern regex = Pattern.compile(regexString);
                        result.add(regex);
                    } catch (PatternSyntaxException e) {
                        Log.e(TAG, "Error parsing regex: " + regexString, e);
                    }
                }
            }
        }
        else if (json instanceof String) {
            String regexString = (String)json;
            try {
                Pattern regex = Pattern.compile(regexString);
                result.add(regex);
            } catch (PatternSyntaxException e) {
                Log.e(TAG, "Error parsing regex: " + regexString, e);
            }
        }

        return result;
    }

    public static boolean stringMatchesAnyRegex(String s, Collection<Pattern> regexes) {
        if (s == null || regexes == null || regexes.isEmpty()) {
            return false;
        }

        for (Pattern regex : regexes) {
            if (regex.matcher(s).matches()) return true;
        }

        return false;
    }

    /** Return the value mapped by the given key, or null if not present or null. */
    public static String optString(JSONObject json, String key)
    {
        if (json == null || key == null) return null;
        // http://code.google.com/p/android/issues/detail?id=13830
        return json.isNull(key) ? null : json.optString(key, null);
    }

    public static String formatDateForCookie(Date date) {
        // for http cookie
        final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";

        SimpleDateFormat format = new SimpleDateFormat(PATTERN_RFC1123, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));

        return format.format(date);
    }
}
