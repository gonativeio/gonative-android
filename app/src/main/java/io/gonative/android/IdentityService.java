package io.gonative.android;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.parse.ParseInstallation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 9/17/15.
 */
public class IdentityService {
    private final static String TAG = IdentityService.class.getName();
    private Context context;
    private String identityResponseData;

    public IdentityService(Context context) {
        this.context = context;
    }

    public void checkUrl(String url) {
        AppConfig appConfig = AppConfig.getInstance(context);
        if (appConfig.identityEndpointUrl == null ||
                appConfig.checkIdentityUrlRegexes == null ||
                appConfig.checkIdentityUrlRegexes.isEmpty()) {
            return;
        }

        for (Pattern pattern : appConfig.checkIdentityUrlRegexes) {
            if (pattern.matcher(url).matches()) {
                getIdentity();;
                break;
            }
        }
    }

    private void getIdentity() {
        AppConfig appConfig = AppConfig.getInstance(context);
        String endpoint = appConfig.identityEndpointUrl;
        if (endpoint == null) return;

        try {
            URL url = new URL(appConfig.identityEndpointUrl);
            new GetIdentityTask().execute(url);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed identityEndpointUrl", e);
            return;
        }
    }

    private class GetIdentityTask extends AsyncTask<URL, Void, Map<String, Object>> {
        private String readInputStream(InputStream is) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringWriter writer = new StringWriter();
            char[] buf = new char[1024];
            int charsRead;

            while((charsRead = reader.read(buf)) != -1) {
                writer.write(buf, 0, charsRead);
            }

            IOUtils.close(is);

            return writer.toString();
        }

        @Override
        protected Map<String, Object> doInBackground(URL... urls) {
            URL url = urls[0];

            try {
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoInput(true);

                // do query
                connection.connect();
                int response = connection.getResponseCode();
                if (response != 200) {
                    Log.w(TAG, "Identity response status code was " + response);
                    return null;
                }

                InputStream inputStream = connection.getInputStream();
                String data = readInputStream(inputStream);

                if (identityResponseData != null && data.contentEquals(identityResponseData)) {
                    return null;
                }
                identityResponseData = data;

                // parse some JSON
                JSONObject json = new JSONObject(data);

                Map<String, Object> objectForParse = new HashMap<String, Object>();
                Iterator <String> iterator = json.keys();
                jsonObjectLoop:
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    Object value = json.get(key);

                    // only allow numbers, bools, strings, nulls, arrays of numbers and strings
                    if (!(value instanceof Number) &&
                            !(value instanceof Boolean) &&
                            !(value instanceof String) &&
                            !(value == null) &&
                            !(value instanceof JSONArray)) {
                        Log.w(TAG, "Type not allowed in identity object key " + key);
                        continue;
                    }

                    if (value instanceof JSONArray) {
                        JSONArray array = (JSONArray)value;
                        for (int i = 0; i < array.length(); i++) {
                            Object arrayVal = array.get(i);
                            if (!(arrayVal instanceof String) &&
                                    !(arrayVal instanceof Number)) {
                                Log.w(TAG, "Type not allowed in identity array key " + key);
                                continue jsonObjectLoop;
                            }
                        }
                    }

                    // append GN to key names, in case of conflict with something in parse (like deviceToken)
                    String parseKey = "GN" + key;
                    objectForParse.put(parseKey, value);
                }

                return objectForParse;
            } catch (IOException e) {
                Log.d(TAG, "Error getting identity", e);
                return null;
            } catch (JSONException e) {
                Log.w(TAG, "Error parsing identity JSON", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Map<String, Object> objectToSave) {
            if (objectToSave == null) return;

            ParseInstallation parseInstall = ParseInstallation.getCurrentInstallation();
            for (String key : objectToSave.keySet()) {
                parseInstall.put(key, objectToSave.get(key));
            }

            // delete keys starting with GN that are not in our data. Note that we cannot remove
            // keys while iterating in the same loop, so collect them and remove afterwards.
            LinkedList<String> keysToRemove = new LinkedList<String>();
            for (String key : parseInstall.keySet()) {
                if (key.startsWith("GN") && !objectToSave.containsKey(key)) {
                    keysToRemove.add(key);
                }
            }

            for (String key : keysToRemove) {
                parseInstall.remove(key);
            }

            parseInstall.saveInBackground();
        }
    }
}
