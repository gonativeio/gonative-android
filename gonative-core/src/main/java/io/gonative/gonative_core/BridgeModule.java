package io.gonative.gonative_core;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

public interface BridgeModule {
    void onApplicationCreate(GoNativeContext context);
    <T extends Activity & GoNativeActivity> void onActivityCreate(T activity, boolean isRoot);
    <T extends Activity & GoNativeActivity> boolean shouldOverrideUrlLoading(T activity, Uri url);
    <T extends Activity & GoNativeActivity> void onActivityResult(T activity, int requestCode, int resultCode, Intent data);
}
