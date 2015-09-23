package org.apache.cordova.inappbrowser;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.StringTokenizer;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.Config;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.apache.cordova.globalization.Globalization;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SuppressLint({"SetJavaScriptEnabled"})
public class InAppBrowser extends CordovaPlugin {
    private static final String CLEAR_ALL_CACHE = "clearcache";
    private static final String CLEAR_SESSION_CACHE = "clearsessioncache";
    private static final String EXIT_EVENT = "exit";
    private static final String HARDWARE_BACK_BUTTON = "hardwareback";
    private static final String HIDDEN = "hidden";
    private static final String LOAD_ERROR_EVENT = "loaderror";
    private static final String LOAD_START_EVENT = "loadstart";
    private static final String LOAD_STOP_EVENT = "loadstop";
    private static final String LOCATION = "location";
    protected static final String LOG_TAG = "InAppBrowser";
    private static final String NULL = "null";
    private static final String SELF = "_self";
    private static final String SYSTEM = "_system";
    private static final String ZOOM = "zoom";
    private CallbackContext callbackContext;
    private boolean clearAllCache;
    private boolean clearSessionCache;
    private InAppBrowserDialog dialog;
    private EditText edittext;
    private boolean hadwareBackButton;
    private WebView inAppWebView;
    private boolean openWindowHidden;
    private boolean showLocationBar;
    private boolean showZoomControls;

    /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.1 */
    class C01651 implements Runnable {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ HashMap val$features;
        final /* synthetic */ String val$target;
        final /* synthetic */ String val$url;

        C01651(String str, String str2, HashMap hashMap, CallbackContext callbackContext) {
            this.val$target = str;
            this.val$url = str2;
            this.val$features = hashMap;
            this.val$callbackContext = callbackContext;
        }

        public void run() {
            String result = "";
            if (InAppBrowser.SELF.equals(this.val$target)) {
                Log.d(InAppBrowser.LOG_TAG, "in self");
                Boolean shouldAllowNavigation = null;
                if (this.val$url.startsWith("javascript:")) {
                    shouldAllowNavigation = Boolean.valueOf(true);
                }
                if (shouldAllowNavigation == null) {
                    try {
                        shouldAllowNavigation = (Boolean) Config.class.getMethod("isUrlWhiteListed", new Class[]{String.class}).invoke(null, new Object[]{this.val$url});
                    } catch (NoSuchMethodException e) {
                    } catch (IllegalAccessException e2) {
                    } catch (InvocationTargetException e3) {
                    }
                }
                if (shouldAllowNavigation == null) {
                    try {
                        PluginManager pm = (PluginManager) InAppBrowser.this.webView.getClass().getMethod("getPluginManager", new Class[0]).invoke(InAppBrowser.this.webView, new Object[0]);
                        shouldAllowNavigation = (Boolean) pm.getClass().getMethod("shouldAllowNavigation", new Class[]{String.class}).invoke(pm, new Object[]{this.val$url});
                    } catch (NoSuchMethodException e4) {
                    } catch (IllegalAccessException e5) {
                    } catch (InvocationTargetException e6) {
                    }
                }
                if (Boolean.TRUE.equals(shouldAllowNavigation)) {
                    Log.d(InAppBrowser.LOG_TAG, "loading in webview");
                    InAppBrowser.this.webView.loadUrl(this.val$url);
                } else if (this.val$url.startsWith("tel:")) {
                    try {
                        Log.d(InAppBrowser.LOG_TAG, "loading in dialer");
                        Intent intent = new Intent("android.intent.action.DIAL");
                        intent.setData(Uri.parse(this.val$url));
                        InAppBrowser.this.cordova.getActivity().startActivity(intent);
                    } catch (ActivityNotFoundException e7) {
                        LOG.m12e(InAppBrowser.LOG_TAG, "Error dialing " + this.val$url + ": " + e7.toString());
                    }
                } else {
                    Log.d(InAppBrowser.LOG_TAG, "loading in InAppBrowser");
                    result = InAppBrowser.this.showWebPage(this.val$url, this.val$features);
                }
            } else if (InAppBrowser.SYSTEM.equals(this.val$target)) {
                Log.d(InAppBrowser.LOG_TAG, "in system");
                result = InAppBrowser.this.openExternal(this.val$url);
            } else {
                Log.d(InAppBrowser.LOG_TAG, "in blank");
                result = InAppBrowser.this.showWebPage(this.val$url, this.val$features);
            }
            PluginResult pluginResult = new PluginResult(Status.OK, result);
            pluginResult.setKeepCallback(true);
            this.val$callbackContext.sendPluginResult(pluginResult);
        }
    }

