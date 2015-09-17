package io.gonative.android;

import android.app.Application;
import android.util.Log;

import com.parse.Parse;
import com.parse.ParseInstallation;

/**
 * Created by weiyin on 9/2/15.
 * Copyright 2014 GoNative.io LLC
 */
public class GoNativeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        AppConfig appConfig = AppConfig.getInstance(this);
        if (appConfig.parseEnabled) {
            Parse.initialize(this, appConfig.parseApplicationId, appConfig.parseClientKey);
            ParseInstallation.getCurrentInstallation().saveInBackground();
        }
    }
}
