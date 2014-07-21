package io.gonative.android;

import android.util.Log;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

    private JsonMenuAdapter() {
    }

    public JsonMenuAdapter(MainActivity activity) {
        this.mainActivity = activity;
        menuItems = null;
    }


    public synchronized void update(String status) {
        if (status == null) status = "default";

        menuItems = AppConfig.getInstance(mainActivity).getMenus().get(status);
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
        return itemString("name", groupPosition);
    }

    String getTitle(int groupPosition, int childPosition) {
        return itemString("name", groupPosition, childPosition);
    }

    String getUrl(int groupPosition) {
        return itemString("url", groupPosition);
    }

    String getUrl(int groupPosition, int childPosition) {
        return itemString("url", groupPosition, childPosition);
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
            if (AppConfig.getInstance(mainActivity).getSidebarForegroundColor() != null) {
                TextView title = (TextView) convertView.findViewById(R.id.menu_item_title);
                title.setTextColor(AppConfig.getInstance(mainActivity).getSidebarForegroundColor());
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
            if (AppConfig.getInstance(mainActivity).getSidebarForegroundColor() != null) {
                iconDrawable = iconDrawable.color(AppConfig.getInstance(mainActivity).getSidebarForegroundColor());
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
                if (AppConfig.getInstance(mainActivity).getSidebarForegroundColor() != null) {
                    iconDrawable = iconDrawable.color(AppConfig.getInstance(mainActivity).getSidebarForegroundColor());
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
            if (AppConfig.getInstance(mainActivity).getSidebarForegroundColor() != null) {
                TextView title = (TextView) convertView.findViewById(R.id.menu_item_title);
                title.setTextColor(AppConfig.getInstance(mainActivity).getSidebarForegroundColor());
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
                if (AppConfig.getInstance(mainActivity).getSidebarForegroundColor() != null) {
                    iconDrawable = iconDrawable.color(AppConfig.getInstance(mainActivity).getSidebarForegroundColor());
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
                loadUrl(getUrl(groupPosition));
                return true; // tell android that we have handled it
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return false;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
        loadUrl(getUrl(groupPosition, childPosition));
        return true;
    }

    void loadUrl(String url) {
        // check for GONATIVE_USERID
        if (UrlInspector.getInstance().getUserId() != null) {
            url = url.replaceAll("GONATIVE_USERID", UrlInspector.getInstance().getUserId());
        }

        mainActivity.loadUrl(url);
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
