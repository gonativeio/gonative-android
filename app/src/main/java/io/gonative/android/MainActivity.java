package io.gonative.android;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;

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
import android.os.Build;
import android.os.Bundle;
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
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ExpandableListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.Toast;

public class MainActivity extends Activity implements Observer {
	private static final String MENU_CACHED = "menu_cached.xml";
	
	public static final String userAgentAdd = "gonative";
	private static final String TAG = MainActivity.class.getName();
	private static final int REQUEST_SELECT_PICTURE = 100;
	private static final int REQUEST_CROP_PICTURE = 200;
    private static final int REQUEST_WEBFORM = 300;

	public Stack<LeanWebView> globalWebViews = new Stack<LeanWebView>();

	private ArrayList<DrawerMenuItem> mItems = new ArrayList<DrawerMenuItem>();
	private ValueCallback<Uri> mUploadMessage;
	private DrawerLayout mDrawerLayout;
	private View mDrawerView;
	private ExpandableListView mDrawerList;
    private JsonMenuAdapter menuAdapter = null;
	private ActionBarDrawerToggle mDrawerToggle;
	private CharSequence mDrawerTitle;
	private CharSequence mTitle;
	private Boolean isLoggedIn = null;
	private ConnectivityManager cm = null;
    private ProfilePicker profilePicker = null;
	
	private Uri cameraFileUri;
	private Uri cropFileUri;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        // theme
        if (AppConfig.getInstance(this).containsKey("androidTheme") &&
                AppConfig.getInstance(this).getString("androidTheme").equals("dark")){
            setTheme(R.style.GoNativeDarkActionBar);
        }

		super.onCreate(savedInstanceState);

        // enable httpurlconnection response cache
        try {
            long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
            File httpCacheDir = new File(getCacheDir(), "http");
            Class.forName("android.net.http.HttpResponseCache")
                    .getMethod("install", File.class, long.class)
                    .invoke(null, httpCacheDir, httpCacheSize);
        } catch (Exception httpResponseCacheNotAvailable) {
            Log.i(TAG, "HTTP response cache is unavailable.");
        }

        // url inspector
        UrlInspector.getInstance().init(this);



        // WebView debugging
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
		
		cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

		requestWindowFeature(Window.FEATURE_PROGRESS);
        if (AppConfig.getInstance(this).getBoolean("checkNativeNav"))
	    	setContentView(R.layout.activity_gonative);
        else
            setContentView(R.layout.activity_gonative_nonav);

		LeanWebView wv = (LeanWebView) findViewById(R.id.webview);

        // profile picker
        if (AppConfig.getInstance(this).getBoolean("checkNativeNav")) {
            Spinner spinner = (Spinner) findViewById(R.id.profile_picker);
            profilePicker = new ProfilePicker(this, spinner);
        }

		// to save webview cookies to permanent storage
		CookieSyncManager.createInstance(getApplicationContext());
		
		// proxy cookie manager for httpUrlConnection (syncs to webview cookies)
		CookieHandler.setDefault(new WebkitCookieManagerProxy());

		globalWebViews.add(wv);
		setupWebview(wv);

		wv.loadUrl(AppConfig.getInstance(this).getString("initialURL"));


        if (AppConfig.getInstance(this).getBoolean("checkNativeNav")) {
            // do the list stuff
            mTitle = mDrawerTitle = getTitle();
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
                    getActionBar().setTitle(mTitle);
                    invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }

