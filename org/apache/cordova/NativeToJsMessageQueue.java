package org.apache.cordova;

import android.os.Message;
import android.support.v4.app.NotificationCompat.WearableExtender;
import android.support.v4.media.TransportMediator;
import android.util.Log;
import android.webkit.WebView;
import ch.ti8m.phonegap.plugins.DocumentHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import org.apache.cordova.PluginResult.Status;

public class NativeToJsMessageQueue {
    static final boolean DISABLE_EXEC_CHAINING = false;
    private static final boolean FORCE_ENCODE_USING_EVAL = false;
    private static final String LOG_TAG = "JsMessageQueue";
    private static int MAX_PAYLOAD_SIZE;
    private BridgeMode activeBridgeMode;
    private final CordovaInterface cordova;
    private boolean paused;
    private final LinkedList<JsMessage> queue;
    private final BridgeMode[] registeredListeners;
    private final CordovaWebView webView;

    private abstract class BridgeMode {
        abstract void onNativeToJsMessageAvailable();

        private BridgeMode() {
        }

        void notifyOfFlush(boolean fromOnlineEvent) {
        }

        void reset() {
        }
    }

    private static class JsMessage {
        final String jsPayloadOrCallbackId;
        final PluginResult pluginResult;

        JsMessage(String js) {
            if (js == null) {
                throw new NullPointerException();
            }
            this.jsPayloadOrCallbackId = js;
            this.pluginResult = null;
        }

        JsMessage(PluginResult pluginResult, String callbackId) {
            if (callbackId == null || pluginResult == null) {
                throw new NullPointerException();
            }
            this.jsPayloadOrCallbackId = callbackId;
            this.pluginResult = pluginResult;
        }

        static int calculateEncodedLengthHelper(PluginResult pluginResult) {
            switch (pluginResult.getMessageType()) {
                case DocumentHandler.ERROR_UNKNOWN_ERROR /*1*/:
                    return pluginResult.getStrMessage().length() + 1;
                case WearableExtender.SIZE_MEDIUM /*3*/:
                    return pluginResult.getMessage().length() + 1;
                case TransportMediator.FLAG_KEY_MEDIA_PLAY /*4*/:
                case WearableExtender.SIZE_FULL_SCREEN /*5*/:
                    return 1;
                case FragmentManagerImpl.ANIM_STYLE_FADE_EXIT /*6*/:
                    return pluginResult.getMessage().length() + 1;
                case PluginResult.MESSAGE_TYPE_BINARYSTRING /*7*/:
                    return pluginResult.getMessage().length() + 1;
                case TransportMediator.FLAG_KEY_MEDIA_PLAY_PAUSE /*8*/:
                    int ret = 1;
                    for (int i = 0; i < pluginResult.getMultipartMessagesSize(); i++) {
                        int length = calculateEncodedLengthHelper(pluginResult.getMultipartMessage(i));
                        ret += (String.valueOf(length).length() + 1) + length;
                    }
                    return ret;
                default:
                    return pluginResult.getMessage().length();
            }
        }

        int calculateEncodedLength() {
            if (this.pluginResult == null) {
                return this.jsPayloadOrCallbackId.length() + 1;
            }
            return calculateEncodedLengthHelper(this.pluginResult) + ((((String.valueOf(this.pluginResult.getStatus()).length() + 2) + 1) + this.jsPayloadOrCallbackId.length()) + 1);
        }

        static void encodeAsMessageHelper(StringBuilder sb, PluginResult pluginResult) {
            switch (pluginResult.getMessageType()) {
                case DocumentHandler.ERROR_UNKNOWN_ERROR /*1*/:
                    sb.append('s');
                    sb.append(pluginResult.getStrMessage());
                case WearableExtender.SIZE_MEDIUM /*3*/:
                    sb.append('n').append(pluginResult.getMessage());
                case TransportMediator.FLAG_KEY_MEDIA_PLAY /*4*/:
                    sb.append(pluginResult.getMessage().charAt(0));
                case WearableExtender.SIZE_FULL_SCREEN /*5*/:
                    sb.append('N');
                case FragmentManagerImpl.ANIM_STYLE_FADE_EXIT /*6*/:
                    sb.append('A');
                    sb.append(pluginResult.getMessage());
                case PluginResult.MESSAGE_TYPE_BINARYSTRING /*7*/:
                    sb.append('S');
                    sb.append(pluginResult.getMessage());
                case TransportMediator.FLAG_KEY_MEDIA_PLAY_PAUSE /*8*/:
                    sb.append('M');
                    for (int i = 0; i < pluginResult.getMultipartMessagesSize(); i++) {
                        PluginResult multipartMessage = pluginResult.getMultipartMessage(i);
                        sb.append(String.valueOf(calculateEncodedLengthHelper(multipartMessage)));
                        sb.append(' ');
                        encodeAsMessageHelper(sb, multipartMessage);
                    }
                default:
                    sb.append(pluginResult.getMessage());
            }
        }

