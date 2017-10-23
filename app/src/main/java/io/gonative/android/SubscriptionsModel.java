package io.gonative.android;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by weiyin on 10/23/17.
 */

public class SubscriptionsModel {
    private static final String TAG = SubscriptionsModel.class.getName();

    public static class SubscriptionItem {
        public String identifier;
        public String name;
        public boolean isSubscribed;
    }

    public static class SubscriptionsSection {
        public String name;
        public List<SubscriptionItem> items;
    }

    public List<SubscriptionsSection> sections;

    public static SubscriptionsModel fromJSONString(String json) {
        try {
            JSONObject parsedJSON = new JSONObject(json);

            SubscriptionsModel model = new SubscriptionsModel();
            model.sections = new ArrayList<>();

            JSONArray parsedSections = parsedJSON.optJSONArray("sections");
            if (parsedSections != null) {
                for (int i = 0; i < parsedSections.length(); i++) {
                    JSONObject parsedSection = parsedSections.optJSONObject(i);
                    if (parsedSection != null) {
                        SubscriptionsSection section = new SubscriptionsSection();
                        section.name = parsedSection.optString("name");
                        section.items = new ArrayList<>();

                        JSONArray parsedItems = parsedSection.optJSONArray("items");
                        if (parsedItems != null) {
                            for (int j = 0; j < parsedItems.length(); j++) {
                                JSONObject parsedItem = parsedItems.optJSONObject(j);
                                if (parsedItem != null) {
                                    SubscriptionItem item = new SubscriptionItem();
                                    item.identifier = parsedItem.optString("identifier");
                                    item.name = parsedItem.optString("name");
                                    item.isSubscribed = false;
                                    section.items.add(item);
                                }
                            }
                        }

                        model.sections.add(section);
                    }
                }
            }

            return model;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON for subscriptions", e);
            return null;
        }
    }
}
