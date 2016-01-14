package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import org.json.JSONObject;

import java.util.ArrayList;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 12/20/15.
 * Copyright 2014 GoNative.io LLC
 */
public class SegmentedController implements AdapterView.OnItemSelectedListener {
    private static final String TAG = SegmentedController.class.getName();

    private BroadcastReceiver messageReceiver;

    private MainActivity mainActivity;
    private ArrayList<String> labels;
    private ArrayList<String> urls;
    private int selectedIndex;

    private ArrayAdapter<String> adapter;
    private Spinner spinner;

    public SegmentedController(MainActivity mainActivity, Spinner spinner) {
        this.mainActivity = mainActivity;
        this.spinner = spinner;

        this.labels = new ArrayList<String>();
        this.urls = new ArrayList<String>();

        this.spinner.setAdapter(getAdapter());
        this.spinner.setOnItemSelectedListener(this);

        this.messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;

                if (intent.getAction().equals(AppConfig.PROCESSED_SEGMENTED_CONTROL)) {
                    updateSegmentedControl();
                }
            }
        };
        LocalBroadcastManager.getInstance(this.mainActivity).registerReceiver(
                this.messageReceiver, new IntentFilter(AppConfig.PROCESSED_SEGMENTED_CONTROL));

        updateSegmentedControl();
    }

    private void updateSegmentedControl() {
        this.labels.clear();
        this.urls.clear();
        this.selectedIndex = -1;

        AppConfig appConfig = AppConfig.getInstance(mainActivity);
        if (appConfig.segmentedControl == null) return;

        for (int i = 0; i < appConfig.segmentedControl.size(); i++) {
            JSONObject item = appConfig.segmentedControl.get(i);

            String label = item.optString("label", "Invalid");
            String url = item.optString("url", "");
            Boolean selected = item.optBoolean("selected");

            this.labels.add(i, label);
            this.urls.add(i, url);
            if (selected) this.selectedIndex = i;
        }

        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (selectedIndex > -1) {
                    spinner.setSelection(selectedIndex);
                }

                if (labels.size() > 0) {
                    spinner.setVisibility(View.VISIBLE);
                } else {
                    spinner.setVisibility(View.GONE);
                }

                adapter.notifyDataSetChanged();
            }
        });

    }

    private ArrayAdapter<String> getAdapter() {
        if (this.adapter != null) {
            return this.adapter;
        }

        ArrayAdapter<String> adapter =  new ArrayAdapter<String>(mainActivity,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.adapter = adapter;
        return adapter;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // only load if selection has changed
        if (position != selectedIndex) {
            String url = urls.get(position);

            if (url != null && url.length() > 0) {
                mainActivity.loadUrl(url);
            }

            mainActivity.closeDrawers();
            selectedIndex = position;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // do nothing
    }
}
