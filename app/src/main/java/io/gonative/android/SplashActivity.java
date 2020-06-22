package io.gonative.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;

import androidx.core.content.ContextCompat;
import io.gonative.android.library.AppConfig;

public class SplashActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS_WEBRTC = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Let's not talk how this line works. Having it prevents the screen from getting weird
        // if we do not immediately finish the activity
        setTheme(R.style.SplashScreen);
        super.onCreate(savedInstanceState);

        HashSet<String> permissionsToRequest = new HashSet<>();

        // Get permissions for webRTC.
        // We *could* get permissions for regular webview when the web page requests, but there is
        // bug where the entire app will crash if we ask for permissions at that point
        AppConfig config = AppConfig.getInstance(this);
        if (config.enableWebRTC) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(Manifest.permission.CAMERA);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(Manifest.permission.MODIFY_AUDIO_SETTINGS);
            }
        }

        if (LeanWebView.isCrosswalk()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(Manifest.permission.READ_CONTACTS);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(Manifest.permission.WRITE_CONTACTS);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            startMainActivity(false);
        } else {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[]{}),
                    REQUEST_PERMISSIONS_WEBRTC);
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
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (noSplash) {
            intent.putExtra("noSplash", true);
        }
        startActivity(intent);
        finish();
    }
}