        void encodeAsMessage(StringBuilder sb) {
            if (this.pluginResult == null) {
                sb.append('J').append(this.jsPayloadOrCallbackId);
                return;
            }
            boolean noResult;
            boolean resultOk;
            int status = this.pluginResult.getStatus();
            if (status == Status.NO_RESULT.ordinal()) {
                noResult = true;
            } else {
                noResult = NativeToJsMessageQueue.FORCE_ENCODE_USING_EVAL;
            }
            if (status == Status.OK.ordinal()) {
                resultOk = true;
            } else {
                resultOk = NativeToJsMessageQueue.FORCE_ENCODE_USING_EVAL;
            }
            boolean keepCallback = this.pluginResult.getKeepCallback();
            char c = (noResult || resultOk) ? 'S' : 'F';
            sb.append(c).append(keepCallback ? '1' : '0').append(status).append(' ').append(this.jsPayloadOrCallbackId).append(' ');
            encodeAsMessageHelper(sb, this.pluginResult);
        }

        void encodeAsJsMessage(StringBuilder sb) {
            if (this.pluginResult == null) {
                sb.append(this.jsPayloadOrCallbackId);
                return;
            }
            int status = this.pluginResult.getStatus();
            boolean success = (status == Status.OK.ordinal() || status == Status.NO_RESULT.ordinal()) ? true : NativeToJsMessageQueue.FORCE_ENCODE_USING_EVAL;
            sb.append("cordova.callbackFromNative('").append(this.jsPayloadOrCallbackId).append("',").append(success).append(",").append(status).append(",[");
            switch (this.pluginResult.getMessageType()) {
                case FragmentManagerImpl.ANIM_STYLE_FADE_EXIT /*6*/:
                    sb.append("cordova.require('cordova/base64').toArrayBuffer('").append(this.pluginResult.getMessage()).append("')");
                    break;
                case PluginResult.MESSAGE_TYPE_BINARYSTRING /*7*/:
                    sb.append("atob('").append(this.pluginResult.getMessage()).append("')");
                    break;
                default:
                    sb.append(this.pluginResult.getMessage());
                    break;
            }
            sb.append("],").append(this.pluginResult.getKeepCallback()).append(");");
        }
    }

    private class LoadUrlBridgeMode extends BridgeMode {
        final Runnable runnable;

        /* renamed from: org.apache.cordova.NativeToJsMessageQueue.LoadUrlBridgeMode.1 */
        class C01471 implements Runnable {
            C01471() {
            }

            public void run() {
                String js = NativeToJsMessageQueue.this.popAndEncodeAsJs();
                if (js != null) {
                    NativeToJsMessageQueue.this.webView.loadUrlNow("javascript:" + js);
                }
            }
        }

        private LoadUrlBridgeMode() {
            super(null);
            this.runnable = new C01471();
        }

        void onNativeToJsMessageAvailable() {
            NativeToJsMessageQueue.this.cordova.getActivity().runOnUiThread(this.runnable);
        }
    }

    private class OnlineEventsBridgeMode extends BridgeMode {
        private boolean ignoreNextFlush;
        private boolean online;
        final Runnable resetNetworkRunnable;
        final Runnable toggleNetworkRunnable;

        /* renamed from: org.apache.cordova.NativeToJsMessageQueue.OnlineEventsBridgeMode.1 */
        class C01481 implements Runnable {
            C01481() {
            }

            public void run() {
                if (!NativeToJsMessageQueue.this.queue.isEmpty()) {
                    OnlineEventsBridgeMode.this.ignoreNextFlush = NativeToJsMessageQueue.FORCE_ENCODE_USING_EVAL;
                    NativeToJsMessageQueue.this.webView.setNetworkAvailable(OnlineEventsBridgeMode.this.online);
                }
            }
        }

        /* renamed from: org.apache.cordova.NativeToJsMessageQueue.OnlineEventsBridgeMode.2 */
        class C01492 implements Runnable {
            C01492() {
            }

            public void run() {
                OnlineEventsBridgeMode.this.online = NativeToJsMessageQueue.FORCE_ENCODE_USING_EVAL;
                OnlineEventsBridgeMode.this.ignoreNextFlush = true;
                NativeToJsMessageQueue.this.webView.setNetworkAvailable(true);
            }
        }

