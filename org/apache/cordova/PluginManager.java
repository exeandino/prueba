package org.apache.cordova;

import android.content.Intent;
import android.net.Uri;
import android.os.Debug;
import android.util.Log;
import java.util.HashMap;
import java.util.List;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONException;

public class PluginManager {
    private static final int SLOW_EXEC_WARNING_THRESHOLD;
    private static String TAG;
    private final CordovaWebView app;
    private final CordovaInterface ctx;
    private final HashMap<String, PluginEntry> entryMap;
    private final HashMap<String, CordovaPlugin> pluginMap;
    protected HashMap<String, List<String>> urlMap;

    static {
        TAG = "PluginManager";
        SLOW_EXEC_WARNING_THRESHOLD = Debug.isDebuggerConnected() ? 60 : 16;
    }

    @Deprecated
    PluginManager(CordovaWebView cordovaWebView, CordovaInterface cordova) {
        this(cordovaWebView, cordova, null);
    }

    PluginManager(CordovaWebView cordovaWebView, CordovaInterface cordova, List<PluginEntry> pluginEntries) {
        this.pluginMap = new HashMap();
        this.entryMap = new HashMap();
        this.urlMap = new HashMap();
        this.ctx = cordova;
        this.app = cordovaWebView;
        if (pluginEntries == null) {
            ConfigXmlParser parser = new ConfigXmlParser();
            parser.parse(this.ctx.getActivity());
            pluginEntries = parser.getPluginEntries();
        }
        setPluginEntries(pluginEntries);
    }

    public void setPluginEntries(List<PluginEntry> pluginEntries) {
        onPause(false);
        onDestroy();
        this.pluginMap.clear();
        this.urlMap.clear();
        for (PluginEntry entry : pluginEntries) {
            addService(entry);
        }
    }

    public void init() {
        LOG.m9d(TAG, "init()");
        onPause(false);
        onDestroy();
        this.pluginMap.clear();
        startupPlugins();
    }

    @Deprecated
    public void loadPlugins() {
    }

    @Deprecated
    public void clearPluginObjects() {
        this.pluginMap.clear();
    }

    @Deprecated
    public void startupPlugins() {
        for (PluginEntry entry : this.entryMap.values()) {
            if (entry.onload) {
                getPlugin(entry.service);
            } else {
                this.pluginMap.put(entry.service, null);
            }
        }
    }

    public void exec(String service, String action, String callbackId, String rawArgs) {
        CordovaPlugin plugin = getPlugin(service);
        if (plugin == null) {
            Log.d(TAG, "exec() call to unknown plugin: " + service);
            this.app.sendPluginResult(new PluginResult(Status.CLASS_NOT_FOUND_EXCEPTION), callbackId);
            return;
        }
        CallbackContext callbackContext = new CallbackContext(callbackId, this.app);
        try {
            long pluginStartTime = System.currentTimeMillis();
            boolean wasValidAction = plugin.execute(action, rawArgs, callbackContext);
            long duration = System.currentTimeMillis() - pluginStartTime;
            if (duration > ((long) SLOW_EXEC_WARNING_THRESHOLD)) {
                Log.w(TAG, "THREAD WARNING: exec() call to " + service + "." + action + " blocked the main thread for " + duration + "ms. Plugin should use CordovaInterface.getThreadPool().");
            }
            if (!wasValidAction) {
                callbackContext.sendPluginResult(new PluginResult(Status.INVALID_ACTION));
            }
        } catch (JSONException e) {
            callbackContext.sendPluginResult(new PluginResult(Status.JSON_EXCEPTION));
        } catch (Exception e2) {
            Log.e(TAG, "Uncaught exception from plugin", e2);
            callbackContext.error(e2.getMessage());
        }
    }

    @Deprecated
    public void exec(String service, String action, String callbackId, String jsonArgs, boolean async) {
        exec(service, action, callbackId, jsonArgs);
    }

    public CordovaPlugin getPlugin(String service) {
        CordovaPlugin ret = (CordovaPlugin) this.pluginMap.get(service);
        if (ret == null) {
            PluginEntry pe = (PluginEntry) this.entryMap.get(service);
            if (pe == null) {
                return null;
            }
            if (pe.plugin != null) {
                ret = pe.plugin;
            } else {
                ret = instantiatePlugin(pe.pluginClass);
            }
            ret.privateInitialize(this.ctx, this.app, this.app.getPreferences());
            this.pluginMap.put(service, ret);
        }
        return ret;
    }

    public void addService(String service, String className) {
        addService(new PluginEntry(service, className, false));
    }

    public void addService(PluginEntry entry) {
        this.entryMap.put(entry.service, entry);
        List<String> urlFilters = entry.getUrlFilters();
        if (urlFilters != null) {
            this.urlMap.put(entry.service, urlFilters);
        }
        if (entry.plugin != null) {
            entry.plugin.privateInitialize(this.ctx, this.app, this.app.getPreferences());
            this.pluginMap.put(entry.service, entry.plugin);
        }
    }

    public void onPause(boolean multitasking) {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null) {
                plugin.onPause(multitasking);
            }
        }
    }

    public boolean onReceivedHttpAuthRequest(CordovaWebView view, ICordovaHttpAuthHandler handler, String host, String realm) {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null && plugin.onReceivedHttpAuthRequest(view, handler, host, realm)) {
                return true;
            }
        }
        return false;
    }

    public boolean onReceivedClientCertRequest(CordovaWebView view, ICordovaClientCertRequest request) {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null && plugin.onReceivedClientCertRequest(view, request)) {
                return true;
            }
        }
        return false;
    }

    public void onResume(boolean multitasking) {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null) {
                plugin.onResume(multitasking);
            }
        }
    }

    public void onDestroy() {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null) {
                plugin.onDestroy();
            }
        }
    }

    public Object postMessage(String id, Object data) {
        Object obj = this.ctx.onMessage(id, data);
        if (obj != null) {
            return obj;
        }
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null) {
                obj = plugin.onMessage(id, data);
                if (obj != null) {
                    return obj;
                }
            }
        }
        return null;
    }

    public void onNewIntent(Intent intent) {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null) {
                plugin.onNewIntent(intent);
            }
        }
    }

    public boolean onOverrideUrlLoading(String url) {
        for (PluginEntry entry : this.entryMap.values()) {
            List<String> urlFilters = (List) this.urlMap.get(entry.service);
            if (urlFilters != null) {
                for (String s : urlFilters) {
                    if (url.startsWith(s)) {
                        return getPlugin(entry.service).onOverrideUrlLoading(url);
                    }
                }
                continue;
            } else {
                CordovaPlugin plugin = (CordovaPlugin) this.pluginMap.get(entry.service);
                if (plugin != null && plugin.onOverrideUrlLoading(url)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void onReset() {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null) {
                plugin.onReset();
            }
        }
    }

    Uri remapUri(Uri uri) {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null) {
                Uri ret = plugin.remapUri(uri);
                if (ret != null) {
                    return ret;
                }
            }
        }
        return null;
    }

    private CordovaPlugin instantiatePlugin(String className) {
        Class<?> c = null;
        if (className != null) {
            try {
                if (!"".equals(className)) {
                    c = Class.forName(className);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error adding plugin " + className + ".");
                return null;
            }
        }
        if (((c != null ? 1 : 0) & CordovaPlugin.class.isAssignableFrom(c)) == 0) {
            return null;
        }
        return (CordovaPlugin) c.newInstance();
    }
}
