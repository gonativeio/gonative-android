package io.gonative.android;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import io.gonative.android.library.AppConfig;

/**
* Created by weiyin on 2/2/15.
* Copyright 2014 GoNative.io LLC
*/
class GoNativeWebChromeClient extends WebChromeClient {
    private MainActivity mainActivity;
    private UrlNavigation urlNavigation;
    private View customView;
    private CustomViewCallback callback;
    private boolean isFullScreen = false;

    public GoNativeWebChromeClient(MainActivity mainActivity, UrlNavigation urlNavigation) {
        this.mainActivity = mainActivity;
        this.urlNavigation = urlNavigation;
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result){
        Toast.makeText(mainActivity, message, Toast.LENGTH_LONG).show();
        result.confirm();
        return true;
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
        if (!AppConfig.getInstance(mainActivity).usesGeolocation) {
            callback.invoke(origin, false, false);
            return;
        }

        mainActivity.getRuntimeGeolocationPermission(new Runnable() {
            @Override
            public void run() {
                callback.invoke(origin, true, false);
            }
        });
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

    public boolean exitFullScreen() {
        if (this.isFullScreen) {
            onHideCustomView();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onCloseWindow(WebView window) {
        if (!mainActivity.isRoot()) mainActivity.finish();
    }

    @Override
    @TargetApi(21)
    // This method was added in Lollipop
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        // make sure there is no existing message
        mainActivity.cancelFileUpload();

        boolean multiple = false;
        switch (fileChooserParams.getMode()) {
            case FileChooserParams.MODE_OPEN:
                multiple = false;
                break;
            case FileChooserParams.MODE_OPEN_MULTIPLE:
                multiple = true;
                break;
            case FileChooserParams.MODE_SAVE:
            default:
                // MODE_SAVE is unimplemented
                filePathCallback.onReceiveValue(null);
                return false;
        }

        mainActivity.setUploadMessageLP(filePathCallback);
        return urlNavigation.chooseFileUpload(fileChooserParams.getAcceptTypes(), multiple);
    }

    // For Android > 4.1
    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
        // make sure there is no existing message
        mainActivity.cancelFileUpload();

        mainActivity.setUploadMessage(uploadMsg);
        if (acceptType == null) acceptType = "*/*";
        urlNavigation.chooseFileUpload(new String[]{acceptType});
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

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        return urlNavigation.createNewWindow(resultMsg);
    }
}
