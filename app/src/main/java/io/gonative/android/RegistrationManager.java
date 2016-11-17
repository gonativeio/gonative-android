package io.gonative.android;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by weiyin on 10/4/15.
 */
public class RegistrationManager {
    private final static String TAG = RegistrationManager.class.getName();

    private enum RegistrationDataType {
        Installation,
        Push,
        Parse,
        OneSignal,
        CustomData
    }

    private Context context;
    private String pushRegistrationToken;
    private String parseInstallationId;
    private String oneSignalUserId;
    private JSONObject customData;
    private String lastUrl;

    private List<RegistrationEndpoint> registrationEndpoints;
    private EnumSet<RegistrationDataType> allDataTypes;

    public RegistrationManager(Context context) {
        this.context = context;
        this.registrationEndpoints = new LinkedList<RegistrationEndpoint>();
        this.allDataTypes = EnumSet.noneOf(RegistrationDataType.class);
    }

    public void processConfig(JSONArray endpoints) {
        registrationEndpoints.clear();
        allDataTypes.clear();

        if (endpoints == null) return;

        for (int i = 0; i < endpoints.length(); i++) {
            JSONObject endpoint = endpoints.optJSONObject(i);
            if (endpoint == null) continue;

            String url = LeanUtils.optString(endpoint, "url");
            if (url == null) {
                Log.w(TAG, "Invalid registration endpoint url " + url);
                continue;
            }

            EnumSet<RegistrationDataType> dataTypes = null;
            if (endpoint.optJSONArray("dataType") != null) {
                dataTypes = EnumSet.noneOf(RegistrationDataType.class);
                JSONArray dataTypesArray = endpoint.optJSONArray("dataType");
                for (int j = 0; j < dataTypesArray.length(); j++) {
                    String s = dataTypesArray.optString(j);
                    dataTypes.addAll(getDataTypesFromString(s));
                }
            } else if (LeanUtils.optString(endpoint, "dataType") != null) {
                dataTypes = getDataTypesFromString(LeanUtils.optString(endpoint, "dataType"));
            }

            if (dataTypes == null || dataTypes.isEmpty()) {
                Log.w(TAG, "No data types specified for registration endpoint " + url);
                continue;
            }

            List<Pattern> urlRegexes = LeanUtils.createRegexArrayFromStrings(endpoint.opt("urlRegex"));

            RegistrationEndpoint registrationEndpoint = new RegistrationEndpoint(url, urlRegexes, dataTypes);
            registrationEndpoints.add(registrationEndpoint);
            allDataTypes.addAll(dataTypes);
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

    public void setPushRegistrationToken(String token) {
        this.pushRegistrationToken = token;
        registrationDataChanged(RegistrationDataType.Push);
    }

    public void setParseInstallationId(String installationId) {
        this.parseInstallationId = installationId;
        registrationDataChanged(RegistrationDataType.Parse);
    }

    public void setOneSignalUserId(String oneSignalUserId) {
        this.oneSignalUserId = oneSignalUserId;
        registrationDataChanged(RegistrationDataType.OneSignal);
    }

    public void setCustomData(JSONObject customData) {
        this.customData = customData;
        registrationDataChanged(RegistrationDataType.CustomData);
    }

    public boolean pushEnabled() {
        return this.allDataTypes.contains(RegistrationDataType.Push);
    }

    public void sendToAllEndpoints() {
        for (RegistrationEndpoint endpoint : registrationEndpoints) {
                endpoint.sendRegistrationInfo();
        }
    }

    private EnumSet<RegistrationDataType> getDataTypesFromString(String s) {
        EnumSet<RegistrationDataType> dataTypes = EnumSet.noneOf(RegistrationDataType.class);

        if (s == null) {
            return dataTypes;
        } else if (s.equalsIgnoreCase("installation")) {
            dataTypes.add(RegistrationDataType.Installation);
            dataTypes.add(RegistrationDataType.CustomData);
        } else if (s.equalsIgnoreCase("push")) {
            dataTypes.add(RegistrationDataType.Push);
            dataTypes.add(RegistrationDataType.Installation);
            dataTypes.add(RegistrationDataType.CustomData);
        } else if (s.equalsIgnoreCase("parse")) {
            dataTypes.add(RegistrationDataType.Parse);
            dataTypes.add(RegistrationDataType.Installation);
            dataTypes.add(RegistrationDataType.CustomData);
        } else if (s.equalsIgnoreCase("onesignal")) {
            dataTypes.add(RegistrationDataType.OneSignal);
            dataTypes.add(RegistrationDataType.Installation);
            dataTypes.add(RegistrationDataType.CustomData);
        }

        return dataTypes;
    }

    private void registrationDataChanged(RegistrationDataType type) {
        if (!allDataTypes.contains(type)) return;

        for (RegistrationEndpoint endpoint : registrationEndpoints) {
            if (!endpoint.dataTypes.contains(type)) continue;

            if (this.lastUrl != null &&
                    LeanUtils.stringMatchesAnyRegex(this.lastUrl, endpoint.urlRegexes)) {
                endpoint.sendRegistrationInfo();
            }
        }
    }


    private class RegistrationEndpoint {
        private String postUrl;
        private List<Pattern> urlRegexes;
        private EnumSet<RegistrationDataType> dataTypes;

        public RegistrationEndpoint(String postUrl, List<Pattern> urlRegexes, EnumSet<RegistrationDataType> dataTypes) {
            this.postUrl = postUrl;
            this.urlRegexes = urlRegexes;
            this.dataTypes = dataTypes;
        }

        public void sendRegistrationInfo() {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    Map<String, Object> toSend = new HashMap<String, Object>();

                    if (dataTypes.contains(RegistrationDataType.Installation)) {
                        toSend.putAll(Installation.getInfo(context));
                    }

                    if (dataTypes.contains(RegistrationDataType.Push) && pushRegistrationToken != null) {
                        toSend.put("deviceToken", pushRegistrationToken);
                    }

                    if (dataTypes.contains(RegistrationDataType.Parse) && parseInstallationId != null) {
                        toSend.put("parseInstallationId", parseInstallationId);
                    }

                    if (dataTypes.contains(RegistrationDataType.OneSignal) && oneSignalUserId != null) {
                        toSend.put("oneSignalUserId", oneSignalUserId);
                    }

                    if (dataTypes.contains(RegistrationDataType.CustomData) && customData != null) {
                        Iterator<String> keys = customData.keys();
                        while(keys.hasNext()) {
                            String key = keys.next();
                            toSend.put("customData_" + key, customData.opt(key));
                        }
                    }

                    try {
                        JSONObject json = new JSONObject(toSend);

                        URL url = new URL(postUrl);
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
                            Log.w(TAG, "Recevied status code " + result + " when posting to " + postUrl);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error posting to " + postUrl, e);
                    }

                    return null;
                }
            }.execute();
        }
    }
}
