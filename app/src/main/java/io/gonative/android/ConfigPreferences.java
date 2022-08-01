package io.gonative.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

public class ConfigPreferences {
    private static final String APP_THEME_KEY = "io.gonative.android.appTheme";

    private Context context;
    private SharedPreferences sharedPreferences;

    public ConfigPreferences(Context context) {
        this.context = context;
    }

    private SharedPreferences getSharedPreferences() {
        if (this.sharedPreferences == null) {
            this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        }
        return this.sharedPreferences;
    }

    public String getAppTheme() {
        SharedPreferences preferences = getSharedPreferences();
        return preferences.getString(APP_THEME_KEY, null);
    }

    public void setAppTheme(String appTheme) {
        SharedPreferences preferences = getSharedPreferences();
        if (TextUtils.isEmpty(appTheme)) {
            preferences.edit().remove(APP_THEME_KEY).commit();
        } else {
            preferences.edit().putString(APP_THEME_KEY, appTheme).commit();
        }
    }
}
