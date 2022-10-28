package io.gonative.android;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class AppLinksActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchApp();
    }

    private void launchApp() {
        Intent intent = new Intent(this, MainActivity.class);
        if (getIntent().getData() != null) {
            intent.setData(getIntent().getData());
            intent.setAction(Intent.ACTION_VIEW);
        }
        startActivity(intent);
        finish();
    }
}
