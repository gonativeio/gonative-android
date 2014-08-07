package io.gonative.android;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.Toast;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONObject;

public class MainActivity extends Activity implements Observer {
	private static final String MENU_CACHED = "menu_cached.xml";
	
	public static final String userAgentAdd = "gonative";
    public static final String webviewCacheSubdir = "webviewAppCache";
    public static final String webviewDatabaseSubdir = "webviewDatabase";
	private static final String TAG = MainActivity.class.getName();
	private static final int REQUEST_SELECT_PICTURE = 100;
	private static final int REQUEST_CROP_PICTURE = 200;
    private static final int REQUEST_WEBFORM = 300;
    public static final int REQUEST_WEB_ACTIVITY = 400;

	public Stack<LeanWebView> globalWebViews = new Stack<LeanWebView>();
    private HashMap<LeanWebView,Stack<String>> backHistory = new HashMap<LeanWebView,Stack<String>>();

	private ArrayList<DrawerMenuItem> mItems = new ArrayList<DrawerMenuItem>();
	private ValueCallback<Uri> mUploadMessage;
	private DrawerLayout mDrawerLayout;
	private View mDrawerView;
	private ExpandableListView mDrawerList;
    private ProgressBar mProgress;
    private JsonMenuAdapter menuAdapter = null;
	private ActionBarDrawerToggle mDrawerToggle;
	private CharSequence mDrawerTitle;
	private Boolean isLoggedIn = null;
	private ConnectivityManager cm = null;
    private ProfilePicker profilePicker = null;
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
    private boolean startedLoading = false; // document readstate checker
	
	private Uri cameraFileUri;
	private Uri cropFileUri;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        AppConfig appConfig = AppConfig.getInstance(this);