                //Called when a drawer has settled in a completely open state.
                public void onDrawerOpened(View drawerView) {
                    getActionBar().setTitle(mDrawerTitle);
                    invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }
            };
            mDrawerLayout.setDrawerListener(mDrawerToggle);

            setupMenu();

            // update the menu
            if (AppConfig.getInstance(this).getBoolean("checkUserAuth")) {
                LoginManager.getInstance().init(this);
                LoginManager.getInstance().addObserver(this);
            }
        }

				
		getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        if (AppConfig.getInstance(this).getBoolean("checkNativeNav"))
		    getActionBar().setDisplayHomeAsUpEnabled(true);


        // style sidebar
        if (mDrawerView != null && AppConfig.getInstance(this).getSidebarBackgroundColor() != null) {
            mDrawerView.setBackgroundColor(AppConfig.getInstance(this).getSidebarBackgroundColor());
        }


	}

    public void logout() {
        globalWebViews.peek().stopLoading();

        // log out by clearing all cookies and going to home page
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
        CookieSyncManager.getInstance().sync();

        updateMenu(false);
        LoginManager.getInstance().checkLogin();
        globalWebViews.peek().loadUrl(AppConfig.getInstance(this).getString("initialURL"));
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

        if (AppConfig.getInstance(this).getAllowZoom()) {
        webSettings.setBuiltInZoomControls(true);
		webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        }
        else {
            webSettings.setBuiltInZoomControls(false);
            webSettings.setLoadWithOverviewMode(false);
            webSettings.setUseWideViewPort(false);
        }

		webSettings.setJavaScriptEnabled(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
		webSettings.setSaveFormData(false);
		webSettings.setSavePassword(false);
		webSettings.setUserAgentString(AppConfig.getInstance(this).getUserAgent());
		webSettings.setSupportMultipleWindows(true);
        wv.setWebChromeClient(new CustomWebChromeClient());
		wv.setWebViewClient(new LeanWebviewClient(MainActivity.this));

        if (profilePicker != null) {
            wv.addJavascriptInterface(profilePicker.getProfileJsBridge(), "gonative_profile_picker");
        }
	}
	
	public void clearProgress(){
		setProgress(10000);
	}
	
	public void updatePageTitle(){
		mTitle = globalWebViews.peek().getTitle();
		if (!isDrawerOpen()){
			getActionBar().setTitle(mTitle);
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
                menuAdapter.parseJsonAsset("menu_loggedin.json");
            else
                menuAdapter.parseJsonAsset("menu_default.json");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

	public boolean isDrawerOpen(){
        if (AppConfig.getInstance(this).getBoolean("checkNativeNav"))
    		return mDrawerLayout.isDrawerOpen(mDrawerView);
        else
            return false;
	}
	
	protected void setupMenu(){
        menuAdapter = new JsonMenuAdapter(this);
        try {
            menuAdapter.parseJsonAsset("menu_default.json");
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
        if (AppConfig.getInstance(this).getBoolean("checkNativeNav"))
		    mDrawerToggle.syncState();
	}
	
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
     // Pass any configuration change to the drawer toggles
        if (AppConfig.getInstance(this).getBoolean("checkNativeNav"))
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
                globalWebViews.peek().loadUrl(AppConfig.getInstance(this).getString("initialURL"));
            }

            updateMenu(data.getBooleanExtra("success", false));
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
			else if (globalWebViews.peek().canGoBack()) {
				globalWebViews.peek().goBack();
								return true;
			}
			else if(globalWebViews.size() > 1){
				WebView prev = globalWebViews.pop();
				
				// replace webview with next one on the stack
				ViewGroup parent = (ViewGroup) prev.getParent();
				int index = parent.indexOfChild(prev);
				parent.removeView(prev);
				parent.addView(globalWebViews.peek(), index);
				clearProgress();
				
				// title in actionbar should be the new webview
				updatePageTitle();
				
				prev.destroy();
				
				return true;
			}
		}

		
		return super.onKeyDown(keyCode, event);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.topmenu, menu);
		
		final MenuItem searchItem = menu.findItem(R.id.action_search);
        if (AppConfig.getInstance(this).containsKey("searchTemplateURL")) {
            final SearchView searchView = (SearchView) searchItem.getActionView();

            // listener to process query
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchItem.collapseActionView();

                    try{
                        String q = URLEncoder.encode(query, "UTF-8");
                        globalWebViews.peek().loadUrl(AppConfig.getInstance(getApplicationContext()).getString("searchTemplateURL") + q);
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

        if (AppConfig.getInstance(this).getBoolean("checkNativeNav")) {
            if (mDrawerToggle.onOptionsItemSelected(item)) {
              return true;
            }
        }
        
        // handle other items
        switch (item.getItemId()){
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

    public void launchWebForm(int jsonConfig, String formUrl, String errorUrl,
                              String title, boolean isLogin) {
        Intent intent = new Intent(getBaseContext(), WebFormActivity.class);
        intent.putExtra(WebFormActivity.EXTRA_JSONCONFIG, jsonConfig);
        intent.putExtra(WebFormActivity.EXTRA_FORMURL, formUrl);
        intent.putExtra(WebFormActivity.EXTRA_ERRORURL, errorUrl);
        intent.putExtra(WebFormActivity.EXTRA_TITLE, title);
        intent.putExtra(WebFormActivity.EXTRA_IS_LOGIN, isLogin);
        startActivityForResult(intent, REQUEST_WEBFORM);
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
            if (AppConfig.getInstance(MainActivity.this).getBoolean("useWebpageTitle"))
        	    updatePageTitle();
        }
        
        @Override
        public void onProgressChanged(WebView view, int newProgress){
        	setProgress(newProgress * 100);
        }

    }

}
