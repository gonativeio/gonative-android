package io.gonative.android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import io.gonative.android.library.AppConfig;

/**
 * Activity which displays a login screen to the user, offering registration as
 * well.
 */
public class WebFormActivity extends ActionBarActivity implements Observer{

    private static final String TAG = WebFormActivity.class.getName();

    public static final String EXTRA_FORMNAME = "io.gonative.android.extra.formname";
    public static final String EXTRA_TITLE = "io.gonative.android.extra.title";

    private JSONObject mJson;
    private ArrayList<JSONObject> fields;
    private boolean mIsLogin;
    private String mFormName;
    private String mFormUrl;
    private String mErrorUrl;
    private String mForgotPasswordUrl;
    private String mTitle;

    private ArrayList<View> mFieldRefs;
    private WebView mHiddenWebView;
    private Handler checkAjaxHandler;
    private Handler checkLoginStatusHandler;

    private boolean mSubmitted = false;

    // UI references.
    private View mLoginFormView;
    private View mLoginStatusView;
    private Button mSubmitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppConfig appConfig = AppConfig.getInstance(this);

        super.onCreate(savedInstanceState);

        mFormName = getIntent().getStringExtra(EXTRA_FORMNAME);
        if (mFormName.equals("login")) {
            mJson = appConfig.loginConfig;
            mIsLogin = true;
            mForgotPasswordUrl = AppConfig.optString(mJson, "passwordResetUrl");
        }
        else if (mFormName.equals("signup")) {
            mJson = appConfig.signupConfig;
        }
        else {
            Log.e(TAG, "Unknown form name " + mFormName);
        }

        mTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (mTitle == null) mTitle = mJson.optString("title", appConfig.appName);

        mFormUrl = AppConfig.optString(mJson, "interceptUrl");
        mErrorUrl = AppConfig.optString(mJson, "errorUrl");

        this.setTitle(mTitle);

        mHiddenWebView = new WebView(this);
        WebSettings webSettings = mHiddenWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setSaveFormData(false);
        webSettings.setSavePassword(false);
        webSettings.setUserAgentString(appConfig.userAgent);
        mHiddenWebView.setWebViewClient(new WebFormWebViewClient());
        mHiddenWebView.setWebChromeClient(new WebFormWebChromeClient());
        mHiddenWebView.addJavascriptInterface(new jsBridge(), "gonative_js_bridge");

        setContentView(R.layout.activity_web_form);

        mLoginFormView = findViewById(R.id.login_form);
        mLoginStatusView = findViewById(R.id.login_status);
        mSubmitButton = (Button) findViewById(R.id.submit_button);

        // if login is the first page that loads. Hide form until login check is done.
        if (mIsLogin && AppConfig.getInstance(this).loginIsFirstPage) {
            mLoginStatusView.setVisibility(View.VISIBLE);
            mLoginFormView.setVisibility(View.GONE);

            // observe login manager
            LoginManager.getInstance().addObserver(this);
            LoginManager.getInstance().checkIfNotAlreadyChecking();
        } else {
            // show back button
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        if (getSupportActionBar() != null) {
            if (appConfig.hideTitleInActionBar) {
                getSupportActionBar().setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
            }
        }

        processForm(mFormName);

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
        if (!AppConfig.getInstance(this).loginIsFirstPage)
            mHiddenWebView.loadUrl(this.mJson.optString("interceptUrl", ""));
        else
            LoginManager.getInstance().checkIfNotAlreadyChecking();
    }

    protected void onDestroy() {
        super.onDestroy();
        // stop timers
        if (this.checkLoginStatusHandler != null) {
            this.checkLoginStatusHandler.removeCallbacksAndMessages(null);
        }

        if (this.checkAjaxHandler != null) {
            this.checkAjaxHandler.removeCallbacksAndMessages(null);
        }

        LoginManager.getInstance().deleteObserver(this);

        // destroy webview
        mHiddenWebView.stopLoading();
        mHiddenWebView.destroy();
    }

