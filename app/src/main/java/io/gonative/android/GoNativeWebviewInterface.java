package io.gonative.android;

import android.os.Bundle;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;

/**
 * Created by weiyin on 9/8/15.
 */
public interface GoNativeWebviewInterface {
    // webview stuff
    public void loadUrl(String url);
    public void loadUrlDirect(String url);
    public void loadDataWithBaseURL (String baseUrl, String data, String mimeType, String encoding, String historyUrl);
    public String getUrl();
    public void reload();
    public boolean canGoBack();
    public void goBack();
    public void onPause();
    public void onResume();
    public void stopLoading();
    public ViewParent getParent();
    public void destroy();
    public int getProgress();
    public String getTitle();
    public boolean exitFullScreen();
    public void clearCache(boolean includeDiskFiles);

    // view stuff
    public void setAlpha(float alpha);
    public ViewPropertyAnimator animate();
    public int getWidth();
    public int getScrollY();

    public void runJavascript(String js);
    public boolean checkLoginSignup();
    public void setCheckLoginSignup(boolean checkLoginSignup);

    public boolean isCrosswalk();

    public void saveStateToBundle(Bundle outBundle);
    public void restoreStateFromBundle(Bundle inBundle);
}
