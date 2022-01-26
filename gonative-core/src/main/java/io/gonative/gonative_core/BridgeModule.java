package io.gonative.gonative_core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;

import org.json.JSONObject;

public interface BridgeModule {
    void onApplicationCreate(GoNativeContext context);
    <T extends Activity & GoNativeActivity> void onActivityCreate(T activity, boolean isRoot);
    <T extends Activity & GoNativeActivity> boolean shouldOverrideUrlLoading(T activity, Uri url, JSONObject params);
    <T extends Activity & GoNativeActivity> void onActivityResume(T activity);
    <T extends Activity & GoNativeActivity> void onActivityPause(T activity);
    <T extends Activity & GoNativeActivity> void onActivityStart(T activity);
    <T extends Activity & GoNativeActivity> void onActivityDestroy(T activity);
    <T extends Activity & GoNativeActivity> void onActivityResult(T activity, int requestCode, int resultCode, Intent data);
    boolean onKeyDown(int keyCode, KeyEvent event);
    <T extends Activity & GoNativeActivity> void onPageFinish(T activity, boolean doNativeBridge);
    <T extends Activity & GoNativeActivity> void onConfigurationChange(T activity);
    <T extends Activity & GoNativeActivity> void onHideWebview(T activity);
}
