package io.gonative.android;

import java.util.ArrayList;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class MenuListAdapter extends ArrayAdapter<DrawerMenuItem> {
	ArrayList<DrawerMenuItem> items = null;
    Activity activity = null;
	
	public MenuListAdapter(Activity activity, ArrayList<DrawerMenuItem> items) {
		super(activity, 0, items);
        this.activity = activity;
		this.items = items;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Activity activity = (Activity) getContext();
		LayoutInflater inflater = activity.getLayoutInflater();
		View rowView = inflater.inflate(R.layout.menu_child_noicon, null);
		
		//extract the individual views from the row here by id to change their attributes
		TextView title = (TextView) rowView.findViewById(R.id.menu_item_title);
		//set the title, details, and image for each item in the list
		title.setText(items.get(position).getTitle());

        // style it
        if (AppConfig.getInstance(activity).sidebarForegroundColor != null)
            title.setTextColor(AppConfig.getInstance(activity).sidebarForegroundColor);

		return rowView;
	}

}
