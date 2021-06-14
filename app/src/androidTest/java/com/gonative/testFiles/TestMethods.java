package com.gonative.testFiles;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.espresso.web.webdriver.Locator;
import androidx.test.uiautomator.UiDevice;
import io.gonative.android.HelperClass;
import org.json.JSONArray;
import org.json.JSONException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBackUnconditionally;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressImeActionButton;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withResourceName;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.web.assertion.WebViewAssertions.webMatches;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static androidx.test.espresso.web.webdriver.DriverAtoms.findElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.getText;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webClick;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.equalTo;
import io.gonative.android.MainActivity;
import io.gonative.android.R;

public class TestMethods {

    public TestMethods(MainActivity mainActivity, WebView webView){
        m_mainActivity = mainActivity;
        m_webView = webView;
    }

    protected static String currentURL = "URL Not assigned yet";
    protected static String prevURL = "URL Not assigned yet";
    private final WebView m_webView;
    private final MainActivity m_mainActivity;

    private void m_UpdateCurrentURL() throws InterruptedException {
        m_mainActivity.runOnUiThread(() -> currentURL = m_webView.getOriginalUrl());
        Thread.sleep(1000);
    }

    protected int getNewLoad(){
        return HelperClass.newLoad;
    }

    public boolean isURL(String url){
        try {
            new URL(url);
            return true;
        }catch (MalformedURLException e){
            return false;
        }
    }

    public void waitForPageLoaded() throws InterruptedException {
        int counter = 0;
        while (getNewLoad() == 0) {
            if (counter >= 15) throw new RuntimeException("Page failed to load in less than 15 seconds.");
            Thread.sleep(1000);
            counter++;
        }
        m_UpdateCurrentURL();
        Thread.sleep(1000);
        counter = 0;
        while(currentURL == null && counter <= 10){
            Thread.sleep(1000);
            counter++;
            m_UpdateCurrentURL();
        }
        if(currentURL != null) HelperClass.newLoad = 0;
        else throw new RuntimeException("Current URL cannot be retrieved from the WebView.");
    }

    public void testNavigation(JSONArray sidebarObjects) throws InterruptedException, JSONException {
        for(int i = 0; i < sidebarObjects.length(); i++){
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
            onData(anything()).inAdapterView(withId(R.id.drawer_list)).atPosition(i).perform(click());
            waitForPageLoaded();
            String sidebarURL = sidebarObjects.getJSONObject(i).getString("url");
            if(!currentURL.matches(sidebarURL)) throw new RuntimeException("Sidebar Menu " + (i+1) + " did not load the designated URL - " + sidebarURL);
        }
    }

    public void testInternalvExternalLinks(UiDevice uiDevice) throws InterruptedException {
        m_mainActivity.runOnUiThread(() -> m_webView.loadUrl("https://gonative-test-web.web.app/"));
        waitForPageLoaded();
        onWebView().withElement(findElement(Locator.ID, "facebook_link")).perform(webClick());
        waitForPageLoaded();
        String dFacebook = uiDevice.getCurrentPackageName();

        if(dFacebook.contains("io.gonative.android")){
            uiDevice.pressBack();
            Thread.sleep(2000);
        }else throw new RuntimeException("Facebook link opened externally");

        onWebView().withElement(findElement(Locator.ID, "twitter_link")).perform(webClick());
        Thread.sleep(8000);
        String dTwitter = uiDevice.getCurrentPackageName();
        if(dTwitter.contains("io.gonative.android")) throw new RuntimeException("Twitter opened internally.");
        else {
            uiDevice.pressBack();
            Thread.sleep(1000);
        }
    }

    public void testRefreshButton() throws InterruptedException {
        onView(withId(R.id.action_refresh)).perform(click());
        waitForPageLoaded();
    }

    public void testSearchButton() throws InterruptedException {
        String query = "Gonative";
        onView(withId(R.id.action_search)).perform(click());
        Thread.sleep(1000);
        onView(withResourceName("search_src_text")).perform(typeText(query), pressImeActionButton());
        waitForPageLoaded();
        try{
            onWebView().withElement(findElement(Locator.ID, "search_param")).check(webMatches(getText(), equalTo(query)));
        }catch (Exception exception){
            throw new RuntimeException("Search button failed to load the results with query - " + query);
        }
        pressBackUnconditionally();
        Thread.sleep(2000);
    }

    public void m_testTabNavigation(HashMap<String, JSONArray> tabMenus, ArrayList<Pattern> tabMenuRegexes) throws JSONException, InterruptedException {
        while(!(currentURL.matches(tabMenuRegexes.get(0).pattern()))){
            m_mainActivity.runOnUiThread(() -> m_webView.loadUrl("https://gonative.io/about/"));
            waitForPageLoaded();
        }
        if (tabMenus.size() == 0) throw new RuntimeException("No Tab Menus Added.");
        else {
            for (Pattern p : tabMenuRegexes) {
                if (currentURL.matches(p.pattern())) {
                    try {
                        for (String i : tabMenus.keySet()) {
                            for (int j = 0; j < tabMenus.get(i).length(); ) {
                                if (currentURL.matches(p.pattern())) {
                                    if (isURL(tabMenus.get(i).getJSONObject(j).get("url").toString())) {
                                        Thread.sleep(1000);
                                        onView(withText(tabMenus.get(i).getJSONObject(j).get("label").toString())).perform(click());
                                        waitForPageLoaded();
                                        prevURL = currentURL;
                                        pressBackUnconditionally();
                                        waitForPageLoaded();
                                        String tabURL = tabMenus.get(i).getJSONObject(j).get("url").toString();
                                        if (!(prevURL.matches(tabURL))) throw new RuntimeException("Tab " + (j+1) + " could not load the designated URL - " + prevURL);
                                        j++;
                                        prevURL = currentURL;
                                    } else {
                                        onView(withText(tabMenus.get(i).getJSONObject(j).get("label").toString())).perform(click());
                                        j++;
                                        Thread.sleep(2000);
                                    }
                                }
                            }
                        }
                    } catch (NoMatchingViewException | PerformException noMatchingViewException) {
                        throw new RuntimeException("Tab Menu not displayed in the desired regex: " + p.pattern());
                    }
                } else{
                    throw new RuntimeException("No Tab Menus found on the current page.");
                }
            }
        }
    }

    public void testPullToRefresh() throws InterruptedException {
        onView(withId(R.id.webview)).perform(swipeDown());
        waitForPageLoaded();
    }
}
