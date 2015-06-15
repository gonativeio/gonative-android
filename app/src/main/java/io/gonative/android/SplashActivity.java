package io.gonative.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;

public class SplashActivity extends Activity {
    private BroadcastReceiver messageReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // finish splash activity if received finished loading message from main activity
        this.messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(LeanWebviewClient.FINISHED_LOADING_MESSAGE)) {
                    finish();
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.messageReceiver,
                new IntentFilter(LeanWebviewClient.FINISHED_LOADING_MESSAGE));

        // also finish after delay
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 1500); // 1.5 seconds
    }

}
