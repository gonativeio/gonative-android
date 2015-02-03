package io.gonative.android;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import android.widget.Toast;

/**
* Created by weiyin on 2/2/15.
* Copyright 2014 GoNative.io LLC
*/
class GoNativeWebChromeClient extends WebChromeClient {
    private MainActivity mainActivity;
    private View customView;
    private CustomViewCallback callback;
    private boolean isFullScreen = false;

    public GoNativeWebChromeClient(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result){
        Toast.makeText(mainActivity, message, Toast.LENGTH_LONG).show();
        result.confirm();
        return true;
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        callback.invoke(origin, AppConfig.getInstance(mainActivity).usesGeolocation, true);
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        RelativeLayout fullScreen = this.mainActivity.getFullScreenLayout();
        if (fullScreen == null) return;

        this.customView = view;
        this.callback = callback;
        this.isFullScreen = true;

        fullScreen.setVisibility(View.VISIBLE);
        fullScreen.addView(view, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        this.mainActivity.toggleFullscreen(this.isFullScreen);
    }

    @Override
    public void onHideCustomView() {
        this.customView = null;
        this.isFullScreen = false;

        RelativeLayout fullScreen = this.mainActivity.getFullScreenLayout();
        if (fullScreen != null) {
            fullScreen.setVisibility(View.INVISIBLE);
            fullScreen.removeAllViews();
        }

        if (this.callback != null) {
            callback.onCustomViewHidden();
        }

        this.mainActivity.toggleFullscreen(this.isFullScreen);
    }

    public boolean onBackPressed() {
        if (this.isFullScreen) {
            onHideCustomView();
            return true;
        } else {
            return false;
        }
    }

    // For Android > 4.1
    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
        mainActivity.setUploadMessage(uploadMsg);

        if (acceptType == null) acceptType = "*/*";

        // Filesystem.
        final Intent galleryIntent = new Intent();
        galleryIntent.setType(acceptType);
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);

        mainActivity.startActivityForResult(galleryIntent, MainActivity.REQUEST_SELECT_FILE);
    }

    // Android 3.0 +
    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
        openFileChooser(uploadMsg, acceptType, null);
    }

    //Android 3.0
    public void openFileChooser(ValueCallback<Uri> uploadMsg) {
        openFileChooser(uploadMsg, null, null);
    }

    @Override
    public void onReceivedTitle(WebView view, String title){
        mainActivity.updatePageTitle();
    }
}
