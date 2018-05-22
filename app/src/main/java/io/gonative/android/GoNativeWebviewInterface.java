package io.gonative.android;

import android.os.Bundle;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;

/**
 * Created by weiyin on 9/8/15.
 */
public interface GoNativeWebviewInterface {
    // webview stuff
    void loadUrl(String url);
    void loadUrlDirect(String url);
    void loadDataWithBaseURL (String baseUrl, String data, String mimeType, String encoding, String historyUrl);
    String getUrl();
    void reload();
    boolean canGoBack();
    void goBack();
    void onPause();
    void onResume();
    void stopLoading();
    ViewParent getParent();
    void destroy();
    int getProgress();
    String getTitle();
    boolean exitFullScreen();
    void clearCache(boolean includeDiskFiles);

    // view stuff
    void setAlpha(float alpha);
    ViewPropertyAnimator animate();
    int getWidth();
    int getScrollY();

    void runJavascript(String js);
    boolean checkLoginSignup();
    void setCheckLoginSignup(boolean checkLoginSignup);

    void saveStateToBundle(Bundle outBundle);
    void restoreStateFromBundle(Bundle inBundle);
}
