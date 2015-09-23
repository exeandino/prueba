package org.apache.cordova;

import android.webkit.JavascriptInterface;
import org.json.JSONException;

class ExposedJsApi {
    private CordovaBridge bridge;

    public ExposedJsApi(CordovaBridge bridge) {
        this.bridge = bridge;
    }

    @JavascriptInterface
    public String exec(int bridgeSecret, String service, String action, String callbackId, String arguments) throws JSONException, IllegalAccessException {
        return this.bridge.jsExec(bridgeSecret, service, action, callbackId, arguments);
    }

    @JavascriptInterface
    public void setNativeToJsBridgeMode(int bridgeSecret, int value) throws IllegalAccessException {
        this.bridge.jsSetNativeToJsBridgeMode(bridgeSecret, value);
    }

    @JavascriptInterface
    public String retrieveJsMessages(int bridgeSecret, boolean fromOnlineEvent) throws IllegalAccessException {
        return this.bridge.jsRetrieveJsMessages(bridgeSecret, fromOnlineEvent);
    }
}
