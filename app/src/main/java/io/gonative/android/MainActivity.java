package io.gonative.android;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.astuetz.PagerSlidingTabStrip;
import com.facebook.appevents.AppEventsLogger;
import com.parse.ParseAnalytics;
import com.parse.ParsePushBroadcastReceiver;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.gonative.android.library.AppConfig;

public class MainActivity extends ActionBarActivity implements Observer, SwipeRefreshLayout.OnRefreshListener {
    public static final String webviewCacheSubdir = "webviewAppCache";
    public static final String webviewDatabaseSubdir = "webviewDatabase";
	private static final String TAG = MainActivity.class.getName();
    public static final String INTENT_TARGET_URL = "targetUrl";
    public static final String EXTRA_WEBVIEW_WINDOW_OPEN = "io.gonative.android.MainActivity.Extra.WEBVIEW_WINDOW_OPEN";
	public static final int REQUEST_SELECT_FILE = 100;
    public static final int REQUEST_PERMISSION_READ_EXTERNAL_STORAGE = 101;
    public static final int REQUEST_PERMISSION_GEOLOCATION = 102;
    private static final int REQUEST_WEBFORM = 300;
    public static final int REQUEST_WEB_ACTIVITY = 400;
    public static final int REQUEST_PUSH_NOTIFICATION = 500;
    public static final int REQUEST_PLAY_SERVICES_RESOLUTION = 9000;
    private static final float ACTIONBAR_ELEVATION = 12.0f;

    private GoNativeWebviewInterface mWebview;
    boolean isPoolWebview = false;
    private Stack<String> backHistory = new Stack<String>();

