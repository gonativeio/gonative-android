package io.gonative.android;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Observable;
import java.util.regex.Pattern;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 3/16/14.
 */
public class LoginManager extends Observable {
    private static final String TAG = LoginManager.class.getName();

    private Context context;
    private CheckRedirectionTask task = null;

    private boolean loggedIn = false;

    LoginManager(Context context) {
        this.context = context;
        checkLogin();
    }

    public void checkLogin() {
        if (task != null)
            task.cancel(true);

        String loginDetectionUrl = AppConfig.getInstance(context).loginDetectionUrl;
        if (loginDetectionUrl == null) {
            return;
        }

        task = new CheckRedirectionTask(this);
        task.execute(AppConfig.getInstance(context).loginDetectionUrl);
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }


    private static class CheckRedirectionTask extends AsyncTask<String, Void, String> {
        private WeakReference<LoginManager> loginManagerReference;

        public CheckRedirectionTask(LoginManager loginManager) {
            this.loginManagerReference = new WeakReference<>(loginManager);
        }

        @Override
        protected String doInBackground(String... urls){
            LoginManager loginManager = loginManagerReference.get();
            if (loginManager == null) return null;

            try {
                URL parsedUrl = new URL(urls[0]);
                HttpURLConnection connection = null;
                boolean wasRedirected;
                int numRedirects = 0;
                do {
                    if (connection != null)
                        connection.disconnect();

                    connection = (HttpURLConnection) parsedUrl.openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", AppConfig.getInstance(loginManager.context).userAgent);

                    connection.connect();
                    int responseCode = connection.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                            responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                        wasRedirected = true;
                        parsedUrl = new URL(parsedUrl, connection.getHeaderField("Location"));
                        numRedirects++;
                    } else {
                        wasRedirected = false;
                    }
                } while (!isCancelled() && wasRedirected && numRedirects < 10);

                String finalUrl = connection.getURL().toString();
                connection.disconnect();
                return finalUrl;

            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String finalUrl) {
            LoginManager loginManager = loginManagerReference.get();
            if (loginManager == null) return;

            UrlInspector.getInstance().inspectUrl(finalUrl);
            String loginStatus;

            if (finalUrl == null) {
                loginManager.loggedIn = false;
                loginStatus = "default";
                loginManager.setChanged();
                loginManager.notifyObservers();
                return;
            }

            // iterate through loginDetectionRegexes
            AppConfig appConfig = AppConfig.getInstance(loginManager.context);

            List<Pattern> regexes = appConfig.loginDetectRegexes;
            for (int i = 0; i < regexes.size(); i++) {
                Pattern regex = regexes.get(i);
                if (regex.matcher(finalUrl).matches()) {
                    JSONObject entry = appConfig.loginDetectLocations.get(i);
                    loginManager.loggedIn = entry.optBoolean("loggedIn", false);

                    loginStatus = AppConfig.optString(entry, "menuName");
                    if (loginStatus == null) loginStatus = loginManager.loggedIn ? "loggedIn" : "default";

                    loginManager.setChanged();
                    loginManager.notifyObservers();
                    break;
                }
            }
        }

    }
}
