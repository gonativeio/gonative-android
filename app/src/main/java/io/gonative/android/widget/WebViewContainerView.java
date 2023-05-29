package io.gonative.android.widget;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.gonative.android.GoNativeApplication;
import io.gonative.android.LeanWebView;
import io.gonative.android.MainActivity;
import io.gonative.android.WebViewSetup;
import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.Bridge;
import io.gonative.gonative_core.GoNativeWebviewInterface;

public class WebViewContainerView extends FrameLayout {

    private ViewGroup webview;
    private boolean isGecko = false;

    public WebViewContainerView(@NonNull Context context) {
        super(context);
    }

    public WebViewContainerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initializeWebview(context);
    }

    public WebViewContainerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializeWebview(context);
    }

    private void initializeWebview(Context context) {
        AppConfig appConfig = AppConfig.getInstance(context);

        if (appConfig.geckoViewEnabled) {
            try {
                Class<?> classGecko = Class.forName("io.gonative.plugins.android.gecko.GNGeckoView");
                Constructor<?> consGecko = classGecko.getConstructor(Context.class);
                webview = (ViewGroup) consGecko.newInstance(context);
                this.isGecko = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            webview = new LeanWebView(context);
        }
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webview.setLayoutParams(layoutParams);
        this.addView(webview);
    }

    public void setupWebview(MainActivity activity, boolean isRoot) {
        if (isGecko) {
            try {
                Class<?> geckoSetupClass = Class.forName("io.gonative.plugins.android.gecko.WebViewSetup");
                Method setupWebview = geckoSetupClass.getMethod("setupWebviewForActivity", Activity.class, GoNativeWebviewInterface.class, Bridge.class, boolean.class);
                setupWebview.invoke(geckoSetupClass, activity,  (GoNativeWebviewInterface) webview, ((GoNativeApplication) activity.getApplication()).mBridge, isRoot);
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        } else {
            WebViewSetup.setupWebviewForActivity(getWebview(), activity);
        }
    }

    public GoNativeWebviewInterface getWebview() {
        return (GoNativeWebviewInterface) webview;
    }

}
