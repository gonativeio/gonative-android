package io.gonative.gonative_core;

public interface GoNativeActivity {
    GoNativeWebviewInterface getWebView();
    void runJavascript(String javascript);
}
