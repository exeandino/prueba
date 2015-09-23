package org.apache.cordova.networkinformation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.apache.cordova.globalization.Globalization;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class NetworkManager extends CordovaPlugin {
    public static final String CDMA = "cdma";
    public static final String EDGE = "edge";
    public static final String EHRPD = "ehrpd";
    public static final String GPRS = "gprs";
    public static final String GSM = "gsm";
    public static final String HSDPA = "hsdpa";
    public static final String HSPA = "hspa";
    public static final String HSPA_PLUS = "hspa+";
    public static final String HSUPA = "hsupa";
    private static final String LOG_TAG = "NetworkManager";
    public static final String LTE = "lte";
    public static final String MOBILE = "mobile";
    public static int NOT_REACHABLE = 0;
    public static final String ONEXRTT = "1xrtt";
    public static int REACHABLE_VIA_CARRIER_DATA_NETWORK = 0;
    public static int REACHABLE_VIA_WIFI_NETWORK = 0;
    public static final String TYPE_2G = "2g";
    public static final String TYPE_3G = "3g";
    public static final String TYPE_4G = "4g";
    public static final String TYPE_ETHERNET = "ethernet";
    public static final String TYPE_NONE = "none";
    public static final String TYPE_UNKNOWN = "unknown";
    public static final String TYPE_WIFI = "wifi";
    public static final String UMB = "umb";
    public static final String UMTS = "umts";
    public static final String WIFI = "wifi";
    public static final String WIMAX = "wimax";
    private CallbackContext connectionCallbackContext;
    private JSONObject lastInfo;
    BroadcastReceiver receiver;
    private boolean registered;
    ConnectivityManager sockMan;

    /* renamed from: org.apache.cordova.networkinformation.NetworkManager.1 */
    class C01781 extends BroadcastReceiver {
        C01781() {
        }

        public void onReceive(Context context, Intent intent) {
            if (NetworkManager.this.webView != null) {
                NetworkManager.this.updateConnectionInfo(NetworkManager.this.sockMan.getActiveNetworkInfo());
            }
        }
    }

    static {
        NOT_REACHABLE = 0;
        REACHABLE_VIA_CARRIER_DATA_NETWORK = 1;
        REACHABLE_VIA_WIFI_NETWORK = 2;
    }

    public NetworkManager() {
        this.registered = false;
        this.lastInfo = null;
        this.receiver = null;
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.sockMan = (ConnectivityManager) cordova.getActivity().getSystemService("connectivity");
        this.connectionCallbackContext = null;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        if (this.receiver == null) {
            this.receiver = new C01781();
            cordova.getActivity().registerReceiver(this.receiver, intentFilter);
            this.registered = true;
        }
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (!action.equals("getConnectionInfo")) {
            return false;
        }
        this.connectionCallbackContext = callbackContext;
        String connectionType = "";
        try {
            connectionType = getConnectionInfo(this.sockMan.getActiveNetworkInfo()).get(Globalization.TYPE).toString();
        } catch (JSONException e) {
        }
        PluginResult pluginResult = new PluginResult(Status.OK, connectionType);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
        return true;
    }

    public void onDestroy() {
        if (this.receiver != null && this.registered) {
            try {
                this.cordova.getActivity().unregisterReceiver(this.receiver);
                this.registered = false;
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error unregistering network receiver: " + e.getMessage(), e);
            }
        }
    }

    private void updateConnectionInfo(NetworkInfo info) {
        JSONObject thisInfo = getConnectionInfo(info);
        if (!thisInfo.equals(this.lastInfo)) {
            String connectionType = "";
            try {
                connectionType = thisInfo.get(Globalization.TYPE).toString();
            } catch (JSONException e) {
            }
            sendUpdate(connectionType);
            this.lastInfo = thisInfo;
        }
    }

    private JSONObject getConnectionInfo(NetworkInfo info) {
        String type = TYPE_NONE;
        String extraInfo = "";
        if (info != null) {
            if (info.isConnected()) {
                type = getType(info);
            } else {
                type = TYPE_NONE;
            }
            extraInfo = info.getExtraInfo();
        }
        Log.d("CordovaNetworkManager", "Connection Type: " + type);
        Log.d("CordovaNetworkManager", "Connection Extra Info: " + extraInfo);
        JSONObject connectionInfo = new JSONObject();
        try {
            connectionInfo.put(Globalization.TYPE, type);
            connectionInfo.put("extraInfo", extraInfo);
        } catch (JSONException e) {
        }
        return connectionInfo;
    }

    private void sendUpdate(String type) {
        if (this.connectionCallbackContext != null) {
            PluginResult result = new PluginResult(Status.OK, type);
            result.setKeepCallback(true);
            this.connectionCallbackContext.sendPluginResult(result);
        }
        this.webView.postMessage("networkconnection", type);
    }

    private String getType(NetworkInfo info) {
        if (info == null) {
            return TYPE_NONE;
        }
        String type = info.getTypeName();
        if (type.toLowerCase().equals(WIFI)) {
            return WIFI;
        }
        if (type.toLowerCase().equals(MOBILE)) {
            type = info.getSubtypeName();
            if (type.toLowerCase().equals(GSM) || type.toLowerCase().equals(GPRS) || type.toLowerCase().equals(EDGE)) {
                return TYPE_2G;
            }
            if (type.toLowerCase().startsWith(CDMA) || type.toLowerCase().equals(UMTS) || type.toLowerCase().equals(ONEXRTT) || type.toLowerCase().equals(EHRPD) || type.toLowerCase().equals(HSUPA) || type.toLowerCase().equals(HSDPA) || type.toLowerCase().equals(HSPA)) {
                return TYPE_3G;
            }
            if (type.toLowerCase().equals(LTE) || type.toLowerCase().equals(UMB) || type.toLowerCase().equals(HSPA_PLUS)) {
                return TYPE_4G;
            }
        }
        return TYPE_UNKNOWN;
    }
}
