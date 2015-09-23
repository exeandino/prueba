package net.tunts.webintent;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.Html;
import java.util.HashMap;
import java.util.Map;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.apache.cordova.globalization.Globalization;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WebIntent extends CordovaPlugin {
    private CallbackContext onNewIntentCallback;

    public WebIntent() {
        this.onNewIntentCallback = null;
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        try {
            JSONObject obj;
            JSONObject extras;
            Map<String, String> extrasMap;
            JSONArray extraNames;
            int i;
            String key;
            if (!action.equals("startActivity")) {
                if (!action.equals("hasExtra")) {
                    if (!action.equals("getExtra")) {
                        if (!action.equals("getUri")) {
                            if (!action.equals("onNewIntent")) {
                                if (!action.equals("sendBroadcast")) {
                                    callbackContext.sendPluginResult(new PluginResult(Status.INVALID_ACTION));
                                    return false;
                                } else if (args.length() != 1) {
                                    callbackContext.sendPluginResult(new PluginResult(Status.INVALID_ACTION));
                                    return false;
                                } else {
                                    obj = args.getJSONObject(0);
                                    extras = obj.has("extras") ? obj.getJSONObject("extras") : null;
                                    extrasMap = new HashMap();
                                    if (extras != null) {
                                        extraNames = extras.names();
                                        for (i = 0; i < extraNames.length(); i++) {
                                            key = extraNames.getString(i);
                                            extrasMap.put(key, extras.getString(key));
                                        }
                                    }
                                    sendBroadcast(obj.getString("action"), extrasMap);
                                    callbackContext.success();
                                    return true;
                                }
                            } else if (args.length() != 0) {
                                callbackContext.sendPluginResult(new PluginResult(Status.INVALID_ACTION));
                                return false;
                            } else {
                                this.onNewIntentCallback = callbackContext;
                                PluginResult pluginResult = new PluginResult(Status.NO_RESULT);
                                pluginResult.setKeepCallback(true);
                                callbackContext.sendPluginResult(pluginResult);
                                return true;
                            }
                        } else if (args.length() != 0) {
                            callbackContext.sendPluginResult(new PluginResult(Status.INVALID_ACTION));
                            return false;
                        } else {
                            callbackContext.success(((CordovaActivity) this.cordova.getActivity()).getIntent().getDataString());
                            return true;
                        }
                    } else if (args.length() != 1) {
                        callbackContext.sendPluginResult(new PluginResult(Status.INVALID_ACTION));
                        return false;
                    } else {
                        Intent i2 = ((CordovaActivity) this.cordova.getActivity()).getIntent();
                        String extraName = args.getString(0);
                        if (i2.hasExtra(extraName)) {
                            callbackContext.sendPluginResult(new PluginResult(Status.OK, i2.hasExtra(extraName)));
                            return true;
                        }
                        callbackContext.sendPluginResult(new PluginResult(Status.ERROR));
                        return false;
                    }
                } else if (args.length() != 1) {
                    callbackContext.sendPluginResult(new PluginResult(Status.INVALID_ACTION));
                    return false;
                } else {
                    callbackContext.sendPluginResult(new PluginResult(Status.OK, ((CordovaActivity) this.cordova.getActivity()).getIntent().hasExtra(args.getString(0))));
                    return true;
                }
            } else if (args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(Status.INVALID_ACTION));
                return false;
            } else {
                obj = args.getJSONObject(0);
                String type = obj.has(Globalization.TYPE) ? obj.getString(Globalization.TYPE) : null;
                Uri uri = obj.has("url") ? Uri.parse(obj.getString("url")) : null;
                extras = obj.has("extras") ? obj.getJSONObject("extras") : null;
                JSONObject handler = obj.has("handler") ? obj.getJSONObject("handler") : null;
                extrasMap = new HashMap();
                Map<String, String> handlerMap = null;
                if (extras != null) {
                    extraNames = extras.names();
                    for (i = 0; i < extraNames.length(); i++) {
                        key = extraNames.getString(i);
                        extrasMap.put(key, extras.getString(key));
                    }
                }
                if (handler != null) {
                    handlerMap = new HashMap();
                    handlerMap.put("packageName", handler.getString("packageName"));
                    handlerMap.put("className", handler.getString("className"));
                }
                try {
                    startActivity(obj.getString("action"), uri, type, extrasMap, handlerMap);
                    callbackContext.success();
                    return true;
                } catch (ActivityNotFoundException e) {
                    callbackContext.error(e.getMessage());
                    return false;
                }
            }
        } catch (JSONException e2) {
            callbackContext.error(e2.getMessage());
            return false;
        }
    }

    public void onNewIntent(Intent intent) {
        if (this.onNewIntentCallback != null) {
            this.onNewIntentCallback.success(intent.getDataString());
        }
    }

    void startActivity(String action, Uri uri, String type, Map<String, String> extras, Map<String, String> handlerMap) {
        Intent i = uri != null ? new Intent(action, uri) : new Intent(action);
        if (handlerMap != null) {
            i.setClassName((String) handlerMap.get("packageName"), (String) handlerMap.get("className"));
        }
        if (type != null && uri != null) {
            i.setDataAndType(uri, type);
        } else if (type != null) {
            i.setType(type);
        }
        for (String key : extras.keySet()) {
            String value = (String) extras.get(key);
            if (key.equals("android.intent.extra.TEXT") && type.equals("text/html")) {
                i.putExtra(key, Html.fromHtml(value));
            } else if (key.equals("android.intent.extra.STREAM")) {
                i.putExtra(key, Uri.parse(value));
            } else if (key.equals("android.intent.extra.EMAIL")) {
                i.putExtra("android.intent.extra.EMAIL", new String[]{value});
            } else {
                i.putExtra(key, value);
            }
        }
        ((CordovaActivity) this.cordova.getActivity()).startActivity(i);
    }

    void sendBroadcast(String action, Map<String, String> extras) {
        Intent intent = new Intent();
        intent.setAction(action);
        for (String key : extras.keySet()) {
            intent.putExtra(key, (String) extras.get(key));
        }
        ((CordovaActivity) this.cordova.getActivity()).sendBroadcast(intent);
    }
}
