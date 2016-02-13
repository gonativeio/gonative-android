package io.gonative.android;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 8/11/2014.
 */
public class GcmBroadcastReceiver extends BroadcastReceiver {
    private static final int DEFAULT_NOTIFICATION_ID = 1;
    private static final String DEFAULT_NOTIFICATION_TAG = "gonative_default_tag";

    private static final String TAG = GcmBroadcastReceiver.class.getName();
    @Override
    public void onReceive(Context context, Intent intent) {
        // Do not handle if GoNative push is not enabled.
        if (!AppConfig.getInstance(context).pushNotifications) return;

        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);

        String messageType = gcm.getMessageType(intent);
        if (messageType != null && !extras.isEmpty()) {
            if (messageType.equals(GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE)) {
                // OneSignal push notifications will get received here, so we need to filter them
                // out to prevent duplicates. By looking for a field named "custom" that looks like
                // a JSON object, we can ignore it.
                String custom = extras.getString("custom");
                if (custom == null || !custom.startsWith("{")) {
                    // this did not come from OneSignal, so go ahead and show notification
                    showNotification(context, extras);
                }

            }
        }
    }

    // This will receive notifications sent via Parse, but will not show them because
    // message will be null.
    // Parse puts everything into a JSON string in extras.getString("data").
    private void showNotification(Context context, Bundle extras) {
        String message = extras.getString("message");
        if (message == null) message = extras.getString("alert");
        if (message == null) return;

        String title = extras.getString("title");
        if (title == null) title = AppConfig.getInstance(context).appName;

        String targetUrl = extras.getString("targetUrl");
        if (targetUrl == null) targetUrl = extras.getString("u");

        // we support strings and ints
        Object notificationId = extras.get("n_id");
        if (notificationId == null) notificationId = extras.get("notificationId");

        String tag = DEFAULT_NOTIFICATION_TAG;
        int id = DEFAULT_NOTIFICATION_ID;

        if (notificationId instanceof String) {
            tag =  (String)notificationId;
            id = DEFAULT_NOTIFICATION_ID;
        } else if (notificationId instanceof Integer) {
            tag = "customId";
            id = (Integer)notificationId;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (targetUrl != null && !targetUrl.isEmpty()) {
            intent.putExtra(MainActivity.INTENT_TARGET_URL, targetUrl);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context,
                MainActivity.REQUEST_PUSH_NOTIFICATION,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message))
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(tag, id, builder.build());
    }
}