        // theme
        if (!appConfig.showActionBar)
            setTheme(R.style.GoNativeNoActionBar);
        else if (appConfig.androidTheme != null &&
                appConfig.androidTheme.equals("dark"))
            setTheme(R.style.GoNativeDarkActionBar);
        else
            setTheme(R.style.GoNativeLight);

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
            new UpdateConfigTask().execute();
        }

        // WebView debugging
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
		
		cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

		requestWindowFeature(Window.FEATURE_PROGRESS);
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


		globalWebViews.add(wv);
		setupWebview(wv);

        // load url
        String url = null;
        if (savedInstanceState != null) url = savedInstanceState.getString("url");
        if (url == null && isRoot) url = appConfig.initialUrl;
        if (url == null) url = getIntent().getStringExtra("url");
        if (url != null) wv.loadUrl(url);

        if (isRoot && appConfig.showNavigationMenu) {
            // do the list stuff
            mDrawerTitle = AppConfig.getInstance(this).appName;
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            mDrawerView = findViewById(R.id.left_drawer);
            mDrawerList = (ExpandableListView) findViewById(R.id.drawer_list);

            // programatically set drawer icon
            int[] drawerAttribute = new int[] {R.attr.ic_drawer};
            TypedArray a = obtainStyledAttributes(drawerAttribute);
            int drawerIcon = a.getResourceId(0, R.drawable.ic_drawer_light);
            a.recycle();

            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                    drawerIcon, R.string.drawer_open, R.string.drawer_close){
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

		if (getActionBar() != null) {
            getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            if (!isRoot || AppConfig.getInstance(this).showNavigationMenu)
                getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // style sidebar
        if (mDrawerView != null && AppConfig.getInstance(this).sidebarBackgroundColor != null) {
            mDrawerView.setBackgroundColor(AppConfig.getInstance(this).sidebarBackgroundColor);
        }
	}

    protected void onPause() {
        super.onPause();
        stopCheckingReadyStatus();
    }

    protected void onSaveInstanceState (Bundle outState) {
        outState.putString("url", globalWebViews.peek().getUrl());
        outState.putInt("urlLevel", urlLevel);
    }

    public void addToHistory(String url) {
        if (url == null) return;

        LeanWebView wv = globalWebViews.peek();
        Stack<String> history = backHistory.get(wv);
        if (history == null) {
            history = new Stack<String>();
            backHistory.put(wv, history);
        }

        if (history.isEmpty() || !history.peek().equals(url)) {
            history.push(url);
        }
    }

    public boolean canGoBack() {
        Stack<String> history = backHistory.get(globalWebViews.peek());
        if (history != null) {
            return history.size() >= 2;
        }
        return false;
    }

    public void goBack() {
        Stack<String> history = backHistory.get(globalWebViews.peek());
        history.pop();
        String newUrl = history.pop();
        loadUrl(newUrl);
    }

    public void logout() {
        globalWebViews.peek().stopLoading();

        // log out by clearing all cookies and going to home page
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
        CookieSyncManager.getInstance().sync();

        updateMenu(false);
        LoginManager.getInstance().checkLogin();
        globalWebViews.peek().loadUrl(AppConfig.getInstance(this).initialUrl);
    }

    public void loadUrl(String url) {
        if (url.equalsIgnoreCase("gonative_logout"))
            logout();
        else
            globalWebViews.peek().loadUrl(url);
    }
	
	public boolean isConnected(){
		NetworkInfo ni = cm.getActiveNetworkInfo();
		return ni != null && ni.isConnected();
	}
		
	
	// configures webview settings
	@SuppressWarnings("deprecation")
	@SuppressLint("SetJavaScriptEnabled")
	private void setupWebview(WebView wv){
		WebSettings webSettings = wv.getSettings();

        if (AppConfig.getInstance(this).allowZoom) {
            webSettings.setBuiltInZoomControls(true);
        }
        else {
            webSettings.setBuiltInZoomControls(false);
        }
        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

		webSettings.setJavaScriptEnabled(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        webSettings.setDomStorageEnabled(true);
        File cachePath = new File(getCacheDir(), webviewCacheSubdir);
        webSettings.setAppCachePath(cachePath.getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
        webSettings.setDatabaseEnabled(true);

        //attempt to support persistent localStorage on 4.0 - 4.3
        /*if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			File databasePath = new File(getCacheDir(), webviewDatabaseSubdir);
            webSettings.setDatabasePath(databasePath.getAbsolutePath());
        }*/


		webSettings.setSaveFormData(false);
		webSettings.setSavePassword(false);
		webSettings.setUserAgentString(AppConfig.getInstance(this).userAgent);
		webSettings.setSupportMultipleWindows(true);
        webSettings.setGeolocationEnabled(AppConfig.getInstance(this).usesGeolocation);
        wv.setWebChromeClient(new CustomWebChromeClient());
		wv.setWebViewClient(new LeanWebviewClient(MainActivity.this));
        wv.setDownloadListener(fileDownloader);

        if (profilePicker != null) {
            wv.addJavascriptInterface(profilePicker.getProfileJsBridge(), "gonative_profile_picker");
        }

        wv.addJavascriptInterface(new StatusCheckerBridge(), "gonative_status_checker");
	}

    public void hideWebview() {
        mProgress.setAlpha(1.0f);
        mProgress.setVisibility(View.VISIBLE);

        globalWebViews.peek().setVisibility(View.INVISIBLE);
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

    public void showWebview() {
        startedLoading = false;
        stopCheckingReadyStatus();

        final WebView wv = globalWebViews.peek();
        if (wv.getVisibility() == View.VISIBLE) {
            // don't animate if already visible
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
	
	public void clearProgress(){
		setProgress(10000);
	}

	public void updatePageTitle() {
        if (AppConfig.getInstance(this).useWebpageTitle) {
            setTitle(globalWebViews.peek().getTitle());
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
        if(data != null && data.getBooleanExtra("exit", false))
            finish();

        if (requestCode == REQUEST_WEBFORM && resultCode == RESULT_OK) {
            String url = data.getStringExtra("url");
            if (url != null)
                globalWebViews.peek().loadUrl(url);
            else {
                // go to initialURL without login/signup override
                globalWebViews.peek().setCheckLoginSignup(false);
                globalWebViews.peek().loadUrl(AppConfig.getInstance(this).initialUrl);
            }

            updateMenu(data.getBooleanExtra("success", false));
        }

        if (requestCode == REQUEST_WEB_ACTIVITY && resultCode == RESULT_OK) {
            String url = data.getStringExtra("url");
            if (url != null) {
                int urlLevel = data.getIntExtra("urlLevel", -1);
                if (urlLevel == -1 || parentUrlLevel == -1 || urlLevel > parentUrlLevel) {
                    // open in this activity
                    loadUrl(url);
                } else {
                    // urlLevel <= parentUrlLevel, so pass up the chain
                    setResult(RESULT_OK, data);
                    finish();
                }
            }
        }

        if (requestCode == REQUEST_SELECT_PICTURE) {
            if (null == mUploadMessage)
                return;
            
			if(resultCode == RESULT_OK){
				boolean isCamera;
	            if (data == null)
	                isCamera = true;
	            else
	            {
	                final String action = data.getAction();
	                if (action == null)
	                    isCamera = false;
	                else
	                    isCamera = action.equals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
	            }
	            
	            Uri selectedImageUri;
	            if (isCamera)
	                selectedImageUri = cameraFileUri;
	            else
	                selectedImageUri = data == null ? null : data.getData();
	            
	            // now try cropping
	            try{
		            Intent cropIntent = new Intent("com.android.camera.action.CROP");
			        cropIntent.setDataAndType(selectedImageUri, "image/*");
			        cropIntent.putExtra("aspectX", 1);
			        cropIntent.putExtra("aspectY", 1);

			        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
		            File outFile = new File(getExternalFilesDir(null), "imgcrop_" + timeStamp + ".jpg");
					cropFileUri = Uri.fromFile(outFile);
			        
			        cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, cropFileUri);

			        startActivityForResult(cropIntent, REQUEST_CROP_PICTURE);
			        Toast.makeText(getApplicationContext(), getString(R.string.crop_square), Toast.LENGTH_SHORT).show();
	            }
	            catch (ActivityNotFoundException e){
	            	// just upload the file specified by selectedImageUri
		            mUploadMessage.onReceiveValue(selectedImageUri);
		            mUploadMessage = null;
            	}
			}
			else{
				mUploadMessage.onReceiveValue(null);
				mUploadMessage = null;
			}
        }
        
        if (requestCode == REQUEST_CROP_PICTURE){
        	if(resultCode == RESULT_OK)
        		mUploadMessage.onReceiveValue(cropFileUri);
        	else
        		mUploadMessage.onReceiveValue(null);
        	
        	mUploadMessage = null;
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
			else if(globalWebViews.size() > 1){
                popWebView();
				return true;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

    public void popWebView() {
        WebView prev = globalWebViews.pop();
        backHistory.remove(prev);

        // replace webview with next one on the stack
        ViewGroup parent = (ViewGroup) prev.getParent();
        int index = parent.indexOfChild(prev);
        parent.removeView(prev);
        parent.addView(globalWebViews.peek(), index);
        clearProgress();

        // title in actionbar should be the new webview
        updatePageTitle();

        prev.destroy();
    }


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.topmenu, menu);
		
		final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (AppConfig.getInstance(this).searchTemplateUrl != null) {
            final SearchView searchView = (SearchView) searchItem.getActionView();

            // listener to process query
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchItem.collapseActionView();

                    try{
                        String q = URLEncoder.encode(query, "UTF-8");
                        globalWebViews.peek().loadUrl(AppConfig.getInstance(getApplicationContext()).searchTemplateUrl + q);
                    }
                    catch (UnsupportedEncodingException e){
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
                    if(!hasFocus){
                        searchItem.collapseActionView();
                    }
                }
            });

            // make it visible
            searchItem.setVisible(true);
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
        
        // handle other items
        switch (item.getItemId()){
            case android.R.id.home:
                finish();
                return true;
	        case R.id.action_search:
	        	return true;
	        case R.id.action_refresh:
	        	if (globalWebViews.peek().getUrl() != null && globalWebViews.peek().getUrl().startsWith("data:")){
	        		globalWebViews.peek().goBack();
	        		updateMenu();
	        	}
	        	else {
	        		globalWebViews.peek().reload();
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
            openFileChooser(uploadMsg);
        }

        // Android 3.0 + 
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
            openFileChooser(uploadMsg);
        }

        //Android 3.0
        public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            mUploadMessage = uploadMsg;
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            
            File outFile = new File(getExternalFilesDir(null), "img_" + timeStamp + ".jpg"); 
    		cameraFileUri = Uri.fromFile(outFile);
    		
    		 // Camera.
    	    final List<Intent> cameraIntents = new ArrayList<Intent>();
    	    final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
    	    final PackageManager packageManager = getPackageManager();
    	    final List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
    	    for(ResolveInfo res : listCam) {
    	        final String packageName = res.activityInfo.packageName;
    	        final Intent intent = new Intent(captureIntent);
    	        intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
    	        intent.setPackage(packageName);
    	        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraFileUri);
    	        cameraIntents.add(intent);
    	    }

    	    // Filesystem.
    	    final Intent galleryIntent = new Intent();
    	    galleryIntent.setType("image/*");
    	    galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
    	    galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);

    	    // Chooser of filesystem options.
    	    final Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.pick_photo));

    	    // Add the camera options.
    	    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

    	    startActivityForResult(chooserIntent, REQUEST_SELECT_PICTURE);
        }

        @Override
        public boolean onCreateWindow (WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg){
        	//Log.d(TAG, "onCreateWindow isdialog: " + isDialog + " isUserGesture: " + isUserGesture + " originalurl:" + view.getOriginalUrl());
            LeanWebView wv = new LeanWebView(view.getContext());
            
            setupWebview(wv);

        	if (!isDialog){
    		//if (true){
        		// dialogs are social media share buttons, where we don't want to add to the view because it
        		// creates an extra blank page. TODO: This may create memory leaks for dialog boxes.
	    		globalWebViews.add(wv);
	    		
	    		// replace the current web view in the parent with the new view
	            ViewGroup parent = (ViewGroup) view.getParent();
	            int index = parent.indexOfChild(view);
	            parent.removeView(view);
	            parent.addView(wv, index);
        	}
        	
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(wv);
            resultMsg.sendToTarget();
            return true;
        }
        
        @Override
        public void onCloseWindow (WebView view){
        	//Log.d(TAG, "onclosewindow: " + (view == globalWebViews.peek()));
        	
        	// remove from webview stack if this is the top view
        	if (view == globalWebViews.peek() && globalWebViews.size() > 1){
        		globalWebViews.pop();
        		
    			ViewGroup parent = (ViewGroup) view.getParent();
    			int index = parent.indexOfChild(view);
    			
    			parent.removeView(view);
    			parent.addView(globalWebViews.peek(), index);
    			clearProgress();
    			
    			view.destroy();
        	}
        }
        
        @Override
        public void onReceivedTitle(WebView view, String title){
            updatePageTitle();
        }
        
        @Override
        public void onProgressChanged(WebView view, int newProgress){
        	setProgress(newProgress * 100);
        }

    }

    public boolean isRoot() {
        return isRoot;
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

        if (getActionBar() != null) {
            getActionBar().setTitle(title);
        }
    }

    public void startCheckingReadyStatus() {
        statusChecker.run();
    }

    public void stopCheckingReadyStatus() {
        handler.removeCallbacks(statusChecker);
    }

    public void checkReadyStatus() {
        globalWebViews.peek().loadUrl("javascript: gonative_status_checker.onReadyState(document.readyState)");
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

    private class UpdateConfigTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            String appnumHashed = AppConfig.getInstance(MainActivity.this).publicKey;
            if (appnumHashed == null) return null;

            try {
                URL url = new URL(String.format("https://gonative.io/static/appConfig/%s.json", appnumHashed));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                int responseCode = connection.getResponseCode();
                if (responseCode >= 400) return null;

                // verify json
                ByteArrayOutputStream baos;
                if (connection.getContentLength() > 0) baos = new ByteArrayOutputStream(connection.getContentLength());
                else baos = new ByteArrayOutputStream();

                InputStream is = new BufferedInputStream(connection.getInputStream());
                IOUtils.copy(is, baos);
                is.close();
                baos.close();
                new JSONObject(baos.toString("UTF-8"));

                // save file
                File destination = AppConfig.getInstance(MainActivity.this).fileForOTAconfig();
                OutputStream os = new BufferedOutputStream(new FileOutputStream(destination));
                is = new BufferedInputStream(new ByteArrayInputStream(baos.toByteArray()));
                IOUtils.copy(is, os);
                is.close();
                os.close();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            return null;
        }
    }
}
