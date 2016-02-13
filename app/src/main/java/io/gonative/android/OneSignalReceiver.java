package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.onesignal.OneSignal;

import org.json.JSONObject;

/**
 * Created by weiyin on 2/10/16.
 */
public class OneSignalReceiver extends BroadcastReceiver implements OneSignal.NotificationOpenedHandler {
    private Context context;

    public OneSignalReceiver() {
        // default construct needed to be a broadcast receiver
    }

    public OneSignalReceiver(Context context) {
        this.context = context;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // This broadcast receiver is only here to suppress OneSignal's default notification opened handling.
        // Don't do any work here. The notification handling will be performed by OneSignalNotifcationOpenedHandler.
    }

    @Override
    public void notificationOpened(String message, JSONObject additionalData, boolean isActive) {
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
