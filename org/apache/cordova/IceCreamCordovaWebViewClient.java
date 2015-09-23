package org.apache.cordova;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build.VERSION;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.widget.ViewDragHelper;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import java.io.FileNotFoundException;
import org.apache.cordova.CordovaResourceApi.OpenForReadResult;

@TargetApi(11)
public class IceCreamCordovaWebViewClient extends CordovaWebViewClient {
    private static final String TAG = "IceCreamCordovaWebViewClient";
    private CordovaUriHelper helper;

    public IceCreamCordovaWebViewClient(CordovaInterface cordova) {
        super(cordova);
    }

    public IceCreamCordovaWebViewClient(CordovaInterface cordova, CordovaWebView view) {
        super(cordova, view);
    }

    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        try {
            if (isUrlHarmful(url)) {
                LOG.m21w(TAG, "URL blocked by whitelist: " + url);
                return new WebResourceResponse("text/plain", "UTF-8", null);
            }
            CordovaResourceApi resourceApi = this.appView.getResourceApi();
            Uri origUri = Uri.parse(url);
            Uri remappedUri = resourceApi.remapUri(origUri);
            if (origUri.equals(remappedUri) && !needsSpecialsInAssetUrlFix(origUri) && !needsKitKatContentUrlFix(origUri)) {
                return null;
            }
            OpenForReadResult result = resourceApi.openForRead(remappedUri, true);
            return new WebResourceResponse(result.mimeType, "UTF-8", result.inputStream);
        } catch (Throwable e) {
            if (!(e instanceof FileNotFoundException)) {
                LOG.m13e(TAG, "Error occurred while loading a file (returning a 404).", e);
            }
            return new WebResourceResponse("text/plain", "UTF-8", null);
        }
    }

    private boolean isUrlHarmful(String url) {
        return ((url.startsWith("http:") || url.startsWith("https:")) && !this.appView.getWhitelist().isUrlWhiteListed(url)) || url.contains("app_webview");
    }

    private static boolean needsKitKatContentUrlFix(Uri uri) {
        return VERSION.SDK_INT >= 19 && "content".equals(uri.getScheme());
    }

    private static boolean needsSpecialsInAssetUrlFix(Uri uri) {
        if (CordovaResourceApi.getUriType(uri) != 1) {
            return false;
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            return true;
        }
        if (!uri.toString().contains("%")) {
            return false;
        }
        switch (VERSION.SDK_INT) {
            case MotionEventCompat.AXIS_RZ /*14*/:
            case ViewDragHelper.EDGE_ALL /*15*/:
                return true;
            default:
                return false;
        }
    }
}
