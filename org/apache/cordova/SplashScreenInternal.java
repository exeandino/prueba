package org.apache.cordova;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Handler;
import android.support.v4.view.ViewCompat;
import android.view.Display;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import com.squareup.okhttp.internal.http.HttpTransport;
import org.json.JSONArray;
import org.json.JSONException;

public class SplashScreenInternal extends CordovaPlugin {
    private static final String LOG_TAG = "SplashScreenInternal";
    private static boolean firstShow;
    private static ProgressDialog spinnerDialog;
    private static Dialog splashDialog;

    /* renamed from: org.apache.cordova.SplashScreenInternal.1 */
    class C01501 implements Runnable {
        C01501() {
        }

        public void run() {
            SplashScreenInternal.this.webView.postMessage("splashscreen", "hide");
        }
    }

    /* renamed from: org.apache.cordova.SplashScreenInternal.2 */
    class C01512 implements Runnable {
        C01512() {
        }

        public void run() {
            SplashScreenInternal.this.webView.postMessage("splashscreen", "show");
        }
    }

    /* renamed from: org.apache.cordova.SplashScreenInternal.3 */
    class C01523 implements Runnable {
        final /* synthetic */ String val$message;
        final /* synthetic */ String val$title;

        C01523(String str, String str2) {
            this.val$title = str;
            this.val$message = str2;
        }

        public void run() {
            SplashScreenInternal.this.spinnerStart(this.val$title, this.val$message);
        }
    }

    /* renamed from: org.apache.cordova.SplashScreenInternal.4 */
    class C01534 implements Runnable {
        C01534() {
        }

        public void run() {
            if (SplashScreenInternal.splashDialog != null && SplashScreenInternal.splashDialog.isShowing()) {
                SplashScreenInternal.splashDialog.dismiss();
                SplashScreenInternal.splashDialog = null;
            }
        }
    }

    /* renamed from: org.apache.cordova.SplashScreenInternal.5 */
    class C01555 implements Runnable {
        final /* synthetic */ int val$drawableId;
        final /* synthetic */ boolean val$hideAfterDelay;
        final /* synthetic */ int val$splashscreenTime;

        /* renamed from: org.apache.cordova.SplashScreenInternal.5.1 */
        class C01541 implements Runnable {
            C01541() {
            }

            public void run() {
                SplashScreenInternal.this.removeSplashScreen();
            }
        }

        C01555(int i, boolean z, int i2) {
            this.val$drawableId = i;
            this.val$hideAfterDelay = z;
            this.val$splashscreenTime = i2;
        }

        public void run() {
            Display display = SplashScreenInternal.this.cordova.getActivity().getWindowManager().getDefaultDisplay();
            Context context = SplashScreenInternal.this.webView.getContext();
            LinearLayout root = new LinearLayout(context);
            root.setMinimumHeight(display.getHeight());
            root.setMinimumWidth(display.getWidth());
            root.setOrientation(1);
            root.setBackgroundColor(SplashScreenInternal.this.preferences.getInteger("backgroundColor", ViewCompat.MEASURED_STATE_MASK));
            root.setLayoutParams(new LayoutParams(-1, -1, 0.0f));
            root.setBackgroundResource(this.val$drawableId);
            SplashScreenInternal.splashDialog = new Dialog(context, 16973840);
            if ((SplashScreenInternal.this.cordova.getActivity().getWindow().getAttributes().flags & HttpTransport.DEFAULT_CHUNK_LENGTH) == HttpTransport.DEFAULT_CHUNK_LENGTH) {
                SplashScreenInternal.splashDialog.getWindow().setFlags(HttpTransport.DEFAULT_CHUNK_LENGTH, HttpTransport.DEFAULT_CHUNK_LENGTH);
            }
            SplashScreenInternal.splashDialog.setContentView(root);
            SplashScreenInternal.splashDialog.setCancelable(false);
            SplashScreenInternal.splashDialog.show();
            if (this.val$hideAfterDelay) {
                new Handler().postDelayed(new C01541(), (long) this.val$splashscreenTime);
            }
        }
    }

    /* renamed from: org.apache.cordova.SplashScreenInternal.6 */
    class C01576 implements Runnable {
        final /* synthetic */ String val$message;
        final /* synthetic */ String val$title;

        /* renamed from: org.apache.cordova.SplashScreenInternal.6.1 */
        class C01561 implements OnCancelListener {
            C01561() {
            }

