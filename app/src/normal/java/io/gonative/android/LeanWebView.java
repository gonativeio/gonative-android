package io.gonative.android;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Pass calls WebViewClient.shouldOverrideUrlLoading when loadUrl, reload, or goBack are called.
 */
public class LeanWebView extends WebView implements GoNativeWebviewInterface {
    private WebViewClient mClient = null;
    private WebChromeClient mChromeClient = null;
    private boolean checkLoginSignup = true;
    private GestureDetector gd;
    private OnSwipeListener onSwipeListener;

    public LeanWebView(Context context) {
        super(context);
        gd = new GestureDetector(context, sogl);
    }

    public LeanWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gd = new GestureDetector(context, sogl);
    }

    public LeanWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        gd = new GestureDetector(context, sogl);
    }

    GestureDetector.SimpleOnGestureListener sogl = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
            if (onSwipeListener == null) return true;

            float absVelocityX = Math.abs(velocityX);
            float absVelocityY = Math.abs(velocityY);
            if (absVelocityX < 500 || absVelocityX < absVelocityY) return true;

            if (velocityX < 0) {
                onSwipeListener.onSwipeLeft();
            } else {
                onSwipeListener.onSwipeRight();
            }
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gd.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        mClient = client;
        super.setWebViewClient(client);
    }

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        mChromeClient = client;
        super.setWebChromeClient(client);
    }

    @Override
    public void loadUrl(String url) {
        if (url == null) return;

        if (url.startsWith("javascript:"))
            runJavascript(url.substring("javascript:".length()));
        else if (mClient == null || !mClient.shouldOverrideUrlLoading(this, url)) {
            super.loadUrl(url);
        }
    }

    @Override
    public void reload() {
        if (mClient == null || !(mClient instanceof GoNativeWebviewClient)) super.reload();
        else if(!((GoNativeWebviewClient)mClient).shouldOverrideUrlLoading(this, getUrl(), true))
            super.reload();
    }

    @Override
    public void goBack() {
        try {
            WebBackForwardList history = copyBackForwardList();
            String currentUrl = getUrl();
            // find first non-offline item that is not equal to current url
            WebHistoryItem item = null;
            int steps = 0;
            for (int i = history.getCurrentIndex() - 1; i >= 0; i--) {
                WebHistoryItem temp = history.getItemAtIndex(i);
                if (!temp.getUrl().equals(UrlNavigation.OFFLINE_PAGE_URL) &&
                    !urlEqualsIgnoreSlash(temp.getUrl(), currentUrl)) {
                    item = temp;
                    steps = i - history.getCurrentIndex();
                    break;
                }
            }

            if (item == null) return;

            // this shouldn't be necessary, but sometimes we are not able to get an updated
            // intercept url from onPageStarted, so this call to shouldOverrideUrlLoading ensures
            // that our html interceptor knows about this url.
            if (mClient.shouldOverrideUrlLoading(this, item.getUrl())) {
                return;
            }

            super.goBackOrForward(steps);
            return;
        } catch (Exception ignored) {
            super.goBack();
            return;
        }
    }

    private boolean urlEqualsIgnoreSlash(String url1, String url2) {
        if (url1 == null || url2 == null) return false;
        if (url1.endsWith("/")) {
            url1 = url1.substring(0, url1.length() - 1);
        }
        if (url2.endsWith("/")) {
            url2 = url2.substring(0, url2.length() - 1);
        }
        return url1.equals(url2);
    }

    // skip shouldOverrideUrlLoading, including its html override logic.
    public void loadUrlDirect(String url) {
        super.loadUrl(url);
    }

    public boolean checkLoginSignup() {
        return checkLoginSignup;
    }

    public void setCheckLoginSignup(boolean checkLoginSignup) {
        this.checkLoginSignup = checkLoginSignup;
    }

    public void runJavascript(String js) {
        // before Kitkat, the only way to run javascript was to load a url that starts with "javascript:".
        // Starting in Kitkat, the "javascript:" method still works, but it expects the rest of the string
        // to be URL encoded, unlike previous versions. Rather than URL encode for Kitkat and above,
        // use the new evaluateJavascript method.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            loadUrlDirect("javascript:" + js);
        } else {
            evaluateJavascript(js, null);
        }
    }

    public boolean exitFullScreen() {
        if (mChromeClient != null && mChromeClient instanceof GoNativeWebChromeClient) {
            return ((GoNativeWebChromeClient) mChromeClient).exitFullScreen();
        } else {
            return false;
        }
    }

    static public boolean isCrosswalk() {
        return false;
    }

    @Override
    public void saveStateToBundle(Bundle outBundle) {
        saveState(outBundle);
    }

    @Override
    public void restoreStateFromBundle(Bundle inBundle) {
        restoreState(inBundle);
    }

    public interface OnSwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
    }

    public OnSwipeListener getOnSwipeListener() {
        return onSwipeListener;
    }

    public void setOnSwipeListener(OnSwipeListener onSwipeListener) {
        this.onSwipeListener = onSwipeListener;
    }
}
