package org.apache.cordova;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.webkit.WebView;

class CordovaUriHelper {
    private static final String TAG = "CordovaUriHelper";
    private CordovaWebView appView;
    private CordovaInterface cordova;

    CordovaUriHelper(CordovaInterface cdv, CordovaWebView webView) {
        this.appView = webView;
        this.cordova = cdv;
    }

    @TargetApi(15)
    boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (this.appView.pluginManager.onOverrideUrlLoading(url)) {
            return true;
        }
        if ((url.startsWith("file://") | url.startsWith("data:")) != 0) {
            return url.contains("app_webview");
        }
        if (this.appView.getWhitelist().isUrlWhiteListed(url)) {
            return false;
        }
        if (!this.appView.getExternalWhitelist().isUrlWhiteListed(url)) {
            return true;
        }
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse(url));
            intent.addCategory("android.intent.category.BROWSABLE");
            intent.setComponent(null);
            if (VERSION.SDK_INT >= 15) {
                intent.setSelector(null);
            }
            this.cordova.getActivity().startActivity(intent);
            return true;
        } catch (Throwable e) {
            LOG.m13e(TAG, "Error loading url " + url, e);
            return true;
        }
    }
}
