package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import org.json.JSONArray;
import org.json.JSONObject;

import io.gonative.android.library.AppConfig;

/**
 * Created by weiyin on 4/14/14.
 */
public class JsonMenuAdapter extends BaseExpandableListAdapter
        implements ExpandableListView.OnGroupClickListener, ExpandableListView.OnChildClickListener {
    private static final String TAG = JsonMenuAdapter.class.getName();

    private MainActivity mainActivity;
    private JSONArray menuItems;

    private boolean groupsHaveIcons = false;
    private boolean childrenHaveIcons = false;
    private String status;
    private BroadcastReceiver broadcastReceiver;

    private JsonMenuAdapter() {
    }

    public JsonMenuAdapter(MainActivity activity) {
        this.mainActivity = activity;
        menuItems = null;

        // broadcast messages
        this.broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && intent.getAction().equals(AppConfig.PROCESSED_MENU_MESSAGE)) {
                    update();
                }
            }
        };
        LocalBroadcastManager.getInstance(this.mainActivity)
                .registerReceiver(this.broadcastReceiver,
                        new IntentFilter(AppConfig.PROCESSED_MENU_MESSAGE));
    }

    public synchronized void update (){
        update(this.status);
    }

    public synchronized void update(String status) {
        if (status == null) status = "default";
        this.status = status;

        menuItems = AppConfig.getInstance(mainActivity).menus.get(status);
        if (menuItems == null) menuItems = new JSONArray();

        // figure out groupsHaveIcons and childrenHaveIcons (for layout alignment)
        groupsHaveIcons = false;
        childrenHaveIcons = false;
        for (int i = 0; i < menuItems.length(); i++) {
            JSONObject item = menuItems.optJSONObject(i);
            if (item == null) continue;

            if (!item.isNull("icon") && !item.optString("icon").isEmpty()) {
                groupsHaveIcons = true;
            }

            if (item.optBoolean("isGrouping", false)) {
                JSONArray sublinks = item.optJSONArray("subLinks");
                if (sublinks != null) {
                    for (int j = 0; j < sublinks.length(); j++) {
                        JSONObject sublink = sublinks.optJSONObject(j);
                        if (sublink != null && !sublink.isNull("icon") && !sublink.optString("icon").isEmpty()) {
                            childrenHaveIcons = true;
                            break;
                        }
                    }
                }

            }
        }

        notifyDataSetChanged();
    }


    String itemString(String s, int groupPosition) {
        String value = null;
        try {
            JSONObject section = (JSONObject)menuItems.get(groupPosition);
            if (!section.isNull(s))
                value = section.getString(s).trim();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return value;
    }

    String itemString(String s, int groupPosition, int childPosition) {
        String value = null;
        try {
            JSONObject section = (JSONObject)menuItems.get(groupPosition);
            JSONObject sublink = section.getJSONArray("subLinks").getJSONObject(childPosition);
            if (!sublink.isNull(s))
                value = sublink.getString(s).trim();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return value;
    }

    String getTitle(int groupPosition) {
        return itemString("label", groupPosition);
    }

    String getTitle(int groupPosition, int childPosition) {
        return itemString("label", groupPosition, childPosition);
    }

    Pair<String,String> getUrlAndJavascript(int groupPosition) {
        String url = itemString("url", groupPosition);
        String js = itemString("javascript", groupPosition);
        return new Pair<String, String>(url, js);

    }

    Pair<String,String> getUrlAndJavascript(int groupPosition, int childPosition) {
        String url = itemString("url", groupPosition, childPosition);
        String js = itemString("javascript", groupPosition, childPosition);
        return new Pair<String, String>(url, js);
    }

    boolean isGrouping(int groupPosition) {
        try {
            JSONObject section = (JSONObject)menuItems.get(groupPosition);
            return section.optBoolean("isGrouping", false);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public int getGroupCount() {
        return menuItems.length();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        int count = 0;
        try {
            JSONObject section = (JSONObject)menuItems.get(groupPosition);
            if (section.optBoolean("isGrouping", false)) {
                count = section.getJSONArray("subLinks").length();
            } else {
                count = 0;
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return count;
    }

    @Override
    public Object getGroup(int i) {
        return null;
    }

    @Override
    public Object getChild(int i, int i2) {
        return null;
    }

    @Override
    public long getGroupId(int i) {
        return 0;
    }

    @Override
    public long getChildId(int i, int i2) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = mainActivity.getLayoutInflater();

            convertView = inflater.inflate(groupsHaveIcons ?
                    R.layout.menu_group_icon : R.layout.menu_group_noicon, null);

            // style it
            if (AppConfig.getInstance(mainActivity).sidebarForegroundColor != null) {
                TextView title = (TextView) convertView.findViewById(R.id.menu_item_title);
                title.setTextColor(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
            }
        }

        // expand/collapse indicator
        ImageView indicator = (ImageView)convertView.findViewById(R.id.menu_group_indicator);
        if (isGrouping(groupPosition)) {
            IconDrawable iconDrawable = null;
            if (isExpanded)
                iconDrawable = new IconDrawable(mainActivity, Iconify.IconValue.fa_angle_up);
            else
                iconDrawable = new IconDrawable(mainActivity, Iconify.IconValue.fa_angle_down);

            iconDrawable = iconDrawable.sizeRes(R.dimen.sidebar_expand_indicator_size);
            if (AppConfig.getInstance(mainActivity).sidebarForegroundColor != null) {
                iconDrawable = iconDrawable.color(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
            }
            indicator.setImageDrawable(iconDrawable);
            indicator.setVisibility(View.VISIBLE);
        } else {
            indicator.setVisibility(View.GONE);
        }


        //set the title
        TextView title = (TextView) convertView.findViewById(R.id.menu_item_title);
        title.setText(getTitle(groupPosition));

        // set icon
        String icon = itemString("icon", groupPosition);
        ImageView imageView = (ImageView) convertView.findViewById(R.id.menu_item_icon);
        if (icon != null && !icon.isEmpty()) {
            icon = icon.replaceAll("-", "_");
            try {
                IconDrawable iconDrawable = new IconDrawable(mainActivity, Iconify.IconValue.valueOf(icon));
                iconDrawable = iconDrawable.sizeRes(R.dimen.sidebar_icon_size);
                if (AppConfig.getInstance(mainActivity).sidebarForegroundColor != null) {
                    iconDrawable = iconDrawable.color(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
                }
                imageView.setImageDrawable(iconDrawable);
                imageView.setVisibility(View.VISIBLE);
            } catch (IllegalArgumentException e) {
                // icon was not found in IconValue enum
                Log.e(TAG, e.getMessage(), e);
                imageView.setVisibility(View.INVISIBLE);
            }
        } else if (imageView != null) {
            imageView.setVisibility(View.INVISIBLE);
        }

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = mainActivity.getLayoutInflater();

            if (groupsHaveIcons || childrenHaveIcons)
                convertView = inflater.inflate(R.layout.menu_child_icon, null);
            else
                convertView = inflater.inflate(R.layout.menu_child_noicon, null);

            // style it
            if (AppConfig.getInstance(mainActivity).sidebarForegroundColor != null) {
                TextView title = (TextView) convertView.findViewById(R.id.menu_item_title);
                title.setTextColor(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
            }
        }

        // set title
        TextView title = (TextView) convertView.findViewById(R.id.menu_item_title);
        title.setText(getTitle(groupPosition, childPosition));


        // set icon
        String icon = itemString("icon", groupPosition, childPosition);
        ImageView imageView = (ImageView) convertView.findViewById(R.id.menu_item_icon);
        if (icon != null && !icon.isEmpty()) {
            icon = icon.replaceAll("-", "_");
            try {
                IconDrawable iconDrawable = new IconDrawable(mainActivity, Iconify.IconValue.valueOf(icon));
                iconDrawable = iconDrawable.sizeRes(R.dimen.sidebar_icon_size);
                if (AppConfig.getInstance(mainActivity).sidebarForegroundColor != null) {
                    iconDrawable = iconDrawable.color(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
                }
                imageView.setImageDrawable(iconDrawable);
                imageView.setVisibility(View.VISIBLE);
            } catch (IllegalArgumentException e) {
                // icon was not found in IconValue enum
                Log.e(TAG, e.getMessage(), e);
                imageView.setVisibility(View.INVISIBLE);
            }
        } else if (imageView != null) {
            imageView.setVisibility(View.INVISIBLE);
        }

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
        try {
            if (isGrouping(groupPosition)) {
                // return false for default handling behavior
                return false;
            } else {
                Pair<String,String> urlAndJavascript = getUrlAndJavascript(groupPosition);
                loadUrlAndJavascript(urlAndJavascript.first, urlAndJavascript.second);
                return true; // tell android that we have handled it
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return false;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
        Pair<String,String> urlAndJavascript = getUrlAndJavascript(groupPosition, childPosition);
        loadUrlAndJavascript(urlAndJavascript.first, urlAndJavascript.second);
        return true;
    }

    void loadUrlAndJavascript(String url, String javascript) {
        // check for GONATIVE_USERID
        if (UrlInspector.getInstance().getUserId() != null) {
            url = url.replaceAll("GONATIVE_USERID", UrlInspector.getInstance().getUserId());
        }

        if (javascript == null) mainActivity.loadUrl(url);
        else mainActivity.loadUrlAndJavascript(url, javascript);

        mainActivity.closeDrawers();
    }

    @Override
    public int getChildType(int groupPosition, int childPosition) {
        if (groupsHaveIcons || childrenHaveIcons) return 0;
        else return 1;
    }

    @Override
    public int getChildTypeCount() {
        return 2;
    }

    @Override
    public int getGroupType(int groupPosition) {
        if (groupsHaveIcons) return 0;
        else return 1;
    }

    @Override
    public int getGroupTypeCount() {
        return 2;
    }
}
