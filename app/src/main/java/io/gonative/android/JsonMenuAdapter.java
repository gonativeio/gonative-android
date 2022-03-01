package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome;

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
    private int selectedIndex;
    private ExpandableListView expandableListView;
    private Integer highlightColor;
    private int sidebar_icon_size;

    JsonMenuAdapter(MainActivity activity, ExpandableListView expandableListView) {
        this.mainActivity = activity;
        sidebar_icon_size = mainActivity.getResources().getInteger(R.integer.sidebar_icon_size);
        this.expandableListView = expandableListView;
        menuItems = null;
        this.highlightColor = AppConfig.getInstance(mainActivity).sidebarHighlightColor;
        if (this.highlightColor == null) {
            this.highlightColor = Color.parseColor("#442f79fe");
        }

        // broadcast messages
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && intent.getAction().equals(AppConfig.PROCESSED_MENU_MESSAGE)) {
                    update();
                }
            }
        };
        LocalBroadcastManager.getInstance(this.mainActivity)
                .registerReceiver(broadcastReceiver,
                        new IntentFilter(AppConfig.PROCESSED_MENU_MESSAGE));

    }

    private synchronized void update() {
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


    private String itemString(String s, int groupPosition) {
        String value = null;
        try {
            JSONObject section = (JSONObject) menuItems.get(groupPosition);
            if (!section.isNull(s))
                value = section.getString(s).trim();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return value;
    }

    private String itemString(String s, int groupPosition, int childPosition) {
        String value = null;
        try {
            JSONObject section = (JSONObject) menuItems.get(groupPosition);
            JSONObject sublink = section.getJSONArray("subLinks").getJSONObject(childPosition);
            if (!sublink.isNull(s))
                value = sublink.getString(s).trim();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return value;
    }

    private String getTitle(int groupPosition) {
        return itemString("label", groupPosition);
    }

    private String getTitle(int groupPosition, int childPosition) {
        return itemString("label", groupPosition, childPosition);
    }

    private Pair<String, String> getUrlAndJavascript(int groupPosition) {
        String url = itemString("url", groupPosition);
        String js = itemString("javascript", groupPosition);
        return new Pair<>(url, js);
    }

    private Pair<String, String> getUrlAndJavascript(int groupPosition, int childPosition) {
        String url = itemString("url", groupPosition, childPosition);
        String js = itemString("javascript", groupPosition, childPosition);
        return new Pair<>(url, js);
    }

    private boolean isGrouping(int groupPosition) {
        try {
            JSONObject section = (JSONObject) menuItems.get(groupPosition);
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
            JSONObject section = (JSONObject) menuItems.get(groupPosition);
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
                TextView title = convertView.findViewById(R.id.menu_item_title);
                title.setTextColor(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
            }
        }
        RelativeLayout menuItem = convertView.findViewById(R.id.menu_item);
        GradientDrawable shape = getHighlightDrawable();
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_activated}, shape);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, shape);

        menuItem.setBackground(stateListDrawable);

        // expand/collapse indicator
        ImageView indicator = convertView.findViewById(R.id.menu_group_indicator);
        if (isGrouping(groupPosition)) {
            IconicsDrawable iconDrawable;
            if (isExpanded)
                iconDrawable = mainActivity.getFontAwesomeIcon("faw_angle_up");
            else
                iconDrawable = mainActivity.getFontAwesomeIcon("faw_angle_down");

            iconDrawable.setSizeXPx(R.dimen.sidebar_expand_indicator_size);
            iconDrawable.setSizeYPx(R.dimen.sidebar_expand_indicator_size);
            if (groupPosition == this.selectedIndex) {
                iconDrawable.setColorList(ColorStateList.valueOf(this.highlightColor));
            } else if (AppConfig.getInstance(mainActivity).sidebarForegroundColor != null) {
                iconDrawable.setColorList(ColorStateList.valueOf(AppConfig.getInstance(mainActivity).sidebarForegroundColor));
            }

            indicator.setImageDrawable(iconDrawable);
            indicator.setVisibility(View.VISIBLE);
        } else {
            indicator.setVisibility(View.GONE);
        }

        //set the title
        TextView title = convertView.findViewById(R.id.menu_item_title);
        title.setText(getTitle(groupPosition));
        if (this.selectedIndex == groupPosition) {
            title.setTextColor(this.highlightColor);
        } else {
            title.setTextColor(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
        }

        // set icon
        String icon = itemString("icon", groupPosition);
        ImageView imageView = convertView.findViewById(R.id.menu_item_icon);
        if (icon != null && !icon.isEmpty()) {
            IconicsDrawable iconDrawable = mainActivity.getFontAwesomeIcon(icon);
            iconDrawable.setSizeXPx(sidebar_icon_size);
            iconDrawable.setSizeYPx(sidebar_icon_size);
            if (groupPosition == this.selectedIndex) {
                iconDrawable.setColorList(ColorStateList.valueOf(this.highlightColor));
            } else {
                iconDrawable.setColorList(ColorStateList.valueOf(AppConfig.getInstance(mainActivity).sidebarForegroundColor));
            }
            imageView.setImageDrawable(iconDrawable);
            imageView.setVisibility(View.VISIBLE);
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
                convertView = inflater.inflate(R.layout.menu_child_icon, parent, false);
            else
                convertView = inflater.inflate(R.layout.menu_child_noicon, parent, false);

            // style it
            if (AppConfig.getInstance(mainActivity).sidebarForegroundColor != null) {
                TextView title = convertView.findViewById(R.id.menu_item_title);
                title.setTextColor(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
            }
        }

        RelativeLayout menuItem = convertView.findViewById(R.id.menu_item);
        GradientDrawable shape = getHighlightDrawable();
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_activated}, shape);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, shape);

        menuItem.setBackground(stateListDrawable);

        // set title
        TextView title = convertView.findViewById(R.id.menu_item_title);
        title.setText(getTitle(groupPosition, childPosition));
        if (this.selectedIndex == (groupPosition + childPosition) + 1) {
            title.setTextColor(this.highlightColor);
        } else {
            title.setTextColor(AppConfig.getInstance(mainActivity).sidebarForegroundColor);
        }

        // set icon
        String icon = itemString("icon", groupPosition, childPosition);
        ImageView imageView = convertView.findViewById(R.id.menu_item_icon);
        if (icon != null && !icon.isEmpty()) {
            IconicsDrawable iconDrawable = mainActivity.getFontAwesomeIcon(icon);
            iconDrawable.setSizeXPx(sidebar_icon_size);
            iconDrawable.setSizeYPx(sidebar_icon_size);
            if (this.selectedIndex == (groupPosition + childPosition) + 1) {
                iconDrawable.setColorList(ColorStateList.valueOf(this.highlightColor));
            } else {
                iconDrawable.setColorList(ColorStateList.valueOf(AppConfig.getInstance(mainActivity).sidebarForegroundColor));
            }
            imageView.setImageDrawable(iconDrawable);
            imageView.setVisibility(View.VISIBLE);
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
        int index = parent.getFlatListPosition(ExpandableListView.getPackedPositionForChild(groupPosition, childPosition));
        parent.setItemChecked(index, true);
        this.selectedIndex = index;
        Pair<String, String> urlAndJavascript = getUrlAndJavascript(groupPosition, childPosition);
        loadUrlAndJavascript(urlAndJavascript.first, urlAndJavascript.second);
        return true;
    }

    private void loadUrlAndJavascript(String url, String javascript) {
        // check for GONATIVE_USERID
        if (UrlInspector.getInstance().getUserId() != null) {
            url = url.replaceAll("GONATIVE_USERID", UrlInspector.getInstance().getUserId());
        }

        if (javascript == null) mainActivity.loadUrl(url);
        else mainActivity.loadUrlAndJavascript(url, javascript);

        mainActivity.closeDrawers();
    }

    public void autoSelectItem(String url) {
        String formattedUrl = url.replaceAll("/$", "");
        if (menuItems == null) return;

        for (int i = 0; i < menuItems.length(); i++) {
            if (formattedUrl.equals(menuItems.optJSONObject(i).optString("url").replaceAll("/$", ""))) {
                expandableListView.setItemChecked(i, true);
                selectedIndex = i;
                return;
            }
        }
    }

    private GradientDrawable getHighlightDrawable() {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(10);
        if (this.highlightColor == null) {
            shape.setColor(Color.parseColor("#442f79fe"));
        } else {
            shape.setColor(this.highlightColor);
        }
        shape.setAlpha(30);

        return shape;
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
