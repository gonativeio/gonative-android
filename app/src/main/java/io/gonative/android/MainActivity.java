package io.gonative.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
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
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.aurelhubert.ahbottomnavigation.AHBottomNavigation;
import com.squareup.seismic.ShakeDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.CookieHandler;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;
import java.util.UUID;
import java.util.regex.Pattern;

import io.gonative.android.files.CapturedImageSaver;
import io.gonative.gonative_core.AppConfig;
import io.gonative.android.widget.GoNativeDrawerLayout;
import io.gonative.android.widget.GoNativeSwipeRefreshLayout;
import io.gonative.android.widget.SwipeHistoryNavigationLayout;
import io.gonative.android.widget.WebViewContainerView;
import io.gonative.gonative_core.GNLog;
import io.gonative.gonative_core.GoNativeActivity;
import io.gonative.gonative_core.GoNativeWebviewInterface;
import io.gonative.gonative_core.LeanUtils;

public class MainActivity extends AppCompatActivity implements Observer,
        GoNativeActivity,
        GoNativeSwipeRefreshLayout.OnRefreshListener,
        ShakeDetector.Listener,
        ShakeDialogFragment.ShakeDialogListener {
    public static final String BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED = "io.gonative.android.MainActivity.Extra.BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED";
    private static final String webviewDatabaseSubdir = "webviewDatabase";
	private static final String TAG = MainActivity.class.getName();
    public static final String INTENT_TARGET_URL = "targetUrl";
    public static final String EXTRA_WEBVIEW_WINDOW_OPEN = "io.gonative.android.MainActivity.Extra.WEBVIEW_WINDOW_OPEN";
    public static final String EXTRA_NEW_ROOT_URL = "newRootUrl";
    public static final String EXTRA_EXCESS_WINDOW_ID = "excessWindowId";
    public static final String EXTRA_IGNORE_INTERCEPT_MAXWINDOWS = "ignoreInterceptMaxWindows";
	public static final int REQUEST_SELECT_FILE = 100;
    private static final int REQUEST_PERMISSION_READ_EXTERNAL_STORAGE = 101;
    private static final int REQUEST_PERMISSION_GEOLOCATION = 102;
    private static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 103;
    private static final int REQUEST_PERMISSION_GENERIC = 199;
    private static final int REQUEST_WEBFORM = 300;
    public static final int REQUEST_WEB_ACTIVITY = 400;
    public static final int GOOGLE_SIGN_IN = 500;
    private static final String ON_RESUME_CALLBACK = "gonative_app_resumed";

    private static final String SAVED_STATE_ACTIVITY_ID = "activityId";
    private static final String SAVED_STATE_IS_ROOT = "isRoot";
    private static final String SAVED_STATE_URL_LEVEL = "urlLevel";
    private static final String SAVED_STATE_PARENT_URL_LEVEL = "parentUrlLevel";
    private static final String SAVED_STATE_SCROLL_X = "scrollX";
    private static final String SAVED_STATE_SCROLL_Y = "scrollY";
    private static final String SAVED_STATE_WEBVIEW_STATE = "webViewState";
    private static final String SAVED_STATE_IGNORE_THEME_SETUP = "ignoreThemeSetup";

    private boolean isActivityPaused = false;

    private WebViewContainerView mWebviewContainer;
    private GoNativeWebviewInterface mWebview;
    private View webviewOverlay;
    boolean isPoolWebview = false;
    private Stack<String> backHistory = new Stack<>();
    private String initialUrl;
    private boolean sidebarNavigationEnabled = true;

	private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> uploadMessageLP;
    private Uri directUploadImageUri;
	private GoNativeDrawerLayout mDrawerLayout;
	private View mDrawerView;
	private ExpandableListView mDrawerList;
    private ProgressBar mProgress;
    private MySwipeRefreshLayout swipeRefreshLayout;
    private SwipeHistoryNavigationLayout swipeNavLayout;
    private RelativeLayout fullScreenLayout;
    private JsonMenuAdapter menuAdapter = null;
	private ActionBarDrawerToggle mDrawerToggle;
    private AHBottomNavigation bottomNavigationView;
	private ConnectivityManager cm = null;
    private ProfilePicker profilePicker = null;
    private TabManager tabManager;
    private ActionManager actionManager;
    private boolean isRoot;
    private float hideWebviewAlpha = 0.0f;
    private boolean isFirstHideWebview = true;
    private boolean webviewIsHidden = false;
    private Handler handler = new Handler();
    private Menu mOptionsMenu;
    private String activityId;

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
    private FileDownloader fileDownloader;
    private FileWriterSharer fileWriterSharer;
    private boolean startedLoading = false; // document readystate checker
    private LoginManager loginManager;
    private RegistrationManager registrationManager;
    private ConnectivityChangeReceiver connectivityReceiver;
    private KeyboardManager keyboardManager;
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
    private boolean restoreBrightnessOnNavigation = false;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private String deviceInfoCallback = "";
    private boolean flagThemeConfigurationChange = false;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        final AppConfig appConfig = AppConfig.getInstance(this);
        GoNativeApplication application = (GoNativeApplication)getApplication();
        GoNativeWindowManager windowManager = application.getWindowManager();

        if(appConfig.androidFullScreen){
            toggleFullscreen(true);
        }
        // must be done AFTER toggleFullScreen to force screen orientation
        setScreenOrientationPreference();

        if (appConfig.keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        this.hideWebviewAlpha  = appConfig.hideWebviewAlpha;

        // App theme setup
        ConfigPreferences configPreferences = new ConfigPreferences(this);
        String appTheme = configPreferences.getAppTheme();

        if (TextUtils.isEmpty(appTheme)) {
            if (!TextUtils.isEmpty(appConfig.androidTheme)) {
                appTheme = appConfig.androidTheme;
            } else {
                appTheme = "light"; // default is 'light' to support apps with no night assets provided
            }
            configPreferences.setAppTheme(appTheme);
        }

        boolean ignoreThemeUpdate = false;
        if (savedInstanceState != null) {
            ignoreThemeUpdate = savedInstanceState.getBoolean(SAVED_STATE_IGNORE_THEME_SETUP, false);
        }

        if (ignoreThemeUpdate) {
            // Ignore app theme setup cause its already called from function setupAppTheme()
            Log.d("GNDebug", "onCreate: configuration change from setupAppTheme(), ignoring theme setup");
        } else {
            if ("light".equals(appTheme)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if ("dark".equals(appTheme)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else if ("auto".equals(appTheme)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            } else {
                // default
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                configPreferences.setAppTheme("light");
            }
        }

        super.onCreate(savedInstanceState);

        this.activityId = UUID.randomUUID().toString();
        this.isRoot = getIntent().getBooleanExtra("isRoot", true);
        int urlLevel = getIntent().getIntExtra("urlLevel", -1);
        int parentUrlLevel = getIntent().getIntExtra("parentUrlLevel", -1);

        if (savedInstanceState != null) {
            this.activityId = savedInstanceState.getString(SAVED_STATE_ACTIVITY_ID, activityId);
            this.isRoot = savedInstanceState.getBoolean(SAVED_STATE_IS_ROOT, isRoot);
            urlLevel = savedInstanceState.getInt(SAVED_STATE_URL_LEVEL, urlLevel);
            parentUrlLevel = savedInstanceState.getInt(SAVED_STATE_PARENT_URL_LEVEL, parentUrlLevel);
        }

        application.mBridge.onActivityCreate(this, isRoot);

        windowManager.addNewWindow(activityId, isRoot);
        windowManager.setUrlLevels(activityId, urlLevel, parentUrlLevel);

        if (appConfig.maxWindowsEnabled) {
            windowManager.setIgnoreInterceptMaxWindows(activityId, getIntent().getBooleanExtra(EXTRA_IGNORE_INTERCEPT_MAXWINDOWS, false));
        }

        if (isRoot) {
            initialRootSetup();
        }

        this.loginManager = application.getLoginManager();

        this.fileWriterSharer = new FileWriterSharer(this);
        this.fileDownloader = new FileDownloader(this);

        // webview pools
        application.getWebViewPool().init(this);

		cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        setContentView(R.layout.activity_gonative);

        mProgress = findViewById(R.id.progress);
        this.fullScreenLayout = findViewById(R.id.fullscreen);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setEnabled(appConfig.pullToRefresh);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setCanChildScrollUpCallback(() -> mWebview.getWebViewScrollY() > 0);

        if (isAndroidGestureEnabled()) {
            appConfig.swipeGestures = false;
        }
        swipeNavLayout = findViewById(R.id.swipe_history_nav);
        swipeNavLayout.setEnabled(appConfig.swipeGestures);
        swipeNavLayout.setSwipeNavListener(new SwipeHistoryNavigationLayout.OnSwipeNavListener() {
            @Override
            public boolean canSwipeLeftEdge() {
                if (mWebview.getMaxHorizontalScroll() > 0) {
                    if (mWebview.getScrollX() > 0) return false;
                }
                return canGoBack();
            }
    
            @Override
            public boolean canSwipeRightEdge() {
                if (mWebview.getMaxHorizontalScroll() > 0) {
                    if (mWebview.getScrollX() < mWebview.getMaxHorizontalScroll()) return false;
                }
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

        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.pull_to_refresh_color));
        swipeNavLayout.setActiveColor(getResources().getColor(R.color.pull_to_refresh_color));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(getResources().getColor(R.color.swipe_nav_background));
        swipeNavLayout.setBackgroundColor(getResources().getColor(R.color.swipe_nav_background));

        this.webviewOverlay = findViewById(R.id.webviewOverlay);
        this.mWebviewContainer = this.findViewById(R.id.webviewContainer);
        this.mWebview = this.mWebviewContainer.getWebview();
        this.mWebviewContainer.setupWebview(this, isRoot);
        setupWebviewTheme(appTheme);

        boolean isWebViewStateRestored = false;
        if (savedInstanceState != null) {
            Bundle webViewStateBundle = savedInstanceState.getBundle(SAVED_STATE_WEBVIEW_STATE);
            if (webViewStateBundle != null) {
                // Restore page and history
                mWebview.restoreStateFromBundle(webViewStateBundle);
                isWebViewStateRestored = true;
            }

            // Restore scroll state
            int scrollX = savedInstanceState.getInt(SAVED_STATE_SCROLL_X, 0);
            int scrollY = savedInstanceState.getInt(SAVED_STATE_SCROLL_Y, 0);
            mWebview.scrollTo(scrollX, scrollY);
        }

        // profile picker
        if (isRoot && (appConfig.showActionBar || appConfig.showNavigationMenu)) {
            setupProfilePicker();
        }

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
        if (!appConfig.showActionBar && !appConfig.showNavigationMenu) {
            getSupportActionBar().hide();
        }

        if (!appConfig.showLogoInSideBar && !appConfig.showAppNameInSideBar) {
            RelativeLayout headerLayout = findViewById(R.id.header_layout);
            if (headerLayout != null) {
                headerLayout.setVisibility(View.GONE);
            }
        }

        if (!appConfig.showLogoInSideBar) {
            ImageView appIcon = findViewById(R.id.app_logo);
            if (appIcon != null) {
                appIcon.setVisibility(View.GONE);
            }
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
        this.actionManager.setupActionBar(isRoot);

        // overflow menu icon color
        if (toolbar!= null && toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().setColorFilter(getResources().getColor(R.color.titleTextColor), PorterDuff.Mode.SRC_ATOP);
        }

        // load url
        String url;

        if (isWebViewStateRestored) {
            // WebView already has loaded URL when function mWebview.restoreStateFromBundle() was called
            url = mWebview.getUrl();
        } else {
            Intent intent = getIntent();
            url = getUrlFromIntent(intent);

            if (url == null && isRoot) url = appConfig.getInitialUrl();
            // url from intent (hub and spoke nav)
            if (url == null) url = intent.getStringExtra("url");

            if (url != null) {

                // let plugins add query params to url before loading to WebView
                Map<String, String> queries = application.mBridge.getInitialUrlQueryItems(this, isRoot);
                if (queries != null && !queries.isEmpty()) {
                    Uri.Builder builder = Uri.parse(url).buildUpon();
                    for (Map.Entry<String, String> entry : queries.entrySet()) {
                        builder.appendQueryParameter(entry.getKey(), entry.getValue());
                    }
                    url = builder.build().toString();
                }

                this.initialUrl = url;
                this.mWebview.loadUrl(url);
            } else if (intent.getBooleanExtra(EXTRA_WEBVIEW_WINDOW_OPEN, false)) {
                // no worries, loadUrl will be called when this new web view is passed back to the message
            } else {
                GNLog.getInstance().logError(TAG, "No url specified for MainActivity");
            }
        }

        showNavigationMenu(isRoot && appConfig.showNavigationMenu);

        actionManager.setupTitleDisplayForUrl(url);

        updateStatusBarOverlay(appConfig.enableOverlayInStatusBar);
        updateStatusBarStyle(appConfig.statusBarStyle);

        this.keyboardManager = new KeyboardManager(this, this.findViewById(android.R.id.content));

        // style sidebar
        if (mDrawerView != null) {
            mDrawerView.setBackgroundColor(getResources().getColor(R.color.sidebarBackground));
        }

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

                    String excessWindowId = intent.getStringExtra(EXTRA_EXCESS_WINDOW_ID);
                    if (!TextUtils.isEmpty(excessWindowId)) {
                        if (excessWindowId.equals(activityId)) finish();
                        return;
                    }

                    boolean isActivityRoot = getGNWindowManager().isRoot(activityId);
                    if (!isActivityRoot) {
                        finish();
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(this.webviewLimitReachedReceiver,
                new IntentFilter(BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED));
    

        application.mBridge.onSendInstallationInfo(this, Installation.getInfo(this), mWebview.getUrl());

        validateGoogleService();

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            runGonativeDeviceInfo(deviceInfoCallback, false);
        });
    }

    public String getActivityId() {
        return this.activityId;
    }

    private void initialRootSetup() {
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
        this.registrationManager = ((GoNativeApplication) getApplication()).getRegistrationManager();
    }

    private void setupProfilePicker() {
        Spinner profileSpinner = findViewById(R.id.profile_picker);
        profilePicker = new ProfilePicker(this, profileSpinner);

        Spinner segmentedSpinner = findViewById(R.id.segmented_control);
        new SegmentedController(this, segmentedSpinner);
    }

    private void showNavigationMenu(boolean showNavigation) {
        AppConfig appConfig = AppConfig.getInstance(this);
        // do the list stuff
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerView = findViewById(R.id.left_drawer);
        mDrawerList = findViewById(R.id.drawer_list);

        if (showNavigation) {

            // unlock drawer
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

            // set shadow
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                    R.string.drawer_open, R.string.drawer_close) {
                //Called when a drawer has settled in a completely closed state.
                public void onDrawerClosed(View view) {
                    invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                    mDrawerLayout.setDisableTouch(appConfig.swipeGestures && canGoBack());
                }

                //Called when a drawer has settled in a completely open state.
                public void onDrawerOpened(View drawerView) {
                    invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                    mDrawerLayout.setDisableTouch(false);
                }
            };

            mDrawerToggle.setDrawerIndicatorEnabled(true);
            mDrawerToggle.getDrawerArrowDrawable().setColor(getResources().getColor(R.color.titleTextColor));

            mDrawerLayout.addDrawerListener(mDrawerToggle);

            setupMenu();

            // update the menu
            if (appConfig.loginDetectionUrl != null) {
                this.loginManager.addObserver(this);
            }
        } else {
            // lock drawer so it could not be swiped
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
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
        this.isActivityPaused = true;
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

        if (isActivityPaused) {
            this.isActivityPaused = false;
            runJavascript(LeanUtils.createJsForCallback(ON_RESUME_CALLBACK, null));
        }

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
        GoNativeApplication application = (GoNativeApplication)getApplication();
        application.mBridge.onActivityStop(this);
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
        application.getWindowManager().removeWindow(activityId);

        if (fileDownloader != null) fileDownloader.unbindDownloadService();

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
    
    @Override
    public void onSubscriptionChanged() {
        if (registrationManager == null) return;
        registrationManager.subscriptionInfoChanged();
    }
    
    @Override
    public void launchNotificationActivity(String extra) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (extra != null && !extra.isEmpty()) {
            mainIntent.putExtra(INTENT_TARGET_URL, extra);
        }
        
        startActivity(mainIntent);
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
        // Saves current WebView's history and URL or loaded page state
        Bundle webViewOutState = new Bundle();
        mWebview.saveStateToBundle(webViewOutState);
        outState.putBundle(SAVED_STATE_WEBVIEW_STATE, webViewOutState);

        // Save other WebView data
        outState.putString(SAVED_STATE_ACTIVITY_ID, activityId);
        outState.putBoolean(SAVED_STATE_IS_ROOT, getGNWindowManager().isRoot(activityId));
        outState.putInt(SAVED_STATE_URL_LEVEL, getGNWindowManager().getUrlLevel(activityId));
        outState.putInt(SAVED_STATE_PARENT_URL_LEVEL, getGNWindowManager().getParentUrlLevel(activityId));
        outState.putInt(SAVED_STATE_SCROLL_X, mWebview.getWebViewScrollX());
        outState.putInt(SAVED_STATE_SCROLL_Y, mWebview.getWebViewScrollY());
        if (flagThemeConfigurationChange) {
            outState.putBoolean(SAVED_STATE_IGNORE_THEME_SETUP, true);
        }

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

    @Override
    public void sharePage(String optionalUrl, String optionalText) {
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
        if (optionalText != null) {
            share.putExtra(Intent.EXTRA_SUBJECT, optionalText);
        }
        startActivity(Intent.createChooser(share, getString(R.string.action_share)));
    }

    private void logout() {
        this.mWebview.stopLoading();

        // log out by clearing all cookies and going to home page
        clearWebviewCookies();
 
        updateMenu(false);
        this.loginManager.checkLogin();
        this.mWebview.loadUrl(AppConfig.getInstance(this).getInitialUrl());
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

	@Override
	public void clearWebviewCache() {
        mWebview.clearCache(true);
    }

    @Override
    public void clearWebviewCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(aBoolean -> Log.d(TAG, "clearWebviewCookies: onReceiveValue callback: " + aBoolean));
        AsyncTask.THREAD_POOL_EXECUTOR.execute(cookieManager::flush);
    }

    @Override
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
        this.isFirstHideWebview = false;
        webviewIsHidden = false;
        startedLoading = false;
        stopCheckingReadyStatus();
        this.webviewOverlay.setAlpha(0.0f);
        this.mProgress.setVisibility(View.INVISIBLE);

        injectCSSviaJavascript();
        injectJSviaJavascript();
    }

    @Override
    public void showWebview() {
        this.isFirstHideWebview = false;
        startedLoading = false;
        stopCheckingReadyStatus();

        if (!webviewIsHidden) {
            // don't animate if already visible
            mProgress.setVisibility(View.INVISIBLE);
            return;
        }

        injectCSSviaJavascript();
        injectJSviaJavascript();

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
            String encoded = Base64.encodeToString(builder.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
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
            GNLog.getInstance().logError(TAG, "Error injecting customCSS via javascript", e);
        }
    }

    private void injectJSviaJavascript() {
        AppConfig appConfig = AppConfig.getInstance(this);
        if ((appConfig.customJS == null || appConfig.customJS.isEmpty())
                && (appConfig.androidCustomJS == null || appConfig.androidCustomJS.isEmpty())) return;

        try {
            StringBuilder builder = new StringBuilder();
            if(appConfig.customJS != null)
                builder.append(appConfig.customJS).append(" ");
            if(appConfig.androidCustomJS != null)
                builder.append(appConfig.androidCustomJS);

            String encoded = Base64.encodeToString(builder.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            String js = "javascript:(function() {" +
                    "var parent = document.getElementsByTagName('head').item(0);" +
                    "var script = document.createElement('script');" +
                    "script.type = 'text/javascript';" +
                    "script.innerHTML = window.atob('" + encoded + "');" +
                    "parent.appendChild(script)" +
                    "})()";
            runJavascript(js);
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, "Error injecting customJS via javascript", e);
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

    @Override
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
            GNLog.getInstance().logError(TAG, e.getMessage(), e);
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
            GNLog.getInstance().logError(TAG, "Error setting up menu", e);
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
        this.actionManager.setupActionBarDisplay();

        GoNativeApplication application = (GoNativeApplication)getApplication();
     // Pass any configuration change to the drawer toggles
        if (mDrawerToggle != null)
            mDrawerToggle.onConfigurationChanged(newConfig);
//        if (swipeRefreshLayout != null)
//       TODO     swipeRefreshLayout.onConfigurationChanged(newConfig);
        application.mBridge.onConfigurationChange(this);
    }

	@Override
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
                this.mWebview.loadUrl(AppConfig.getInstance(this).getInitialUrl());
            }

            if (AppConfig.getInstance(this).showNavigationMenu) {
                updateMenu(success);
            }
        }

        if (requestCode == REQUEST_WEB_ACTIVITY && resultCode == RESULT_OK) {
            if (url != null) {
                int urlLevel = data.getIntExtra("urlLevel", -1);
                int parentUrlLevel = getGNWindowManager().getParentUrlLevel(activityId);
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
                Uri currentCaptureUri = new CapturedImageSaver().saveCapturedBitmap(this, this.directUploadImageUri);
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(currentCaptureUri);
                    mUploadMessage = null;
                }
                if (uploadMessageLP != null) {
                    uploadMessageLP.onReceiveValue(new Uri[]{currentCaptureUri});
                    uploadMessageLP = null;
                }
                getContentResolver().delete(this.directUploadImageUri, null, null);
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
            if (!urlEqualsIgnoreSlash(url, mWebview.getUrl()))
                loadUrl(url);
            return;
        }
        Log.w(TAG, "Received intent without url");

        ((GoNativeApplication) getApplication()).mBridge.onActivityNewIntent(this, intent);
    }

    private boolean urlEqualsIgnoreSlash(String url1, String url2) {
        if (url1 == null || url2 == null) return false;
        if (url1.endsWith("/")) {
            url1 = url1.substring(0, url1.length() - 1);
        }
        if (url2.endsWith("/")) {
            url2 = url2.substring(0, url2.length() - 1);
        }
        if (url1.startsWith("http://")) {
            url1 = "https://" + url1.substring(7);
        }
        return url1.equals(url2);
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
        this.mWebviewContainer.setupWebview(this, isRoot);

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
        mOptionsMenu = menu;

        if (this.actionManager != null) {
            this.actionManager.addActions(menu);
        }

		return true;
	}

    public Menu getOptionsMenu () {
        return mOptionsMenu;
    }

	public void setMenuItemsVisible (boolean visible) {
        setMenuItemsVisible(visible, null);
    }

	public void setMenuItemsVisible(boolean visible, MenuItem exception) {

        for (int i = 0; i < mOptionsMenu.size(); i++) {
            MenuItem item = mOptionsMenu.getItem(i);
            if (item == exception) {
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
        if (item.getItemId() == android.R.id.home) {
            if (this.actionManager.isOnSearchMode()) {
                this.actionManager.closeSearchView();
                this.actionManager.setOnSearchMode(false);
                return true;
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

    public void refreshPage() {
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

    // onPageFinished
    @Override
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
    }

    // onPageStarted
    @Override
    public void checkPreNavigationForPage(String url) {
        if (this.tabManager != null) {
            this.tabManager.autoSelectTab(url);
        }

        if (this.menuAdapter != null) {
            this.menuAdapter.autoSelectItem(url);
        }

        if (this.actionManager != null) {
            this.actionManager.cleanSidebarMenuTitleOffset();
        }

        AppConfig appConfig = AppConfig.getInstance(this);
        setDrawerEnabled(appConfig.shouldShowSidebarForUrl(url) && sidebarNavigationEnabled);

        // When current URL canGoBack and swipeGestures are enabled, disable touch events on DrawerLayout
        if (this.mDrawerLayout != null && this.mDrawerLayout.getDrawerLockMode(GravityCompat.START) != DrawerLayout.LOCK_MODE_LOCKED_CLOSED) {
            mDrawerLayout.setDisableTouch(appConfig.swipeGestures && canGoBack());
        }
    }

    public ActionManager getActionManager() {
        return this.actionManager;
    }

    @Override
    public void setupTitleDisplayForUrl(String url) {
        if (this.actionManager == null) return;
        this.actionManager.setupTitleDisplayForUrl(url);
    }

    @Override
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

    @Override
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

    @Override
    public int getParentUrlLevel() {
        return getGNWindowManager().getParentUrlLevel(activityId);
    }

    @Override
    public int getUrlLevel() {
        return getGNWindowManager().getUrlLevel(activityId);
    }

    @Override
    public void setUrlLevel(int urlLevel) {
        getGNWindowManager().setUrlLevel(activityId, urlLevel);
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

    public StatusCheckerBridge getStatusCheckerBridge() {
        return new StatusCheckerBridge();
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        if (actionManager != null) {
            actionManager.showTextActionBarTitle(title);
        }
    }

    @Override
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

    @Override
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

    public GoNativeWindowManager getGNWindowManager() {
        return ((GoNativeApplication) getApplication()).getWindowManager();
    }

    @Override
    public int getWindowCount() {
        return getGNWindowManager().getWindowCount();
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
        if (isLocationPermissionGranted()) {
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
        if (appConfig.forceScreenOrientation != null) {
            setDeviceOrientation(appConfig.forceScreenOrientation);
            return;
        }

        if (getResources().getBoolean(R.bool.isTablet)) {
            if (appConfig.tabletScreenOrientation != null) {
                setDeviceOrientation(appConfig.tabletScreenOrientation);
                return;
            }
        } else {
            if (appConfig.phoneScreenOrientation != null) {
                setDeviceOrientation(appConfig.phoneScreenOrientation);
                return;
            }
        }

        if (!appConfig.androidFullScreen) {
            setDeviceOrientation(AppConfig.ScreenOrientations.UNSPECIFIED);
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private void setDeviceOrientation(AppConfig.ScreenOrientations orientation) {
        switch (orientation) {
            case UNSPECIFIED:
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

    @Override
    public void deselectTabs() {
        this.bottomNavigationView.setCurrentItem(AHBottomNavigation.CURRENT_ITEM_NONE);
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
                GNLog.getInstance().logError(TAG, "Error getting system telephony manager");
            } else {
                telephonyManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        } catch (Exception e) {
            GNLog.getInstance().logError(TAG, "Error listening for signal strength", e);
        }

    }

    @Override
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
            GNLog.getInstance().logError(TAG, "JSON error sending connectivity", e);
        }
    }

    @Override
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

    @Override
    public void unsubscribeConnectivity() {
        this.connectivityCallback = null;
    }

    public interface GeolocationPermissionCallback {
        void onResult(boolean granted);
    }

    // set brightness to a negative number to restore default
    @Override
    public void setBrightness(float brightness) {
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = brightness;
        getWindow().setAttributes(layout);
    }

    @Override
    public void setSidebarNavigationEnabled(boolean enabled) {
        sidebarNavigationEnabled = enabled;
        setDrawerEnabled(enabled);
    }

    public GoNativeDrawerLayout getDrawerLayout() {
        return this.mDrawerLayout;
    }

    public ActionBarDrawerToggle getDrawerToggle() {
        return this.mDrawerToggle;
    }

    /**
     * @param appTheme set to null if will use sharedPreferences
     */

    @Override
    public void setupAppTheme(String appTheme) {
        ConfigPreferences preferences = new ConfigPreferences(this);
        preferences.setAppTheme(appTheme);

        // Updating app theme on runtime triggers a configuration change and recreates the app
        // To prevent consecutive calls, ignore theme setup on onCreate() by enabling this flag
        flagThemeConfigurationChange = true;

        if ("light".equals(appTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if ("dark".equals(appTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    @SuppressLint("RequiresFeature")
    private void setupWebviewTheme(String appTheme) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            Log.d(TAG, "Dark mode feature is not supported");
            return;
        }

        if (mWebview.getSettings() == null) {
            return;
        }

        if ("dark".equals(appTheme)) {
            WebSettingsCompat.setForceDark(this.mWebview.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
        } else if ("light".equals(appTheme)) {
            WebSettingsCompat.setForceDark(this.mWebview.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
        } else {
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
                        this.mWebview.getSettings(),
                        WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                );
            }
        }
    }

    private void validateGoogleService() {
        try {
            if (BuildConfig.GOOGLE_SERVICE_INVALID) {
                Toast.makeText(this, R.string.google_service_required, Toast.LENGTH_LONG).show();
                GNLog.getInstance().logError(TAG, "validateGoogleService: " + R.string.google_service_required, null, GNLog.TYPE_TOAST_ERROR);
            }
        } catch (NullPointerException ex) {
            GNLog.getInstance().logError(TAG, "validateGoogleService: " + ex.getMessage(), null, GNLog.TYPE_TOAST_ERROR);
        }
    }

    @SuppressLint("DiscouragedApi")
    private boolean isAndroidGestureEnabled() {
        if (Build.VERSION.SDK_INT < 29) return false;
        try {
            int resourceId = getResources().getIdentifier("config_navBarInteractionMode", "integer", "android");
            if (resourceId > 0) {
                // 0 : Navigation is displaying with 3 buttons
                // 1 : Navigation is displaying with 2 button(Android P navigation mode)
                // 2 : Full screen gesture(Gesture on android Q)
                return getResources().getInteger(resourceId) == 2;
            }
            return false;
        } catch (Resources.NotFoundException ex) {
            GNLog.getInstance().logError(TAG, "isAndroidGestureEnabled: ", ex);
            return false;
        }
    }

    @Override
    public void updateStatusBarOverlay(boolean isOverlayEnabled) {
        View decor = getWindow().getDecorView();
        if (isOverlayEnabled) {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() &
                    ~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN &
                    ~View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void updateStatusBarStyle(String statusBarStyle) {
        if (statusBarStyle != null && !statusBarStyle.isEmpty() && Build.VERSION.SDK_INT >= 23) {
            switch (statusBarStyle) {
                case "light": {
                    // light icons and text
                    View decor = getWindow().getDecorView();
                    decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    break;
                }
                case "dark": {
                    // dark icons and text
                    View decor = getWindow().getDecorView();
                    decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    break;
                }
                case "auto":
                    int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                        View decor = getWindow().getDecorView();
                        decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    } else if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) {
                        View decor = getWindow().getDecorView();
                        decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    } else {
                        GNLog.getInstance().logError(TAG, "updateStatusBarStyle: Current mode is undefined");
                    }
                    break;
            }
        }
    }

    @Override
    public void setStatusBarColor(int color) {
        getWindow().setStatusBarColor(color);
    }

    @Override
    public void runGonativeDeviceInfo(String callback, boolean includeCarrierNames) {
        if (includeCarrierNames) {
            deviceInfoCallback = callback;
            requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE);
        } else {
            Map<String, Object> installationInfo = Installation.getInfo(this);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (!sharedPreferences.getBoolean("hasLaunched", false)) {
                sharedPreferences.edit().putBoolean("hasLaunched", true).commit();
                installationInfo.put("isFirstLaunch", true);
            } else {
                installationInfo.put("isFirstLaunch", false);
            }

            JSONObject jsonObject = new JSONObject(installationInfo);
            String js = LeanUtils.createJsForCallback(callback, jsonObject);
            this.runJavascript(js);
        }
    }

    @Override
    public void windowFlag(boolean add, int flag) {
        if (add) {
            getWindow().addFlags(flag);
        } else {
            getWindow().clearFlags(flag);
        }
    }

    @Override
    public void setCustomTitle(String title) {
        if (!title.isEmpty()) {
            setTitle(title);
        } else {
            setTitle(R.string.app_name);
        }
    }

    @Override
    public void downloadFile(String url, String filename, boolean shouldSaveToGallery, boolean open) {
        fileDownloader.downloadFile(url, filename, shouldSaveToGallery, open);
    }

    @Override
    public void selectTab(int tabNumber) {
        if (tabManager == null) return;
        tabManager.selectTabNumber(tabNumber, false);
    }

    @Override
    public void setTabsWithJson(JSONObject tabsJson, int tabMenuId) {
        if (tabManager == null) return;
        tabManager.setTabsWithJson(tabsJson, tabMenuId);
    }

    @Override
    public void focusAudio(boolean enabled) {
        if (enabled) {
            AudioUtils.requestAudioFocus(this);
        } else {
            AudioUtils.abandonFocusRequest(this);
        }
    }

    @Override
    public void clipboardSet(String content) {
        if (content.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("copy", content);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    public void clipboardGet(String callback) {
        if (!TextUtils.isEmpty(callback)) {
            Map<String, String> params = new HashMap<>();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            CharSequence pasteData;
            if (clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                pasteData = item.getText();
                if (pasteData != null)
                    params.put("data", pasteData.toString());
                else
                    params.put("error", "Clipboard item is not a string.");
            } else {
                params.put("error", "No Clipboard item available.");
            }
            JSONObject jsonObject = new JSONObject(params);
            runJavascript(LeanUtils.createJsForCallback(callback, jsonObject));
        }
    }

    @Override
    public void sendRegistration(JSONObject data) {
        if(registrationManager == null) return;

        if(data != null){
            JSONObject customData = data.optJSONObject("customData");
            if(customData == null){
                try { // try converting json string from url to json object
                    customData = new JSONObject(data.optString("customData"));
                } catch (JSONException e){
                    GNLog.getInstance().logError(TAG, "GoNative Registration JSONException:- " + e.getMessage(), e);
                }
            }
            if(customData != null){
                registrationManager.setCustomData(customData);
            }
        }
        registrationManager.sendToAllEndpoints();
    }

    @Override
    public void runCustomNativeBridge(Map<String, String> params) {
        // execute code defined by the CustomCodeHandler
        // call JsCustomCodeExecutor#setHandler to override this default handler
        JSONObject data = JsCustomCodeExecutor.execute(params);
        String callback = params.get("callback");
        if(callback != null && !callback.isEmpty()) {
            final String js = LeanUtils.createJsForCallback(callback, data);
            // run on main thread
            Handler mainHandler = new Handler(getMainLooper());
            mainHandler.post(() -> runJavascript(js));
        }
    }

    @Override
    public void promptLocationService() {
        getRuntimeGeolocationPermission(granted -> Log.d(TAG, "promptLocationService: " + granted));
    }

    @Override
    public boolean isLocationServiceEnabled() {

        if (!isLocationPermissionGranted()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager lm = getSystemService(LocationManager.class);
            return lm.isLocationEnabled();
        } else {
            // This is Deprecated in API 28
            int mode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
            return  (mode != Settings.Secure.LOCATION_MODE_OFF);
        }
    }

    private boolean isLocationPermissionGranted() {
            int checkFine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
            int checkCoarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION);
            return checkFine == PackageManager.PERMISSION_GRANTED && checkCoarse == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void setRestoreBrightnessOnNavigation(boolean restore) {
        this.restoreBrightnessOnNavigation = restore;
    }

    public boolean isRestoreBrightnessOnNavigation() {
        return this.restoreBrightnessOnNavigation;
    }



    public Object getJavascriptBridge() {
        GoNativeApplication application = (GoNativeApplication)getApplication();
        return application.mBridge.getJavaScriptBridge();
    }

    @Override
    public void closeCurrentWindow() {
        if (!getGNWindowManager().isRoot(activityId)) {
            this.finish();
        }
    }

    @Override
    public void openNewWindow(String url) {
        if (TextUtils.isEmpty(url)) return;
        AppConfig appConfig = AppConfig.getInstance(this);

        // Check maxWindows conditions
        if (appConfig.maxWindowsEnabled && appConfig.numWindows > 0 && getGNWindowManager().getWindowCount() >= appConfig.numWindows && onMaxWindowsReached(url))
            return;

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("isRoot", false);
        intent.putExtra("url", url);
        intent.putExtra(MainActivity.EXTRA_IGNORE_INTERCEPT_MAXWINDOWS, true);
        startActivityForResult(intent, MainActivity.REQUEST_WEB_ACTIVITY);
    }

    @Override      
    public boolean onMaxWindowsReached(String url) {
        AppConfig appConfig = AppConfig.getInstance(this);
        GoNativeWindowManager windowManager = getGNWindowManager();

        if (appConfig.autoClose && LeanUtils.urlsMatchIgnoreTrailing(url, appConfig.getInitialUrl())) {

            // Set this activity as new root
            isRoot = true;

            windowManager.setAsNewRoot(activityId);

            // Reset URL levels
            windowManager.setUrlLevels(activityId, -1, -1);

            // Reload activity as root
            initialRootSetup();
            if (appConfig.showActionBar || appConfig.showNavigationMenu) {
                setupProfilePicker();
            }

            showNavigationMenu(appConfig.showNavigationMenu);

            if (actionManager != null) {
                actionManager.setupActionBar(isRoot);
                actionManager.setupTitleDisplayForUrl(url);
            }

            if (mDrawerToggle != null && appConfig.showNavigationMenu) {
                mDrawerToggle.syncState();
            }

            windowManager.setIgnoreInterceptMaxWindows(activityId, true);

            // Send broadcast to close other activity
            Intent intent = new Intent(MainActivity.BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED);
            intent.putExtra(MainActivity.EXTRA_NEW_ROOT_URL, url);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            // Add listener when all excess windows are closed
            windowManager.setOnExcessWindowClosedListener(() -> {
                // Load new URL
                mWebview.loadUrl(url);
                // Remove listener
                windowManager.setOnExcessWindowClosedListener(null);
            });

            return true;
        } else {

            // Get excess window
            String excessWindowId = windowManager.getExcessWindow();

            // Send broadcast to close the excess window
            Intent intent = new Intent(MainActivity.BROADCAST_RECEIVER_ACTION_WEBVIEW_LIMIT_REACHED);
            intent.putExtra(MainActivity.EXTRA_EXCESS_WINDOW_ID, excessWindowId);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            // Remove from window list
            windowManager.removeWindow(excessWindowId);
        }

        return false;
    }

    @Override
    public void getKeyboardInfo(String callback) {
        if (keyboardManager == null || TextUtils.isEmpty(callback)) return;
        runJavascript(LeanUtils.createJsForCallback(callback, keyboardManager.getKeyboardData()));
    }

    @Override
    public void addKeyboardListener(String callback) {
        if (keyboardManager == null) return;
        keyboardManager.setCallback(callback);
    }
}
