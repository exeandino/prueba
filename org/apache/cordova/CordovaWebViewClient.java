package org.apache.cordova;

import android.annotation.TargetApi;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.support.v4.media.TransportMediator;
import android.webkit.ClientCertRequest;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.plugin.gcm.PushPlugin;
import java.util.Hashtable;
import org.json.JSONException;
import org.json.JSONObject;

public class CordovaWebViewClient extends WebViewClient {
    private static final String TAG = "CordovaWebViewClient";
    CordovaWebView appView;
    private Hashtable<String, AuthenticationToken> authenticationTokens;
    CordovaInterface cordova;
    private boolean doClearHistory;
    CordovaUriHelper helper;
    boolean isCurrentlyLoading;

    /* renamed from: org.apache.cordova.CordovaWebViewClient.1 */
    class C01451 implements Runnable {

        /* renamed from: org.apache.cordova.CordovaWebViewClient.1.1 */
        class C01441 implements Runnable {
            C01441() {
            }

            public void run() {
                CordovaWebViewClient.this.appView.postMessage("spinner", "stop");
            }
        }

        C01451() {
        }

        public void run() {
            try {
                Thread.sleep(2000);
                CordovaWebViewClient.this.cordova.getActivity().runOnUiThread(new C01441());
            } catch (InterruptedException e) {
            }
        }
    }

    @Deprecated
    public CordovaWebViewClient(CordovaInterface cordova) {
        this.doClearHistory = false;
        this.authenticationTokens = new Hashtable();
        this.cordova = cordova;
    }

    public CordovaWebViewClient(CordovaInterface cordova, CordovaWebView view) {
        this.doClearHistory = false;
        this.authenticationTokens = new Hashtable();
        this.cordova = cordova;
        this.appView = view;
        this.helper = new CordovaUriHelper(cordova, view);
    }

    @Deprecated
    public void setWebView(CordovaWebView view) {
        this.appView = view;
        this.helper = new CordovaUriHelper(this.cordova, view);
    }

    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return this.helper.shouldOverrideUrlLoading(view, url);
    }

    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
        AuthenticationToken token = getAuthenticationToken(host, realm);
        if (token != null) {
            handler.proceed(token.getUserName(), token.getPassword());
            return;
        }
        PluginManager pluginManager = this.appView.pluginManager;
        if (pluginManager == null || !pluginManager.onReceivedHttpAuthRequest(this.appView, new CordovaHttpAuthHandler(handler), host, realm)) {
            super.onReceivedHttpAuthRequest(view, handler, host, realm);
            return;
        }
        CordovaWebView cordovaWebView = this.appView;
        cordovaWebView.loadUrlTimeout++;
    }

    @TargetApi(21)
    public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
        PluginManager pluginManager = this.appView.pluginManager;
        if (pluginManager == null || !pluginManager.onReceivedClientCertRequest(this.appView, new CordovaClientCertRequest(request))) {
            super.onReceivedClientCertRequest(view, request);
            return;
        }
        CordovaWebView cordovaWebView = this.appView;
        cordovaWebView.loadUrlTimeout++;
    }

    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        this.isCurrentlyLoading = true;
        LOG.m9d(TAG, "onPageStarted(" + url + ")");
        this.appView.bridge.reset(url);
        this.appView.postMessage("onPageStarted", url);
        if (this.appView.pluginManager != null) {
            this.appView.pluginManager.onReset();
        }
    }

    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (this.isCurrentlyLoading || url.startsWith("about:")) {
            this.isCurrentlyLoading = false;
            LOG.m9d(TAG, "onPageFinished(" + url + ")");
            if (this.doClearHistory) {
                view.clearHistory();
                this.doClearHistory = false;
            }
            CordovaWebView cordovaWebView = this.appView;
            cordovaWebView.loadUrlTimeout++;
            this.appView.postMessage("onPageFinished", url);
            if (this.appView.getVisibility() == 4) {
                new Thread(new C01451()).start();
            }
            if (url.equals("about:blank")) {
                this.appView.postMessage(PushPlugin.EXIT, null);
            }
        }
    }

    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        if (this.isCurrentlyLoading) {
            LOG.m11d(TAG, "CordovaWebViewClient.onReceivedError: Error code=%s Description=%s URL=%s", Integer.valueOf(errorCode), description, failingUrl);
            CordovaWebView cordovaWebView = this.appView;
            cordovaWebView.loadUrlTimeout++;
            if (errorCode == -10) {
                if (view.canGoBack()) {
                    view.goBack();
                    return;
                }
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
            JSONObject data = new JSONObject();
            try {
                data.put("errorCode", errorCode);
                data.put("description", description);
                data.put("url", failingUrl);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            this.appView.postMessage("onReceivedError", data);
        }
    }

    @TargetApi(8)
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        try {
            if ((this.cordova.getActivity().getPackageManager().getApplicationInfo(this.cordova.getActivity().getPackageName(), TransportMediator.FLAG_KEY_MEDIA_NEXT).flags & 2) != 0) {
                handler.proceed();
            } else {
                super.onReceivedSslError(view, handler, error);
            }
        } catch (NameNotFoundException e) {
            super.onReceivedSslError(view, handler, error);
        }
    }

    public void setAuthenticationToken(AuthenticationToken authenticationToken, String host, String realm) {
        if (host == null) {
            host = "";
        }
        if (realm == null) {
            realm = "";
        }
        this.authenticationTokens.put(host.concat(realm), authenticationToken);
    }

    public AuthenticationToken removeAuthenticationToken(String host, String realm) {
        return (AuthenticationToken) this.authenticationTokens.remove(host.concat(realm));
    }

    public AuthenticationToken getAuthenticationToken(String host, String realm) {
        AuthenticationToken token = (AuthenticationToken) this.authenticationTokens.get(host.concat(realm));
        if (token != null) {
            return token;
        }
        token = (AuthenticationToken) this.authenticationTokens.get(host);
        if (token == null) {
            token = (AuthenticationToken) this.authenticationTokens.get(realm);
        }
        if (token == null) {
            return (AuthenticationToken) this.authenticationTokens.get("");
        }
        return token;
    }

    public void clearAuthenticationTokens() {
        this.authenticationTokens.clear();
    }
}