        private OnlineEventsBridgeMode() {
            super(null);
            this.toggleNetworkRunnable = new C01481();
            this.resetNetworkRunnable = new C01492();
        }

        void reset() {
            NativeToJsMessageQueue.this.cordova.getActivity().runOnUiThread(this.resetNetworkRunnable);
        }

        void onNativeToJsMessageAvailable() {
            NativeToJsMessageQueue.this.cordova.getActivity().runOnUiThread(this.toggleNetworkRunnable);
        }

        void notifyOfFlush(boolean fromOnlineEvent) {
            if (fromOnlineEvent && !this.ignoreNextFlush) {
                this.online = !this.online ? true : NativeToJsMessageQueue.FORCE_ENCODE_USING_EVAL;
            }
        }
    }

    private class PollingBridgeMode extends BridgeMode {
        private PollingBridgeMode() {
            super(null);
        }

        void onNativeToJsMessageAvailable() {
        }
    }

    private class PrivateApiBridgeMode extends BridgeMode {
        private static final int EXECUTE_JS = 194;
        boolean initFailed;
        Method sendMessageMethod;
        Object webViewCore;

        private PrivateApiBridgeMode() {
            super(null);
        }

        private void initReflection() {
            Object access$600 = NativeToJsMessageQueue.this.webView;
            Class webViewClass = WebView.class;
            try {
                Field f = webViewClass.getDeclaredField("mProvider");
                f.setAccessible(true);
                access$600 = f.get(NativeToJsMessageQueue.this.webView);
                webViewClass = access$600.getClass();
            } catch (Throwable th) {
            }
            try {
                f = webViewClass.getDeclaredField("mWebViewCore");
                f.setAccessible(true);
                this.webViewCore = f.get(access$600);
                if (this.webViewCore != null) {
                    this.sendMessageMethod = this.webViewCore.getClass().getDeclaredMethod("sendMessage", new Class[]{Message.class});
                    this.sendMessageMethod.setAccessible(true);
                }
            } catch (Throwable e) {
                this.initFailed = true;
                Log.e(NativeToJsMessageQueue.LOG_TAG, "PrivateApiBridgeMode failed to find the expected APIs.", e);
            }
        }

        void onNativeToJsMessageAvailable() {
            if (this.sendMessageMethod == null && !this.initFailed) {
                initReflection();
            }
            if (this.sendMessageMethod != null) {
                Message execJsMessage = Message.obtain(null, EXECUTE_JS, NativeToJsMessageQueue.this.popAndEncodeAsJs());
                try {
                    this.sendMessageMethod.invoke(this.webViewCore, new Object[]{execJsMessage});
                } catch (Throwable e) {
                    Log.e(NativeToJsMessageQueue.LOG_TAG, "Reflection message bridge failed.", e);
                }
            }
        }
    }

    static {
        MAX_PAYLOAD_SIZE = 524288000;
    }

    public NativeToJsMessageQueue(CordovaWebView webView, CordovaInterface cordova) {
        this.queue = new LinkedList();
        this.cordova = cordova;
        this.webView = webView;
        this.registeredListeners = new BridgeMode[4];
        this.registeredListeners[0] = new PollingBridgeMode();
        this.registeredListeners[1] = new LoadUrlBridgeMode();
        this.registeredListeners[2] = new OnlineEventsBridgeMode();
        this.registeredListeners[3] = new PrivateApiBridgeMode();
        reset();
    }

    public boolean isBridgeEnabled() {
        return this.activeBridgeMode != null ? true : FORCE_ENCODE_USING_EVAL;
    }

    public void setBridgeMode(int value) {
        if (value < -1 || value >= this.registeredListeners.length) {
            Log.d(LOG_TAG, "Invalid NativeToJsBridgeMode: " + value);
            return;
        }
        BridgeMode newMode = value < 0 ? null : this.registeredListeners[value];
        if (newMode != this.activeBridgeMode) {
            Log.d(LOG_TAG, "Set native->JS mode to " + (newMode == null ? "null" : newMode.getClass().getSimpleName()));
            synchronized (this) {
                this.activeBridgeMode = newMode;
                if (newMode != null) {
                    newMode.reset();
                    if (!(this.paused || this.queue.isEmpty())) {
                        newMode.onNativeToJsMessageAvailable();
                    }
                }
            }
        }
    }

    public void reset() {
        synchronized (this) {
            this.queue.clear();
            setBridgeMode(-1);
        }
    }

    private int calculatePackedMessageLength(JsMessage message) {
        int messageLen = message.calculateEncodedLength();
        return (String.valueOf(messageLen).length() + messageLen) + 1;
    }

