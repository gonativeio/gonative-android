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

    public void updateConfig() {
        if (AppConfig.getInstance(context).disableConfigUpdater) return;

        new UpdateConfigTask().execute();
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
                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
                writer.write(json.toString());
                writer.close();
                connection.connect();
                int result = connection.getResponseCode();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            return null;
        }
    }

    private class UpdateConfigTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            String appnumHashed = AppConfig.getInstance(context).publicKey;
            if (appnumHashed == null) return null;

            try {
                URL url = new URL(String.format("https://gonative.io/static/appConfig/%s.json", appnumHashed));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                int responseCode = connection.getResponseCode();
                if (responseCode >= 400) return null;

                // verify json
                ByteArrayOutputStream baos;
                if (connection.getContentLength() > 0) baos = new ByteArrayOutputStream(connection.getContentLength());
                else baos = new ByteArrayOutputStream();

                InputStream is = new BufferedInputStream(connection.getInputStream());
                IOUtils.copy(is, baos);
                is.close();
                baos.close();
                new JSONObject(baos.toString("UTF-8"));

                // save file
                File destination = AppConfig.getInstance(context).fileForOTAconfig();
                OutputStream os = new BufferedOutputStream(new FileOutputStream(destination));
                is = new BufferedInputStream(new ByteArrayInputStream(baos.toByteArray()));
                IOUtils.copy(is, os);
                is.close();
                os.close();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            return null;
        }
    }
}
