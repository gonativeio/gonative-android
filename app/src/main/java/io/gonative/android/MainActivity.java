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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Base64;
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
import android.webkit.WebSettings;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.aurelhubert.ahbottomnavigation.AHBottomNavigation;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome;
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesomeBrand;
import com.onesignal.OSDeviceState;
import com.onesignal.OneSignal;
import com.squareup.seismic.ShakeDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.gonative.android.library.AppConfig;
import io.gonative.android.widget.GoNativeDrawerLayout;
import io.gonative.android.widget.GoNativeSwipeRefreshLayout;
import io.gonative.android.widget.SwipeHistoryNavigationLayout;
import io.gonative.gonative_core.GoNativeActivity;
import io.gonative.gonative_core.GoNativeWebviewInterface;
import io.gonative.gonative_core.LeanUtils;

public class MainActivity extends AppCompatActivity implements Observer,
        GoNativeActivity,
        GoNativeSwipeRefreshLayout.OnRefreshListener,
        ShakeDetector.Listener,
        ShakeDialogFragment.ShakeDialogListener {
    public static final String BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED = "io.gonative.android.MainActivity.Extra.BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED";
    public static final String webviewCacheSubdir = "webviewAppCache";
    private static final String webviewDatabaseSubdir = "webviewDatabase";
	private static final String TAG = MainActivity.class.getName();
    public static final String INTENT_TARGET_URL = "targetUrl";
    public static final String EXTRA_WEBVIEW_WINDOW_OPEN = "io.gonative.android.MainActivity.Extra.WEBVIEW_WINDOW_OPEN";
	public static final int REQUEST_SELECT_FILE = 100;
    private static final int REQUEST_PERMISSION_READ_EXTERNAL_STORAGE = 101;
    private static final int REQUEST_PERMISSION_GEOLOCATION = 102;
    private static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 103;
    private static final int REQUEST_PERMISSION_GENERIC = 199;
    private static final int REQUEST_WEBFORM = 300;
    public static final int REQUEST_WEB_ACTIVITY = 400;
    public static final int GOOGLE_SIGN_IN = 500;
    private static final float ACTIONBAR_ELEVATION = 12.0f;

    private GoNativeWebviewInterface mWebview;
    private View webviewOverlay;
    boolean isPoolWebview = false;
    private Stack<String> backHistory = new Stack<>();
    private String initialUrl;
    private boolean sidebarNavigationEnabled = true;

    private static int webViewCount = 0;
    private static boolean removeExcessWebView = false;

	private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> uploadMessageLP;
    private Uri directUploadImageUri;
	private GoNativeDrawerLayout mDrawerLayout;
	private View mDrawerView;
	private ExpandableListView mDrawerList;
    private ProgressBar mProgress;
    private Dialog splashDialog;
    private boolean splashDismissRequiresForce;
    private MySwipeRefreshLayout swipeRefreshLayout;
    private SwipeHistoryNavigationLayout swipeNavLayout;
    private RelativeLayout fullScreenLayout;
    private JsonMenuAdapter menuAdapter = null;
	private ActionBarDrawerToggle mDrawerToggle;
    private AHBottomNavigation bottomNavigationView;
    private ImageView navigationTitleImage;
	private ConnectivityManager cm = null;
    private ProfilePicker profilePicker = null;
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
    private ShakeDetector shakeDetector = new ShakeDetector(this);
    private FileDownloader fileDownloader = new FileDownloader(this);
    private FileWriterSharer fileWriterSharer;
    private GNJSBridgeInterface jsBridgeInterface;
    private boolean startedLoading = false; // document readystate checker
    private LoginManager loginManager;
    private RegistrationManager registrationManager;
    private ConnectivityChangeReceiver connectivityReceiver;
    private BroadcastReceiver oneSignalStatusChangedReceiver;
    private BroadcastReceiver navigationTitlesChangedReceiver;
    private BroadcastReceiver navigationLevelsChangedReceiver;
    private BroadcastReceiver webviewLimitReachedReceiver;
    protected String postLoadJavascript;
    protected String postLoadJavascriptForRefresh;
    private Stack<Bundle>previousWebviewStates;
    private GeolocationPermissionCallback geolocationPermissionCallback;
    private ArrayList<PermissionsCallbackPair> pendingPermissionRequests = new ArrayList<>();
    private ArrayList<Intent> pendingStartActivityAfterPermissions = new ArrayList<>();
    private String connectivityCallback;
    private String connectivityOnceCallback;
    private PhoneStateListener phoneStateListener;
    private SignalStrength latestSignalStrength;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        final AppConfig appConfig = AppConfig.getInstance(this);
        GoNativeApplication application = (GoNativeApplication)getApplication();

        if(appConfig.androidFullScreen){
            toggleFullscreen(true);
        }
        // must be done AFTER toggleFullScreen to force screen orientation
        setScreenOrientationPreference();

        if (appConfig.keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        this.hideWebviewAlpha  = appConfig.hideWebviewAlpha;

        super.onCreate(savedInstanceState);

        isRoot = getIntent().getBooleanExtra("isRoot", true);
        application.mBridge.onActivityCreate(this, isRoot);
        parentUrlLevel = getIntent().getIntExtra("parentUrlLevel", -1);
        webViewCount++;

        if (isRoot) {
            // Splash screen stuff
            boolean isFromLauncher = getIntent().hasCategory(Intent.CATEGORY_LAUNCHER);
            // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY does not seem to be set when it should
            // for some devices. I have yet to find a good workaround.
            boolean isFromRecents = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
            boolean noSplash = getIntent().getBooleanExtra("noSplash", false);

            if (!noSplash && isFromLauncher && !isFromRecents) {
                showSplashScreen(appConfig.showSplashMaxTime, appConfig.showSplashForceTime);
            }

            // html5 app cache (manifest)
            File cachePath = new File(getCacheDir(), webviewCacheSubdir);
            if (!cachePath.mkdirs()) {
                Log.v(TAG, "cachePath " + cachePath.toString() + " exists");
            }
            File databasePath = new File(getCacheDir(), webviewDatabaseSubdir);
            if (databasePath.mkdirs()) {
                Log.v(TAG, "databasePath " + databasePath.toString() + " exists");

            }

            // url inspector
            UrlInspector.getInstance().init(this);

            // Register launch
            ConfigUpdater configUpdater = new ConfigUpdater(this);
            configUpdater.registerEvent();

            // registration service
            this.registrationManager = application.getRegistrationManager();
        }

        this.loginManager = application.getLoginManager();

        this.fileWriterSharer = new FileWriterSharer(this);
        this.jsBridgeInterface = new GNJSBridgeInterface(this);

        // webview pools
        application.getWebViewPool().init(this);

		cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        if (appConfig.showActionBar || appConfig.showNavigationMenu)
	    	setContentView(R.layout.activity_gonative);
        else
            setContentView(R.layout.activity_gonative_nonav);


        mProgress = findViewById(R.id.progress);
        this.fullScreenLayout = findViewById(R.id.fullscreen);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setEnabled(appConfig.pullToRefresh);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setCanChildScrollUpCallback(() -> mWebview.getScrollY() > 0);
        
        swipeNavLayout = findViewById(R.id.swipe_history_nav);
        swipeNavLayout.setEnabled(appConfig.swipeGestures);
        swipeNavLayout.setSwipeNavListener(new SwipeHistoryNavigationLayout.OnSwipeNavListener() {
            @Override
            public boolean canSwipeLeftEdge() {
                return canGoBack();
            }
    
            @Override
            public boolean canSwipeRightEdge() {
                return canGoForward();
            }
    
            @NonNull
            @Override
            public String getGoBackLabel() {
                return "";
            }
    
            @Override
            public boolean navigateBack() {
                if (appConfig.swipeGestures && canGoBack()) {
                    goBack();
                    return true;
                }
                return false;
            }
    
            @Override
            public boolean navigateForward() {
                if (appConfig.swipeGestures && canGoForward()) {
                    goForward();
                    return true;
                }
                return false;
            }
    
            @Override
            public void leftSwipeReachesLimit() {
        
            }
    
            @Override
            public void rightSwipeReachesLimit() {
        
            }

            @Override
            public boolean isSwipeEnabled() {
                return appConfig.swipeGestures;
            }
        });
        
        if (appConfig.pullToRefreshColor != null) {
            swipeRefreshLayout.setColorSchemeColors(appConfig.pullToRefreshColor);
            swipeNavLayout.setActiveColor(appConfig.pullToRefreshColor);
        }

        this.webviewOverlay = findViewById(R.id.webviewOverlay);
        this.mWebview = findViewById(R.id.webview);
        setupWebview(this.mWebview);

        // profile picker
        if (isRoot && (appConfig.showActionBar || appConfig.showNavigationMenu)) {
            Spinner profileSpinner = findViewById(R.id.profile_picker);
            profilePicker = new ProfilePicker(this, profileSpinner);

            Spinner segmentedSpinner = findViewById(R.id.segmented_control);
            new SegmentedController(this, segmentedSpinner);
        }

		// to save webview cookies to permanent storage
		CookieSyncManager.createInstance(getApplicationContext());

		// proxy cookie manager for httpUrlConnection (syncs to webview cookies)
        CookieHandler.setDefault(new WebkitCookieManagerProxy());


        this.postLoadJavascript = getIntent().getStringExtra("postLoadJavascript");
        this.postLoadJavascriptForRefresh = this.postLoadJavascript;

        this.previousWebviewStates = new Stack<>();

        // tab navigation
        this.bottomNavigationView = findViewById(R.id.bottom_navigation);
        this.tabManager = new TabManager(this, bottomNavigationView);

        hideTabs();

        Toolbar toolbar = findViewById(R.id.toolbar);
        // Add action bar if getSupportActionBar() is null
        // regardless of appConfig.showActionBar value to setup drawers, sidenav
        if (getSupportActionBar() == null) {
            // Set Material Toolbar as Action Bar.
            setSupportActionBar(toolbar);
        }
        // Hide action bar if showActionBar is FALSE and showNavigationMenu is FALSE
        if (!appConfig.showActionBar && !appConfig.showNavigationMenu && toolbar != null) {
            toolbar.setVisibility(View.GONE);
        }

        if (!appConfig.showLogoInSideBar && !appConfig.showAppNameInSideBar) {
            findViewById(R.id.header_layout).setVisibility(View.GONE);
        }

        if (!appConfig.showLogoInSideBar) {
            ImageView appIcon = findViewById(R.id.app_logo);
            appIcon.setVisibility(View.GONE);
        }
        TextView appName = findViewById(R.id.app_name);
        if (appName != null) {
            if(appConfig.showAppNameInSideBar) {
                appName.setText(appConfig.appName);
            } else {
                appName.setVisibility(View.INVISIBLE);
            }
        }

        // actions in action bar
        this.actionManager = new ActionManager(this);

        Intent intent = getIntent();
        // load url
        String url = getUrlFromIntent(intent);

        if (url == null && savedInstanceState != null) url = savedInstanceState.getString("url");
        if (url == null && isRoot) url = new ConfigPreferences(this).getInitialUrl();
        if (url == null && isRoot) url = appConfig.initialUrl;
        // url from intent (hub and spoke nav)
        if (url == null) url = intent.getStringExtra("url");

        if (url != null) {
            this.initialUrl = url;
            this.mWebview.loadUrl(url);
        } else if (intent.getBooleanExtra(EXTRA_WEBVIEW_WINDOW_OPEN, false)){
            // no worries, loadUrl will be called when this new web view is passed back to the message
        } else {
            Log.e(TAG, "No url specified for MainActivity");
        }

        if (isRoot && appConfig.showNavigationMenu) {
            // do the list stuff
            mDrawerLayout = findViewById(R.id.drawer_layout);
            mDrawerView = findViewById(R.id.left_drawer);
            mDrawerList = findViewById(R.id.drawer_list);

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
            mDrawerLayout.addDrawerListener(mDrawerToggle);
            mDrawerLayout.setDisableTouch(appConfig.swipeGestures);

            setupMenu();

            // update the menu
            if (appConfig.loginDetectionUrl != null) {
                this.loginManager.addObserver(this);
            }
        }

		if (getSupportActionBar() != null) {
            if (!isRoot || appConfig.showNavigationMenu) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                Drawable backArrow = ContextCompat.getDrawable(this, R.drawable.abc_ic_ab_back_material);
                backArrow.setColorFilter(AppConfig.getInstance(this).actionbarForegroundColor, PorterDuff.Mode.SRC_ATOP);
                getSupportActionBar().setHomeAsUpIndicator(backArrow);
            }

            showLogoInActionBar(appConfig.shouldShowNavigationTitleImageForUrl(url));
        }

        // style sidebar
        if (mDrawerView != null && AppConfig.getInstance(this).sidebarBackgroundColor != null) {
            mDrawerView.setBackgroundColor(AppConfig.getInstance(this).sidebarBackgroundColor);
        }

        this.oneSignalStatusChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (GoNativeApplication.ONESIGNAL_STATUS_CHANGED_MESSAGE.equals(intent.getAction())) {
                    sendOneSignalInfo("gonative_onesignal_info");
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.oneSignalStatusChangedReceiver,
                new IntentFilter(GoNativeApplication.ONESIGNAL_STATUS_CHANGED_MESSAGE));

        // respond to navigation titles processed
        this.navigationTitlesChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AppConfig.PROCESSED_NAVIGATION_TITLES.equals(intent.getAction())) {
                    String url = mWebview.getUrl();
                    if (url == null) return;
                    String title = titleForUrl(url);
                    if (title != null) {
                        setTitle(title);
                    } else {
                        setTitle(R.string.app_name);
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.navigationTitlesChangedReceiver,
            new IntentFilter(AppConfig.PROCESSED_NAVIGATION_TITLES));

        this.navigationLevelsChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AppConfig.PROCESSED_NAVIGATION_LEVELS.equals(intent.getAction())) {
                    String url = mWebview.getUrl();
                    if (url == null) return;
                    int level = urlLevelForUrl(url);
                    setUrlLevel(level);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.navigationLevelsChangedReceiver,
                new IntentFilter(AppConfig.PROCESSED_NAVIGATION_LEVELS));

        this.webviewLimitReachedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED.equals(intent.getAction())) {
                    if (!isRoot && removeExcessWebView) {
                        removeExcessWebView = false;
                        finish();
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.webviewLimitReachedReceiver,
                new IntentFilter(BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED));

        setupAppTheme();
    }

    private String getUrlFromIntent(Intent intent) {
        if (intent == null) return null;
        // first check intent in case it was created from push notification
        String targetUrl = intent.getStringExtra(INTENT_TARGET_URL);
        if (targetUrl != null && !targetUrl.isEmpty()){
            return targetUrl;
        }

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null && (uri.getScheme().endsWith(".http") || uri.getScheme().endsWith(".https"))) {
                Uri.Builder builder = uri.buildUpon();
                if (uri.getScheme().endsWith(".https")) {
                    builder.scheme("https");
                } else if (uri.getScheme().endsWith(".http")) {
                    builder.scheme("http");
                }
                return builder.build().toString();
            } else {
                return intent.getDataString();
            }
        }

        return null;
    }

    protected void onPause() {
        super.onPause();
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityPause(this);
        stopCheckingReadyStatus();
    
        if (application.mBridge.pauseWebViewOnActivityPause()) {
            this.mWebview.onPause();
        }

        // unregister connectivity
        if (this.connectivityReceiver != null) {
            unregisterReceiver(this.connectivityReceiver);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush();
        }

        shakeDetector.stop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityStart(this);
        if (AppConfig.getInstance(this).oneSignalEnabled) {
            OneSignal.clearOneSignalNotifications();
        }
        if (AppConfig.getInstance(this).enableWebRTCBluetoothAudio) {
            AudioUtils.initAudioFocusListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityResume(this);
        this.mWebview.onResume();

        retryFailedPage();
        // register to listen for connectivity changes
        this.connectivityReceiver = new ConnectivityChangeReceiver();
        registerReceiver(this.connectivityReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // check login status
        this.loginManager.checkLogin();

        if (AppConfig.getInstance(this).shakeToClearCache) {
            SensorManager sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            shakeDetector.setSensitivity(ShakeDetector.SENSITIVITY_HARD);
            shakeDetector.start(sensorManager);
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
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityDestroy(this);
        webViewCount--;

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

        this.loginManager.deleteObserver(this);

        if (this.oneSignalStatusChangedReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(this.oneSignalStatusChangedReceiver);
        }
        if (this.navigationTitlesChangedReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(this.navigationTitlesChangedReceiver);
        }
        if (this.navigationLevelsChangedReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(this.navigationLevelsChangedReceiver);
        }
        if (this.webviewLimitReachedReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(this.webviewLimitReachedReceiver);
        }
    }

    private void retryFailedPage() {
        // skip if webview is currently loading
        if (this.mWebview.getProgress() < 100) return;

        // skip if webview has a page loaded
        String currentUrl = this.mWebview.getUrl();
        if (currentUrl != null && !currentUrl.equals(UrlNavigation.OFFLINE_PAGE_URL)) return;

        // skip if there is nothing in history
        if (this.backHistory.isEmpty()) return;

        // skip if no network connectivity
        if (this.isDisconnected()) return;

        // finally, retry loading the page
        this.loadUrl(this.backHistory.pop());
    }

    protected void onSaveInstanceState (Bundle outState) {
        outState.putString("url", mWebview.getUrl());
        outState.putInt("urlLevel", urlLevel);
        super.onSaveInstanceState(outState);
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

    @Override
    public void hearShake() {
        String FRAGMENT_TAG = "ShakeDialogFragment";
        if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) != null) {
            return;
        }

        ShakeDialogFragment dialog = new ShakeDialogFragment();
        dialog.show(getSupportFragmentManager(), FRAGMENT_TAG);
    }

    @Override
    public void onClearCache(DialogFragment dialog) {
        clearWebviewCache();
        Toast.makeText(this, R.string.cleared_cache, Toast.LENGTH_SHORT).show();
    }

    public boolean canGoBack() {
        if (this.mWebview == null) return false;
        return this.mWebview.canGoBack();
    }

    public void goBack() {
        if (this.mWebview == null) return;
        if (LeanWebView.isCrosswalk()) {
            // not safe to do for non-crosswalk, as we may never get a page finished callback
            // for single-page apps
            hideWebview();
        }

        this.mWebview.goBack();
    }

    private boolean canGoForward() {
        return this.mWebview.canGoForward();
    }

    private void goForward() {
        if (LeanWebView.isCrosswalk()) {
            // not safe to do for non-crosswalk, as we may never get a page finished callback
            // for single-page apps
            hideWebview();
        }

        this.mWebview.goForward();
    }

    public void sharePage(String optionalUrl) {
        String shareUrl;
        String currentUrl = this.mWebview.getUrl();
        if (optionalUrl == null || optionalUrl.isEmpty()) {
            shareUrl = currentUrl;
        } else {
            try {
                java.net.URI optionalUri = new java.net.URI(optionalUrl);
                if (optionalUri.isAbsolute()) {
                    shareUrl = optionalUrl;
                } else {
                    java.net.URI currentUri = new java.net.URI(currentUrl);
                    shareUrl = currentUri.resolve(optionalUri).toString();
                }
            } catch (URISyntaxException e) {
                shareUrl = optionalUrl;
            }
        }

        if (shareUrl == null || shareUrl.isEmpty()) return;

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareUrl);
        startActivity(Intent.createChooser(share, getString(R.string.action_share)));
    }

    private void logout() {
        this.mWebview.stopLoading();

        // log out by clearing all cookies and going to home page
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
        CookieSyncManager.getInstance().sync();

        updateMenu(false);
        this.loginManager.checkLogin();
        this.mWebview.loadUrl(AppConfig.getInstance(this).initialUrl);
    }

    public void loadUrl(String url) {
        loadUrl(url, false);
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

	public boolean isDisconnected(){
		NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni == null || !ni.isConnected();
	}

	public void clearWebviewCache() {
        mWebview.clearCache(true);
    }
	// configures webview settings
	private void setupWebview(GoNativeWebviewInterface wv){
        WebViewSetup.setupWebviewForActivity(wv, this);
	}

    private void showSplashScreen(double maxTime, double forceTime) {
        splashDialog = new Dialog(this, R.style.SplashScreen);
        if (splashDialog.getWindow() != null) {
            splashDialog.getWindow().getAttributes().windowAnimations = R.style.SplashScreenAnimation;
        }
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
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onHideWebview(this);

        if (AppConfig.getInstance(this).disableAnimations) return;

        this.webviewIsHidden = true;
        mProgress.setAlpha(1.0f);
        mProgress.setVisibility(View.VISIBLE);

        if (this.isFirstHideWebview) {
            this.webviewOverlay.setAlpha(1.0f);
        } else {
            this.webviewOverlay.setAlpha(1 - this.hideWebviewAlpha);
        }

        showWebview(10);
    }

    private void showWebview(double delay) {
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
        this.webviewOverlay.setAlpha(0.0f);
        this.mProgress.setVisibility(View.INVISIBLE);

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            injectCSSviaJavascript();
        }
    }

    public void showWebview() {
        hideSplashScreen(false);

        this.isFirstHideWebview = false;
        startedLoading = false;
        stopCheckingReadyStatus();

        if (!webviewIsHidden) {
            // don't animate if already visible
            mProgress.setVisibility(View.INVISIBLE);
            return;
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            injectCSSviaJavascript();
        }

        webviewIsHidden = false;

        webviewOverlay.animate().alpha(0.0f)
                .setDuration(300)
                .setStartDelay(150);

        mProgress.animate().alpha(0.0f)
                .setDuration(60);
    }

    private void injectCSSviaJavascript() {
        AppConfig appConfig = AppConfig.getInstance(this);
        if ((appConfig.customCSS == null || appConfig.customCSS.isEmpty())
                && (appConfig.androidCustomCSS == null || appConfig.androidCustomCSS.isEmpty())) return;

        try {
            StringBuilder builder = new StringBuilder();
            if(appConfig.customCSS != null)
                builder.append(appConfig.customCSS).append(" ");
            if(appConfig.androidCustomCSS != null)
                builder.append(appConfig.androidCustomCSS);
            String encoded = Base64.encodeToString(builder.toString().getBytes("utf-8"), Base64.NO_WRAP);
            String js = "(function() {" +
                    "var parent = document.getElementsByTagName('head').item(0);" +
                    "var style = document.createElement('style');" +
                    "style.type = 'text/css';" +
                    // Tell the browser to BASE64-decode the string into your script !!!
                    "style.innerHTML = window.atob('" + encoded + "');" +
                    "parent.appendChild(style)" +
                    "})()";
            runJavascript(js);
        } catch (Exception e) {
            Log.e(TAG, "Error injecting customCSS via javascript", e);
        }
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
        this.loginManager.checkLogin();
	}

    private void updateMenu(boolean isLoggedIn){
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

	private boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mDrawerView);
    }

    private void setDrawerEnabled(boolean enabled) {
        if (!isRoot) return;

        AppConfig appConfig = AppConfig.getInstance(this);
        if (!appConfig.showNavigationMenu) return;

        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(enabled ? GoNativeDrawerLayout.LOCK_MODE_UNLOCKED :
                    GoNativeDrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }

        if((sidebarNavigationEnabled || appConfig.showActionBar ) && enabled){
            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setVisibility(View.VISIBLE);
            }
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(enabled);
        }
    }

	private void setupMenu(){
        menuAdapter = new JsonMenuAdapter(this, mDrawerList);
        try {
            menuAdapter.update("default");
            mDrawerList.setAdapter(menuAdapter);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up menu", e);
        }

        mDrawerList.setOnGroupClickListener(menuAdapter);
        mDrawerList.setOnChildClickListener(menuAdapter);
	}


	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onPostCreate(this, savedInstanceState, isRoot);

		// Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null)
		    mDrawerToggle.syncState();
	}

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        GoNativeApplication application = (GoNativeApplication)getApplication();
     // Pass any configuration change to the drawer toggles
        if (mDrawerToggle != null)
            mDrawerToggle.onConfigurationChanged(newConfig);
