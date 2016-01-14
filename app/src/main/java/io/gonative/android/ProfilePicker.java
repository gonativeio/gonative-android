package io.gonative.android;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 5/9/14.
 */
public class ProfilePicker implements AdapterView.OnItemSelectedListener {
    private static final String TAG = ProfilePicker.class.getName();

    private MainActivity mainActivity;
    private JSONArray json;
    private ArrayList<String> names;
    private ArrayList<String> links;
    private int selectedIndex;

    private ArrayAdapter<String> adapter;
    private Spinner spinner;
    private ProfileJsBridge profileJsBridge = null;

    public ProfilePicker(MainActivity mainActivity, Spinner spinner) {
        this.mainActivity = mainActivity;
        this.spinner = spinner;
        this.names = new ArrayList<String>();
        this.links = new ArrayList<String>();
        this.spinner.setAdapter(getAdapter());
        this.spinner.setOnItemSelectedListener(this);
        this.profileJsBridge = new ProfileJsBridge();
    }

    public void parseJson(String s){
        try {
            json = new JSONArray(s);
            this.names.clear();
            this.links.clear();

            for (int i = 0; i < json.length(); i++) {
                JSONObject item = json.getJSONObject(i);

                this.names.add(item.optString("name", ""));
                this.links.add(item.optString("link", ""));

                if (item.optBoolean("selected", false)){
                    selectedIndex = i;
                }
            }

            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if (selectedIndex < ProfilePicker.this.names.size()) {
                        ProfilePicker.this.spinner.setSelection(selectedIndex);
                    }
                    if (ProfilePicker.this.json != null &&
                            ProfilePicker.this.json.length() > 0)
                        ProfilePicker.this.spinner.setVisibility(View.VISIBLE);
                    else
                        ProfilePicker.this.spinner.setVisibility(View.GONE);
                    getAdapter().notifyDataSetChanged();
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public ArrayAdapter<String> getAdapter(){
        if (adapter == null) {
            adapter = new ArrayAdapter<String>(mainActivity, R.layout.profile_picker_dropdown, names) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getView(position, convertView, parent);
                    view.setTextColor(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
                    return view;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                    view.setTextColor(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
                    return view;
                }
            };
        }

        return adapter;
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // only load if selection has changed
        if (position != selectedIndex) {
            mainActivity.loadUrl(links.get(position));
            mainActivity.closeDrawers();
            selectedIndex = position;
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // do nothing
    }

    public ProfileJsBridge getProfileJsBridge() {
        return profileJsBridge;
    }

    public class ProfileJsBridge {
        @JavascriptInterface
        public void parseJson(String s) {
            ProfilePicker.this.parseJson(s);
        }
    }
}
