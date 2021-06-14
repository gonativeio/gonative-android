package com.gonative.testFiles;

import android.webkit.WebView;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.uiautomator.UiDevice;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.gonative.android.MainActivity;
import io.gonative.android.R;
import io.gonative.android.library.AppConfig;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 18)
@LargeTest
public class FirstTestClass{
    TestMethods testMethods;
    AppConfig appConfig;
    WebView webView;
    private UiDevice uiDevice;

    @Rule
    public ActivityScenarioRule<MainActivity> activityScenarioRule = new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void initMethod() throws InterruptedException {
        for(int i = 0; i < 10; i++){
            try{
                uiDevice = UiDevice.getInstance(getInstrumentation());
            }catch (RuntimeException runtimeException){
                Thread.sleep(2000);
                continue;
            }
            Thread.sleep(1000);
            break;
        }
        activityScenarioRule.getScenario().onActivity(activity -> {
            appConfig = AppConfig.getInstance(activity);
            webView = activity.findViewById(R.id.webview);
            testMethods = new TestMethods(activity, webView);
        });
    }

    //Sidebar Navigation Test
    @Test
    public void testSidebarNavigation() throws InterruptedException, JSONException {
        if(appConfig.showNavigationMenu && (appConfig.menus.get("default") != null)){
            if(appConfig.menus.get("default") == null) throw new RuntimeException("Navigation drawer list not found.");
            else {
                testMethods.waitForPageLoaded();
                testMethods.testNavigation(appConfig.menus.get("default"));
            }
        }
    }

    //Tab Menu Navigation Test
    @Test
    public void testTabMenuNavigation() throws JSONException, InterruptedException {
        if(appConfig.tabMenuRegexes.size() == 0) throw new RuntimeException("No Tab Menus found.");
        else{
            testMethods.waitForPageLoaded();
            testMethods.m_testTabNavigation(appConfig.tabMenus, appConfig.tabMenuRegexes);
        }
    }

    //Internal vs External Links Test
    @Test
    public void testIvE() throws InterruptedException {
        testMethods.waitForPageLoaded();
        testMethods.testInternalvExternalLinks(uiDevice);
    }

    //Pull to Refresh Test
    @Test
    public void pullToRefresh() throws InterruptedException {
        if(appConfig.pullToRefresh){
            testMethods.waitForPageLoaded();
            testMethods.testPullToRefresh();
        }
    }

    //Search Button Test
    @Test
    public void testSearch() throws InterruptedException {
        if(appConfig.searchTemplateUrl != null && !appConfig.searchTemplateUrl.isEmpty()){
            testMethods.waitForPageLoaded();
            testMethods.testSearchButton();
        }
    }

    //Refresh Button Test
    @Test
    public void testRefreshButton() throws InterruptedException {
        if(appConfig.showRefreshButton){
            testMethods.waitForPageLoaded();
            testMethods.testRefreshButton();
        }
    }
}
