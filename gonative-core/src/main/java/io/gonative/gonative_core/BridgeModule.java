package io.gonative.gonative_core;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;

public interface BridgeModule {
    void onApplicationCreate(Context context);
    <T extends Activity & GoNativeActivity> void onActivityCreate(T activity, boolean isRoot);
    <T extends Activity & GoNativeActivity> boolean shouldOverrideUrlLoading(T activity, Uri url);
}
