package org.apache.cordova;

import android.content.Intent;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;

public class CordovaPlugin {
    static final /* synthetic */ boolean $assertionsDisabled;
    public CordovaInterface cordova;
    @Deprecated
    public String id;
    protected CordovaPreferences preferences;
    public CordovaWebView webView;

    static {
        $assertionsDisabled = !CordovaPlugin.class.desiredAssertionStatus();
    }

    public final void privateInitialize(CordovaInterface cordova, CordovaWebView webView, CordovaPreferences preferences) {
        if ($assertionsDisabled || this.cordova == null) {
            this.cordova = cordova;
            this.webView = webView;
            this.preferences = preferences;
            initialize(cordova, webView);
            pluginInitialize();
            return;
        }
        throw new AssertionError();
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    }

    protected void pluginInitialize() {
    }

    public boolean execute(String action, String rawArgs, CallbackContext callbackContext) throws JSONException {
        return execute(action, new JSONArray(rawArgs), callbackContext);
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        return execute(action, new CordovaArgs(args), callbackContext);
    }

    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        return false;
    }

    public void onPause(boolean multitasking) {
    }

    public void onResume(boolean multitasking) {
    }

    public void onNewIntent(Intent intent) {
    }

    public void onDestroy() {
    }

    public Object onMessage(String id, Object data) {
        return null;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    }

    public boolean onOverrideUrlLoading(String url) {
        return false;
    }

    public Uri remapUri(Uri uri) {
        return null;
    }

    public void onReset() {
    }

    public boolean onReceivedHttpAuthRequest(CordovaWebView view, ICordovaHttpAuthHandler handler, String host, String realm) {
        return false;
    }

    public boolean onReceivedClientCertRequest(CordovaWebView view, ICordovaClientCertRequest request) {
        return false;
    }
}