    /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.2 */
    class C01662 implements Runnable {
        C01662() {
        }

        public void run() {
            InAppBrowser.this.dialog.show();
        }
    }

    /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.3 */
    class C01673 implements Runnable {
        final /* synthetic */ String val$finalScriptToInject;

        C01673(String str) {
            this.val$finalScriptToInject = str;
        }

        @SuppressLint({"NewApi"})
        public void run() {
            if (VERSION.SDK_INT < 19) {
                InAppBrowser.this.inAppWebView.loadUrl("javascript:" + this.val$finalScriptToInject);
            } else {
                InAppBrowser.this.inAppWebView.evaluateJavascript(this.val$finalScriptToInject, null);
            }
        }
    }

    /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.4 */
    class C01694 implements Runnable {
        final /* synthetic */ WebView val$childView;

        /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.4.1 */
        class C01681 extends WebViewClient {
            C01681() {
            }

            public void onPageFinished(WebView view, String url) {
                if (InAppBrowser.this.dialog != null) {
                    InAppBrowser.this.dialog.dismiss();
                }
            }
        }

        C01694(WebView webView) {
            this.val$childView = webView;
        }

        public void run() {
            this.val$childView.setWebViewClient(new C01681());
            this.val$childView.loadUrl("about:blank");
        }
    }

    /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.5 */
    class C01745 implements Runnable {
        final /* synthetic */ CordovaWebView val$thatWebView;
        final /* synthetic */ String val$url;

        /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.5.1 */
        class C01701 implements OnClickListener {
            C01701() {
            }

            public void onClick(View v) {
                InAppBrowser.this.goBack();
            }
        }

        /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.5.2 */
        class C01712 implements OnClickListener {
            C01712() {
            }

            public void onClick(View v) {
                InAppBrowser.this.goForward();
            }
        }

