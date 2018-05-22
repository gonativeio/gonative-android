package io.gonative.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import io.gonative.android.library.AppConfig;

public class SplashActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS_WEBRTC = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Let's not talk how this line works. Having it prevents the screen from getting weird
        // if we do not immediately finish the activity
        setTheme(R.style.SplashScreen);
        super.onCreate(savedInstanceState);

        // Get permissions for webRTC.
        // We *could* get permissions for regular webview when the web page requests, but there is
        // bug where the entire app will crash if we ask for permissions at that point
        AppConfig config = AppConfig.getInstance(this);
        if (config.enableWebRTC) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                startMainActivity(false);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS
                }, REQUEST_PERMISSIONS_WEBRTC);
            }
        } else {
            startMainActivity(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