//        if (swipeRefreshLayout != null)
//       TODO     swipeRefreshLayout.onConfigurationChanged(newConfig);
        application.mBridge.onConfigurationChange(this);
    }

	@Override
    @TargetApi(21)
    // Lollipop target API for REQEUST_SELECT_FILE_LOLLIPOP
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityResult(this, requestCode, resultCode, data);

        if (data != null && data.getBooleanExtra("exit", false))
            finish();

        String url = null;
        boolean success = false;
        if (data != null) {
            url = data.getStringExtra("url");
            success = data.getBooleanExtra("success", false);
        }

        if (requestCode == REQUEST_WEBFORM && resultCode == RESULT_OK) {
            if (url != null)
                loadUrl(url);
            else {
                // go to initialURL without login/signup override
                this.mWebview.setCheckLoginSignup(false);
                this.mWebview.loadUrl(AppConfig.getInstance(this).initialUrl);
            }

            if (AppConfig.getInstance(this).showNavigationMenu) {
                updateMenu(success);
            }
        }

        if (requestCode == REQUEST_WEB_ACTIVITY && resultCode == RESULT_OK) {
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
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        Toast.makeText(this, R.string.external_storage_explanation, Toast.LENGTH_LONG).show();
                    }

                    ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
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
        super.onNewIntent(intent);
        String url = getUrlFromIntent(intent);
        if (url != null && !url.isEmpty()) {
            loadUrl(url);
        }
        Log.w(TAG, "Received intent without url");
    }

    @Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
		    if (AppConfig.getInstance(this).disableBackButton) {
		        return true;
            }

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

        if (((GoNativeApplication) getApplication()).mBridge.onKeyDown(keyCode, event)) {
            return true;
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
                SearchView.SearchAutoComplete searchAutoComplete = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
                if (searchAutoComplete != null) {
                    searchAutoComplete.setTextColor(appConfig.actionbarForegroundColor);
                    int hintColor = appConfig.actionbarForegroundColor;
                    hintColor = Color.argb(192, Color.red(hintColor), Color.green(hintColor),
                            Color.blue(hintColor));
                    searchAutoComplete.setHintTextColor(hintColor);
                }

                ImageView closeButtonImage = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
                if (closeButtonImage != null) {
                    closeButtonImage.setColorFilter(appConfig.actionbarForegroundColor);
                }

                searchView.setOnSearchClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setMenuItemsVisible(menu, false, searchItem);
                    }
                });

                searchView.setOnCloseListener(new SearchView.OnCloseListener() {
                    @Override
                    public boolean onClose() {
                        setMenuItemsVisible(menu, true, searchItem);
                        return false;
                    }
                });

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
                            setMenuItemsVisible(menu, true, searchItem);
                        }
                    }
                });
            }
        }

        if (!appConfig.showRefreshButton) {
            MenuItem refreshItem = menu.findItem(R.id.action_refresh);
            if (refreshItem != null) {
                refreshItem.setVisible(false);
                refreshItem.setEnabled(false);
            }
        }

        if (this.actionManager != null) {
            this.actionManager.addActions(menu);
        }

		return true;
	}

	private void setMenuItemsVisible(Menu menu, boolean visible, MenuItem exception) {
        MenuItem refreshItem = menu.findItem(R.id.action_refresh);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item == exception) {
                continue;
            }

            if (visible && item == refreshItem && !AppConfig.getInstance(this).showRefreshButton) {
                continue;
            }

            item.setVisible(visible);
            item.setEnabled(visible);
        }
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
        stopNavAnimation(true, 1000);
    }
    
    private void stopNavAnimation(boolean isConsumed){
        stopNavAnimation(isConsumed, 100);
    }
    
    private void stopNavAnimation(boolean isConsumed, int delay){
        // let the refreshing spinner stay for a little bit if the native show/hide is disabled
        // otherwise there isn't enough of a user confirmation that the page is refreshing
        if (isConsumed && AppConfig.getInstance(this).disableAnimations) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, delay);
        } else {
            this.swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void refreshPage() {
        String url = this.mWebview.getUrl();
        if (url != null && url.equals(UrlNavigation.OFFLINE_PAGE_URL)){
            if (this.mWebview.canGoBack()) {
                this.mWebview.goBack();
            } else if (this.initialUrl != null) {
                this.mWebview.loadUrl(this.initialUrl);
            }
            updateMenu();
        }
        else {
            this.postLoadJavascript = this.postLoadJavascriptForRefresh;
            this.mWebview.loadUrl(url);
        }
    }

    public void sendOneSignalInfo(String callback) {
        boolean doNativeBridge = true;
        String currentUrl = this.mWebview.getUrl();
        if (currentUrl != null) {
            doNativeBridge = LeanUtils.checkNativeBridgeUrls(currentUrl, this);
        }

        // onesignal javsacript callback
        if (!AppConfig.getInstance(this).oneSignalEnabled && doNativeBridge) {
            try {
                String userId = null;
                String pushToken = null;
                boolean subscribed = false;

                OSDeviceState state = OneSignal.getDeviceState();
                if (state != null) {
                    userId = state.getUserId();
                    pushToken = state.getPushToken();
                    subscribed = state.isSubscribed();
                }

                Map installationInfo = Installation.getInfo(this);

                JSONObject jsonObject = new JSONObject(installationInfo);
                if (userId != null) {
                    jsonObject.put("oneSignalUserId", userId);
                }
                if (pushToken != null) {
                    // registration id is old GCM name, but keep it for compatibility
                    jsonObject.put("oneSignalregistrationId", pushToken);
                    jsonObject.put("oneSignalPushToken", pushToken);
                }
                jsonObject.put("oneSignalSubscribed", subscribed);
                jsonObject.put("oneSignalRequiresUserPrivacyConsent", !OneSignal.userProvidedPrivacyConsent());
                String js = LeanUtils.createJsForCallback(callback, jsonObject);
                runJavascript(js);
            } catch (Exception e) {
                Log.e(TAG, "Error with onesignal javscript callback", e);
            }
        }
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

        if (this.registrationManager != null) {
            this.registrationManager.checkUrl(url);
        }

        if (this.menuAdapter != null) {
            this.menuAdapter.autoSelectItem(url);
        }

        sendOneSignalInfo("gonative_onesignal_info");
    }

    // onPageStarted
    public void checkPreNavigationForPage(String url) {
        if (this.tabManager != null) {
            this.tabManager.autoSelectTab(url);
        }

        if (this.menuAdapter != null) {
            this.menuAdapter.autoSelectItem(url);
        }

        AppConfig appConfig = AppConfig.getInstance(this);
        setDrawerEnabled(appConfig.shouldShowSidebarForUrl(url) && sidebarNavigationEnabled);
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

    public boolean isNotRoot() {
        return !isRoot;
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

    public FileWriterSharer getFileWriterSharer() {
        return fileWriterSharer;
    }

    public GNJSBridgeInterface getJsBridgeInterface() {
        return jsBridgeInterface;
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

    private void stopCheckingReadyStatus() {
        handler.removeCallbacks(statusChecker);
    }

    private void checkReadyStatus() {
        this.mWebview.runJavascript("if (gonative_status_checker && typeof gonative_status_checker.onReadyState === 'function') gonative_status_checker.onReadyState(document.readyState);");
    }

    private void checkReadyStatusResult(String status) {
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
        this.bottomNavigationView.setVisibility(View.VISIBLE);
    }

    public void hideTabs() {
        this.bottomNavigationView.setVisibility(View.GONE);
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
            if (actionBar != null && AppConfig.getInstance(this).showActionBar) actionBar.show();

            // Fix for webview keyboard not showing, see https://github.com/mozilla-tw/FirefoxLite/issues/842
            this.mWebview.clearFocus();
        }

        decorView.setSystemUiVisibility(visibility);

        // Full-screen is used for playing videos.
        // Allow sensor-based rotation when in full screen (even overriding user rotation preference)
        // If orientation is forced landscape don't set sensor based orientation
        if (fullscreen && AppConfig.getInstance(this).forceScreenOrientation != AppConfig.ScreenOrientations.LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else {
            setScreenOrientationPreference();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ((GoNativeApplication) getApplication()).mBridge.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_READ_EXTERNAL_STORAGE:
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
                break;
            case REQUEST_PERMISSION_GEOLOCATION:
                if (this.geolocationPermissionCallback != null) {
                    if (grantResults.length >= 2 &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                            grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                        this.geolocationPermissionCallback.onResult(true);
                    } else {
                        this.geolocationPermissionCallback.onResult(false);
                    }
                    this.geolocationPermissionCallback = null;
                }
                break;
            case REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    this.fileDownloader.gotExternalStoragePermissions(true);
                }
                break;
            case REQUEST_PERMISSION_GENERIC:
                Iterator<PermissionsCallbackPair> it = pendingPermissionRequests.iterator();
                while (it.hasNext()) {
                    PermissionsCallbackPair pair = it.next();
                    if (pair.permissions.length != permissions.length) continue;
                    boolean skip = false;
                    for (int i = 0; i < pair.permissions.length && i < permissions.length; i++) {
                        if (!pair.permissions[i].equals(permissions[i])) {
                            skip = true;
                            break;
                        }
                    }
                    if (skip) continue;

                    // matches PermissionsCallbackPair
                    if (pair.callback != null) {
                        pair.callback.onPermissionResult(permissions, grantResults);
                    }
                    it.remove();
                }

                if (pendingPermissionRequests.size() == 0 && pendingStartActivityAfterPermissions.size() > 0) {
                    Iterator<Intent> i = pendingStartActivityAfterPermissions.iterator();
                    while (i.hasNext()) {
                        Intent intent = i.next();
                        startActivity(intent);
                        i.remove();
                    }
                }
                break;
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

    @Override
    public GoNativeWebviewInterface getWebView() {
        return mWebview;
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
            if (connectivityCallback != null) {
                sendConnectivity(connectivityCallback);
            }
        }
    }

    public void getRuntimeGeolocationPermission(final GeolocationPermissionCallback callback) {
        int checkFine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
        int checkCoarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION);

        if (checkFine == PackageManager.PERMISSION_GRANTED && checkCoarse == PackageManager.PERMISSION_GRANTED) {
            callback.onResult(true);
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

    public void getExternalStorageWritePermission() {
        // check external storage permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, R.string.request_permission_explanation_storage, Toast.LENGTH_LONG).show();
            }

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE);
        } else {
            this.fileDownloader.gotExternalStoragePermissions(true);
        }
    }

    public void getPermission(String[] permissions, PermissionCallback callback) {
        boolean needToRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needToRequest = true;
                break;
            }
        }

        if (needToRequest) {
            if (callback != null) {
                pendingPermissionRequests.add(new PermissionsCallbackPair(permissions, callback));
            }

            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_GENERIC);
        } else {
            // send all granted result
            if (callback != null) {
                int[] results = new int[permissions.length];
                for (int i = 0; i < results.length; i++) {
                    results[i] = PackageManager.PERMISSION_GRANTED;
                }
                callback.onPermissionResult(permissions, results);
            }
        }
    }

    public void startActivityAfterPermissions(Intent intent) {
        if (pendingPermissionRequests.size() == 0) {
            startActivity(intent);
        } else {
            pendingStartActivityAfterPermissions.add(intent);
        }
    }

    private void setScreenOrientationPreference() {
        AppConfig appConfig = AppConfig.getInstance(this);
        if (appConfig.forceScreenOrientation == null) return;

        switch (appConfig.forceScreenOrientation) {
            case UNSPECIFIED:
                if(appConfig.androidFullScreen) return;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
            case PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
        }
    }

    public IconicsDrawable getFontAwesomeIcon(String icon){

        IconicsDrawable iconDrawable;
        try {
            // Get icon name
            String iconName;
            // if exact icon name is provided (ex: faw_angle_up)
            if(icon.contains("_")){
                iconName = icon.substring(icon.indexOf("_") + 1);
            }
            // if icon name provided from appConfig (Ex: fas fa-home)
            else {
                iconName = icon.substring(icon.indexOf("-") + 1).replaceAll("-", "_");
            }

            // Get FontAwesome Icon from the iconName categorized by fab (Brand) and faw (Solid)
            if (icon.startsWith("fab")) {
                icon = "fab_" + iconName;
                iconDrawable = new IconicsDrawable(this, FontAwesomeBrand.Icon.valueOf(icon));
            } else {
                icon = "faw_" + iconName;
                iconDrawable = new IconicsDrawable(this, FontAwesome.Icon.valueOf(icon));
            }
        } catch (IllegalArgumentException e) {
            // icon was not found in IconValue enum
            icon = "faw_question";
            iconDrawable = new IconicsDrawable(this, FontAwesome.Icon.valueOf(icon));
            Log.e(TAG, e.getMessage(), e);
        }
        return iconDrawable;
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    public interface PermissionCallback {
        void onPermissionResult(String[] permissions, int[] grantResults);
    }

    private class PermissionsCallbackPair {
        String[] permissions;
        PermissionCallback callback;

        PermissionsCallbackPair(String[] permissions, PermissionCallback callback) {
            this.permissions = permissions;
            this.callback = callback;
        }
    }

    public void enableSwipeRefresh() {
        if (this.swipeRefreshLayout != null) {
            this.swipeRefreshLayout.setEnabled(true);
        }
    }

    public void restoreSwipRefreshDefault() {
        if (this.swipeRefreshLayout != null) {
            AppConfig appConfig = AppConfig.getInstance(this);
            this.swipeRefreshLayout.setEnabled(appConfig.pullToRefresh);
        }
    }

    public void deselectTabs() {
        this.bottomNavigationView.refresh();
    }

    private void listenForSignalStrength() {
        if (this.phoneStateListener != null) return;

        this.phoneStateListener = new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                latestSignalStrength = signalStrength;
                sendConnectivityOnce();
                if (connectivityCallback != null) {
                    sendConnectivity(connectivityCallback);
                }
            }
        };

        try {
            TelephonyManager telephonyManager = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) {
                Log.e(TAG, "Error getting system telephony manager");
            } else {
                telephonyManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listening for signal strength", e);
        }

    }

    public void sendConnectivityOnce(String callback) {
        if (callback == null) return;

        this.connectivityOnceCallback = callback;
        if (this.phoneStateListener != null) {
            sendConnectivity(callback);
        } else {
            listenForSignalStrength();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendConnectivityOnce();
                }
            }, 500);
        }
    }

    private void sendConnectivityOnce() {
        if (this.connectivityOnceCallback == null) return;
        sendConnectivity(this.connectivityOnceCallback);
        this.connectivityOnceCallback = null;
    }

    private void sendConnectivity(String callback) {
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean connected = activeNetwork != null && activeNetwork.isConnected();
        String typeString;
        if (activeNetwork != null) {
            typeString = activeNetwork.getTypeName();
        } else {
            typeString = "DISCONNECTED";
        }

        try {
            JSONObject data = new JSONObject();
            data.put("connected", connected);
            data.put("type", typeString);

            if (this.latestSignalStrength != null) {
                JSONObject signalStrength = new JSONObject();

                signalStrength.put("cdmaDbm", latestSignalStrength.getCdmaDbm());
                signalStrength.put("cdmaEcio", latestSignalStrength.getCdmaEcio());
                signalStrength.put("evdoDbm", latestSignalStrength.getEvdoDbm());
                signalStrength.put("evdoEcio", latestSignalStrength.getEvdoEcio());
                signalStrength.put("evdoSnr", latestSignalStrength.getEvdoSnr());
                signalStrength.put("gsmBitErrorRate", latestSignalStrength.getGsmBitErrorRate());
                signalStrength.put("gsmSignalStrength", latestSignalStrength.getGsmSignalStrength());
                if (Build.VERSION.SDK_INT >= 23) {
                    signalStrength.put("level", latestSignalStrength.getLevel());
                }
                data.put("cellSignalStrength", signalStrength);
            }

            String js = LeanUtils.createJsForCallback(callback, data);
            runJavascript(js);
        } catch (JSONException e) {
            Log.e(TAG, "JSON error sending connectivity", e);
        }
    }

    public void subscribeConnectivity(final String callback) {
        this.connectivityCallback = callback;
        listenForSignalStrength();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                sendConnectivity(callback);
            }
        }, 500);
    }

    public void unsubscribeConnectivity() {
        this.connectivityCallback = null;
    }

    public interface GeolocationPermissionCallback {
        void onResult(boolean granted);
    }

    // set brightness to a negative number to restore default
    public void setBrightness(float brightness) {
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = brightness;
        getWindow().setAttributes(layout);
    }

    public void setRemoveExcessWebView(boolean removeExcessWebView){
        this.removeExcessWebView = removeExcessWebView;
    }

    public int getWebViewCount(){
        return webViewCount;
    }

    public void setSidebarNavigationEnabled(boolean enabled){
        sidebarNavigationEnabled = enabled;
        setDrawerEnabled(enabled);
    }

    public void setupAppTheme() {
        WebSettings settings = this.mWebview.getSettings();

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {

            switch (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) {
                case Configuration.UI_MODE_NIGHT_YES:
                    WebSettingsCompat.setForceDark(this.mWebview.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
                    break;
                case Configuration.UI_MODE_NIGHT_NO:
                case Configuration.UI_MODE_NIGHT_UNDEFINED:
                    WebSettingsCompat.setForceDark(this.mWebview.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
                    break;
            }

            // Force dark on if supported, and only use theme from web
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(
                        settings,
                        WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                );
            }
        }
    }
}
