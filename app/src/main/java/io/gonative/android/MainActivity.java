package io.gonative.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.astuetz.PagerSlidingTabStrip;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends ActionBarActivity implements Observer {
    public static final String webviewCacheSubdir = "webviewAppCache";
    public static final String webviewDatabaseSubdir = "webviewDatabase";
	private static final String TAG = MainActivity.class.getName();
    public static final String INTENT_TARGET_URL = "targetUrl";
	private static final int REQUEST_SELECT_FILE = 100;
    private static final int REQUEST_WEBFORM = 300;
    public static final int REQUEST_WEB_ACTIVITY = 400;
    public static final int REQUEST_PUSH_NOTIFICATION = 500;
    public static final int REQUEST_PLAY_SERVICES_RESOLUTION = 9000;
    private static final float ACTIONBAR_ELEVATION = 12.0f;

    private LeanWebView mWebview;
    boolean isPoolWebview = false;
    private Stack<String> backHistory = new Stack<String>();

	private ValueCallback<Uri> mUploadMessage;
	private DrawerLayout mDrawerLayout;
	private View mDrawerView;
	private ExpandableListView mDrawerList;
    private ProgressBar mProgress;
    private JsonMenuAdapter menuAdapter = null;
	private ActionBarDrawerToggle mDrawerToggle;
    private PagerSlidingTabStrip slidingTabStrip;
    private ImageView navigationTitleImage;
	private ConnectivityManager cm = null;
    private ProfilePicker profilePicker = null;
    private TabManager tabManager;
    private ActionManager actionManager;
    private boolean isRoot;
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
    private ConnectivityChangeReceiver connectivityReceiver;
    protected String postLoadJavascript;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        AppConfig appConfig = AppConfig.getInstance(this);

        super.onCreate(savedInstanceState);

        isRoot = getIntent().getBooleanExtra("isRoot", true);
        parentUrlLevel = getIntent().getIntExtra("parentUrlLevel", -1);

        if (isRoot) {
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

            // Push notifications
            if (appConfig.pushNotifications) {
                this.pushManager = new PushManager(this);
                this.pushManager.register();
            }

            // webview pools
            WebViewPool.getInstance().init(this);
        }

        // WebView debugging
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Map<String,Object> installation = Installation.getInfo(this);
            String dist = (String)installation.get("distribution");
            if (dist != null && (dist.equals("debug") || dist.equals("adhoc"))) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }
		
		cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        if (isRoot && AppConfig.getInstance(this).showNavigationMenu)
	    	setContentView(R.layout.activity_gonative);
        else
            setContentView(R.layout.activity_gonative_nonav);

        mProgress = (ProgressBar) findViewById(R.id.progress);

		LeanWebView wv = (LeanWebView) findViewById(R.id.webview);

        // profile picker
        if (isRoot && AppConfig.getInstance(this).showNavigationMenu) {
            Spinner spinner = (Spinner) findViewById(R.id.profile_picker);
            profilePicker = new ProfilePicker(this, spinner);
        }

		// to save webview cookies to permanent storage
		CookieSyncManager.createInstance(getApplicationContext());
		
		// proxy cookie manager for httpUrlConnection (syncs to webview cookies)
		CookieHandler.setDefault(new WebkitCookieManagerProxy());

        this.mWebview = wv;
		setupWebview(wv);

        this.postLoadJavascript = getIntent().getStringExtra("postLoadJavascript");

        // load url
        String url = null;
        // first check intent in case it was created from push notification
        String targetUrl = getIntent().getStringExtra(INTENT_TARGET_URL);
        if (targetUrl != null && !targetUrl.isEmpty()){
            url = targetUrl;
        }
        if (url == null && savedInstanceState != null) url = savedInstanceState.getString("url");
        if (url == null && isRoot) url = appConfig.initialUrl;
        // url from intent (hub and spoke nav)
        if (url == null) url = getIntent().getStringExtra("url");

        if (url != null) {
            wv.loadUrl(url);
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
            if (!isRoot || AppConfig.getInstance(this).showNavigationMenu) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }

            showLogoInActionBar(appConfig.shouldShowNavigationTitleImageForUrl(url));
        }

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

        // style sidebar
        if (mDrawerView != null && AppConfig.getInstance(this).sidebarBackgroundColor != null) {
            mDrawerView.setBackgroundColor(AppConfig.getInstance(this).sidebarBackgroundColor);
        }
	}

    protected void onPause() {
        super.onPause();
        stopCheckingReadyStatus();
        this.mWebview.onPause();

        // unregister connectivity
        if (this.connectivityReceiver != null) {
            unregisterReceiver(this.connectivityReceiver);
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
                parent.removeView(this.mWebview);
            }
            this.mWebview.destroy();
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
    }

    public boolean canGoBack() {
        return this.backHistory.size() >= 2;
    }

    public void goBack() {
        this.backHistory.pop();
        loadUrl(this.backHistory.pop());
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
        if (url.equalsIgnoreCase("gonative_logout"))
            logout();
        else
            this.mWebview.loadUrl(url);

        if (this.tabManager != null) this.tabManager.selectTab(url, null);
    }

    public void loadUrlAndJavascript(String url, String javascript) {
        String currentUrl = this.mWebview.getUrl();

        if (url != null && currentUrl != null && url.equals(currentUrl)) {
            hideWebview();
            runJavascript(javascript);
            showWebview();
        } else {
            this.postLoadJavascript = javascript;
            loadUrl(url);
        }

        if (this.tabManager != null) this.tabManager.selectTab(url, javascript);
    }

    public void runJavascript(String javascript) {
        if (javascript == null) return;

        LeanUtils.runJavascriptOnWebView(this.mWebview, javascript);
    }
	
	public boolean isConnected(){
		NetworkInfo ni = cm.getActiveNetworkInfo();
		return ni != null && ni.isConnected();
	}
		
	
	// configures webview settings
	private void setupWebview(WebView wv){
        LeanUtils.setupWebview(wv, this);

        wv.setWebChromeClient(new CustomWebChromeClient());
        wv.setWebViewClient(new LeanWebviewClient(MainActivity.this));
        wv.setDownloadListener(fileDownloader);

        wv.removeJavascriptInterface("gonative_profile_picker");
        if (profilePicker != null) {
            wv.addJavascriptInterface(profilePicker.getProfileJsBridge(), "gonative_profile_picker");
        }

        wv.removeJavascriptInterface("gonative_dynamic_update");
        if (AppConfig.getInstance(this).updateConfigJS != null) {
            wv.addJavascriptInterface(AppConfig.getInstance(this).getJsBridge(), "gonative_dynamic_update");
        }

        wv.removeJavascriptInterface("gonative_status_checker");
        wv.addJavascriptInterface(new StatusCheckerBridge(), "gonative_status_checker");
	}

    public void hideWebview() {
        mProgress.setAlpha(1.0f);
        mProgress.setVisibility(View.VISIBLE);

        this.mWebview.setVisibility(View.INVISIBLE);
    }

    public void showWebview(double delay) {
        if (delay > 0) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showWebview();
                }
            }, (int)(delay * 1000));
        } else {
            showWebview();
        }
    }

    public void showWebviewImmediately() {
        startedLoading = false;
        stopCheckingReadyStatus();
        this.mWebview.setVisibility(View.VISIBLE);
        this.mProgress.setVisibility(View.INVISIBLE);
    }

    public void showWebview() {
        startedLoading = false;
        stopCheckingReadyStatus();

        final WebView wv = this.mWebview;
        if (wv.getVisibility() == View.VISIBLE) {
            // don't animate if already visible
            mProgress.setVisibility(View.INVISIBLE);
            return;
        }

        Animation fadein = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        Animation fadeout = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
        fadein.setDuration(300);
        fadeout.setDuration(60);
        fadein.setStartOffset(150);


        mProgress.setAlpha(1.0f);

        wv.setVisibility(View.VISIBLE);

        fadeout.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mProgress.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        mProgress.startAnimation(fadeout);
        wv.startAnimation(fadein);
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
            if (null == mUploadMessage)
                return;

            if (resultCode == RESULT_OK) {
                Uri selectedImageUri = data == null ? null : data.getData();
                mUploadMessage.onReceiveValue(selectedImageUri);
                mUploadMessage = null;
            }
        }
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
			if (isDrawerOpen()){
				mDrawerLayout.closeDrawers();
				return true;
			}
            else if (canGoBack()) {
                goBack();
                return true;
            }
		}

		return super.onKeyDown(keyCode, event);
	}

    public void switchToWebview(LeanWebView newWebview, boolean isPoolWebview) {
        setupWebview(newWebview);

        // scroll to top
        newWebview.scrollTo(0, 0);

        LeanWebView prev = this.mWebview;

        // replace the current web view in the parent with the new view
        if (newWebview != prev) {
            // a view can only have one parent, and attempting to add newWebview if it already has
            // a parent will cause a runtime exception. So be extra safe by removing it from its parent.
            ViewParent temp = newWebview.getParent();
            if (temp instanceof  ViewGroup) {
                ((ViewGroup) temp).removeView(newWebview);
            }

            ViewGroup parent = (ViewGroup) prev.getParent();
            int index = parent.indexOfChild(prev);
            parent.removeView(prev);
            parent.addView(newWebview, index);
            newWebview.setLayoutParams(prev.getLayoutParams());

            if (!this.isPoolWebview) prev.destroy();
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
	        	if (this.mWebview.getUrl() != null && this.mWebview.getUrl().startsWith("data:")){
                    this.mWebview.goBack();
	        		updateMenu();
	        	}
	        	else {
                    this.mWebview.reload();
	        	}
	        	return true;
        	default:
                return super.onOptionsItemSelected(item);	        		
        }
    }

    public void launchWebForm(String formName, String title) {
        Intent intent = new Intent(getBaseContext(), WebFormActivity.class);
        intent.putExtra(WebFormActivity.EXTRA_FORMNAME, formName);
        intent.putExtra(WebFormActivity.EXTRA_TITLE, title);
        startActivityForResult(intent, REQUEST_WEBFORM);
    }

    public void checkNavigationForPage(String url) {
        if (this.tabManager != null) {
            this.tabManager.checkTabs(url);
        }

        if (this.actionManager != null) {
            this.actionManager.checkActions(url);
        }
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

    protected class CustomWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result){
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            result.confirm();
            return true;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, AppConfig.getInstance(MainActivity.this).usesGeolocation, true);
        }

        // For Android > 4.1
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
            mUploadMessage = uploadMsg;

            if (acceptType == null) acceptType = "*/*";

            // Filesystem.
            final Intent galleryIntent = new Intent();
            galleryIntent.setType(acceptType);
            galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
            galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);

            startActivityForResult(galleryIntent, REQUEST_SELECT_FILE);
        }

        // Android 3.0 + 
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
            openFileChooser(uploadMsg, acceptType, null);
        }

        //Android 3.0
        public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            openFileChooser(uploadMsg, null, null);
        }
        
        @Override
        public void onReceivedTitle(WebView view, String title){
            updatePageTitle();
        }
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
        LeanUtils.runJavascriptOnWebView(this.mWebview, "gonative_status_checker.onReadyState(document.readyState)");
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
}
