package io.gonative.android;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class JsCustomCodeExecutor {
    private static final String TAG = JsCustomCodeExecutor.class.getName();

    public static interface CustomCodeHandler {
        JSONObject execute(Map<String, String> params);
    }

    // The default CustomCodeHandler "Echo"
    // Simply maps all the key/values of the given params into a JSONObject
    private static CustomCodeHandler handler = new CustomCodeHandler() {
        @Override
        public JSONObject execute(Map<String, String> params) {
            if(params != null) {
                JSONObject json = new JSONObject();
                try {
                    for(Map.Entry<String, String> entry : params.entrySet()) {
                        json.put(entry.getKey(), entry.getValue());
                    }
                }
                catch(JSONException e) {
                    Log.e(TAG, "Error building custom Json Data", e);
                }
                return json;
            }
            return null;
        }
    };

    /**
     * Set new CustomCodeHandler to override the default "Echo" handler
     * @param customHandler
     */
    public static void setHandler(CustomCodeHandler customHandler) {
        if(customHandler == null)
            return;
        handler = customHandler;
    }

    /**
     * Code Handler gets triggered by the UrlNavigation class
     *
     * @param params A map consisting of all URI parameters and their values
     * @return A JSONObject as defined by the Code Handler
     *
     * @see UrlNavigation#shouldOverrideUrlLoading
     */
    public static JSONObject execute(Map<String, String> params) {
        try {
            return handler.execute(params);
        } catch(Exception e) {
            Log.e(TAG, "Error executing custom code", e);
            return null;
        }
    }
}