    private void packMessage(JsMessage message, StringBuilder sb) {
        sb.append(message.calculateEncodedLength()).append(' ');
        message.encodeAsMessage(sb);
    }

    public String popAndEncode(boolean fromOnlineEvent) {
        String str = null;
        synchronized (this) {
            if (this.activeBridgeMode == null) {
            } else {
                this.activeBridgeMode.notifyOfFlush(fromOnlineEvent);
                if (this.queue.isEmpty()) {
                } else {
                    int totalPayloadLen = 0;
                    int numMessagesToSend = 0;
                    Iterator i$ = this.queue.iterator();
                    while (i$.hasNext()) {
                        int messageSize = calculatePackedMessageLength((JsMessage) i$.next());
                        if (numMessagesToSend > 0 && totalPayloadLen + messageSize > MAX_PAYLOAD_SIZE && MAX_PAYLOAD_SIZE > 0) {
                            break;
                        }
                        totalPayloadLen += messageSize;
                        numMessagesToSend++;
                    }
                    StringBuilder sb = new StringBuilder(totalPayloadLen);
                    for (int i = 0; i < numMessagesToSend; i++) {
                        packMessage((JsMessage) this.queue.removeFirst(), sb);
                    }
                    if (!this.queue.isEmpty()) {
                        sb.append('*');
                    }
                    str = sb.toString();
                }
            }
        }
        return str;
    }

    private String popAndEncodeAsJs() {
        String str;
        synchronized (this) {
            if (this.queue.size() == 0) {
                str = null;
            } else {
                boolean willSendAllMessages;
                int totalPayloadLen = 0;
                int numMessagesToSend = 0;
                Iterator i$ = this.queue.iterator();
                while (i$.hasNext()) {
                    int messageSize = ((JsMessage) i$.next()).calculateEncodedLength() + 50;
                    if (numMessagesToSend > 0 && totalPayloadLen + messageSize > MAX_PAYLOAD_SIZE && MAX_PAYLOAD_SIZE > 0) {
                        break;
                    }
                    totalPayloadLen += messageSize;
                    numMessagesToSend++;
                }
                if (numMessagesToSend == this.queue.size()) {
                    willSendAllMessages = true;
                } else {
                    willSendAllMessages = FORCE_ENCODE_USING_EVAL;
                }
                StringBuilder sb = new StringBuilder((willSendAllMessages ? 0 : 100) + totalPayloadLen);
                int i = 0;
                while (i < numMessagesToSend) {
                    JsMessage message = (JsMessage) this.queue.removeFirst();
                    if (willSendAllMessages && i + 1 == numMessagesToSend) {
                        message.encodeAsJsMessage(sb);
                    } else {
                        sb.append("try{");
                        message.encodeAsJsMessage(sb);
                        sb.append("}finally{");
                    }
                    i++;
                }
                if (!willSendAllMessages) {
                    sb.append("window.setTimeout(function(){cordova.require('cordova/plugin/android/polling').pollOnce();},0);");
                }
                i = willSendAllMessages ? 1 : 0;
                while (i < numMessagesToSend) {
                    sb.append('}');
                    i++;
                }
                str = sb.toString();
            }
        }
        return str;
    }

    public void addJavaScript(String statement) {
        enqueueMessage(new JsMessage(statement));
    }

    public void addPluginResult(PluginResult result, String callbackId) {
        if (callbackId == null) {
            Log.e(LOG_TAG, "Got plugin result with no callbackId", new Throwable());
            return;
        }
        boolean noResult = result.getStatus() == Status.NO_RESULT.ordinal() ? true : FORCE_ENCODE_USING_EVAL;
        boolean keepCallback = result.getKeepCallback();
        if (!noResult || !keepCallback) {
            enqueueMessage(new JsMessage(result, callbackId));
        }
    }

    private void enqueueMessage(JsMessage message) {
        synchronized (this) {
            if (this.activeBridgeMode == null) {
                Log.d(LOG_TAG, "Dropping Native->JS message due to disabled bridge");
                return;
            }
            this.queue.add(message);
            if (!this.paused) {
                this.activeBridgeMode.onNativeToJsMessageAvailable();
            }
        }
    }

    public void setPaused(boolean value) {
        if (this.paused && value) {
            Log.e(LOG_TAG, "nested call to setPaused detected.", new Throwable());
        }
        this.paused = value;
        if (!value) {
            synchronized (this) {
                if (!(this.queue.isEmpty() || this.activeBridgeMode == null)) {
                    this.activeBridgeMode.onNativeToJsMessageAvailable();
                }
            }
        }
    }
}
