package io.gonative.android;

import java.io.*;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import org.json.*;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;


/**
 * Activity which displays a login screen to the user, offering registration as
 * well.
 */
public class WebFormActivity extends Activity implements Observer{

    private static final String TAG = WebFormActivity.class.getName();

    public static final String EXTRA_JSONCONFIG = "io.gonative.android.extra.jsonconfig";
    public static final String EXTRA_FORMURL = "io.gonative.android.extra.formurl";
    public static final String EXTRA_ERRORURL = "io.gonative.android.extra.errorurl";
    public static final String EXTRA_TITLE = "io.gonative.android.extra.title";
    // login pages can show password recovery menu option
    public static final String EXTRA_IS_LOGIN = "io.gonative.android.extra.isLogin";

    private JSONObject mJson;
    private String mFormUrl;
    private String mErrorUrl;
    private String mForgotPasswordUrl;
    private String mTitle;
    private boolean mIsLogin;
    private boolean checkingLogin;
    private ArrayList<View> mFieldRefs;
    private WebView mHiddenWebView;
    private Handler handler;

    private boolean mSubmitted = false;

    // Values for email and password at the time of the login attempt.
    private String mEmail;
    private String mPassword;

    // UI references.
    private EditText mEmailView;
    private EditText mPasswordView;
    private View mLoginFormView;
    private View mLoginStatusView;
    private TextView mLoginStatusMessageView;
    private Button mSubmitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (AppConfig.getInstance(this).containsKey("androidTheme") &&
                AppConfig.getInstance(this).getString("androidTheme").equals("dark")){
            setTheme(R.style.GoNativeDarkActionBar);
        }

        super.onCreate(savedInstanceState);

        mFormUrl = getIntent().getStringExtra(EXTRA_FORMURL);
        mErrorUrl = getIntent().getStringExtra(EXTRA_ERRORURL);
        mTitle = getIntent().getStringExtra(EXTRA_TITLE);
        mIsLogin = getIntent().getBooleanExtra(EXTRA_IS_LOGIN, false);

        if (AppConfig.getInstance(this).containsKey("forgotPasswordURL")){
            mForgotPasswordUrl = AppConfig.getInstance(this).getString("forgotPasswordURL");
        }
        else {
            mForgotPasswordUrl = "";
        }

        this.setTitle(mTitle);

        mHiddenWebView = new WebView(this);
        WebSettings webSettings = mHiddenWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setSaveFormData(false);
        webSettings.setSavePassword(false);
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
        mHiddenWebView.setWebViewClient(new WebFormWebViewClient());
        mHiddenWebView.setWebChromeClient(new WebFormWebChromeClient());
        mHiddenWebView.addJavascriptInterface(new jsBridge(), "gonative_js_bridge");

        setContentView(R.layout.activity_web_form);

        mLoginFormView = findViewById(R.id.login_form);
        mLoginStatusView = findViewById(R.id.login_status);
        mLoginStatusMessageView = (TextView) findViewById(R.id.login_status_message);
        mSubmitButton = (Button) findViewById(R.id.submit_button);

        // if login is the first page that loads. Hide form until login check is done.
        if (mIsLogin && AppConfig.getInstance(this).loginIsFirstPage()) {
            mLoginStatusView.setVisibility(View.VISIBLE);
            mLoginFormView.setVisibility(View.GONE);

            // observe login manager
            LoginManager.getInstance().addObserver(this);
//            LoginManager.getInstance().checkLogin();
            LoginManager.getInstance().checkIfNotAlreadyChecking();
        }

        loadJsonResource(getIntent().getIntExtra(EXTRA_JSONCONFIG, -1));

