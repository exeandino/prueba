package nl.xservices.plugins;

import android.content.Intent;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;

public class LaunchMyApp extends CordovaPlugin {
    private static final String ACTION_CHECKINTENT = "checkIntent";

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (ACTION_CHECKINTENT.equalsIgnoreCase(action)) {
            Intent intent = ((CordovaActivity) this.webView.getContext()).getIntent();
            if (intent.getDataString() != null) {
                callbackContext.sendPluginResult(new PluginResult(Status.OK, intent.getDataString()));
                intent.setData(null);
                return true;
            }
            callbackContext.error("App was not started via the launchmyapp URL scheme. Ignoring this errorcallback is the best approach.");
            return false;
        }
        callbackContext.error("This plugin only responds to the checkIntent action.");
        return false;
    }

    public void onNewIntent(Intent intent) {
        String intentString = intent.getDataString();
        if (intent.getDataString() != null) {
            intent.setData(null);
            this.webView.loadUrl("javascript:handleOpenURL('" + intentString + "');");
        }
    }
}
