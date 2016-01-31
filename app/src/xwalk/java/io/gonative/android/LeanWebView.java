package io.gonative.android;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.xwalk.core.XWalkNavigationHistory;
import org.xwalk.core.XWalkView;

/**
 * Pass calls WebViewClient.shouldOverrideUrlLoading when loadUrl, reload, or goBack are called.
 */
public class LeanWebView extends XWalkView implements GoNativeWebviewInterface {
    private boolean checkLoginSignup = true;

    public LeanWebView(Activity activity) {
        super(activity, activity);
    }

    public LeanWebView(Context context, Activity activity) {
        super(context, activity);
    }

    public LeanWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void loadUrl(String url) {
        load(url, null);
    }

    @Override
    public void reload() {
        reload(RELOAD_NORMAL);
    }

    @Override
    public void goBack() {
        getNavigationHistory().navigate(XWalkNavigationHistory.Direction.BACKWARD, 1);
    }

    @Override
    public void onPause() {
        pauseTimers();
    }

    @Override
    public void onResume() {
        resumeTimers();
    }

    @Override
    public void destroy() {
        onDestroy();
    }

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public boolean exitFullScreen() {
        if (hasEnteredFullscreen()) {
            leaveFullscreen();
            return true;
        }
        return false;
    }

    @Override
    public void runJavascript(String js) {
        evaluateJavascript(js, null);
    }

    @Override
    public void loadUrlDirect(String url) {
        load(url, null);
    }

    @Override
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        load(baseUrl, data);
    }

    @Override
    public boolean checkLoginSignup() {
        return checkLoginSignup;
    }

    @Override
    public void setCheckLoginSignup(boolean checkLoginSignup) {
        this.checkLoginSignup = checkLoginSignup;
    }

    @Override
    public boolean isCrosswalk() {
        return true;
    }

    @Override
    public boolean canGoBack() {
        XWalkNavigationHistory history = getNavigationHistory();
        if (history == null) return false;
        else return history.canGoBack();
    }

    @Override
    public void saveStateToBundle(Bundle outBundle) {
        saveState(outBundle);
    }

    @Override
    public void restoreStateFromBundle(Bundle inBundle) {
        restoreState(inBundle);
    }

    @Override
    // This makes crosswalk behave the same as the regular android webview in regards
    // to the back button.
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // do not handle the back key. We will handle it in our activity.
            return false;
        }
        return super.dispatchKeyEvent(event);
    }
}
