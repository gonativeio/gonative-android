package io.gonative.android;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

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

    // singleton
    private static LoginManager instance = null;
    private Context context;
    private CheckRedirectionTask task = null;

    private boolean loggedIn = false;
    public String loginStatus;

    public static LoginManager getInstance(){
        if (instance == null) {
            instance = new LoginManager();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        this.checkLogin();
    }

    private LoginManager() {
        // prevent direct initialization
    }

    public void checkLogin() {
        if (task != null)
            task.cancel(true);

        String loginDetectionUrl = AppConfig.getInstance(context).loginDetectionUrl;
        if (loginDetectionUrl == null) {
            Log.w(TAG, "Trying to detect login without a testURL");
            return;
        }

        task = new CheckRedirectionTask();
        task.execute(AppConfig.getInstance(context).loginDetectionUrl);
    }

    public void checkIfNotAlreadyChecking() {
        if (task == null || task.getStatus() == AsyncTask.Status.FINISHED)
            checkLogin();
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }


    private class CheckRedirectionTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls){
            try {
                URL parsedUrl = new URL(urls[0]);
                HttpURLConnection connection = null;
                boolean wasRedirected = false;
                int numRedirects = 0;
                do {
                    if (connection != null)
                        connection.disconnect();

                    connection = (HttpURLConnection) parsedUrl.openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", AppConfig.getInstance(context).userAgent);

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
            UrlInspector.getInstance().inspectUrl(finalUrl);

            if (finalUrl == null) {
                loggedIn = false;
                loginStatus = "default";
                setChanged();
                notifyObservers();
                return;
            }

            // iterate through loginDetectionRegexes
            AppConfig appConfig = AppConfig.getInstance(LoginManager.this.context);

            List<Pattern> regexes = appConfig.loginDetectRegexes;
            for (int i = 0; i < regexes.size(); i++) {
                Pattern regex = regexes.get(i);
                if (regex.matcher(finalUrl).matches()) {
                    JSONObject entry = appConfig.loginDetectLocations.get(i);
                    loggedIn = entry.optBoolean("loggedIn", false);

                    loginStatus = AppConfig.optString(entry, "menuName");
                    if (loginStatus == null) loginStatus = loggedIn ? "loggedIn" : "default";

                    setChanged();
                    notifyObservers();
                    break;
                }
            }
        }

    }
}
