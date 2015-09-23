package org.apache.cordova;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import com.plugin.gcm.PushPlugin;
import com.squareup.okhttp.internal.http.HttpTransport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CordovaActivity extends Activity implements CordovaInterface {
    private static int ACTIVITY_EXITING;
    private static int ACTIVITY_RUNNING;
    private static int ACTIVITY_STARTING;
    public static String TAG;
    protected CordovaPlugin activityResultCallback;
    protected boolean activityResultKeepRunning;
    protected int activityResultRequestCode;
    private int activityState;
    protected CordovaWebView appView;
    protected Whitelist externalWhitelist;
    private String initCallbackClass;
    protected Whitelist internalWhitelist;
    protected boolean keepRunning;
    protected String launchUrl;
    protected int loadUrlTimeoutValue;
    protected ArrayList<PluginEntry> pluginEntries;
    protected CordovaPreferences preferences;
    @Deprecated
    protected LinearLayout root;
    @Deprecated
    protected int splashscreen;
    @Deprecated
    protected int splashscreenTime;
    private final ExecutorService threadPool;
    @Deprecated
    protected CordovaWebViewClient webViewClient;

    /* renamed from: org.apache.cordova.CordovaActivity.1 */
    class C01271 implements Runnable {
        final /* synthetic */ String val$errorUrl;
        final /* synthetic */ CordovaActivity val$me;

        C01271(CordovaActivity cordovaActivity, String str) {
            this.val$me = cordovaActivity;
            this.val$errorUrl = str;
        }

        public void run() {
            this.val$me.appView.showWebPage(this.val$errorUrl, false, true, null);
        }
    }

    /* renamed from: org.apache.cordova.CordovaActivity.2 */
    class C01282 implements Runnable {
        final /* synthetic */ String val$description;
        final /* synthetic */ boolean val$exit;
        final /* synthetic */ String val$failingUrl;
        final /* synthetic */ CordovaActivity val$me;

        C01282(boolean z, CordovaActivity cordovaActivity, String str, String str2) {
            this.val$exit = z;
            this.val$me = cordovaActivity;
            this.val$description = str;
            this.val$failingUrl = str2;
        }

        public void run() {
            if (this.val$exit) {
                this.val$me.appView.setVisibility(8);
                this.val$me.displayError("Application Error", this.val$description + " (" + this.val$failingUrl + ")", "OK", this.val$exit);
            }
        }
    }

    /* renamed from: org.apache.cordova.CordovaActivity.3 */
    class C01303 implements Runnable {
        final /* synthetic */ String val$button;
        final /* synthetic */ boolean val$exit;
        final /* synthetic */ CordovaActivity val$me;
        final /* synthetic */ String val$message;
        final /* synthetic */ String val$title;

        /* renamed from: org.apache.cordova.CordovaActivity.3.1 */
        class C01291 implements OnClickListener {
            C01291() {
            }

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (C01303.this.val$exit) {
                    C01303.this.val$me.endActivity();
                }
            }
        }

        C01303(CordovaActivity cordovaActivity, String str, String str2, String str3, boolean z) {
            this.val$me = cordovaActivity;
            this.val$message = str;
            this.val$title = str2;
            this.val$button = str3;
            this.val$exit = z;
        }

        public void run() {
            try {
                Builder dlg = new Builder(this.val$me);
                dlg.setMessage(this.val$message);
                dlg.setTitle(this.val$title);
                dlg.setCancelable(false);
                dlg.setPositiveButton(this.val$button, new C01291());
                dlg.create();
                dlg.show();
            } catch (Exception e) {
                CordovaActivity.this.finish();
            }
        }
    }

    public CordovaActivity() {
        this.threadPool = Executors.newCachedThreadPool();
        this.activityState = 0;
        this.splashscreen = 0;
        this.splashscreenTime = -1;
        this.loadUrlTimeoutValue = 20000;
        this.keepRunning = true;
    }

    static {
        TAG = "CordovaActivity";
        ACTIVITY_STARTING = 0;
        ACTIVITY_RUNNING = 1;
        ACTIVITY_EXITING = 2;
    }

    public void setAuthenticationToken(AuthenticationToken authenticationToken, String host, String realm) {
        if (this.appView != null && this.appView.viewClient != null) {
            this.appView.viewClient.setAuthenticationToken(authenticationToken, host, realm);
        }
    }

    public AuthenticationToken removeAuthenticationToken(String host, String realm) {
        if (this.appView == null || this.appView.viewClient == null) {
            return null;
        }
        return this.appView.viewClient.removeAuthenticationToken(host, realm);
    }

    public AuthenticationToken getAuthenticationToken(String host, String realm) {
        if (this.appView == null || this.appView.viewClient == null) {
            return null;
        }
        return this.appView.viewClient.getAuthenticationToken(host, realm);
    }

    public void clearAuthenticationTokens() {
        if (this.appView != null && this.appView.viewClient != null) {
            this.appView.viewClient.clearAuthenticationTokens();
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        LOG.m15i(TAG, "Apache Cordova native platform version 3.7.1 is starting");
        LOG.m9d(TAG, "CordovaActivity.onCreate()");
        loadConfig();
        if (!this.preferences.getBoolean("ShowTitle", false)) {
            getWindow().requestFeature(1);
        }
        if (this.preferences.getBoolean("SetFullscreen", false)) {
            Log.d(TAG, "The SetFullscreen configuration is deprecated in favor of Fullscreen, and will be removed in a future version.");
            getWindow().setFlags(HttpTransport.DEFAULT_CHUNK_LENGTH, HttpTransport.DEFAULT_CHUNK_LENGTH);
        } else if (this.preferences.getBoolean("Fullscreen", false)) {
            getWindow().setFlags(HttpTransport.DEFAULT_CHUNK_LENGTH, HttpTransport.DEFAULT_CHUNK_LENGTH);
        } else {
            getWindow().setFlags(AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT, AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT);
        }
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            this.initCallbackClass = savedInstanceState.getString("callbackClass");
        }
    }

    protected void loadConfig() {
        ConfigXmlParser parser = new ConfigXmlParser();
        parser.parse((Activity) this);
        this.preferences = parser.getPreferences();
        this.preferences.setPreferencesBundle(getIntent().getExtras());
        this.preferences.copyIntoIntentExtras(this);
        this.internalWhitelist = parser.getInternalWhitelist();
        this.externalWhitelist = parser.getExternalWhitelist();
        this.launchUrl = parser.getLaunchUrl();
        this.pluginEntries = parser.getPluginEntries();
        Config.parser = parser;
    }

    protected void createViews() {
        LOG.m9d(TAG, "CordovaActivity.createViews()");
        Display display = getWindowManager().getDefaultDisplay();
        this.root = new LinearLayoutSoftKeyboardDetect(this, display.getWidth(), display.getHeight());
        this.root.setOrientation(1);
        this.root.setLayoutParams(new LayoutParams(-1, -1, 0.0f));
        this.appView.setId(100);
        this.appView.setLayoutParams(new LayoutParams(-1, -1, 1.0f));
        ViewParent parent = this.appView.getParent();
        if (!(parent == null || parent == this.root)) {
            LOG.m9d(TAG, "removing appView from existing parent");
            ((ViewGroup) parent).removeView(this.appView);
        }
        this.root.addView(this.appView);
        setContentView(this.root);
        this.root.setBackgroundColor(this.preferences.getInteger("BackgroundColor", ViewCompat.MEASURED_STATE_MASK));
    }

    public Activity getActivity() {
        return this;
    }

    protected CordovaWebView makeWebView() {
        return new CordovaWebView(this);
    }

    protected CordovaWebViewClient makeWebViewClient(CordovaWebView webView) {
        return webView.makeWebViewClient(this);
    }

    protected CordovaChromeClient makeChromeClient(CordovaWebView webView) {
        return webView.makeWebChromeClient(this);
    }

    public void init() {
        init(this.appView, null, null);
    }

    @SuppressLint({"NewApi"})
    @Deprecated
    public void init(CordovaWebView webView, CordovaWebViewClient webViewClient, CordovaChromeClient webChromeClient) {
        LOG.m9d(TAG, "CordovaActivity.init()");
        if (this.splashscreenTime >= 0) {
            this.preferences.set("SplashScreenDelay", this.splashscreenTime);
        }
        if (this.splashscreen != 0) {
            this.preferences.set("SplashDrawableId", this.splashscreen);
        }
        if (webView == null) {
            webView = makeWebView();
        }
        this.appView = webView;
        if (this.preferences.getBoolean("DisallowOverscroll", false)) {
            this.appView.setOverScrollMode(2);
        }
        createViews();
        if (this.appView.pluginManager == null) {
            this.appView.init(this, webViewClient != null ? webViewClient : makeWebViewClient(this.appView), webChromeClient != null ? webChromeClient : makeChromeClient(this.appView), this.pluginEntries, this.internalWhitelist, this.externalWhitelist, this.preferences);
        }
        if ("media".equals(this.preferences.getString("DefaultVolumeStream", "").toLowerCase(Locale.ENGLISH))) {
            setVolumeControlStream(3);
        }
    }

    public void loadUrl(String url) {
        if (this.appView == null) {
            init();
        }
        this.keepRunning = this.preferences.getBoolean("KeepRunning", true);
        this.appView.loadUrlIntoView(url, true);
    }

    @Deprecated
    public void loadUrl(String url, int time) {
        this.splashscreenTime = time;
        loadUrl(url);
    }

    @Deprecated
    public void cancelLoadUrl() {
    }

    @Deprecated
    public void clearCache() {
        if (this.appView == null) {
            init();
        }
        this.appView.clearCache(true);
    }

    @Deprecated
    public void clearHistory() {
        this.appView.clearHistory();
    }

    @Deprecated
    public boolean backHistory() {
        if (this.appView != null) {
            return this.appView.backHistory();
        }
        return false;
    }

    @Deprecated
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        return this.preferences.getBoolean(name, defaultValue);
    }

    @Deprecated
    public int getIntegerProperty(String name, int defaultValue) {
        return this.preferences.getInteger(name, defaultValue);
    }

    @Deprecated
    public String getStringProperty(String name, String defaultValue) {
        return this.preferences.getString(name, defaultValue);
    }

    @Deprecated
    public double getDoubleProperty(String name, double defaultValue) {
        return this.preferences.getDouble(name, defaultValue);
    }

    @Deprecated
    public void setBooleanProperty(String name, boolean value) {
        Log.d(TAG, "Setting boolean properties in CordovaActivity will be deprecated in 3.0 on July 2013, please use config.xml");
        getIntent().putExtra(name.toLowerCase(), value);
    }

    @Deprecated
    public void setIntegerProperty(String name, int value) {
        Log.d(TAG, "Setting integer properties in CordovaActivity will be deprecated in 3.0 on July 2013, please use config.xml");
        getIntent().putExtra(name.toLowerCase(), value);
    }

    @Deprecated
    public void setStringProperty(String name, String value) {
        Log.d(TAG, "Setting string properties in CordovaActivity will be deprecated in 3.0 on July 2013, please use config.xml");
        getIntent().putExtra(name.toLowerCase(), value);
    }

    @Deprecated
    public void setDoubleProperty(String name, double value) {
        Log.d(TAG, "Setting double properties in CordovaActivity will be deprecated in 3.0 on July 2013, please use config.xml");
        getIntent().putExtra(name.toLowerCase(), value);
    }

    protected void onPause() {
        super.onPause();
        LOG.m9d(TAG, "Paused the application!");
        if (this.activityState != ACTIVITY_EXITING && this.appView != null) {
            this.appView.handlePause(this.keepRunning);
        }
    }

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (this.appView != null) {
            this.appView.onNewIntent(intent);
        }
    }

    protected void onResume() {
        super.onResume();
        LOG.m9d(TAG, "Resuming the App");
        if (this.activityState == ACTIVITY_STARTING) {
            this.activityState = ACTIVITY_RUNNING;
        } else if (this.appView != null) {
            getWindow().getDecorView().requestFocus();
            this.appView.handleResume(this.keepRunning, this.activityResultKeepRunning);
            if ((!this.keepRunning || this.activityResultKeepRunning) && this.activityResultKeepRunning) {
                this.keepRunning = this.activityResultKeepRunning;
                this.activityResultKeepRunning = false;
            }
        }
    }

    public void onDestroy() {
        LOG.m9d(TAG, "CordovaActivity.onDestroy()");
        super.onDestroy();
        if (this.appView != null) {
            this.appView.handleDestroy();
        } else {
            this.activityState = ACTIVITY_EXITING;
        }
    }

    public void postMessage(String id, Object data) {
        if (this.appView != null) {
            this.appView.postMessage(id, data);
        }
    }

    @Deprecated
    public void addService(String serviceType, String className) {
        if (this.appView != null && this.appView.pluginManager != null) {
            this.appView.pluginManager.addService(serviceType, className);
        }
    }

    @Deprecated
    public void sendJavascript(String statement) {
        if (this.appView != null) {
            this.appView.bridge.getMessageQueue().addJavaScript(statement);
        }
    }

    @Deprecated
    public void spinnerStart(String title, String message) {
        JSONArray args = new JSONArray();
        args.put(title);
        args.put(message);
        doSplashScreenAction("spinnerStart", args);
    }

    @Deprecated
    public void spinnerStop() {
        doSplashScreenAction("spinnerStop", null);
    }

    public void endActivity() {
        this.activityState = ACTIVITY_EXITING;
        super.finish();
    }

    public void startActivityForResult(CordovaPlugin command, Intent intent, int requestCode) {
        setActivityResultCallback(command);
        this.activityResultKeepRunning = this.keepRunning;
        if (command != null) {
            this.keepRunning = false;
        }
        try {
            startActivityForResult(intent, requestCode);
        } catch (RuntimeException e) {
            this.activityResultCallback = null;
            throw e;
        }
    }

    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        this.activityResultRequestCode = requestCode;
        super.startActivityForResult(intent, requestCode, options);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        LOG.m9d(TAG, "Incoming Result. Request code = " + requestCode);
        super.onActivityResult(requestCode, resultCode, intent);
        CordovaPlugin callback = this.activityResultCallback;
        if (callback == null && this.initCallbackClass != null) {
            callback = this.appView.pluginManager.getPlugin(this.initCallbackClass);
        }
        this.initCallbackClass = null;
        this.activityResultCallback = null;
        if (callback != null) {
            LOG.m9d(TAG, "We have a callback to send this result to");
            callback.onActivityResult(requestCode, resultCode, intent);
            return;
        }
        LOG.m21w(TAG, "Got an activity result, but no plugin was registered to receive it.");
    }

    public void setActivityResultCallback(CordovaPlugin plugin) {
        if (this.activityResultCallback != null) {
            this.activityResultCallback.onActivityResult(this.activityResultRequestCode, 0, null);
        }
        this.activityResultCallback = plugin;
    }

    public void onReceivedError(int errorCode, String description, String failingUrl) {
        CordovaActivity me = this;
        String errorUrl = this.preferences.getString("errorUrl", null);
        if (errorUrl == null || (!(errorUrl.startsWith("file://") || this.internalWhitelist.isUrlWhiteListed(errorUrl)) || failingUrl.equals(errorUrl))) {
            runOnUiThread(new C01282(errorCode != -2, me, description, failingUrl));
        } else {
            runOnUiThread(new C01271(me, errorUrl));
        }
    }

    public void displayError(String title, String message, String button, boolean exit) {
        runOnUiThread(new C01303(this, message, title, button, exit));
    }

    @Deprecated
    public boolean isUrlWhiteListed(String url) {
        return this.internalWhitelist.isUrlWhiteListed(url);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        postMessage("onCreateOptionsMenu", menu);
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        postMessage("onPrepareOptionsMenu", menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        postMessage("onOptionsItemSelected", item);
        return true;
    }

    @Deprecated
    public Context getContext() {
        LOG.m9d(TAG, "This will be deprecated December 2012");
        return this;
    }

    @Deprecated
    public void showWebPage(String url, boolean openExternal, boolean clearHistory, HashMap<String, Object> params) {
        if (this.appView != null) {
            this.appView.showWebPage(url, openExternal, clearHistory, params);
        }
    }

    private void doSplashScreenAction(String action, JSONArray args) {
        CordovaPlugin p = this.appView.pluginManager.getPlugin("org.apache.cordova.splashscreeninternal");
        if (p != null) {
            if (args == null) {
                args = new JSONArray();
            }
            try {
                p.execute(action, args, null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Deprecated
    public void removeSplashScreen() {
        doSplashScreenAction("hide", null);
    }

    @Deprecated
    protected void showSplashScreen(int time) {
        this.preferences.set("SplashScreenDelay", time);
        doSplashScreenAction("show", null);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (this.appView == null || ((!this.appView.isCustomViewShowing() && this.appView.getFocusedChild() == null) || (keyCode != 4 && keyCode != 82))) {
            return super.onKeyUp(keyCode, event);
        }
        return this.appView.onKeyUp(keyCode, event);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (this.appView == null || this.appView.getFocusedChild() == null || (keyCode != 4 && keyCode != 82)) {
            return super.onKeyDown(keyCode, event);
        }
        return this.appView.onKeyDown(keyCode, event);
    }

    public Object onMessage(String id, Object data) {
        if (!"onScrollChanged".equals(id)) {
            LOG.m9d(TAG, "onMessage(" + id + "," + data + ")");
        }
        if ("onReceivedError".equals(id)) {
            JSONObject d = (JSONObject) data;
            try {
                onReceivedError(d.getInt("errorCode"), d.getString("description"), d.getString("url"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (PushPlugin.EXIT.equals(id)) {
            endActivity();
        }
        return null;
    }

    public ExecutorService getThreadPool() {
        return this.threadPool;
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.activityResultCallback != null) {
            outState.putString("callbackClass", this.activityResultCallback.getClass().getName());
        }
    }
}
