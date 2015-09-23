package org.apache.cordova.statusbar;

import android.graphics.Color;
import android.os.Build.VERSION;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.widget.ExploreByTouchHelper;
import android.util.Log;
import android.view.Window;
import com.squareup.okhttp.internal.http.HttpTransport;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONException;

public class StatusBar extends CordovaPlugin {
    private static final String TAG = "StatusBar";

    /* renamed from: org.apache.cordova.statusbar.StatusBar.1 */
    class C01791 implements Runnable {
        final /* synthetic */ CordovaInterface val$cordova;

        C01791(CordovaInterface cordovaInterface) {
            this.val$cordova = cordovaInterface;
        }

        public void run() {
            this.val$cordova.getActivity().getWindow().clearFlags(AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT);
            StatusBar.this.setStatusBarBackgroundColor(StatusBar.this.preferences.getString("StatusBarBackgroundColor", "#000000"));
        }
    }

    /* renamed from: org.apache.cordova.statusbar.StatusBar.2 */
    class C01802 implements Runnable {
        final /* synthetic */ Window val$window;

        C01802(Window window) {
            this.val$window = window;
        }

        public void run() {
            this.val$window.clearFlags(HttpTransport.DEFAULT_CHUNK_LENGTH);
        }
    }

    /* renamed from: org.apache.cordova.statusbar.StatusBar.3 */
    class C01813 implements Runnable {
        final /* synthetic */ Window val$window;

        C01813(Window window) {
            this.val$window = window;
        }

        public void run() {
            this.val$window.addFlags(HttpTransport.DEFAULT_CHUNK_LENGTH);
        }
    }

    /* renamed from: org.apache.cordova.statusbar.StatusBar.4 */
    class C01824 implements Runnable {
        final /* synthetic */ CordovaArgs val$args;

        C01824(CordovaArgs cordovaArgs) {
            this.val$args = cordovaArgs;
        }

        public void run() {
            try {
                StatusBar.this.setStatusBarBackgroundColor(this.val$args.getString(0));
            } catch (JSONException e) {
                Log.e(StatusBar.TAG, "Invalid hexString argument, use f.i. '#777777'");
            }
        }
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        Log.v(TAG, "StatusBar: initialization");
        super.initialize(cordova, webView);
        this.cordova.getActivity().runOnUiThread(new C01791(cordova));
    }

    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        Log.v(TAG, "Executing action: " + action);
        Window window = this.cordova.getActivity().getWindow();
        if ("_ready".equals(action)) {
            callbackContext.sendPluginResult(new PluginResult(Status.OK, (window.getAttributes().flags & HttpTransport.DEFAULT_CHUNK_LENGTH) == 0));
        }
        if ("show".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new C01802(window));
            return true;
        } else if ("hide".equals(action)) {
            this.cordova.getActivity().runOnUiThread(new C01813(window));
            return true;
        } else if (!"backgroundColorByHexString".equals(action)) {
            return false;
        } else {
            this.cordova.getActivity().runOnUiThread(new C01824(args));
            return true;
        }
    }

    private void setStatusBarBackgroundColor(String colorPref) {
        if (VERSION.SDK_INT >= 21 && colorPref != null && !colorPref.isEmpty()) {
            Window window = this.cordova.getActivity().getWindow();
            window.clearFlags(67108864);
            window.addFlags(ExploreByTouchHelper.INVALID_ID);
            try {
                window.getClass().getDeclaredMethod("setStatusBarColor", new Class[]{Integer.TYPE}).invoke(window, new Object[]{Integer.valueOf(Color.parseColor(colorPref))});
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid hexString argument, use f.i. '#999999'");
            } catch (Exception e2) {
                Log.w(TAG, "Method window.setStatusBarColor not found for SDK level " + VERSION.SDK_INT);
            }
        }
    }
}
