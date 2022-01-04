package io.gonative.gonative_core;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

public class BaseBridgeModule implements BridgeModule{
    @Override
    public void onApplicationCreate(GoNativeContext context) { }

    @Override
    public <T extends Activity & GoNativeActivity> void onActivityCreate(T activity, boolean isRoot) { }

    @Override
    public <T extends Activity & GoNativeActivity> boolean shouldOverrideUrlLoading(T activity, Uri url) {
        return false;
    }

    @Override
    public <T extends Activity & GoNativeActivity> void onActivityResult(T activity, int requestCode, int resultCode, Intent data) { }
}
