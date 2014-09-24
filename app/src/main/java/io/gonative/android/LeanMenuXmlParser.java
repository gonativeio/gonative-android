package io.gonative.android;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;

public class LeanMenuXmlParser {
	public LeanMenuXmlResult parse(InputStream in) throws Exception{
		LeanMenuXmlResult result = new LeanMenuXmlResult();
		
		XmlPullParser parser = Xml.newPullParser();
		parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,  false);
		parser.setInput(in,  null);
		
		// start parsing
		while (parser.next() != XmlPullParser.END_DOCUMENT){
			if(parser.getEventType() != XmlPullParser.START_TAG){
				continue;
			}
			String name = parser.getName();
			
			if(name.equals("notloggedin")){
				result.setLoggedIn(false);
				parser.next();
			}
			else if(name.equals("user")){
				result.setLoggedIn(true);
			}
			else if(name.equals("name")){
				if (parser.next() == XmlPullParser.TEXT)
					result.setUserName(parser.getText());
				parser.next();
			}
			else if(name.equals("Uid")){
				if (parser.next() == XmlPullParser.TEXT)
					result.setUid(Integer.parseInt(parser.getText()));
				parser.next();
			}
			else if(name.equals("Avatar")){
				if (parser.next() == XmlPullParser.TEXT)
					result.setAvatarUrl(parser.getText());
				parser.next();
			}
			else if(name.equals("Bio")){
				if (parser.next() == XmlPullParser.TEXT)
					result.setUserBio(parser.getText());
				parser.next();
			}
			else if(name.equals("link")){
				DrawerMenuItem menuItem = new DrawerMenuItem();
				
				for(int i = 0; i < parser.getAttributeCount(); i++){
					if(parser.getAttributeName(i).equals("name"))
						menuItem.setTitle(parser.getAttributeValue(i));
					else if(parser.getAttributeName(i).equals("URL"))
						menuItem.setUrl(parser.getAttributeValue(i));
				}
				
				result.getMenuItems().add(menuItem);
			}
		}
		
		return result;
	}
	

	
	public static class LeanMenuXmlResult{
		Exception exception = null;
		boolean loggedIn = false;
		String userName = null;
		int uid = 0;
		String avatarUrl = null;
		String userBio = null;
		ArrayList<DrawerMenuItem> menuItems = new ArrayList<DrawerMenuItem>();
		
		public Exception getException() {
			return exception;
		}
		public void setException(Exception e) {
			this.exception = e;
		}
		public boolean isLoggedIn() {
			return loggedIn;
		}
		public void setLoggedIn(boolean loggedIn) {
			this.loggedIn = loggedIn;
		}
		public String getUserName() {
			return userName;
		}
		public void setUserName(String userName) {
			this.userName = userName;
		}
		public int getUid() {
			return uid;
		}
		public void setUid(int uid) {
			this.uid = uid;
		}
		public String getAvatarUrl() {
			return avatarUrl;
		}
		public void setAvatarUrl(String avatarUrl) {
			this.avatarUrl = avatarUrl;
		}
		public String getUserBio() {
			return userBio;
		}
		public void setUserBio(String userBio) {
			this.userBio = userBio;
		}
		public ArrayList<DrawerMenuItem> getMenuItems() {
			return menuItems;
		}
		public void setMenuItems(ArrayList<DrawerMenuItem> menuItems) {
			this.menuItems = menuItems;
		}
	}
	
}