        findViewById(R.id.submit_button).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        submit();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSubmitted = false;
        mSubmitButton.setEnabled(true);
        if (!AppConfig.getInstance(this).loginIsFirstPage())
            mHiddenWebView.loadUrl(mFormUrl);
        else
            LoginManager.getInstance().checkIfNotAlreadyChecking();;
    }

    public void update (Observable sender, Object data) {
        if (sender instanceof LoginManager) {
            sender.deleteObserver(this);
            if (((LoginManager) sender).isLoggedIn()) {
                // back to main activity
                Intent returnIntent = new Intent();
                returnIntent.putExtra("success", true);
                setResult(RESULT_OK, returnIntent);
                finish();
            } else {
                mHiddenWebView.loadUrl(mFormUrl);
                mLoginStatusView.setVisibility(View.GONE);
                mLoginFormView.setVisibility(View.VISIBLE);
            }
        }
    }

    public void loadJsonResource(int resourceId) {
        InputStream is = getResources().openRawResource(resourceId);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        String jsonString = null;
        try {
            IOUtils.copy(is,  os);
            jsonString = os.toString("UTF-8");

            mJson = new JSONObject(jsonString);
            JSONArray fields = mJson.getJSONArray("fields");

            mFieldRefs = new ArrayList<View>(fields.length());

            LinearLayout formLayout = (LinearLayout) findViewById(R.id.form_layout);
            for (int i = 0; i < fields.length(); i++){
                JSONObject field = fields.getJSONObject(i);
                String type = field.getString("type");

                if (type.equals("email") || type.equals("name") ||
                        type.equals("text") || type.equals("number")){

                    LayoutInflater.from(getBaseContext()).inflate(R.layout.form_text, formLayout, true);
                    EditText textField = (EditText) formLayout.getChildAt(formLayout.getChildCount() - 1);
                    textField.setHint(field.optString("label"));

                    if (type.equals("email")) {
                        textField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                    }
                    else if(type.equals("name")) {
                        textField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
                    }
                    else if(type.equals("number")) {
                        textField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
                    }

                    mFieldRefs.add(i, textField);
                }
                else if (type.equals("password")) {
                    LayoutInflater.from(getBaseContext()).inflate(R.layout.form_password, formLayout, true);
                    final EditText textField = (EditText) formLayout.getChildAt(formLayout.getChildCount() - 2);
                    CheckBox checkBox = (CheckBox) formLayout.getChildAt(formLayout.getChildCount() - 1);

                    textField.setHint(field.getString("label"));

                    checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                            if (b) {
                                textField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                            }
                            else {
                                textField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                            }
                        }
                    });

                    mFieldRefs.add(i, textField);
                }
                else if (type.equals("options")) {
                    LayoutInflater.from(getBaseContext()).inflate(R.layout.form_option, formLayout, true);
                    TextView label = (TextView) formLayout.getChildAt(formLayout.getChildCount() - 2);
                    label.setText(field.getString("label"));

                    RadioGroup rg = (RadioGroup) formLayout.getChildAt(formLayout.getChildCount() - 1);
                    JSONArray choices = field.getJSONArray("choices");

                    for (int j = 0; j < choices.length(); j++) {
                        RadioButton rb = new RadioButton(this);
                        rb.setText(choices.getJSONObject(j).getString("label"));
                        rg.addView(rb);
                    }

                    mFieldRefs.add(i, rg);
                }
                else if (type.equals("list")) {
                    LayoutInflater.from(getBaseContext()).inflate(R.layout.form_list, formLayout, true);
                    TextView label = (TextView) formLayout.getChildAt(formLayout.getChildCount() - 2);
                    label.setText(field.getString("label"));

                    RadioGroup rg = (RadioGroup) formLayout.getChildAt(formLayout.getChildCount() - 1);
                    JSONArray choices = field.getJSONArray("choices");

                    for (int j = 0; j < choices.length(); j++) {
                        RadioButton rb = new RadioButton(this);
                        rb.setText(choices.getJSONObject(j).getString("label"));
                        rg.addView(rb);
                    }

                    mFieldRefs.add(i, rg);
                }



            }



        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.web_form, menu);

        if (mIsLogin && AppConfig.getInstance(this).containsKey("forgotPasswordURL")){
            MenuItem forgotPassword = (MenuItem) menu.findItem(R.id.action_forgot_password);
            forgotPassword.setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_forgot_password) {
            // hide keyboard
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mLoginFormView.getWindowToken(), 0);

            // show forgot password in main view
            Intent returnIntent = new Intent();
            returnIntent.putExtra("url", AppConfig.getInstance(this).getString("forgotPasswordURL"));
            returnIntent.putExtra("success", false);
            setResult(RESULT_OK, returnIntent);
            finish();

            return true;
        }
        else
            return super.onOptionsItemSelected(item);
    }

    public void submit() {
        try {
            if (validateForm()){
                //setContentView(mHiddenWebView);

                JSONArray fields = mJson.getJSONArray("fields");

                for (int i = 0; i < fields.length(); i++) {
                    JSONObject field = fields.getJSONObject(i);
                    String type = field.getString("type");

                    if (type.equals("email") || type.equals("name") ||
                            type.equals("text") || type.equals("number") ||
                            type.equals("password")) {
                        // these have an EditText
                        EditText textField = (EditText) mFieldRefs.get(i);

                        mHiddenWebView.loadUrl(String.format("javascript:jQuery(%s).val(%s);",
                                LeanUtils.jsWrapString(field.getString("selector")),
                                LeanUtils.jsWrapString(textField.getText().toString())));

                    }
                    if ( (type.equals("options") || type.equals("list")) && field.getBoolean("required")) {
                        // RadioGroup
                        RadioGroup rg = (RadioGroup) mFieldRefs.get(i);
                        for(int j = 0; j < rg.getChildCount(); j++){
                            RadioButton rb = (RadioButton) rg.getChildAt(j);
                            if (rb.isChecked()) {
                                mHiddenWebView.loadUrl(String.format("javascript:jQuery(%s).click();",
                                        LeanUtils.jsWrapString(field.getString("selector"))));
                            }
                        }
                    }
                }

                // hide keyboard
                InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mLoginFormView.getWindowToken(), 0);

                // submit the form
                mHiddenWebView.loadUrl(String.format("javascript:jQuery(%s).submit();",
                        LeanUtils.jsWrapString(mJson.getString("formSelector"))));
                mSubmitted = true;
                mSubmitButton.setEnabled(false);

                // for ajax login forms
                if (mJson.optBoolean("isAjax", false)) {
                    scheduleSubmissionCheck();
                }
            }
        }
        catch(Exception e) {
            Log.e(TAG, e.toString(), e);
        }

    }

    void scheduleSubmissionCheck() {
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                runOnUiThread(new Runnable(){
                    @Override
                    public void run() {
                        WebFormActivity.this.checkSubmissionStatus();
                    }
                });
            }
        };

        Message message = Message.obtain();
        message.what = 1;
        handler.sendMessageDelayed(message, 1000);
    }

    void cancelSubmissionCheck() {
        if (handler != null)
            handler.removeMessages(1);
    }

    void checkSubmissionStatus() {
        if (mSubmitted) {
            try {
                if (mJson.has("errorSelector2")){
                    mHiddenWebView.loadUrl(String.format("javascript:gonative_js_bridge.send(jQuery(%s).html(), jQuery(%s).html());",
                            LeanUtils.jsWrapString(mJson.getString("errorSelector")), LeanUtils.jsWrapString(mJson.getString("errorSelector2"))));
                }
                else{
                    mHiddenWebView.loadUrl(String.format("javascript:gonative_js_bridge.send(jQuery(%s).html(), null);",
                            LeanUtils.jsWrapString(mJson.getString("errorSelector"))));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    public boolean validateForm() throws Exception {
        boolean valid = true;
        View toFocus = null;

        JSONArray fields = mJson.getJSONArray("fields");

        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            String type = field.getString("type");

            if (type.equals("email") || type.equals("name") ||
                    type.equals("text") || type.equals("number") ||
                    type.equals("password")) {
                // these have an edittext
                EditText textField = (EditText) mFieldRefs.get(i);
                textField.setError(null);

                // trim text
                textField.setText(textField.getText().toString().trim());


                if (field.optBoolean("required", false) && textField.getText().length() == 0) {
                    textField.setError(String.format(getString(R.string.error_field_required), field.getString("label")));
                    if (toFocus == null)
                        toFocus = textField;
                    valid = false;
                }

                if (field.has("minLength") && textField.getText().length() < field.getInt("minLength")) {
                    textField.setError(String.format(getString(R.string.error_field_length),
                            field.getString("label"), field.getInt("minLength")));
                    if (toFocus == null)
                        toFocus = textField;
                    valid = false;
                }

                if (type.equals("email") && textField.getText().length() > 0 &&
                        !LeanUtils.isValidEmail(textField.getText().toString())) {

                    textField.setError(getString(R.string.error_invalid_email));
                    if (toFocus == null)
                        toFocus = textField;
                    valid = false;
                }

            }
            if ( (type.equals("options") || type.equals("list")) && field.getBoolean("required")) {
                // RadioGroup
                RadioGroup rg = (RadioGroup) mFieldRefs.get(i);
                boolean anySelected = false;
                for(int j = 0; j < rg.getChildCount(); j++){
                    RadioButton rb = (RadioButton) rg.getChildAt(j);
                    if (rb.isChecked()) {
                        anySelected = true;
                        break;
                    }
                }

                if (!anySelected){
                    if (toFocus == null) {
                        Toast.makeText(this, String.format(getString(R.string.error_field_required),
                                field.getString("label")), Toast.LENGTH_LONG).show();
                        toFocus = rg;
                    }
                    valid = false;
                }
            }
        }

        if (toFocus != null) {
            toFocus.requestFocus();
        }

        return valid;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mIsLogin &&
                AppConfig.getInstance(this).getBoolean("loginIsFirstPage")) {
            // exit application
            Intent returnIntent = new Intent();
            returnIntent.putExtra("exit", true);
            setResult(RESULT_CANCELED, returnIntent);
            finish();

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    public class WebFormWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result){
            Toast.makeText(WebFormActivity.this, message, Toast.LENGTH_LONG).show();
            result.confirm();
            return true;
        }
    }

    public class WebFormWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
//            Log.d(TAG, "page finished " + url);
            super.onPageFinished(view, url);
            UrlInspector.getInstance().inspectUrl(url);

            CookieSyncManager.getInstance().sync();

            // add jquery if it is not already loaded
            view.loadUrl("javascript: if (!window.jQuery) {\n" +
                    "  gonativejq = document.createElement('script');\n" +
                    "  gonativejq.type = 'text/javascript';\n" +
                    "  gonativejq.src = '//ajax.googleapis.com/ajax/libs/jquery/2.1.0/jquery.min.js';\n" +
                    "  document.body.appendChild(gonativejq);\n" +
                    "}");

            boolean success = false;

            // if redirected to different page, then we are done
            if (!LeanUtils.urlsMatchOnPath(url, mFormUrl) &&
                    !LeanUtils.urlsMatchOnPath(url, mErrorUrl) &&
                    !LeanUtils.urlsMatchOnPath(url, mForgotPasswordUrl)) {
                success = true;
            }

            if (mSubmitted) {
                if (LeanUtils.urlsMatchOnPath(url, mErrorUrl)) {
                    mSubmitted = false;
                    mSubmitButton.setEnabled(true);

                    try {
                        mHiddenWebView.loadUrl(String.format("javascript:if(jQuery(%s).length > 0) alert(jQuery(%s).text()); else alert('Error submitting form');",
                                LeanUtils.jsWrapString(mJson.getString("errorSelector")),
                                LeanUtils.jsWrapString(mJson.getString("errorSelector"))));
                    } catch (JSONException e) {
                        Log.e(TAG, e.toString(), e);
                    }

                    mHiddenWebView.loadUrl(mFormUrl);
                }
                else
                    success = true;
            }

            if (success) {
                // stops submission check
                cancelSubmissionCheck();
                mSubmitted = false;

                Intent returnIntent = new Intent();
                returnIntent.putExtra("url", url);
                returnIntent.putExtra("success", true);
                setResult(RESULT_OK, returnIntent);
                finish();
            }
        }

    }

    public class jsBridge{
        @JavascriptInterface
        public void send(final String s1, final String s2){
            if (!mSubmitted)
                return;

            // string can be "undefined" if page is changing and jQuery is not available.
            if (s1 != null && s1.equals("undefined"))
                return;


            // triggered by ajax submission check
            if ((s1 == null || s1.trim().isEmpty()) && (s2 == null || s2.trim().isEmpty())) {
                // no error. Continue Checking.
                scheduleSubmissionCheck();
            } else {
                if(!(s1 == null || s1.trim().isEmpty())){
                    // error with submission
                    WebFormActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WebFormActivity.this, s1.trim(), Toast.LENGTH_LONG).show();
                        }
                    });

                }
                if(!(s2 == null || s2.trim().isEmpty())){
                    // error with submission
                    WebFormActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WebFormActivity.this, s2.trim(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                mSubmitted = false;

                // disable button on UI thread (jsBridge.send() is called from a webview internal thread)
                WebFormActivity.this.runOnUiThread(new Runnable(){
                    public void run(){
                        mSubmitButton.setEnabled(true);
                    }
                });
            }
        }
    }

}