    public void update (Observable sender, Object data) {
        if (sender instanceof LoginManager && mIsLogin) {
            sender.deleteObserver(this);

            if (((LoginManager) sender).isLoggedIn()) {
                // back to main activity
                Intent returnIntent = new Intent();
                returnIntent.putExtra("success", true);

                String currentUrl = mHiddenWebView.getUrl();
                if (currentUrl != null && !currentUrl.equals(this.mJson.optString("interceptUrl", ""))) {
                    returnIntent.putExtra("url", mHiddenWebView.getUrl());
                }

                setResult(RESULT_OK, returnIntent);
                finish();
            } else if(AppConfig.getInstance(this).loginIsFirstPage) {
                mHiddenWebView.loadUrl(this.mJson.optString("interceptUrl", ""));
                mLoginStatusView.setVisibility(View.GONE);
                mLoginFormView.setVisibility(View.VISIBLE);
            }
        }
    }

    public void processForm(String configName) {
        try {
            JSONArray jsonFields = mJson.getJSONArray("formInputs");

            fields = new ArrayList<JSONObject>();
            mFieldRefs = new ArrayList<View>();

            JSONObject lastPasswordField = null;

            LinearLayout formLayout = (LinearLayout) findViewById(R.id.form_layout);
            LayoutInflater layoutInflater = getLayoutInflater();
            for (int i = 0; i < jsonFields.length(); i++){
                JSONObject field = jsonFields.optJSONObject(i);
                if (field == null) continue;

                String type = field.getString("type");

                if (type.equals("email") || type.equals("name") ||
                        type.equals("text") || type.equals("number")){

                    layoutInflater.inflate(R.layout.form_text, formLayout, true);
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
                    fields.add(field);
                    mFieldRefs.add(textField);
                }
                else if (type.equals("password")) {
                    layoutInflater.inflate(R.layout.form_password, formLayout, true);
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

                    lastPasswordField = field;
                    fields.add(field);
                    mFieldRefs.add(textField);
                }
                else if (type.equals("password (hidden)")) {
                    if (lastPasswordField != null) {
                        lastPasswordField.put("selector2", AppConfig.optString(field, "selector"));
                    }
                }
                else if (type.equals("options")) {
                    layoutInflater.inflate(R.layout.form_option, formLayout, true);
                    TextView label = (TextView) formLayout.getChildAt(formLayout.getChildCount() - 2);
                    label.setText(field.getString("label"));

                    RadioGroup rg = (RadioGroup) formLayout.getChildAt(formLayout.getChildCount() - 1);
                    JSONArray choices = field.getJSONArray("choices");

                    for (int j = 0; j < choices.length(); j++) {
                        RadioButton rb = new RadioButton(this);
                        rb.setText(choices.getJSONObject(j).getString("label"));
                        rg.addView(rb);
                    }

                    fields.add(field);
                    mFieldRefs.add(rg);
                }
                else if (type.equals("list")) {
                    layoutInflater.inflate(R.layout.form_list, formLayout, true);
                    TextView label = (TextView) formLayout.getChildAt(formLayout.getChildCount() - 2);
                    label.setText(field.getString("label"));

                    RadioGroup rg = (RadioGroup) formLayout.getChildAt(formLayout.getChildCount() - 1);
                    JSONArray choices = field.getJSONArray("choices");

                    for (int j = 0; j < choices.length(); j++) {
                        RadioButton rb = new RadioButton(this);
                        rb.setText(choices.getJSONObject(j).getString("label"));
                        rg.addView(rb);
                    }

                    fields.add(field);
                    mFieldRefs.add(rg);
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

        if (mIsLogin && mForgotPasswordUrl != null){
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
            returnIntent.putExtra("url", mForgotPasswordUrl);
            returnIntent.putExtra("success", false);
            setResult(RESULT_OK, returnIntent);
            finish();

            return true;
        }
        else if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }
        else
            return super.onOptionsItemSelected(item);
    }

    public void submit() {
        try {
            if (validateForm()){
//                setContentView(mHiddenWebView);

                for (int i = 0; i < fields.size(); i++) {
                    JSONObject field = fields.get(i);
                    String type = field.getString("type");

                    if (type.equals("email") || type.equals("name") ||
                            type.equals("text") || type.equals("number") ||
                            type.equals("password")) {
                        // these have an EditText
                        EditText textField = (EditText) mFieldRefs.get(i);

                        runJavascript(String.format("jQuery(%s).val(%s);",
                                LeanUtils.jsWrapString(field.getString("selector")),
                                LeanUtils.jsWrapString(textField.getText().toString())));

                        // for password confirmations
                        String selector2 = AppConfig.optString(field, "selector2");
                        if (selector2 != null) {
                            runJavascript(String.format("jQuery(%s).val(%s);",
                                    LeanUtils.jsWrapString(selector2),
                                    LeanUtils.jsWrapString(textField.getText().toString())));
                        }
                    }
                    if ( (type.equals("options") || type.equals("list")) && field.getBoolean("required")) {
                        // RadioGroup
                        RadioGroup rg = (RadioGroup) mFieldRefs.get(i);
                        for(int j = 0; j < rg.getChildCount(); j++){
                            RadioButton rb = (RadioButton) rg.getChildAt(j);
                            if (rb.isChecked()) {
                                runJavascript(String.format("jQuery(%s).click();",
                                        LeanUtils.jsWrapString(field.getString("selector"))));
                            }
                        }
                    }
                }

                // hide keyboard
                InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mLoginFormView.getWindowToken(), 0);

                // submit the form
                String submitButtonSelector = AppConfig.optString(mJson, "submitButtonSelector");
                if (submitButtonSelector != null && submitButtonSelector.length() > 0) {
                    runJavascript(String.format("jQuery(%s).click();",
                            LeanUtils.jsWrapString(submitButtonSelector)));
                } else {
                    runJavascript(String.format("jQuery(%s).submit();",
                            LeanUtils.jsWrapString(mJson.getString("formSelector"))));
                }

                mSubmitted = true;
                mSubmitButton.setEnabled(false);

                // for ajax login forms
                if (mJson.optBoolean("isAjax", false)) {
                    scheduleSubmissionCheck();
                }

                // check login status in 5 seconds just in case we don't get the onPageFinished
                LoginManager.getInstance().addObserver(this);
                this.checkLoginStatusHandler = new Handler();
                this.checkLoginStatusHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        LoginManager.getInstance().checkLogin();
                    }
                }, 5 * 1000);
            }
        }
        catch(Exception e) {
            Log.e(TAG, e.toString(), e);
        }

    }

    void scheduleSubmissionCheck() {
        checkAjaxHandler = new Handler() {
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
        checkAjaxHandler.sendMessageDelayed(message, 1000);
    }

    void cancelSubmissionCheck() {
        if (checkAjaxHandler != null)
            checkAjaxHandler.removeMessages(1);
    }

    void checkSubmissionStatus() {
        if (mSubmitted) {
            try {
                if (mJson.has("errorSelector2")){
                    runJavascript(String.format("gonative_js_bridge.send(jQuery(%s).html(), jQuery(%s).html());",
                            LeanUtils.jsWrapString(mJson.getString("errorSelector")), LeanUtils.jsWrapString(mJson.getString("errorSelector2"))));
                }
                else{
                    runJavascript(String.format("gonative_js_bridge.send(jQuery(%s).html(), null);",
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

        for (int i = 0; i < fields.size(); i++) {
            JSONObject field = fields.get(i);
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
                AppConfig.getInstance(this).loginIsFirstPage) {
            // exit application
            Intent returnIntent = new Intent();
            returnIntent.putExtra("exit", true);
            setResult(RESULT_CANCELED, returnIntent);
            finish();

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void runJavascript(String js) {
        LeanUtils.runJavascriptOnWebView(this.mHiddenWebView, js);
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
            LeanUtils.runJavascriptOnWebView(view, "if (!window.jQuery) {\n" +
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

                    String errorSelector = mJson.optString("errorSelector", "");
                    if (errorSelector.length() > 0) {
                        runJavascript(String.format("if(jQuery(%s).length > 0) alert(jQuery(%s).text()); else alert('Error submitting form');",
                                LeanUtils.jsWrapString(errorSelector),
                                LeanUtils.jsWrapString(errorSelector)));
                    }
                    else {
                        Toast.makeText(WebFormActivity.this, R.string.form_error, Toast.LENGTH_LONG).show();
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
