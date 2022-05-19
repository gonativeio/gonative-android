package io.gonative.android;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.gonative.gonative_core.LeanUtils;

/**
 * Created by weiyin on 10/4/15.
 */
public class RegistrationManager {
    private final static String TAG = RegistrationManager.class.getName();

    private Context context;
    private JSONObject customData;
    private String lastUrl;

    private List<RegistrationEndpoint> registrationEndpoints;

    RegistrationManager(Context context) {
        this.context = context;
        this.registrationEndpoints = new LinkedList<>();
    }

    public void processConfig(JSONArray endpoints) {
        registrationEndpoints.clear();

        if (endpoints == null) return;

        for (int i = 0; i < endpoints.length(); i++) {
            JSONObject endpoint = endpoints.optJSONObject(i);
            if (endpoint == null) continue;

            String url = LeanUtils.optString(endpoint, "url");
            if (url == null) {
                Log.w(TAG, "Invalid registration: endpoint url is null");
                continue;
            }

            List<Pattern> urlRegexes = LeanUtils.createRegexArrayFromStrings(endpoint.opt("urlRegex"));

            RegistrationEndpoint registrationEndpoint = new RegistrationEndpoint(url, urlRegexes);
            registrationEndpoints.add(registrationEndpoint);
        }
    }

    public void checkUrl(String url) {
        this.lastUrl = url;
        for (RegistrationEndpoint endpoint : registrationEndpoints) {
            if (LeanUtils.stringMatchesAnyRegex(url, endpoint.urlRegexes)) {
                endpoint.sendRegistrationInfo();
            }
        }
    }

    public void setCustomData(JSONObject customData) {
        this.customData = customData;
        registrationDataChanged();
    }

    public void sendToAllEndpoints() {
        for (RegistrationEndpoint endpoint : registrationEndpoints) {
                endpoint.sendRegistrationInfo();
        }
    }

    private void registrationDataChanged() {
        for (RegistrationEndpoint endpoint : registrationEndpoints) {
            if (this.lastUrl != null &&
                    LeanUtils.stringMatchesAnyRegex(this.lastUrl, endpoint.urlRegexes)) {
                endpoint.sendRegistrationInfo();
            }
        }
    }

    public void subscriptionInfoChanged(){
        registrationDataChanged();
    }

    private class RegistrationEndpoint {
        private String postUrl;
        private List<Pattern> urlRegexes;

        RegistrationEndpoint(String postUrl, List<Pattern> urlRegexes) {
            this.postUrl = postUrl;
            this.urlRegexes = urlRegexes;
        }

        void sendRegistrationInfo() {
            new SendRegistrationTask(context, this, RegistrationManager.this).execute();
        }
    }

    private static class SendRegistrationTask extends AsyncTask<Void,Void,Void> {
        private RegistrationEndpoint registrationEndpoint;
        private RegistrationManager registrationManager;
        private Context context;

        SendRegistrationTask(Context context, RegistrationEndpoint registrationEndpoint, RegistrationManager registrationManager) {
            this.registrationEndpoint = registrationEndpoint;
            this.registrationManager = registrationManager;
            this.context = context;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Map<String, Object> toSend = new HashMap<>();

            toSend.putAll(Installation.getInfo(registrationManager.context));

            // Append provider info to Map toSend
            if (((GoNativeApplication) context).getAnalyticsProviderInfo() != null) {
                toSend.putAll(((GoNativeApplication) context).getAnalyticsProviderInfo());
            }

            if (registrationManager.customData != null) {
                Iterator<String> keys = registrationManager.customData.keys();
                while(keys.hasNext()) {
                    String key = keys.next();
                    toSend.put("customData_" + key, registrationManager.customData.opt(key));
                }
            }

            try {
                JSONObject json = new JSONObject(toSend);

                URL url = new URL(registrationEndpoint.postUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
                writer.write(json.toString());
                writer.close();
                connection.connect();
                int result = connection.getResponseCode();

                if (result < 200 || result > 299) {
                    Log.w(TAG, "Recevied status code " + result + " when posting to " + registrationEndpoint.postUrl);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error posting to " + registrationEndpoint.postUrl, e);
            }

            return null;
        }
    }
}
