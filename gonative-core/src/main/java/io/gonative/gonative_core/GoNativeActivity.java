package io.gonative.gonative_core;

public interface GoNativeActivity {
    GoNativeWebviewInterface getWebView();
    void runJavascript(String javascript);
    boolean canGoBack();
    void goBack();
}
