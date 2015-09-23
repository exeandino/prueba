package org.apache.cordova;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.support.v4.media.TransportMediator;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.CustomViewCallback;
import android.webkit.WebHistoryItem;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout.LayoutParams;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class CordovaWebView extends WebView {
    public static final String CORDOVA_VERSION = "3.7.1";
    static final LayoutParams COVER_SCREEN_GRAVITY_CENTER;
    public static final String TAG = "CordovaWebView";
    private App appPlugin;
    private HashSet<Integer> boundKeyCodes;
    CordovaBridge bridge;
    private CordovaChromeClient chromeClient;
    private CordovaInterface cordova;
    private Whitelist externalWhitelist;
    private Whitelist internalWhitelist;
    private long lastMenuEventTime;
    int loadUrlTimeout;
    String loadedUrl;
    private View mCustomView;
    private CustomViewCallback mCustomViewCallback;
    private boolean paused;
    public PluginManager pluginManager;
    private CordovaPreferences preferences;
    private BroadcastReceiver receiver;
    private CordovaResourceApi resourceApi;
    CordovaWebViewClient viewClient;

    /* renamed from: org.apache.cordova.CordovaWebView.1 */
    class C01401 extends BroadcastReceiver {
        C01401() {
        }

        public void onReceive(Context context, Intent intent) {
            CordovaWebView.this.getSettings().getUserAgentString();
        }
    }

    /* renamed from: org.apache.cordova.CordovaWebView.2 */
    class C01412 implements Runnable {
        final /* synthetic */ CordovaWebView val$me;
        final /* synthetic */ String val$url;

        C01412(CordovaWebView cordovaWebView, String str) {
            this.val$me = cordovaWebView;
            this.val$url = str;
        }

        public void run() {
            this.val$me.stopLoading();
            LOG.m12e(CordovaWebView.TAG, "CordovaWebView: TIMEOUT ERROR!");
            if (CordovaWebView.this.viewClient != null) {
                CordovaWebView.this.viewClient.onReceivedError(this.val$me, -6, "The connection to the server was unsuccessful.", this.val$url);
            }
        }
    }

    /* renamed from: org.apache.cordova.CordovaWebView.3 */
    class C01423 implements Runnable {
        final /* synthetic */ int val$currentLoadUrlTimeout;
        final /* synthetic */ Runnable val$loadError;
        final /* synthetic */ int val$loadUrlTimeoutValue;
        final /* synthetic */ CordovaWebView val$me;

        C01423(int i, CordovaWebView cordovaWebView, int i2, Runnable runnable) {
            this.val$loadUrlTimeoutValue = i;
            this.val$me = cordovaWebView;
            this.val$currentLoadUrlTimeout = i2;
            this.val$loadError = runnable;
        }

        public void run() {
            try {
                synchronized (this) {
                    wait((long) this.val$loadUrlTimeoutValue);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (this.val$me.loadUrlTimeout == this.val$currentLoadUrlTimeout) {
                this.val$me.cordova.getActivity().runOnUiThread(this.val$loadError);
            }
        }
    }

    /* renamed from: org.apache.cordova.CordovaWebView.4 */
    class C01434 implements Runnable {
        final /* synthetic */ CordovaWebView val$me;
        final /* synthetic */ Runnable val$timeoutCheck;
        final /* synthetic */ String val$url;

        C01434(Runnable runnable, CordovaWebView cordovaWebView, String str) {
            this.val$timeoutCheck = runnable;
            this.val$me = cordovaWebView;
            this.val$url = str;
        }

        public void run() {
            CordovaWebView.this.cordova.getThreadPool().execute(this.val$timeoutCheck);
            this.val$me.loadUrlNow(this.val$url);
        }
    }

    class ActivityResult {
        Intent incoming;
        int request;
        int result;

        public ActivityResult(int req, int res, Intent intent) {
            this.request = req;
            this.result = res;
            this.incoming = intent;
        }
    }

    @TargetApi(16)
    private static final class Level16Apis {
        private Level16Apis() {
        }

        static void enableUniversalAccess(WebSettings settings) {
            settings.setAllowUniversalAccessFromFileURLs(true);
        }
    }

    @TargetApi(17)
    private static final class Level17Apis {
        private Level17Apis() {
        }

        static void setMediaPlaybackRequiresUserGesture(WebSettings settings, boolean value) {
            settings.setMediaPlaybackRequiresUserGesture(value);
        }
    }

    static {
        COVER_SCREEN_GRAVITY_CENTER = new LayoutParams(-1, -1, 17);
    }

    public CordovaWebView(Context context) {
        this(context, null);
    }

    public CordovaWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.boundKeyCodes = new HashSet();
        this.loadUrlTimeout = 0;
        this.lastMenuEventTime = 0;
    }

    @Deprecated
    public CordovaWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.boundKeyCodes = new HashSet();
        this.loadUrlTimeout = 0;
        this.lastMenuEventTime = 0;
    }

    @TargetApi(11)
    @Deprecated
    public CordovaWebView(Context context, AttributeSet attrs, int defStyle, boolean privateBrowsing) {
        super(context, attrs, defStyle, privateBrowsing);
        this.boundKeyCodes = new HashSet();
        this.loadUrlTimeout = 0;
        this.lastMenuEventTime = 0;
    }

    public void init(CordovaInterface cordova, CordovaWebViewClient webViewClient, CordovaChromeClient webChromeClient, List<PluginEntry> pluginEntries, Whitelist internalWhitelist, Whitelist externalWhitelist, CordovaPreferences preferences) {
        if (this.cordova != null) {
            throw new IllegalStateException();
        }
        this.cordova = cordova;
        this.viewClient = webViewClient;
        this.chromeClient = webChromeClient;
        this.internalWhitelist = internalWhitelist;
        this.externalWhitelist = externalWhitelist;
        this.preferences = preferences;
        super.setWebChromeClient(webChromeClient);
        super.setWebViewClient(webViewClient);
        this.pluginManager = new PluginManager(this, this.cordova, pluginEntries);
        this.bridge = new CordovaBridge(this.pluginManager, new NativeToJsMessageQueue(this, cordova), this.cordova.getActivity().getPackageName());
        this.resourceApi = new CordovaResourceApi(getContext(), this.pluginManager);
        this.pluginManager.addService(App.PLUGIN_NAME, "org.apache.cordova.App");
        this.pluginManager.addService(new PluginEntry("SplashScreenInternal", "org.apache.cordova.SplashScreenInternal", true));
        this.pluginManager.init();
        initWebViewSettings();
        exposeJsInterface();
    }

    private void initIfNecessary() {
        if (this.pluginManager == null) {
            Log.w(TAG, "CordovaWebView.init() was not called. This will soon be required.");
            CordovaInterface cdv = (CordovaInterface) getContext();
            if (!Config.isInitialized()) {
                Config.init(cdv.getActivity());
            }
            init(cdv, makeWebViewClient(cdv), makeWebChromeClient(cdv), Config.getPluginEntries(), Config.getWhitelist(), Config.getExternalWhitelist(), Config.getPreferences());
        }
    }

    @SuppressLint({"SetJavaScriptEnabled"})
    private void initWebViewSettings() {
        setInitialScale(0);
        setVerticalScrollBarEnabled(false);
        if (shouldRequestFocusOnInit()) {
            requestFocusFromTouch();
        }
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLayoutAlgorithm(LayoutAlgorithm.NORMAL);
        if (VERSION.SDK_INT >= 21) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        }
        try {
            Method gingerbread_getMethod = WebSettings.class.getMethod("setNavDump", new Class[]{Boolean.TYPE});
            Log.d(TAG, "CordovaWebView is running on device made by: " + Build.MANUFACTURER);
            if (VERSION.SDK_INT < 11 && Build.MANUFACTURER.contains("HTC")) {
                gingerbread_getMethod.invoke(settings, new Object[]{Boolean.valueOf(true)});
            }
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "We are on a modern version of Android, we will deprecate HTC 2.3 devices in 2.8");
        } catch (IllegalArgumentException e2) {
            Log.d(TAG, "Doing the NavDump failed with bad arguments");
        } catch (IllegalAccessException e3) {
            Log.d(TAG, "This should never happen: IllegalAccessException means this isn't Android anymore");
        } catch (InvocationTargetException e4) {
            Log.d(TAG, "This should never happen: InvocationTargetException means this isn't Android anymore.");
        }
        settings.setSaveFormData(false);
        settings.setSavePassword(false);
        if (VERSION.SDK_INT >= 16) {
            Level16Apis.enableUniversalAccess(settings);
        }
        if (VERSION.SDK_INT >= 17) {
            Level17Apis.setMediaPlaybackRequiresUserGesture(settings, false);
        }
        String databasePath = getContext().getApplicationContext().getDir("database", 0).getPath();
        settings.setDatabaseEnabled(true);
        settings.setDatabasePath(databasePath);
        if ((getContext().getApplicationContext().getApplicationInfo().flags & 2) != 0 && VERSION.SDK_INT >= 19) {
            enableRemoteDebugging();
        }
        settings.setGeolocationDatabasePath(databasePath);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAppCacheMaxSize(5242880);
        settings.setAppCachePath(databasePath);
        settings.setAppCacheEnabled(true);
        settings.getUserAgentString();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.CONFIGURATION_CHANGED");
        if (this.receiver == null) {
            this.receiver = new C01401();
            getContext().registerReceiver(this.receiver, intentFilter);
        }
    }

    @TargetApi(19)
    private void enableRemoteDebugging() {
        try {
            WebView.setWebContentsDebuggingEnabled(true);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "You have one job! To turn on Remote Web Debugging! YOU HAVE FAILED! ");
            e.printStackTrace();
        }
    }

    public CordovaChromeClient makeWebChromeClient(CordovaInterface cordova) {
        return new CordovaChromeClient(cordova, this);
    }

    public CordovaWebViewClient makeWebViewClient(CordovaInterface cordova) {
        if (VERSION.SDK_INT < 14) {
            return new CordovaWebViewClient(cordova, this);
        }
        return new IceCreamCordovaWebViewClient(cordova, this);
    }

    protected boolean shouldRequestFocusOnInit() {
        return true;
    }

    private void exposeJsInterface() {
        if (VERSION.SDK_INT < 17) {
            Log.i(TAG, "Disabled addJavascriptInterface() bridge since Android version is old.");
        } else {
            addJavascriptInterface(new ExposedJsApi(this.bridge), "_cordovaNative");
        }
    }

    public void setWebViewClient(WebViewClient client) {
        this.viewClient = (CordovaWebViewClient) client;
        super.setWebViewClient(client);
    }

    public void setWebChromeClient(WebChromeClient client) {
        this.chromeClient = (CordovaChromeClient) client;
        super.setWebChromeClient(client);
    }

    public CordovaChromeClient getWebChromeClient() {
        return this.chromeClient;
    }

    public Whitelist getWhitelist() {
        return this.internalWhitelist;
    }

    public Whitelist getExternalWhitelist() {
        return this.externalWhitelist;
    }

    public void loadUrl(String url) {
        if (url.equals("about:blank") || url.startsWith("javascript:")) {
            loadUrlNow(url);
        } else {
            loadUrlIntoView(url);
        }
    }

    @Deprecated
    public void loadUrl(String url, int time) {
        if (url == null) {
            loadUrlIntoView(Config.getStartUrl());
        } else {
            loadUrlIntoView(url);
        }
    }

    public void loadUrlIntoView(String url) {
        loadUrlIntoView(url, true);
    }

    public void loadUrlIntoView(String url, boolean recreatePlugins) {
        LOG.m9d(TAG, ">>> loadUrl(" + url + ")");
        initIfNecessary();
        if (recreatePlugins) {
            if (this.loadedUrl != null) {
                this.pluginManager.init();
            }
            this.loadedUrl = url;
        }
        int currentLoadUrlTimeout = this.loadUrlTimeout;
        this.cordova.getActivity().runOnUiThread(new C01434(new C01423(Integer.parseInt(getProperty("LoadUrlTimeoutValue", "20000")), this, currentLoadUrlTimeout, new C01412(this, url)), this, url));
    }

    void loadUrlNow(String url) {
        if (LOG.isLoggable(3) && !url.startsWith("javascript:")) {
            LOG.m9d(TAG, ">>> loadUrlNow()");
        }
        if (url.startsWith("file://") || url.startsWith("javascript:") || url.startsWith("about:") || this.internalWhitelist.isUrlWhiteListed(url)) {
            super.loadUrl(url);
        }
    }

    public void loadUrlIntoView(String url, int time) {
        if (!(url.startsWith("javascript:") || canGoBack())) {
            LOG.m11d(TAG, "loadUrlIntoView(%s, %d)", url, Integer.valueOf(time));
        }
        loadUrlIntoView(url);
    }

    public void stopLoading() {
        this.viewClient.isCurrentlyLoading = false;
        super.stopLoading();
    }

    public void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        postMessage("onScrollChanged", new ScrollEvent(l, t, oldl, oldt, this));
    }

    @Deprecated
    public void sendJavascript(String statement) {
        this.bridge.getMessageQueue().addJavaScript(statement);
    }

    public void sendPluginResult(PluginResult result, String callbackId) {
        this.bridge.getMessageQueue().addPluginResult(result, callbackId);
    }

    public void postMessage(String id, Object data) {
        if (this.pluginManager != null) {
            this.pluginManager.postMessage(id, data);
        }
    }

    public boolean backHistory() {
        if (!super.canGoBack()) {
            return false;
        }
        super.goBack();
        return true;
    }

    public void showWebPage(String url, boolean openExternal, boolean clearHistory, HashMap<String, Object> hashMap) {
        LOG.m11d(TAG, "showWebPage(%s, %b, %b, HashMap", url, Boolean.valueOf(openExternal), Boolean.valueOf(clearHistory));
        if (clearHistory) {
            clearHistory();
        }
        if (!openExternal) {
            if (url.startsWith("file://") || this.internalWhitelist.isUrlWhiteListed(url)) {
                loadUrl(url);
                return;
            }
            LOG.m21w(TAG, "showWebPage: Cannot load URL into webview since it is not in white list.  Loading into browser instead. (URL=" + url + ")");
        }
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            Uri uri = Uri.parse(url);
            if ("file".equals(uri.getScheme())) {
                intent.setDataAndType(uri, this.resourceApi.getMimeType(uri));
            } else {
                intent.setData(uri);
            }
            this.cordova.getActivity().startActivity(intent);
        } catch (Throwable e) {
            LOG.m13e(TAG, "Error loading url " + url, e);
        }
    }

    public String getProperty(String name, String defaultValue) {
        Bundle bundle = this.cordova.getActivity().getIntent().getExtras();
        if (bundle == null) {
            return defaultValue;
        }
        Object p = bundle.get(name.toLowerCase(Locale.getDefault()));
        return p != null ? p.toString() : defaultValue;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean z = false;
        if (this.boundKeyCodes.contains(Integer.valueOf(keyCode))) {
            if (keyCode == 25) {
                sendJavascriptEvent("volumedownbutton");
                return true;
            } else if (keyCode != 24) {
                return super.onKeyDown(keyCode, event);
            } else {
                sendJavascriptEvent("volumeupbutton");
                return true;
            }
        } else if (keyCode == 4) {
            if (!startOfHistory() || isButtonPlumbedToJs(4)) {
                z = true;
            }
            return z;
        } else if (keyCode != 82) {
            return super.onKeyDown(keyCode, event);
        } else {
            View childView = getFocusedChild();
            if (childView == null) {
                return super.onKeyDown(keyCode, event);
            }
            ((InputMethodManager) this.cordova.getActivity().getSystemService("input_method")).hideSoftInputFromWindow(childView.getWindowToken(), 0);
            this.cordova.getActivity().openOptionsMenu();
            return true;
        }
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == 4) {
            if (this.mCustomView != null) {
                hideCustomView();
                return true;
            } else if (isButtonPlumbedToJs(4)) {
                sendJavascriptEvent("backbutton");
                return true;
            } else if (backHistory()) {
                return true;
            }
        } else if (keyCode == 82) {
            if (this.lastMenuEventTime < event.getEventTime()) {
                sendJavascriptEvent("menubutton");
            }
            this.lastMenuEventTime = event.getEventTime();
            return super.onKeyUp(keyCode, event);
        } else if (keyCode == 84) {
            sendJavascriptEvent("searchbutton");
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void sendJavascriptEvent(String event) {
        if (this.appPlugin == null) {
            this.appPlugin = (App) this.pluginManager.getPlugin(App.PLUGIN_NAME);
        }
        if (this.appPlugin == null) {
            LOG.m21w(TAG, "Unable to fire event without existing plugin");
        } else {
            this.appPlugin.fireJavascriptEvent(event);
        }
    }

    public void setButtonPlumbedToJs(int keyCode, boolean override) {
        switch (keyCode) {
            case TransportMediator.FLAG_KEY_MEDIA_PLAY /*4*/:
            case MotionEventCompat.AXIS_DISTANCE /*24*/:
            case MotionEventCompat.AXIS_TILT /*25*/:
                if (override) {
                    this.boundKeyCodes.add(Integer.valueOf(keyCode));
                } else {
                    this.boundKeyCodes.remove(Integer.valueOf(keyCode));
                }
            default:
                throw new IllegalArgumentException("Unsupported keycode: " + keyCode);
        }
    }

    @Deprecated
    public void bindButton(boolean override) {
        setButtonPlumbedToJs(4, override);
    }

    @Deprecated
    public void bindButton(String button, boolean override) {
        if (button.compareTo("volumeup") == 0) {
            setButtonPlumbedToJs(24, override);
        } else if (button.compareTo("volumedown") == 0) {
            setButtonPlumbedToJs(25, override);
        }
    }

    @Deprecated
    public void bindButton(int keyCode, boolean keyDown, boolean override) {
        setButtonPlumbedToJs(keyCode, override);
    }

    @Deprecated
    public boolean isBackButtonBound() {
        return isButtonPlumbedToJs(4);
    }

    public boolean isButtonPlumbedToJs(int keyCode) {
        return this.boundKeyCodes.contains(Integer.valueOf(keyCode));
    }

    public void handlePause(boolean keepRunning) {
        LOG.m9d(TAG, "Handle the pause");
        sendJavascriptEvent("pause");
        if (this.pluginManager != null) {
            this.pluginManager.onPause(keepRunning);
        }
        if (!keepRunning) {
            pauseTimers();
        }
        this.paused = true;
    }

    public void handleResume(boolean keepRunning, boolean activityResultKeepRunning) {
        sendJavascriptEvent("resume");
        if (this.pluginManager != null) {
            this.pluginManager.onResume(keepRunning);
        }
        resumeTimers();
        this.paused = false;
    }

    public void handleDestroy() {
        this.loadUrlTimeout++;
        loadUrl("about:blank");
        this.chromeClient.destroyLastDialog();
        if (this.pluginManager != null) {
            this.pluginManager.onDestroy();
        }
        if (this.receiver != null) {
            try {
                getContext().unregisterReceiver(this.receiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering configuration receiver: " + e.getMessage(), e);
            }
        }
    }

    public void onNewIntent(Intent intent) {
        if (this.pluginManager != null) {
            this.pluginManager.onNewIntent(intent);
        }
    }

    public boolean isPaused() {
        return this.paused;
    }

    @Deprecated
    public boolean hadKeyEvent() {
        return false;
    }

    public void printBackForwardList() {
        WebBackForwardList currentList = copyBackForwardList();
        int currentSize = currentList.getSize();
        for (int i = 0; i < currentSize; i++) {
            LOG.m9d(TAG, "The URL at index: " + Integer.toString(i) + " is " + currentList.getItemAtIndex(i).getUrl());
        }
    }

    public boolean startOfHistory() {
        WebHistoryItem item = copyBackForwardList().getItemAtIndex(0);
        if (item == null) {
            return false;
        }
        String url = item.getUrl();
        String currentUrl = getUrl();
        LOG.m9d(TAG, "The current URL is: " + currentUrl);
        LOG.m9d(TAG, "The URL at item 0 is: " + url);
        return currentUrl.equals(url);
    }

    public void showCustomView(View view, CustomViewCallback callback) {
        Log.d(TAG, "showing Custom View");
        if (this.mCustomView != null) {
            callback.onCustomViewHidden();
            return;
        }
        this.mCustomView = view;
        this.mCustomViewCallback = callback;
        ViewGroup parent = (ViewGroup) getParent();
        parent.addView(view, COVER_SCREEN_GRAVITY_CENTER);
        setVisibility(8);
        parent.setVisibility(0);
        parent.bringToFront();
    }

    public void hideCustomView() {
        Log.d(TAG, "Hiding Custom View");
        if (this.mCustomView != null) {
            this.mCustomView.setVisibility(8);
            ((ViewGroup) getParent()).removeView(this.mCustomView);
            this.mCustomView = null;
            this.mCustomViewCallback.onCustomViewHidden();
            setVisibility(0);
        }
    }

    public boolean isCustomViewShowing() {
        return this.mCustomView != null;
    }

    public WebBackForwardList restoreState(Bundle savedInstanceState) {
        WebBackForwardList myList = super.restoreState(savedInstanceState);
        Log.d(TAG, "WebView restoration crew now restoring!");
        this.pluginManager.init();
        return myList;
    }

    @Deprecated
    public void storeResult(int requestCode, int resultCode, Intent intent) {
    }

    public CordovaResourceApi getResourceApi() {
        return this.resourceApi;
    }

    public CordovaPreferences getPreferences() {
        return this.preferences;
    }
}
