package io.gonative.android;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.parse.ParseAnalytics;
import com.parse.ParsePushBroadcastReceiver;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by weiyin on 12/15/15.
 * Copyright 2014 GoNative.io LLC
 */
public class GnParsePushBroadcastReceiver extends ParsePushBroadcastReceiver {
    private static final int DEFAULT_NOTIFICATION_ID = 1;
    private static final String TAG = ParsePushBroadcastReceiver.class.getName();

    @Override
    protected void onPushReceive(Context context, Intent intent) {
        String dataJson = intent.getStringExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA);
        try {
            JSONObject json = new JSONObject(dataJson);

            // if alert is null and message exists, modify intent by putting message into alert
            String alert = LeanUtils.optString(json, "alert");
            if (alert == null) {
                String message = LeanUtils.optString(json, "message");
                if (message != null) {
                    json.put("alert", message);
                    intent.putExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA, json.toString());
                }
            }

            // we support strings and ints
            Object notificationId = json.opt("n_id");
            if (notificationId == null) notificationId = json.opt("notificationId");

            // default behavior for parse notifications is to make each one unique
            String tag = "";
            int id = (int)System.currentTimeMillis();

            if (notificationId instanceof String) {
                tag =  (String)notificationId;
                id = DEFAULT_NOTIFICATION_ID;
            } else if (notificationId instanceof Integer) {
                tag = "customId";
                id = (Integer) notificationId;
            }

            Notification notification = getNotification(context, intent);

            if (notification != null) {
                // show the notification
                NotificationManager notificationManager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(tag, id, notification);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON data", e);
        }
    }

    @Override
    protected void onPushOpen(Context context, Intent intent) {
        ParseAnalytics.trackAppOpenedInBackground(intent);
        
        String targetUrl = null;
        try {
            JSONObject pushData = new JSONObject(intent.getStringExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA));
            targetUrl = LeanUtils.optString(pushData, "targetUrl");
            if (targetUrl == null) targetUrl = LeanUtils.optString(pushData, "u");
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON data", e);
        }

        Intent mainIntent = new Intent(context, MainActivity.class);
        if (targetUrl != null) {
            mainIntent.putExtra(MainActivity.INTENT_TARGET_URL, targetUrl);
        }
        // use flag_activity_single_top to send to existing activity
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(mainIntent);
    }
}
