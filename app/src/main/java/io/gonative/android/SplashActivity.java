package io.gonative.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;

import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

import io.gonative.gonative_core.AppConfig;

public class SplashActivity extends AppCompatActivity {
    private static final int REQUEST_STARTUP_PERMISSIONS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        AppConfig config = AppConfig.getInstance(this);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.splash_screen);

        HashSet<String> permissionsToRequest = new HashSet<>();

        if (LeanWebView.isCrosswalk()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(Manifest.permission.READ_CONTACTS);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(Manifest.permission.WRITE_CONTACTS);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            handleSplash(config);
        } else {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[]{}),
                    REQUEST_STARTUP_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        startMainActivity();
    }

    private void handleSplash(AppConfig config){
        // Check app if unlicensed and show banner
        if (!config.isLicensed()) {
            findViewById(R.id.banner_text).setVisibility(View.VISIBLE);
        }

        Handler handler = new Handler(Looper.getMainLooper());

        // handle splash
        double delay = 1.5; // in seconds
        double forceTime = config.showSplashForceTime;
        double maxTime = config.showSplashMaxTime;
        if (forceTime > 0) {
            delay = forceTime;
        } else if (maxTime > 0) {
            delay = maxTime;
        }
        handler.postDelayed(this::startMainActivity, (long)delay * 1000);
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        // Make MainActivity think it was started from launcher
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finish();
    }
}
