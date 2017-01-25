package io.gonative.android;

import android.content.Context;
import android.content.Intent;

import com.onesignal.OSNotification;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OneSignal;

import org.json.JSONObject;

/**
 * Created by weiyin on 2/10/16.
 */
public class OneSignalNotificationHandler implements OneSignal.NotificationOpenedHandler {
    private Context context;

    public OneSignalNotificationHandler() {
        // default construct needed to be a broadcast receiver
    }

    public OneSignalNotificationHandler(Context context) {
        this.context = context;
    }

    @Override
    public void notificationOpened(OSNotificationOpenResult openedResult) {
        OSNotification notification = openedResult.notification;
        JSONObject additionalData = notification.payload.additionalData;

        String targetUrl = LeanUtils.optString(additionalData, "targetUrl");
        if (targetUrl == null) targetUrl = LeanUtils.optString(additionalData, "u");

        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (targetUrl != null && !targetUrl.isEmpty()) {
            mainIntent.putExtra(MainActivity.INTENT_TARGET_URL, targetUrl);
        }

        context.startActivity(mainIntent);
    }
}
