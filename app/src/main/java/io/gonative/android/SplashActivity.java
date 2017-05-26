package io.gonative.android;

import android.*;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;

import io.gonative.android.library.AppConfig;

public class SplashActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS_WEBRTC = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Let's not talk how this line works. Having it prevents the screen from getting weird
        // if we do not immediately finish the activity
        setTheme(R.style.SplashScreen);
        super.onCreate(savedInstanceState);

        if (WebView.class.isAssignableFrom(LeanWebView.class)) {
            // regular webview, can ask for permissions at runtime
            startMainActivity(false);
        } else {
            // CrossWalk webview, need to get permissions at app launch
            AppConfig config = AppConfig.getInstance(this);
            if (!config.enableWebRTC) {
                startMainActivity(false);
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED) {
                    startMainActivity(false);
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                    }, REQUEST_PERMISSIONS_WEBRTC);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        startMainActivity(true);
    }

    private void startMainActivity(boolean noSplash) {
        Intent intent = new Intent(this, MainActivity.class);
        // Make MainActivity think it was started from launcher
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        if (noSplash) {
            intent.putExtra("noSplash", true);
        }
        startActivity(intent);
        finish();
    }
}
