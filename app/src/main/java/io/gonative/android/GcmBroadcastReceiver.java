package io.gonative.android;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * Created by weiyin on 8/11/2014.
 */
public class GcmBroadcastReceiver extends BroadcastReceiver {
    private static final int NOTIFICATION_ID = 1;

    private static final String TAG = GcmBroadcastReceiver.class.getName();
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);

        String messageType = gcm.getMessageType(intent);
        if (!extras.isEmpty()) {
            if (messageType.equals(GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE)) {
                showNotification(context, extras);
            }
        }
    }

    private void showNotification(Context context, Bundle extras) {
        String title = extras.getString("title");
        if (title == null) title = AppConfig.getInstance(context).appName;

        String message = extras.getString("message");
        if (message == null) return;

        String targetUrl = extras.getString("targetUrl");

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (targetUrl != null && !targetUrl.isEmpty()) {
            intent.putExtra("targetUrl", targetUrl);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context,
                MainActivity.REQUEST_PUSH_NOTIFICATION,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message))
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
