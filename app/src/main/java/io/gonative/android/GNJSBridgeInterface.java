package io.gonative.android;

import android.net.Uri;
import android.webkit.JavascriptInterface;
import org.json.JSONException;
import org.json.JSONObject;

public class GNJSBridgeInterface {
    private final JavascriptBridge javascriptBridge;
    private UrlNavigation urlNavigation;
    private final MainActivity mainActivity;

    public GNJSBridgeInterface(MainActivity mainActivity){
        this.mainActivity = mainActivity;
        this.javascriptBridge = new JavascriptBridge();
    }

    public JavascriptBridge getJavascriptBridge() {
        return javascriptBridge;
    }

    public void setUrlNavigation(UrlNavigation urlNavigation){
        this.urlNavigation = urlNavigation;
    }

    private class JavascriptBridge {
        @JavascriptInterface
        public void postMessage(String message) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(message.isEmpty()) return;
                    try {
                        JSONObject commandObject = new JSONObject(message);
                        urlNavigation.handleJSBridgeFunctions(commandObject);
                    } catch (JSONException jsonException){ // pass it as a uri
                        urlNavigation.handleJSBridgeFunctions(Uri.parse(message));
                    }
                }
            });
        }
    }
}
