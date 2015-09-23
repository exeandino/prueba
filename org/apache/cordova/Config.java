package org.apache.cordova;

import android.app.Activity;
import android.util.Log;
import java.util.List;

@Deprecated
public class Config {
    private static final String TAG = "Config";
    static ConfigXmlParser parser;

    private Config() {
    }

    public static void init(Activity action) {
        parser = new ConfigXmlParser();
        parser.parse(action);
        parser.getPreferences().setPreferencesBundle(action.getIntent().getExtras());
        parser.getPreferences().copyIntoIntentExtras(action);
    }

    public static void init() {
        if (parser == null) {
            parser = new ConfigXmlParser();
        }
    }

    public static void addWhiteListEntry(String origin, boolean subdomains) {
        if (parser == null) {
            Log.e(TAG, "Config was not initialised. Did you forget to Config.init(this)?");
        } else {
            parser.getInternalWhitelist().addWhiteListEntry(origin, subdomains);
        }
    }

    public static boolean isUrlWhiteListed(String url) {
        if (parser != null) {
            return parser.getInternalWhitelist().isUrlWhiteListed(url);
        }
        Log.e(TAG, "Config was not initialised. Did you forget to Config.init(this)?");
        return false;
    }

    public static boolean isUrlExternallyWhiteListed(String url) {
        if (parser != null) {
            return parser.getExternalWhitelist().isUrlWhiteListed(url);
        }
        Log.e(TAG, "Config was not initialised. Did you forget to Config.init(this)?");
        return false;
    }

    public static String getStartUrl() {
        if (parser == null) {
            return "file:///android_asset/www/index.html";
        }
        return parser.getLaunchUrl();
    }

    public static String getErrorUrl() {
        return parser.getPreferences().getString("errorurl", null);
    }

    public static Whitelist getWhitelist() {
        return parser.getInternalWhitelist();
    }

    public static Whitelist getExternalWhitelist() {
        return parser.getExternalWhitelist();
    }

    public static List<PluginEntry> getPluginEntries() {
        return parser.getPluginEntries();
    }

    public static CordovaPreferences getPreferences() {
        return parser.getPreferences();
    }

    public static boolean isInitialized() {
        return parser != null;
    }
}
