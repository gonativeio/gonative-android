package io.gonative.android;


import android.util.Log;
import android.net.Uri;

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
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(".+@([A-Za-z0-9]+\\.)+[A-Za-z]{2}[A-Za-z]*");
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
}