	private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> uploadMessageLP;
    private Uri directUploadImageUri;
	private DrawerLayout mDrawerLayout;
	private View mDrawerView;
	private ExpandableListView mDrawerList;
    private ProgressBar mProgress;
    private Dialog splashDialog;
    private boolean splashDismissRequiresForce;
    private MySwipeRefreshLayout swipeRefresh;
    private RelativeLayout fullScreenLayout;
    private JsonMenuAdapter menuAdapter = null;
	private ActionBarDrawerToggle mDrawerToggle;
    private PagerSlidingTabStrip slidingTabStrip;
    private ImageView navigationTitleImage;
	private ConnectivityManager cm = null;
    private ProfilePicker profilePicker = null;
    private SegmentedController segmentedController = null;
    private IdentityService identityService;
    private TabManager tabManager;
    private ActionManager actionManager;
    private boolean isRoot;
    private float hideWebviewAlpha = 0.0f;
    private boolean isFirstHideWebview = true;
    private boolean webviewIsHidden = false;
    private int urlLevel = -1;
    private int parentUrlLevel = -1;
    private Handler handler = new Handler();
    private Runnable statusChecker = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    checkReadyStatus();
                }
            });
            handler.postDelayed(statusChecker, 100); // 0.1 sec
        }
    };
    private FileDownloader fileDownloader = new FileDownloader(this);
    private boolean startedLoading = false; // document readystate checker
    private PushManager pushManager;
    private RegistrationManager registrationManager;
    private ConnectivityChangeReceiver connectivityReceiver;
    protected String postLoadJavascript;
    protected String postLoadJavascriptForRefresh;
    private Stack<Bundle>previousWebviewStates;
    private Runnable geolocationPermissionCallback;


    @Override
	protected void onCreate(Bundle savedInstanceState) {
        AppConfig appConfig = AppConfig.getInstance(this);

        if (appConfig.forceScreenOrientation == AppConfig.ScreenOrientations.PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (appConfig.forceScreenOrientation == AppConfig.ScreenOrientations.LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        if (appConfig.keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        this.hideWebviewAlpha  = appConfig.hideWebviewAlpha;

        super.onCreate(savedInstanceState);

        isRoot = getIntent().getBooleanExtra("isRoot", true);
        parentUrlLevel = getIntent().getIntExtra("parentUrlLevel", -1);

        if (isRoot) {
            // Splash screen stuff
            boolean isFromLauncher = getIntent().hasCategory(Intent.CATEGORY_LAUNCHER);
            // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY does not seem to be set when it should
            // for some devices. I have yet to find a good workaround.
            boolean isFromRecents = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;

            if (isFromLauncher && !isFromRecents) {
                showSplashScreen(appConfig.showSplashMaxTime, appConfig.showSplashForceTime);
            }

            // html5 app cache (manifest)
            File cachePath = new File(getCacheDir(), webviewCacheSubdir);
            cachePath.mkdirs();
            File databasePath = new File(getCacheDir(), webviewDatabaseSubdir);
            databasePath.mkdirs();

            // url inspector
            UrlInspector.getInstance().init(this);

            // OTA configs
            ConfigUpdater configUpdater = new ConfigUpdater(this);
            configUpdater.updateConfig();

            // Register launch
            configUpdater.registerEvent();

            if (appConfig.parseAnalyticsEnabled) {
                ParseAnalytics.trackAppOpenedInBackground(getIntent());
            }

            // registration service
            this.registrationManager = ((GoNativeApplication)getApplication()).getRegistrationManager();

            // Push notifications
            if (appConfig.pushNotifications) {
                this.pushManager = new PushManager(this);
                this.pushManager.register();
            }
        }

        // webview pools
        WebViewPool.getInstance().init(this);

		cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        if (isRoot && AppConfig.getInstance(this).showNavigationMenu)
	    	setContentView(R.layout.activity_gonative);
        else
            setContentView(R.layout.activity_gonative_nonav);


        mProgress = (ProgressBar) findViewById(R.id.progress);
        this.fullScreenLayout = (RelativeLayout)findViewById(R.id.fullscreen);

        swipeRefresh = (MySwipeRefreshLayout) findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this);
        // only enable swipeRefresh if LeanWebView is a subclass of WebView, since XWalkView integrates
        // its own refresh controller.
        if (appConfig.pullToRefresh) {
            swipeRefresh.setEnabled(true);
            swipeRefresh.setCanChildScrollUpCallback(new MySwipeRefreshLayout.CanChildScrollUpCallback() {
                @Override
                public boolean canSwipeRefreshChildScrollUp() {
                    return mWebview.getScrollY() > 0;
                }
            });
        } else {
            swipeRefresh.setEnabled(false);
        }
        if (appConfig.pullToRefreshColor != null) {
            swipeRefresh.setColorSchemeColors(appConfig.pullToRefreshColor);
        }

        this.mWebview = (GoNativeWebviewInterface) findViewById(R.id.webview);
        setupWebview(this.mWebview);

        // profile picker
        if (isRoot && AppConfig.getInstance(this).showNavigationMenu) {
            Spinner profileSpinner = (Spinner) findViewById(R.id.profile_picker);
            profilePicker = new ProfilePicker(this, profileSpinner);

            Spinner segmentedSpinner = (Spinner) findViewById(R.id.segmented_control);
            segmentedController = new SegmentedController(this, segmentedSpinner);
        }

		// to save webview cookies to permanent storage
		CookieSyncManager.createInstance(getApplicationContext());
		
		// proxy cookie manager for httpUrlConnection (syncs to webview cookies)
        CookieHandler.setDefault(new WebkitCookieManagerProxy());


        this.postLoadJavascript = getIntent().getStringExtra("postLoadJavascript");
        this.postLoadJavascriptForRefresh = this.postLoadJavascript;

        this.previousWebviewStates = new Stack<>();

        // tab navigation
        ViewPager pager = (ViewPager) findViewById(R.id.view_pager);
        this.slidingTabStrip = (PagerSlidingTabStrip) findViewById(R.id.tabs);
        this.tabManager = new TabManager(this, pager);
        pager.setAdapter(this.tabManager);
        this.slidingTabStrip.setViewPager(pager);
        this.slidingTabStrip.setTabClickListener(this.tabManager);

        // custom colors
        if (appConfig.tabBarBackgroundColor != null)
            this.slidingTabStrip.setBackgroundColor(appConfig.tabBarBackgroundColor);
        if (appConfig.tabBarTextColor != null)
            this.slidingTabStrip.setTextColor(appConfig.tabBarTextColor);
        if (appConfig.tabBarIndicatorColor != null)
            this.slidingTabStrip.setIndicatorColor(appConfig.tabBarIndicatorColor);
        hideTabs();

        // actions in action bar
        this.actionManager = new ActionManager(this);

        Intent intent = getIntent();
        // load url
        String url = null;
        // first check intent in case it was created from push notification
        String targetUrl = intent.getStringExtra(INTENT_TARGET_URL);
        if (targetUrl != null && !targetUrl.isEmpty()){
            url = targetUrl;
        }
        // if it came from parse, then a bit more work is necessary
        if (url == null && intent.hasExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA)) {
            try {
                String pushJson = intent.getStringExtra(ParsePushBroadcastReceiver.KEY_PUSH_DATA);
                JSONObject object = (JSONObject) new JSONTokener(pushJson).nextValue();
                if (object.has("targetUrl") && !object.isNull("targetUrl")) {
                    url = object.optString("targetUrl");
                }
                if (url == null && object.has("u") && !object.isNull("u")) {
                    url = object.optString("u");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing json from Parse push notification", e);
            }
        }

        if (intent.getAction() == Intent.ACTION_VIEW) {
            url = intent.getDataString();
        }

        if (url == null && savedInstanceState != null) url = savedInstanceState.getString("url");
        if (url == null && isRoot) url = appConfig.initialUrl;
        // url from intent (hub and spoke nav)
        if (url == null) url = intent.getStringExtra("url");

        if (url != null) {
            // Crosswalk does not give us callbacks when location is requested.
            // Ask for it up front, then load the page.
            if (this.mWebview.isCrosswalk() && appConfig.usesGeolocation) {
                final String urlLoadAfterLocation = url;
                this.getRuntimeGeolocationPermission(new Runnable() {
                    @Override
                    public void run() {
                        mWebview.loadUrl(urlLoadAfterLocation);
                    }
                });
            } else {
                this.mWebview.loadUrl(url);
            }
        } else if (intent.getBooleanExtra(EXTRA_WEBVIEW_WINDOW_OPEN, false)){
            // no worries
        } else {
            Log.e(TAG, "No url specified for MainActivity");
        }

        if (isRoot && appConfig.showNavigationMenu) {
            // do the list stuff
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            mDrawerView = findViewById(R.id.left_drawer);
            mDrawerList = (ExpandableListView) findViewById(R.id.drawer_list);

            // set shadow
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                    R.string.drawer_open, R.string.drawer_close){
                //Called when a drawer has settled in a completely closed state.
                public void onDrawerClosed(View view) {
                    invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }

                //Called when a drawer has settled in a completely open state.
                public void onDrawerOpened(View drawerView) {
                    invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }
            };
            mDrawerLayout.setDrawerListener(mDrawerToggle);

            setupMenu();

            // update the menu
            if (appConfig.loginDetectionUrl != null) {
                LoginManager.getInstance().init(this);
                LoginManager.getInstance().addObserver(this);
            }
        }

		if (getSupportActionBar() != null) {
            if (!isRoot || appConfig.showNavigationMenu) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }

            showLogoInActionBar(appConfig.shouldShowNavigationTitleImageForUrl(url));
        }

        // style sidebar
        if (mDrawerView != null && AppConfig.getInstance(this).sidebarBackgroundColor != null) {
            mDrawerView.setBackgroundColor(AppConfig.getInstance(this).sidebarBackgroundColor);
        }

        // identity service
        this.identityService = new IdentityService(this);

        // respond to navigation titles processed
        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(AppConfig.PROCESSED_NAVIGATION_TITLES)) {
                    String url = mWebview.getUrl();
                    if (url == null) return;

                    String title = titleForUrl(mWebview.getUrl());
                    if (title == null) return;

                    setTitle(title);
                }
            }
        }, new IntentFilter(AppConfig.PROCESSED_NAVIGATION_TITLES));
    }

    protected void onPause() {
        super.onPause();
        stopCheckingReadyStatus();
        this.mWebview.onPause();

        // unregister connectivity
        if (this.connectivityReceiver != null) {
            unregisterReceiver(this.connectivityReceiver);
        }

        if (AppConfig.getInstance(this).facebookEnabled) {
            AppEventsLogger.deactivateApp(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.mWebview.onResume();

        retryFailedPage();
        // register to listen for connectivity changes
        this.connectivityReceiver = new ConnectivityChangeReceiver();
        registerReceiver(this.connectivityReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // check login status
        LoginManager.getInstance().checkLogin();

        if (AppConfig.getInstance(this).facebookEnabled) {
            AppEventsLogger.activateApp(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isRoot) {
            if (AppConfig.getInstance(this).clearCache) {
                this.mWebview.clearCache(true);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // destroy webview
        if (this.mWebview != null) {
            this.mWebview.stopLoading();
            // must remove from view hierarchy to destroy
            ViewGroup parent = (ViewGroup) this.mWebview.getParent();
            if (parent != null) {
                parent.removeView((View)this.mWebview);
            }
            if (!this.isPoolWebview) this.mWebview.destroy();
        }
    }

    private void retryFailedPage() {
        // skip if webview is currently loading
        if (this.mWebview.getProgress() < 100) return;

        // skip if webview has a page loaded
        String currentUrl = this.mWebview.getUrl();
        if (currentUrl != null && !currentUrl.equals("file:///android_asset/offline.html")) return;

        // skip if there is nothing in history
        if (this.backHistory.isEmpty()) return;

        // skip if no network connectivity
        if (!this.isConnected()) return;

        // finally, retry loading the page
        this.loadUrl(this.backHistory.pop());
    }

    protected void onSaveInstanceState (Bundle outState) {
        outState.putString("url", mWebview.getUrl());
        outState.putInt("urlLevel", urlLevel);
    }

    public void addToHistory(String url) {
        if (url == null) return;

        if (this.backHistory.isEmpty() || !this.backHistory.peek().equals(url)) {
            this.backHistory.push(url);
        }

        checkNavigationForPage(url);

        // this is a little hack to show the webview after going back in history in single-page apps.
        // We may never get onPageStarted or onPageFinished, hence the webview would be forever
        // hidden when navigating back in single-page apps. We do, however, get an updatedHistory callback.
        showWebview(0.3);
    }

    public boolean canGoBack() {
        return this.mWebview.canGoBack();
    }

    public void goBack() {
        if (this.mWebview.isCrosswalk()) {
            // not safe to do for non-crosswalk, as we may never get a page finished callback
            // for single-page apps
            hideWebview();
        }

        this.mWebview.goBack();
    }

    public void sharePage() {
        String url = this.mWebview.getUrl();
        if (url == null) return;

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, mWebview.getUrl());
        startActivity(Intent.createChooser(share, getString(R.string.action_share)));
    }

    public void logout() {
        this.mWebview.stopLoading();

        // log out by clearing all cookies and going to home page
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
        CookieSyncManager.getInstance().sync();

        updateMenu(false);
        LoginManager.getInstance().checkLogin();
        this.mWebview.loadUrl(AppConfig.getInstance(this).initialUrl);
    }

    public void loadUrl(String url) {
        loadUrl(url, false);
    }

    public void loadUrlUsingJavascript(String url) {
        String js = "window.location.href=" + LeanUtils.jsWrapString(url) + ";";
        runJavascript(js);
    }

    public void loadUrl(String url, boolean isFromTab) {
        if (url == null) return;

        this.postLoadJavascript = null;
        this.postLoadJavascriptForRefresh = null;

        if (url.equalsIgnoreCase("gonative_logout"))
            logout();
        else
            this.mWebview.loadUrl(url);

        if (!isFromTab && this.tabManager != null) this.tabManager.selectTab(url, null);
    }

    public void loadUrlAndJavascript(String url, String javascript) {
        loadUrlAndJavascript(url, javascript, false);
    }

    public void loadUrlAndJavascript(String url, String javascript, boolean isFromTab) {
        String currentUrl = this.mWebview.getUrl();

        if (url != null && currentUrl != null && url.equals(currentUrl)) {
//            hideWebview();
            runJavascript(javascript);
            this.postLoadJavascriptForRefresh = javascript;
//            showWebview();
        } else {
            this.postLoadJavascript = javascript;
            this.postLoadJavascriptForRefresh = javascript;
            this.mWebview.loadUrl(url);
        }

        if (!isFromTab && this.tabManager != null) this.tabManager.selectTab(url, javascript);
    }

    public void runJavascript(String javascript) {
        if (javascript == null) return;
        this.mWebview.runJavascript(javascript);
    }
	
	public boolean isConnected(){
		NetworkInfo ni = cm.getActiveNetworkInfo();
		return ni != null && ni.isConnected();
	}
	
	// configures webview settings
	private void setupWebview(GoNativeWebviewInterface wv){
        WebViewSetup.setupWebviewForActivity(wv, this);
	}

    private void showSplashScreen(double maxTime, double forceTime) {
        splashDialog = new Dialog(this, R.style.SplashScreen);
        splashDialog.getWindow().getAttributes().windowAnimations = R.style.SplashScreenAnimation;
        splashDialog.setContentView(R.layout.splash_screen);
        splashDialog.setCancelable(false);
        splashDialog.show();

        double delay;

        if (forceTime > 0) {
            delay = forceTime;
            splashDismissRequiresForce = true;
        } else {
            delay = maxTime;
            splashDismissRequiresForce = false;
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                hideSplashScreen(true);
            }
        }, (long) (delay * 1000));
    }

    private void hideSplashScreen(boolean isForce) {
        if (splashDialog != null && (!splashDismissRequiresForce || isForce)) {
            splashDialog.dismiss();
            splashDialog = null;
        }
    }

    public void hideWebview() {
        if (AppConfig.getInstance(this).disableAnimations) return;

        this.webviewIsHidden = true;
        mProgress.setAlpha(1.0f);
        mProgress.setVisibility(View.VISIBLE);

        if (this.isFirstHideWebview) {
            this.mWebview.setAlpha(0.0f);
        } else {
            this.mWebview.setAlpha(this.hideWebviewAlpha);
        }
    }

    public void showWebview(double delay) {
        hideSplashScreen(false);

        if (delay > 0) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showWebview();
                }
            }, (int) (delay * 1000));
        } else {
            showWebview();
        }
    }

    // shows webview with no animation
    public void showWebviewImmediately() {
        hideSplashScreen(false);

        this.isFirstHideWebview = false;
        webviewIsHidden = false;
        startedLoading = false;
        stopCheckingReadyStatus();
        this.mWebview.setAlpha(1.0f);
        this.mProgress.setVisibility(View.INVISIBLE);
    }

    public void showWebview() {
        hideSplashScreen(false);

        this.isFirstHideWebview = false;
        startedLoading = false;
        stopCheckingReadyStatus();

        final GoNativeWebviewInterface wv = this.mWebview;
        if (!webviewIsHidden) {
            // don't animate if already visible
            mProgress.setVisibility(View.INVISIBLE);
            return;
        }

        webviewIsHidden = false;

        wv.animate().alpha(1.0f)
                .setDuration(300)
                .setStartDelay(150);

        mProgress.animate().alpha(0.0f)
                .setDuration(60);
    }

    public void showLogoInActionBar(boolean show) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) return;

        actionBar.setDisplayOptions(show ? 0 : ActionBar.DISPLAY_SHOW_TITLE, ActionBar.DISPLAY_SHOW_TITLE);

        if (show) {
            // disable text title
            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);

            // why use a custom view and not setDisplayUseLogoEnabled and setLogo?
            // Because logo doesn't work!
            actionBar.setDisplayShowCustomEnabled(true);
            if (this.navigationTitleImage == null) {
                this.navigationTitleImage = new ImageView(this);
                this.navigationTitleImage.setImageResource(R.drawable.ic_actionbar);
            }
            actionBar.setCustomView(this.navigationTitleImage);
        } else {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE, ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setDisplayShowCustomEnabled(false);
        }
    }

	public void updatePageTitle() {
        if (AppConfig.getInstance(this).useWebpageTitle) {
            setTitle(this.mWebview.getTitle());
        }
    }

    public void update (Observable sender, Object data) {
        if (sender instanceof LoginManager) {
            updateMenu(((LoginManager) sender).isLoggedIn());
        }
    }

	public void updateMenu(){
        LoginManager.getInstance().checkLogin();
	}

    public void updateMenu(boolean isLoggedIn){
        if (menuAdapter == null)
            setupMenu();

        try {
            if (isLoggedIn)
                menuAdapter.update("loggedIn");
            else
                menuAdapter.update("default");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

	public boolean isDrawerOpen(){
        if (mDrawerLayout != null)
    		return mDrawerLayout.isDrawerOpen(mDrawerView);
        else
            return false;
	}

    public void setDrawerEnabled(boolean enabled) {
        if (!isRoot) return;

        AppConfig appConfig = AppConfig.getInstance(this);
        if (!appConfig.showNavigationMenu) return;

        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(enabled ? DrawerLayout.LOCK_MODE_UNLOCKED :
                    DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(enabled);
        }
    }
	
	protected void setupMenu(){
        menuAdapter = new JsonMenuAdapter(this);
        try {
            menuAdapter.update("default");
            mDrawerList.setAdapter(menuAdapter);
        } catch (Exception e) {
        }

        mDrawerList.setOnGroupClickListener(menuAdapter);
        mDrawerList.setOnChildClickListener(menuAdapter);
	}
	
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null)
		    mDrawerToggle.syncState();
	}
	
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
     // Pass any configuration change to the drawer toggles
        if (mDrawerToggle != null)
            mDrawerToggle.onConfigurationChanged(newConfig);
    }
	
	@Override
    @TargetApi(21)
    // Lollipop target API for REQEUST_SELECT_FILE_LOLLIPOP
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null && data.getBooleanExtra("exit", false))
            finish();

        if (requestCode == REQUEST_WEBFORM && resultCode == RESULT_OK) {
            String url = data.getStringExtra("url");
            if (url != null)
                loadUrl(url);
            else {
                // go to initialURL without login/signup override
                this.mWebview.setCheckLoginSignup(false);
                this.mWebview.loadUrl(AppConfig.getInstance(this).initialUrl);
            }

            if (AppConfig.getInstance(this).showNavigationMenu) {
                updateMenu(data.getBooleanExtra("success", false));
            }
        }

        if (requestCode == REQUEST_WEB_ACTIVITY && resultCode == RESULT_OK) {
            String url = data.getStringExtra("url");
            if (url != null) {
                int urlLevel = data.getIntExtra("urlLevel", -1);
                if (urlLevel == -1 || parentUrlLevel == -1 || urlLevel > parentUrlLevel) {
                    // open in this activity
                    this.postLoadJavascript = data.getStringExtra("postLoadJavascript");
                    loadUrl(url);
                } else {
                    // urlLevel <= parentUrlLevel, so pass up the chain
                    setResult(RESULT_OK, data);
                    finish();
                }
            }
        }

        if (requestCode == REQUEST_SELECT_FILE) {
            if (resultCode != RESULT_OK) {
                cancelFileUpload();
                return;
            }

            // from documents (and video camera)
            if (data != null && data.getData() != null) {
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(data.getData());
                    mUploadMessage = null;
                }

                if (uploadMessageLP != null) {
                    uploadMessageLP.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                    uploadMessageLP = null;
                }

                return;
            }

            // we may get clip data for multi-select documents
            if (data != null && data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                ArrayList<Uri> files = new ArrayList<>(clipData.getItemCount());
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    if (item.getUri() != null) {
                        files.add(item.getUri());
                    }
                }

                if (mUploadMessage != null) {
                    // shouldn never happen, but just in case, send the first item
                    if (files.size() > 0) {
                        mUploadMessage.onReceiveValue(files.get(0));
                    } else {
                        mUploadMessage.onReceiveValue(null);
                    }
                    mUploadMessage = null;
                }

                if (uploadMessageLP != null) {
                    uploadMessageLP.onReceiveValue(files.toArray(new Uri[files.size()]));
                    uploadMessageLP = null;
                }

                return;
            }

            // from camera
            if (this.directUploadImageUri != null) {
                // check if we have external storage permissions
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        Toast.makeText(this, R.string.external_storage_explanation, Toast.LENGTH_LONG).show();
                    }

                    ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                            REQUEST_PERMISSION_READ_EXTERNAL_STORAGE);
                    // wait for the onRequestPermissionsResult callback
                    return;
                }


                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(this.directUploadImageUri);
                    mUploadMessage = null;
                }
                if (uploadMessageLP != null) {
                    uploadMessageLP.onReceiveValue(new Uri[]{this.directUploadImageUri});
                    uploadMessageLP = null;
                }
                this.directUploadImageUri = null;

                return;
            }

            // Should not reach here.
            cancelFileUpload();
        }
    }

    public void cancelFileUpload() {
        if (mUploadMessage != null) {
            mUploadMessage.onReceiveValue(null);
            mUploadMessage = null;
        }

        if (uploadMessageLP != null) {
            uploadMessageLP.onReceiveValue(null);
            uploadMessageLP = null;
        }

        this.directUploadImageUri = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String targetUrl = intent.getStringExtra(INTENT_TARGET_URL);
        if (targetUrl != null && !targetUrl.isEmpty()){
            loadUrl(targetUrl);
        }
    }

    @Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (this.mWebview.exitFullScreen()) {
                return true;
            }

			if (isDrawerOpen()){
				mDrawerLayout.closeDrawers();
				return true;
			}
            else if (canGoBack()) {
                goBack();
                return true;
            }
            else if (!this.previousWebviewStates.isEmpty()) {
                Bundle state = previousWebviewStates.pop();
                LeanWebView webview = new LeanWebView(this);
                webview.restoreStateFromBundle(state);
                switchToWebview(webview, /* isPool */ false, /* isBack */ true);
                return true;
            }
		}

		return super.onKeyDown(keyCode, event);
	}

    // isPoolWebView is used to keep track of whether we are showing a pooled webview, which has implications
    // for page navigation, namely notifying the pool to disown the webview.
    // isBack means the webview is being switched in as part of back navigation behavior. If isBack=false,
    // then we will save the state of the old one switched out.
    public void switchToWebview(GoNativeWebviewInterface newWebview, boolean isPoolWebview, boolean isBack) {
        setupWebview(newWebview);

        // scroll to top
        ((View)newWebview).scrollTo(0, 0);

        View prev = (View)this.mWebview;

        if (!isBack) {
            // save the state for back button behavior
            Bundle stateBundle = new Bundle();
            this.mWebview.saveStateToBundle(stateBundle);
            this.previousWebviewStates.add(stateBundle);
        }

        // replace the current web view in the parent with the new view
        if (newWebview != prev) {
            // a view can only have one parent, and attempting to add newWebview if it already has
            // a parent will cause a runtime exception. So be extra safe by removing it from its parent.
            ViewParent temp = newWebview.getParent();
            if (temp instanceof  ViewGroup) {
                ((ViewGroup) temp).removeView((View)newWebview);
            }

            ViewGroup parent = (ViewGroup) prev.getParent();
            int index = parent.indexOfChild(prev);
            parent.removeView(prev);
            parent.addView((View) newWebview, index);
            ((View)newWebview).setLayoutParams(prev.getLayoutParams());

            // webviews can still send some extraneous events to this activity if we do not remove
            // its callbacks
            WebViewSetup.removeCallbacks((LeanWebView) prev);

            if (!this.isPoolWebview) {
                ((GoNativeWebviewInterface)prev).destroy();
            }
        }

        this.isPoolWebview = isPoolWebview;
        this.mWebview = newWebview;

        if (this.postLoadJavascript != null) {
            runJavascript(this.postLoadJavascript);
            this.postLoadJavascript = null;
        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.topmenu, menu);

        AppConfig appConfig = AppConfig.getInstance(this);

        // search item in action bar
		final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (appConfig.searchTemplateUrl != null) {
            // make it visible
            searchItem.setVisible(true);

            final SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                // listener to process query
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        searchItem.collapseActionView();

                        try {
                            String q = URLEncoder.encode(query, "UTF-8");
                            loadUrl(AppConfig.getInstance(getApplicationContext()).searchTemplateUrl + q);
                        } catch (UnsupportedEncodingException e) {
                            return true;
                        }

                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        // do nothing
                        return true;
                    }
                });

                // listener to collapse action view when soft keyboard is closed
                searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (!hasFocus) {
                            searchItem.collapseActionView();
                        }
                    }
                });
            }
        }

        if (!appConfig.showRefreshButton) {
            MenuItem refreshItem = menu.findItem(R.id.action_refresh);
            if (refreshItem != null) {
                refreshItem.setVisible(false);
            }
        }

        if (this.actionManager != null) {
            this.actionManager.addActions(menu);
        }

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event

        if (mDrawerToggle != null) {
            if (mDrawerToggle.onOptionsItemSelected(item)) {
              return true;
            }
        }

        // actions
        if (this.actionManager != null) {
            if (this.actionManager.onOptionsItemSelected(item)) {
                return true;
            }
        }
        
        // handle other items
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
	        case R.id.action_search:
	        	return true;
	        case R.id.action_refresh:
                onRefresh();
	        	return true;
        	default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRefresh() {
        refreshPage();
        // let the refreshing spinner stay for a little bit if the native show/hide is disabled
        // otherwise there isn't enough of a user confirmation that the page is refreshing
        if (AppConfig.getInstance(this).disableAnimations) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    swipeRefresh.setRefreshing(false);
                }
            }, 1000); // 1 second
        } else {
            this.swipeRefresh.setRefreshing(false);
        }
    }

    private void refreshPage() {
        String url = this.mWebview.getUrl();
        if (url != null && url.startsWith("file:///android_asset/offline")){
            this.mWebview.goBack();
            updateMenu();
        }
        else {
            this.postLoadJavascript = this.postLoadJavascriptForRefresh;
            this.mWebview.loadUrl(url);
        }
    }

    public void launchWebForm(String formName, String title) {
        Intent intent = new Intent(getBaseContext(), WebFormActivity.class);
        intent.putExtra(WebFormActivity.EXTRA_FORMNAME, formName);
        intent.putExtra(WebFormActivity.EXTRA_TITLE, title);
        startActivityForResult(intent, REQUEST_WEBFORM);
    }

    // onPageFinished
    public void checkNavigationForPage(String url) {
        // don't change anything on navigation if the url that just finished was a file download
        if (url.equals(this.fileDownloader.getLastDownloadedUrl())) return;

        if (this.tabManager != null) {
            this.tabManager.checkTabs(url);
        }

        if (this.actionManager != null) {
            this.actionManager.checkActions(url);
        }

        if (this.identityService != null) {
            this.identityService.checkUrl(url);
        }

        if (this.registrationManager != null) {
            this.registrationManager.checkUrl(url);
        }
    }

    // onPageStarted
    public void checkPreNavigationForPage(String url) {
        if (this.tabManager != null) {
            this.tabManager.autoSelectTab(url);
        }

        AppConfig appConfig = AppConfig.getInstance(this);
        setDrawerEnabled(appConfig.shouldShowSidebarForUrl(url));
    }

    public int urlLevelForUrl(String url) {
        ArrayList<Pattern> entries = AppConfig.getInstance(this).navStructureLevelsRegex;
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                Pattern regex = entries.get(i);
                if (regex.matcher(url).matches()) {
                    return AppConfig.getInstance(this).navStructureLevels.get(i);
                }
            }
        }

        // return unknown
        return -1;
    }

    public String titleForUrl(String url) {
        ArrayList<HashMap<String,Object>> entries = AppConfig.getInstance(this).navTitles;
        String title = null;

        if (entries != null) {
            for (HashMap<String,Object> entry : entries) {
                Pattern regex = (Pattern)entry.get("regex");

                if (regex.matcher(url).matches()) {
                    if (entry.containsKey("title")) {
                        title = (String)entry.get("title");
                    }

                    if (title == null && entry.containsKey("urlRegex")) {
                        Pattern urlRegex = (Pattern)entry.get("urlRegex");
                        Matcher match = urlRegex.matcher(url);
                        if (match.find() && match.groupCount() >= 1) {
                            String temp = match.group(1);
                            // dashes to spaces, capitalize
                            temp = temp.replace("-", " ");
                            temp = LeanUtils.capitalizeWords(temp);

                            title = temp;
                        }

                        // remove words from end of title
                        if (title != null && entry.containsKey("urlChompWords") &&
                                (Integer)entry.get("urlChompWords") > 0) {
                            int chompWords = (Integer)entry.get("urlChompWords");
                            String[] words = title.split("\\s+");
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < words.length - chompWords - 1; i++){
                                sb.append(words[i]);
                                sb.append(" ");
                            }
                            if (words.length > chompWords) {
                                sb.append(words[words.length - chompWords - 1]);
                            }
                            title = sb.toString();
                        }
                    }

                    break;
                }
            }
        }

        return title;
    }

    public void closeDrawers() {
        mDrawerLayout.closeDrawers();
    }

    public boolean isRoot() {
        return isRoot;
    }

    public int getParentUrlLevel() {
        return parentUrlLevel;
    }

    public int getUrlLevel() {
        return urlLevel;
    }

    public void setUrlLevel(int urlLevel) {
        this.urlLevel = urlLevel;
    }

    public ProfilePicker getProfilePicker() {
        return profilePicker;
    }

    public FileDownloader getFileDownloader() {
        return fileDownloader;
    }

    public StatusCheckerBridge getStatusCheckerBridge() {
        return new StatusCheckerBridge();
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    public void startCheckingReadyStatus() {
        statusChecker.run();
    }

    public void stopCheckingReadyStatus() {
        handler.removeCallbacks(statusChecker);
    }

    public void checkReadyStatus() {
        this.mWebview.runJavascript("gonative_status_checker.onReadyState(document.readyState)");
    }

    public void checkReadyStatusResult(String status) {
        // if interactiveDelay is specified, then look for readyState=interactive, and show webview
        // with a delay. If not specified, wait for readyState=complete.
        double interactiveDelay = AppConfig.getInstance(this).interactiveDelay;

        if (status.equals("loading") || (Double.isNaN(interactiveDelay) && status.equals("interactive"))) {
            startedLoading = true;
        }
        else if ((!Double.isNaN(interactiveDelay) && status.equals("interactive"))
                || (startedLoading && status.equals("complete"))) {

            if (status.equals("interactive")) {
                showWebview(interactiveDelay);
            } else {
                showWebview();
            }
        }
    }

    public void showTabs() {
        this.slidingTabStrip.setVisibility(View.VISIBLE);
        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setElevation(0);
        }
        ViewCompat.setElevation(this.slidingTabStrip, ACTIONBAR_ELEVATION);
    }

    public void hideTabs() {
        this.slidingTabStrip.setVisibility(View.GONE);
        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setElevation(ACTIONBAR_ELEVATION);
        }
    }

    public void toggleFullscreen(boolean fullscreen) {
        ActionBar actionBar = this.getSupportActionBar();
        View decorView = getWindow().getDecorView();
        int visibility = decorView.getSystemUiVisibility();
        int fullscreenFlags = View.SYSTEM_UI_FLAG_LOW_PROFILE |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

        if (Build.VERSION.SDK_INT >= 16) {
            fullscreenFlags |= View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }

        if (Build.VERSION.SDK_INT >= 19) {
            fullscreenFlags |= View.SYSTEM_UI_FLAG_IMMERSIVE |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }

        if (fullscreen) {
            visibility |= fullscreenFlags;
            if (actionBar != null) actionBar.hide();
        } else {
            visibility &= ~fullscreenFlags;
            if (actionBar != null) actionBar.show();
        }

        decorView.setSystemUiVisibility(visibility);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (this.directUploadImageUri == null) {
                    cancelFileUpload();
                    return;
                }

                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(this.directUploadImageUri);
                    mUploadMessage = null;
                }
                if (uploadMessageLP != null) {
                    uploadMessageLP.onReceiveValue(new Uri[]{this.directUploadImageUri});
                    uploadMessageLP = null;
                }

                this.directUploadImageUri = null;
            } else {
                cancelFileUpload();
            }
        }
        else if (requestCode == REQUEST_PERMISSION_GEOLOCATION) {
            // don't care about result
            if (this.geolocationPermissionCallback != null) {
                this.geolocationPermissionCallback.run();
                this.geolocationPermissionCallback = null;
            }
        }
    }

    public void setUploadMessage(ValueCallback<Uri> mUploadMessage) {
        this.mUploadMessage = mUploadMessage;
    }

    public void setUploadMessageLP(ValueCallback<Uri[]> uploadMessageLP) {
        this.uploadMessageLP = uploadMessageLP;
    }

    public void setDirectUploadImageUri(Uri directUploadImageUri) {
        this.directUploadImageUri = directUploadImageUri;
    }

    public RelativeLayout getFullScreenLayout() {
        return fullScreenLayout;
    }

    public class StatusCheckerBridge {
        @JavascriptInterface
        public void onReadyState(final String state) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    checkReadyStatusResult(state);
                }
            });
        }
    }

    private class ConnectivityChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            retryFailedPage();
        }
    }

    public void getRuntimeGeolocationPermission(final Runnable callback) {
        int checkFine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
        int checkCoarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION);

        if (checkFine == PackageManager.PERMISSION_GRANTED && checkCoarse == PackageManager.PERMISSION_GRANTED) {
            if (callback != null) callback.run();
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            Toast.makeText(this, R.string.request_permission_explanation_geolocation, Toast.LENGTH_SHORT).show();
        }

        this.geolocationPermissionCallback = callback;
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQUEST_PERMISSION_GEOLOCATION);
    }
}
