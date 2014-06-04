package io.gonative.android;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Observable;

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

        task = new CheckRedirectionTask();
        task.execute(AppConfig.getInstance(context).getString("loginDetectionURL"));
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
                    connection.setRequestProperty("User-Agent", AppConfig.getInstance(context).getUserAgent());

                    connection.connect();
                    int responseCode = connection.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                            responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                        wasRedirected = true;
                        parsedUrl = new URL(connection.getHeaderField("Location"));
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

            if (finalUrl == null)
                loggedIn = false;
            else if (LeanUtils.urlsMatchOnPath(finalUrl,
                    AppConfig.getInstance(LoginManager.this.context).getString("loginDetectionURLnotloggedin")))
                loggedIn = false;
            else
                loggedIn = true;

            LoginManager.this.setChanged();
            LoginManager.this.notifyObservers();
        }

    }
}
