package org.apache.cordova;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions.Callback;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.CustomViewCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebStorage.QuotaUpdater;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout.LayoutParams;

public class CordovaChromeClient extends WebChromeClient {
    public static final int FILECHOOSER_RESULTCODE = 5173;
    private long MAX_QUOTA;
    private String TAG;
    protected CordovaWebView appView;
    protected CordovaInterface cordova;
    private AlertDialog lastHandledDialog;
    private View mVideoProgressView;

    /* renamed from: org.apache.cordova.CordovaChromeClient.1 */
    class C01311 implements OnClickListener {
        final /* synthetic */ JsResult val$result;

        C01311(JsResult jsResult) {
            this.val$result = jsResult;
        }

        public void onClick(DialogInterface dialog, int which) {
            this.val$result.confirm();
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.2 */
    class C01322 implements OnCancelListener {
        final /* synthetic */ JsResult val$result;

        C01322(JsResult jsResult) {
            this.val$result = jsResult;
        }

        public void onCancel(DialogInterface dialog) {
            this.val$result.cancel();
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.3 */
    class C01333 implements OnKeyListener {
        final /* synthetic */ JsResult val$result;

        C01333(JsResult jsResult) {
            this.val$result = jsResult;
        }

        public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
            if (keyCode != 4) {
                return true;
            }
            this.val$result.confirm();
            return false;
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.4 */
    class C01344 implements OnClickListener {
        final /* synthetic */ JsResult val$result;

        C01344(JsResult jsResult) {
            this.val$result = jsResult;
        }

        public void onClick(DialogInterface dialog, int which) {
            this.val$result.confirm();
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.5 */
    class C01355 implements OnClickListener {
        final /* synthetic */ JsResult val$result;

        C01355(JsResult jsResult) {
            this.val$result = jsResult;
        }

        public void onClick(DialogInterface dialog, int which) {
            this.val$result.cancel();
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.6 */
    class C01366 implements OnCancelListener {
        final /* synthetic */ JsResult val$result;

        C01366(JsResult jsResult) {
            this.val$result = jsResult;
        }

        public void onCancel(DialogInterface dialog) {
            this.val$result.cancel();
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.7 */
    class C01377 implements OnKeyListener {
        final /* synthetic */ JsResult val$result;

        C01377(JsResult jsResult) {
            this.val$result = jsResult;
        }

        public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
            if (keyCode != 4) {
                return true;
            }
            this.val$result.cancel();
            return false;
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.8 */
    class C01388 implements OnClickListener {
        final /* synthetic */ EditText val$input;
        final /* synthetic */ JsPromptResult val$res;

        C01388(EditText editText, JsPromptResult jsPromptResult) {
            this.val$input = editText;
            this.val$res = jsPromptResult;
        }

        public void onClick(DialogInterface dialog, int which) {
            this.val$res.confirm(this.val$input.getText().toString());
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.9 */
    class C01399 implements OnClickListener {
        final /* synthetic */ JsPromptResult val$res;

        C01399(JsPromptResult jsPromptResult) {
            this.val$res = jsPromptResult;
        }

        public void onClick(DialogInterface dialog, int which) {
            this.val$res.cancel();
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.10 */
    class AnonymousClass10 extends CordovaPlugin {
        final /* synthetic */ ValueCallback val$uploadMsg;

        AnonymousClass10(ValueCallback valueCallback) {
            this.val$uploadMsg = valueCallback;
        }

        public void onActivityResult(int requestCode, int resultCode, Intent intent) {
            Uri result = (intent == null || resultCode != -1) ? null : intent.getData();
            Log.d(CordovaChromeClient.this.TAG, "Receive file chooser URL: " + result);
            this.val$uploadMsg.onReceiveValue(result);
        }
    }

    /* renamed from: org.apache.cordova.CordovaChromeClient.11 */
    class AnonymousClass11 extends CordovaPlugin {
        final /* synthetic */ ValueCallback val$filePathsCallback;

        AnonymousClass11(ValueCallback valueCallback) {
            this.val$filePathsCallback = valueCallback;
        }

        public void onActivityResult(int requestCode, int resultCode, Intent intent) {
            Uri[] result = FileChooserParams.parseResult(resultCode, intent);
            Log.d(CordovaChromeClient.this.TAG, "Receive file chooser URL: " + result);
            this.val$filePathsCallback.onReceiveValue(result);
        }
    }

    @Deprecated
    public CordovaChromeClient(CordovaInterface cordova) {
        this.TAG = "CordovaLog";
        this.MAX_QUOTA = 104857600;
        this.cordova = cordova;
    }

    public CordovaChromeClient(CordovaInterface ctx, CordovaWebView app) {
        this.TAG = "CordovaLog";
        this.MAX_QUOTA = 104857600;
        this.cordova = ctx;
        this.appView = app;
    }

    @Deprecated
    public void setWebView(CordovaWebView view) {
        this.appView = view;
    }

    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        Builder dlg = new Builder(this.cordova.getActivity());
        dlg.setMessage(message);
        dlg.setTitle("Alert");
        dlg.setCancelable(true);
        dlg.setPositiveButton(17039370, new C01311(result));
        dlg.setOnCancelListener(new C01322(result));
        dlg.setOnKeyListener(new C01333(result));
        this.lastHandledDialog = dlg.show();
        return true;
    }

    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        Builder dlg = new Builder(this.cordova.getActivity());
        dlg.setMessage(message);
        dlg.setTitle("Confirm");
        dlg.setCancelable(true);
        dlg.setPositiveButton(17039370, new C01344(result));
        dlg.setNegativeButton(17039360, new C01355(result));
        dlg.setOnCancelListener(new C01366(result));
        dlg.setOnKeyListener(new C01377(result));
        this.lastHandledDialog = dlg.show();
        return true;
    }

    public boolean onJsPrompt(WebView view, String origin, String message, String defaultValue, JsPromptResult result) {
        String handledRet = this.appView.bridge.promptOnJsPrompt(origin, message, defaultValue);
        if (handledRet != null) {
            result.confirm(handledRet);
        } else {
            JsPromptResult res = result;
            Builder dlg = new Builder(this.cordova.getActivity());
            dlg.setMessage(message);
            EditText input = new EditText(this.cordova.getActivity());
            if (defaultValue != null) {
                input.setText(defaultValue);
            }
            dlg.setView(input);
            dlg.setCancelable(false);
            dlg.setPositiveButton(17039370, new C01388(input, res));
            dlg.setNegativeButton(17039360, new C01399(res));
            this.lastHandledDialog = dlg.show();
        }
        return true;
    }

    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize, long totalUsedQuota, QuotaUpdater quotaUpdater) {
        LOG.m11d(this.TAG, "onExceededDatabaseQuota estimatedSize: %d  currentQuota: %d  totalUsedQuota: %d", Long.valueOf(estimatedSize), Long.valueOf(currentQuota), Long.valueOf(totalUsedQuota));
        quotaUpdater.updateQuota(this.MAX_QUOTA);
    }

    public void onConsoleMessage(String message, int lineNumber, String sourceID) {
        if (VERSION.SDK_INT == 7) {
            LOG.m11d(this.TAG, "%s: Line %d : %s", sourceID, Integer.valueOf(lineNumber), message);
            super.onConsoleMessage(message, lineNumber, sourceID);
        }
    }

    @TargetApi(8)
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        if (consoleMessage.message() != null) {
            LOG.m11d(this.TAG, "%s: Line %d : %s", consoleMessage.sourceId(), Integer.valueOf(consoleMessage.lineNumber()), consoleMessage.message());
        }
        return super.onConsoleMessage(consoleMessage);
    }

    public void onGeolocationPermissionsShowPrompt(String origin, Callback callback) {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
        callback.invoke(origin, true, false);
    }

    public void onShowCustomView(View view, CustomViewCallback callback) {
        this.appView.showCustomView(view, callback);
    }

    public void onHideCustomView() {
        this.appView.hideCustomView();
    }

    public View getVideoLoadingProgressView() {
        if (this.mVideoProgressView == null) {
            LinearLayout layout = new LinearLayout(this.appView.getContext());
            layout.setOrientation(1);
            LayoutParams layoutParams = new LayoutParams(-2, -2);
            layoutParams.addRule(13);
            layout.setLayoutParams(layoutParams);
            ProgressBar bar = new ProgressBar(this.appView.getContext());
            LinearLayout.LayoutParams barLayoutParams = new LinearLayout.LayoutParams(-2, -2);
            barLayoutParams.gravity = 17;
            bar.setLayoutParams(barLayoutParams);
            layout.addView(bar);
            this.mVideoProgressView = layout;
        }
        return this.mVideoProgressView;
    }

    public void openFileChooser(ValueCallback<Uri> uploadMsg) {
        openFileChooser(uploadMsg, "*/*");
    }

    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
        openFileChooser(uploadMsg, acceptType, null);
    }

    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        this.cordova.startActivityForResult(new AnonymousClass10(uploadMsg), intent, FILECHOOSER_RESULTCODE);
    }

    @TargetApi(21)
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathsCallback, FileChooserParams fileChooserParams) {
        try {
            this.cordova.startActivityForResult(new AnonymousClass11(filePathsCallback), fileChooserParams.createIntent(), FILECHOOSER_RESULTCODE);
        } catch (ActivityNotFoundException e) {
            Log.w("No activity found to handle file chooser intent.", e);
            filePathsCallback.onReceiveValue(null);
        }
        return true;
    }

    public void destroyLastDialog() {
        if (this.lastHandledDialog != null) {
            this.lastHandledDialog.cancel();
        }
    }
}