        /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.5.3 */
        class C01723 implements OnKeyListener {
            C01723() {
            }

            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != 0 || keyCode != 66) {
                    return false;
                }
                InAppBrowser.this.navigate(InAppBrowser.this.edittext.getText().toString());
                return true;
            }
        }

        /* renamed from: org.apache.cordova.inappbrowser.InAppBrowser.5.4 */
        class C01734 implements OnClickListener {
            C01734() {
            }

            public void onClick(View v) {
                InAppBrowser.this.closeDialog();
            }
        }

        C01745(String str, CordovaWebView cordovaWebView) {
            this.val$url = str;
            this.val$thatWebView = cordovaWebView;
        }

        private int dpToPixels(int dipValue) {
            return (int) TypedValue.applyDimension(1, (float) dipValue, InAppBrowser.this.cordova.getActivity().getResources().getDisplayMetrics());
        }

        @SuppressLint({"NewApi"})
        public void run() {
            boolean enableDatabase;
            InAppBrowser.this.dialog = new InAppBrowserDialog(InAppBrowser.this.cordova.getActivity(), 16973830);
            InAppBrowser.this.dialog.getWindow().getAttributes().windowAnimations = 16973826;
            InAppBrowser.this.dialog.requestWindowFeature(1);
            InAppBrowser.this.dialog.setCancelable(true);
            InAppBrowser.this.dialog.setInAppBroswer(InAppBrowser.this.getInAppBrowser());
            View linearLayout = new LinearLayout(InAppBrowser.this.cordova.getActivity());
            linearLayout.setOrientation(1);
            View toolbar = new RelativeLayout(InAppBrowser.this.cordova.getActivity());
            toolbar.setBackgroundColor(-3355444);
            toolbar.setLayoutParams(new LayoutParams(-1, dpToPixels(44)));
            toolbar.setPadding(dpToPixels(2), dpToPixels(2), dpToPixels(2), dpToPixels(2));
            toolbar.setHorizontalGravity(3);
            toolbar.setVerticalGravity(48);
            RelativeLayout actionButtonContainer = new RelativeLayout(InAppBrowser.this.cordova.getActivity());
            actionButtonContainer.setLayoutParams(new LayoutParams(-2, -2));
            actionButtonContainer.setHorizontalGravity(3);
            actionButtonContainer.setVerticalGravity(16);
            actionButtonContainer.setId(1);
            Button back = new Button(InAppBrowser.this.cordova.getActivity());
            LayoutParams backLayoutParams = new LayoutParams(-2, -1);
            backLayoutParams.addRule(5);
            back.setLayoutParams(backLayoutParams);
            back.setContentDescription("Back Button");
            back.setId(2);
            Resources activityRes = InAppBrowser.this.cordova.getActivity().getResources();
            Drawable backIcon = activityRes.getDrawable(activityRes.getIdentifier("ic_action_previous_item", "drawable", InAppBrowser.this.cordova.getActivity().getPackageName()));
            if (VERSION.SDK_INT < 16) {
                back.setBackgroundDrawable(backIcon);
            } else {
                back.setBackground(backIcon);
            }
            back.setOnClickListener(new C01701());
            linearLayout = new Button(InAppBrowser.this.cordova.getActivity());
            LayoutParams layoutParams = new LayoutParams(-2, -1);
            layoutParams.addRule(1, 2);
            linearLayout.setLayoutParams(layoutParams);
            linearLayout.setContentDescription("Forward Button");
            linearLayout.setId(3);
            Drawable fwdIcon = activityRes.getDrawable(activityRes.getIdentifier("ic_action_next_item", "drawable", InAppBrowser.this.cordova.getActivity().getPackageName()));
            if (VERSION.SDK_INT < 16) {
                linearLayout.setBackgroundDrawable(fwdIcon);
            } else {
                linearLayout.setBackground(fwdIcon);
            }
            linearLayout.setOnClickListener(new C01712());
            InAppBrowser.this.edittext = new EditText(InAppBrowser.this.cordova.getActivity());
            ViewGroup.LayoutParams layoutParams2 = new LayoutParams(-1, -1);
            layoutParams2.addRule(1, 1);
            layoutParams2.addRule(0, 5);
            InAppBrowser.this.edittext.setLayoutParams(layoutParams2);
            InAppBrowser.this.edittext.setId(4);
            InAppBrowser.this.edittext.setSingleLine(true);
            InAppBrowser inAppBrowser = InAppBrowser.this;
            r0.edittext.setText(this.val$url);
            InAppBrowser.this.edittext.setInputType(16);
            InAppBrowser.this.edittext.setImeOptions(2);
            InAppBrowser.this.edittext.setInputType(0);
            InAppBrowser.this.edittext.setOnKeyListener(new C01723());
            Button close = new Button(InAppBrowser.this.cordova.getActivity());
            LayoutParams closeLayoutParams = new LayoutParams(-2, -1);
            closeLayoutParams.addRule(11);
            close.setLayoutParams(closeLayoutParams);
            linearLayout.setContentDescription("Close Button");
            close.setId(5);
            Drawable closeIcon = activityRes.getDrawable(activityRes.getIdentifier("ic_action_remove", "drawable", InAppBrowser.this.cordova.getActivity().getPackageName()));
            if (VERSION.SDK_INT < 16) {
                close.setBackgroundDrawable(closeIcon);
            } else {
                close.setBackground(closeIcon);
            }
            close.setOnClickListener(new C01734());
            InAppBrowser.this.inAppWebView = new WebView(InAppBrowser.this.cordova.getActivity());
            InAppBrowser.this.inAppWebView.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
            InAppBrowser.this.inAppWebView.setWebChromeClient(new InAppChromeClient(this.val$thatWebView));
            WebViewClient client = new InAppBrowserClient(this.val$thatWebView, InAppBrowser.this.edittext);
            InAppBrowser.this.inAppWebView.setWebViewClient(client);
            WebSettings settings = InAppBrowser.this.inAppWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
            settings.setBuiltInZoomControls(InAppBrowser.this.getShowZoomControls());
            settings.setPluginState(PluginState.ON);
            Bundle appSettings = InAppBrowser.this.cordova.getActivity().getIntent().getExtras();
            if (appSettings == null) {
                enableDatabase = true;
            } else {
                enableDatabase = appSettings.getBoolean("InAppBrowserStorageEnabled", true);
            }
            if (enableDatabase) {
                WebSettings webSettings = settings;
                webSettings.setDatabasePath(InAppBrowser.this.cordova.getActivity().getApplicationContext().getDir("inAppBrowserDB", 0).getPath());
                settings.setDatabaseEnabled(true);
            }
            settings.setDomStorageEnabled(true);
            if (InAppBrowser.this.clearAllCache) {
                CookieManager.getInstance().removeAllCookie();
            } else {
                if (InAppBrowser.this.clearSessionCache) {
                    CookieManager.getInstance().removeSessionCookie();
                }
            }
            inAppBrowser = InAppBrowser.this;
            r0.inAppWebView.loadUrl(this.val$url);
            InAppBrowser.this.inAppWebView.setId(6);
            InAppBrowser.this.inAppWebView.getSettings().setLoadWithOverviewMode(true);
            InAppBrowser.this.inAppWebView.getSettings().setUseWideViewPort(true);
            InAppBrowser.this.inAppWebView.requestFocus();
            InAppBrowser.this.inAppWebView.requestFocusFromTouch();
            actionButtonContainer.addView(back);
            actionButtonContainer.addView(linearLayout);
            toolbar.addView(actionButtonContainer);
            toolbar.addView(InAppBrowser.this.edittext);
            toolbar.addView(close);
            if (InAppBrowser.this.getShowLocationBar()) {
                linearLayout.addView(toolbar);
            }
            linearLayout.addView(InAppBrowser.this.inAppWebView);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(InAppBrowser.this.dialog.getWindow().getAttributes());
            lp.width = -1;
            lp.height = -1;
            InAppBrowser.this.dialog.setContentView(linearLayout);
            InAppBrowser.this.dialog.show();
            InAppBrowser.this.dialog.getWindow().setAttributes(lp);
            if (InAppBrowser.this.openWindowHidden) {
                InAppBrowser.this.dialog.hide();
            }
        }
    }

    public class InAppBrowserClient extends WebViewClient {
        EditText edittext;
        CordovaWebView webView;

        public InAppBrowserClient(CordovaWebView webView, EditText mEditText) {
            this.webView = webView;
            this.edittext = mEditText;
        }

        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            String newloc = "";
            if (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:")) {
                newloc = url;
            } else if (url.startsWith("tel:")) {
                try {
                    intent = new Intent("android.intent.action.DIAL");
                    intent.setData(Uri.parse(url));
                    InAppBrowser.this.cordova.getActivity().startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    LOG.m12e(InAppBrowser.LOG_TAG, "Error dialing " + url + ": " + e.toString());
                }
            } else if (url.startsWith("geo:") || url.startsWith("mailto:") || url.startsWith("market:")) {
                try {
                    intent = new Intent("android.intent.action.VIEW");
                    intent.setData(Uri.parse(url));
                    InAppBrowser.this.cordova.getActivity().startActivity(intent);
                } catch (ActivityNotFoundException e2) {
                    LOG.m12e(InAppBrowser.LOG_TAG, "Error with " + url + ": " + e2.toString());
                }
            } else if (url.startsWith("sms:")) {
                try {
                    String address;
                    intent = new Intent("android.intent.action.VIEW");
                    int parmIndex = url.indexOf(63);
                    if (parmIndex == -1) {
                        address = url.substring(4);
                    } else {
                        address = url.substring(4, parmIndex);
                        String query = Uri.parse(url).getQuery();
                        if (query != null && query.startsWith("body=")) {
                            intent.putExtra("sms_body", query.substring(5));
                        }
                    }
                    intent.setData(Uri.parse("sms:" + address));
                    intent.putExtra("address", address);
                    intent.setType("vnd.android-dir/mms-sms");
                    InAppBrowser.this.cordova.getActivity().startActivity(intent);
                } catch (ActivityNotFoundException e22) {
                    LOG.m12e(InAppBrowser.LOG_TAG, "Error sending sms " + url + ":" + e22.toString());
                }
            } else {
                newloc = "http://" + url;
            }
            if (!newloc.equals(this.edittext.getText().toString())) {
                this.edittext.setText(newloc);
            }
            try {
                JSONObject obj = new JSONObject();
                obj.put(Globalization.TYPE, InAppBrowser.LOAD_START_EVENT);
                obj.put("url", newloc);
                InAppBrowser.this.sendUpdate(obj, true);
            } catch (JSONException e3) {
                Log.d(InAppBrowser.LOG_TAG, "Should never happen");
            }
        }

        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            try {
                JSONObject obj = new JSONObject();
                obj.put(Globalization.TYPE, InAppBrowser.LOAD_STOP_EVENT);
                obj.put("url", url);
                InAppBrowser.this.sendUpdate(obj, true);
            } catch (JSONException e) {
                Log.d(InAppBrowser.LOG_TAG, "Should never happen");
            }
        }

        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            try {
                JSONObject obj = new JSONObject();
                obj.put(Globalization.TYPE, InAppBrowser.LOAD_ERROR_EVENT);
                obj.put("url", failingUrl);
                obj.put("code", errorCode);
                obj.put("message", description);
                InAppBrowser.this.sendUpdate(obj, true, Status.ERROR);
            } catch (JSONException e) {
                Log.d(InAppBrowser.LOG_TAG, "Should never happen");
            }
        }
    }

    public InAppBrowser() {
        this.showLocationBar = true;
        this.showZoomControls = true;
        this.openWindowHidden = false;
        this.clearAllCache = false;
        this.clearSessionCache = false;
        this.hadwareBackButton = true;
    }

    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("open")) {
            this.callbackContext = callbackContext;
            String url = args.getString(0);
            String t = args.optString(1);
            if (t == null || t.equals("") || t.equals(NULL)) {
                t = SELF;
            }
            String target = t;
            HashMap<String, Boolean> features = parseFeature(args.optString(2));
            Log.d(LOG_TAG, "target = " + target);
            this.cordova.getActivity().runOnUiThread(new C01651(target, url, features, callbackContext));
        } else if (action.equals("close")) {
            closeDialog();
        } else if (action.equals("injectScriptCode")) {
            jsWrapper = null;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("prompt(JSON.stringify([eval(%%s)]), 'gap-iab://%s')", new Object[]{callbackContext.getCallbackId()});
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        } else if (action.equals("injectScriptFile")) {
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('script'); c.src = %%s; c.onload = function() { prompt('', 'gap-iab://%s'); }; d.body.appendChild(c); })(document)", new Object[]{callbackContext.getCallbackId()});
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('script'); c.src = %s; d.body.appendChild(c); })(document)";
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        } else if (action.equals("injectStyleCode")) {
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('style'); c.innerHTML = %%s; d.body.appendChild(c); prompt('', 'gap-iab://%s');})(document)", new Object[]{callbackContext.getCallbackId()});
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('style'); c.innerHTML = %s; d.body.appendChild(c); })(document)";
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        } else if (action.equals("injectStyleFile")) {
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %%s; d.head.appendChild(c); prompt('', 'gap-iab://%s');})(document)", new Object[]{callbackContext.getCallbackId()});
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %s; d.head.appendChild(c); })(document)";
            }
            injectDeferredObject(args.getString(0), jsWrapper);
        } else if (!action.equals("show")) {
            return false;
        } else {
            this.cordova.getActivity().runOnUiThread(new C01662());
            PluginResult pluginResult = new PluginResult(Status.OK);
            pluginResult.setKeepCallback(true);
            this.callbackContext.sendPluginResult(pluginResult);
        }
        return true;
    }

    public void onReset() {
        closeDialog();
    }

    public void onDestroy() {
        closeDialog();
    }

    private void injectDeferredObject(String source, String jsWrapper) {
        String scriptToInject;
        if (jsWrapper != null) {
            JSONArray jsonEsc = new JSONArray();
            jsonEsc.put(source);
            String jsonRepr = jsonEsc.toString();
            String jsonSourceString = jsonRepr.substring(1, jsonRepr.length() - 1);
            scriptToInject = String.format(jsWrapper, new Object[]{jsonSourceString});
        } else {
            scriptToInject = source;
        }
        this.cordova.getActivity().runOnUiThread(new C01673(scriptToInject));
    }

    private HashMap<String, Boolean> parseFeature(String optString) {
        if (optString.equals(NULL)) {
            return null;
        }
        HashMap<String, Boolean> map = new HashMap();
        StringTokenizer features = new StringTokenizer(optString, ",");
        while (features.hasMoreElements()) {
            StringTokenizer option = new StringTokenizer(features.nextToken(), "=");
            if (option.hasMoreElements()) {
                map.put(option.nextToken(), option.nextToken().equals("no") ? Boolean.FALSE : Boolean.TRUE);
            }
        }
        return map;
    }

    public String openExternal(String url) {
        ActivityNotFoundException e;
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            try {
                Uri uri = Uri.parse(url);
                if ("file".equals(uri.getScheme())) {
                    intent.setDataAndType(uri, this.webView.getResourceApi().getMimeType(uri));
                } else {
                    intent.setData(uri);
                }
                intent.putExtra("com.android.browser.application_id", this.cordova.getActivity().getPackageName());
                this.cordova.getActivity().startActivity(intent);
                return "";
            } catch (ActivityNotFoundException e2) {
                e = e2;
                Intent intent2 = intent;
                Log.d(LOG_TAG, "InAppBrowser: Error loading url " + url + ":" + e.toString());
                return e.toString();
            }
        } catch (ActivityNotFoundException e3) {
            e = e3;
            Log.d(LOG_TAG, "InAppBrowser: Error loading url " + url + ":" + e.toString());
            return e.toString();
        }
    }

    public void closeDialog() {
        WebView childView = this.inAppWebView;
        if (childView != null) {
            this.cordova.getActivity().runOnUiThread(new C01694(childView));
            try {
                JSONObject obj = new JSONObject();
                obj.put(Globalization.TYPE, EXIT_EVENT);
                sendUpdate(obj, false);
            } catch (JSONException e) {
                Log.d(LOG_TAG, "Should never happen");
            }
        }
    }

    public void goBack() {
        if (this.inAppWebView.canGoBack()) {
            this.inAppWebView.goBack();
        }
    }

    public boolean canGoBack() {
        return this.inAppWebView.canGoBack();
    }

    public boolean hardwareBack() {
        return this.hadwareBackButton;
    }

    private void goForward() {
        if (this.inAppWebView.canGoForward()) {
            this.inAppWebView.goForward();
        }
    }

    private void navigate(String url) {
        ((InputMethodManager) this.cordova.getActivity().getSystemService("input_method")).hideSoftInputFromWindow(this.edittext.getWindowToken(), 0);
        if (url.startsWith("http") || url.startsWith("file:")) {
            this.inAppWebView.loadUrl(url);
        } else {
            this.inAppWebView.loadUrl("http://" + url);
        }
        this.inAppWebView.requestFocus();
    }

    private boolean getShowLocationBar() {
        return this.showLocationBar;
    }

    private boolean getShowZoomControls() {
        return this.showZoomControls;
    }

    private InAppBrowser getInAppBrowser() {
        return this;
    }

    public String showWebPage(String url, HashMap<String, Boolean> features) {
        this.showLocationBar = true;
        this.showZoomControls = true;
        this.openWindowHidden = false;
        if (features != null) {
            Boolean show = (Boolean) features.get(LOCATION);
            if (show != null) {
                this.showLocationBar = show.booleanValue();
            }
            Boolean zoom = (Boolean) features.get(ZOOM);
            if (zoom != null) {
                this.showZoomControls = zoom.booleanValue();
            }
            Boolean hidden = (Boolean) features.get(HIDDEN);
            if (hidden != null) {
                this.openWindowHidden = hidden.booleanValue();
            }
            Boolean hardwareBack = (Boolean) features.get(HARDWARE_BACK_BUTTON);
            if (hardwareBack != null) {
                this.hadwareBackButton = hardwareBack.booleanValue();
            }
            Boolean cache = (Boolean) features.get(CLEAR_ALL_CACHE);
            if (cache != null) {
                this.clearAllCache = cache.booleanValue();
            } else {
                cache = (Boolean) features.get(CLEAR_SESSION_CACHE);
                if (cache != null) {
                    this.clearSessionCache = cache.booleanValue();
                }
            }
        }
        this.cordova.getActivity().runOnUiThread(new C01745(url, this.webView));
        return "";
    }

    private void sendUpdate(JSONObject obj, boolean keepCallback) {
        sendUpdate(obj, keepCallback, Status.OK);
    }

    private void sendUpdate(JSONObject obj, boolean keepCallback, Status status) {
        if (this.callbackContext != null) {
            PluginResult result = new PluginResult(status, obj);
            result.setKeepCallback(keepCallback);
            this.callbackContext.sendPluginResult(result);
            if (!keepCallback) {
                this.callbackContext = null;
            }
        }
    }
}