            public void onCancel(DialogInterface dialog) {
                SplashScreenInternal.spinnerDialog = null;
            }
        }

        C01576(String str, String str2) {
            this.val$title = str;
            this.val$message = str2;
        }

        public void run() {
            SplashScreenInternal.this.spinnerStop();
            SplashScreenInternal.spinnerDialog = ProgressDialog.show(SplashScreenInternal.this.webView.getContext(), this.val$title, this.val$message, true, true, new C01561());
        }
    }

    /* renamed from: org.apache.cordova.SplashScreenInternal.7 */
    class C01587 implements Runnable {
        C01587() {
        }

        public void run() {
            if (SplashScreenInternal.spinnerDialog != null && SplashScreenInternal.spinnerDialog.isShowing()) {
                SplashScreenInternal.spinnerDialog.dismiss();
                SplashScreenInternal.spinnerDialog = null;
            }
        }
    }

    static {
        firstShow = true;
    }

    protected void pluginInitialize() {
        if (firstShow) {
            this.webView.setVisibility(4);
            if (this.preferences.getInteger("SplashDrawableId", 0) == 0) {
                String splashResource = this.preferences.getString("SplashScreen", null);
                if (splashResource != null) {
                    int drawableId = this.cordova.getActivity().getResources().getIdentifier(splashResource, "drawable", this.cordova.getActivity().getClass().getPackage().getName());
                    if (drawableId == 0) {
                        drawableId = this.cordova.getActivity().getResources().getIdentifier(splashResource, "drawable", this.cordova.getActivity().getPackageName());
                    }
                    this.preferences.set("SplashDrawableId", drawableId);
                }
            }
            firstShow = false;
            loadSpinner();
            showSplashScreen(true);
        }
    }

    public void onPause(boolean multitasking) {
        removeSplashScreen();
    }

    public void onDestroy() {
        removeSplashScreen();
        firstShow = true;
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("hide")) {
            this.cordova.getActivity().runOnUiThread(new C01501());
        } else if (action.equals("show")) {
            this.cordova.getActivity().runOnUiThread(new C01512());
        } else if (!action.equals("spinnerStart")) {
            return false;
        } else {
            this.cordova.getActivity().runOnUiThread(new C01523(args.getString(0), args.getString(1)));
        }
        callbackContext.success();
        return true;
    }

    public Object onMessage(String id, Object data) {
        if ("splashscreen".equals(id)) {
            if ("hide".equals(data.toString())) {
                removeSplashScreen();
            } else {
                showSplashScreen(false);
            }
        } else if ("spinner".equals(id)) {
            if ("stop".equals(data.toString())) {
                spinnerStop();
                this.webView.setVisibility(0);
            }
        } else if ("onReceivedError".equals(id)) {
            spinnerStop();
        }
        return null;
    }

    private void removeSplashScreen() {
        this.cordova.getActivity().runOnUiThread(new C01534());
    }

    private void showSplashScreen(boolean hideAfterDelay) {
        int splashscreenTime = this.preferences.getInteger("SplashScreenDelay", 3000);
        int drawableId = this.preferences.getInteger("SplashDrawableId", 0);
        if ((splashDialog != null && splashDialog.isShowing()) || drawableId == 0) {
            return;
        }
        if (splashscreenTime > 0 || !hideAfterDelay) {
            this.cordova.getActivity().runOnUiThread(new C01555(drawableId, hideAfterDelay, splashscreenTime));
        }
    }

    private void loadSpinner() {
        String loading;
        if (this.webView.canGoBack()) {
            loading = this.preferences.getString("LoadingDialog", null);
        } else {
            loading = this.preferences.getString("LoadingPageDialog", null);
        }
        if (loading != null) {
            String title = "";
            String message = "Loading Application...";
            if (loading.length() > 0) {
                int comma = loading.indexOf(44);
                if (comma > 0) {
                    title = loading.substring(0, comma);
                    message = loading.substring(comma + 1);
                } else {
                    title = "";
                    message = loading;
                }
            }
            spinnerStart(title, message);
        }
    }

    private void spinnerStart(String title, String message) {
        this.cordova.getActivity().runOnUiThread(new C01576(title, message));
    }

    private void spinnerStop() {
        this.cordova.getActivity().runOnUiThread(new C01587());
    }
}
