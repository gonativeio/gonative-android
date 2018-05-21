package io.gonative.android;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 8/8/14.
 */
public class ConfigUpdater {
    private static final String TAG = ConfigUpdater.class.getName();

    private Context context;

    public ConfigUpdater(Context context) {
        this.context = context;
    }

    public void registerEvent() {
        if (AppConfig.getInstance(context).disableEventRecorder) return;

        new EventTask().execute();
    }

    private class EventTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            JSONObject json = new JSONObject(Installation.getInfo(context));

            try {
                json.put("event", "launch");
            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e);
                return null;
            }

            try {
                URL url = new URL("https://events.gonative.io/api/events/new");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setDoInput(false); // we do not care about response
                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
                writer.write(json.toString());
                writer.close();
                connection.connect();
                connection.getResponseCode();
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            return null;
        }
    }
}
