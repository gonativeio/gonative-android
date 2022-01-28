package io.gonative.gonative_core;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import org.json.JSONObject;

public class BaseBridgeModule implements BridgeModule{
    @Override
    public void onApplicationCreate(GoNativeContext context) { }

    @Override
    public <T extends Activity & GoNativeActivity> void onActivityCreate(T activity, boolean isRoot) { }

    @Override
    public <T extends Activity & GoNativeActivity> boolean shouldOverrideUrlLoading(T activity, Uri url, JSONObject params) {
        return false;
    }

    @Override
    public <T extends Activity & GoNativeActivity> void onActivityResume(T activity) { }

    @Override
    public <T extends Activity & GoNativeActivity> void onActivityPause(T activity) { }

    @Override
    public <T extends Activity & GoNativeActivity> void onActivityStart(T activity) { }

    @Override
    public <T extends Activity & GoNativeActivity> void onActivityDestroy(T activity) { }

    @Override
    public <T extends Activity & GoNativeActivity> void onActivityResult(T activity, int requestCode, int resultCode, Intent data) { }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public <T extends Activity & GoNativeActivity> void onPageFinish(T activity, boolean doNativeBridge) { }

    @Override
    public <T extends Activity & GoNativeActivity> void onConfigurationChange(T activity) { }
    
    @Override
    public <T extends Activity & GoNativeActivity> void onHideWebview(T activity) { }

    @Override
    public <T extends Activity & GoNativeActivity> void  onRequestPermissionsResult(T activity, int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { }
}
