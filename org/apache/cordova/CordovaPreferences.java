package org.apache.cordova;

import android.app.Activity;
import android.os.Bundle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CordovaPreferences {
    private Bundle preferencesBundleExtras;
    private HashMap<String, String> prefs;

    public CordovaPreferences() {
        this.prefs = new HashMap(20);
    }

    public void setPreferencesBundle(Bundle extras) {
        this.preferencesBundleExtras = extras;
    }

    public void set(String name, String value) {
        this.prefs.put(name.toLowerCase(Locale.ENGLISH), value);
    }

    public void set(String name, boolean value) {
        set(name, "" + value);
    }

    public void set(String name, int value) {
        set(name, "" + value);
    }

    public void set(String name, double value) {
        set(name, "" + value);
    }

    public Map<String, String> getAll() {
        return this.prefs;
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        name = name.toLowerCase(Locale.ENGLISH);
        String value = (String) this.prefs.get(name);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        if (this.preferencesBundleExtras == null) {
            return defaultValue;
        }
        Object bundleValue = this.preferencesBundleExtras.get(name);
        if (bundleValue instanceof String) {
            return "true".equals(bundleValue);
        }
        return this.preferencesBundleExtras.getBoolean(name, defaultValue);
    }

    public int getInteger(String name, int defaultValue) {
        name = name.toLowerCase(Locale.ENGLISH);
        String value = (String) this.prefs.get(name);
        if (value != null) {
            return (int) Long.decode(value).longValue();
        }
        if (this.preferencesBundleExtras == null) {
            return defaultValue;
        }
        Object bundleValue = this.preferencesBundleExtras.get(name);
        if (bundleValue instanceof String) {
            return Integer.valueOf((String) bundleValue).intValue();
        }
        return this.preferencesBundleExtras.getInt(name, defaultValue);
    }

    public double getDouble(String name, double defaultValue) {
        name = name.toLowerCase(Locale.ENGLISH);
        String value = (String) this.prefs.get(name);
        if (value != null) {
            return Double.valueOf(value).doubleValue();
        }
        if (this.preferencesBundleExtras == null) {
            return defaultValue;
        }
        Object bundleValue = this.preferencesBundleExtras.get(name);
        if (bundleValue instanceof String) {
            return Double.valueOf((String) bundleValue).doubleValue();
        }
        return this.preferencesBundleExtras.getDouble(name, defaultValue);
    }

    public String getString(String name, String defaultValue) {
        name = name.toLowerCase(Locale.ENGLISH);
        String value = (String) this.prefs.get(name);
        if (value != null) {
            return value;
        }
        if (!(this.preferencesBundleExtras == null || "errorurl".equals(name))) {
            Object bundleValue = this.preferencesBundleExtras.get(name);
            if (bundleValue != null) {
                return bundleValue.toString();
            }
        }
        return defaultValue;
    }

    public void copyIntoIntentExtras(Activity action) {
        for (String name : this.prefs.keySet()) {
            String value = (String) this.prefs.get(name);
            if (value != null) {
                if (name.equals("loglevel")) {
                    LOG.setLogLevel(value);
                } else if (name.equals("splashscreen")) {
                    int resource = action.getResources().getIdentifier(value, "drawable", action.getClass().getPackage().getName());
                    if (resource == 0) {
                        resource = action.getResources().getIdentifier(value, "drawable", action.getPackageName());
                    }
                    action.getIntent().putExtra(name, resource);
                } else if (name.equals("backgroundcolor")) {
                    action.getIntent().putExtra(name, (int) Long.decode(value).longValue());
                } else if (name.equals("loadurltimeoutvalue")) {
                    action.getIntent().putExtra(name, Integer.decode(value).intValue());
                } else if (name.equals("splashscreendelay")) {
                    action.getIntent().putExtra(name, Integer.decode(value).intValue());
                } else if (name.equals("keeprunning")) {
                    action.getIntent().putExtra(name, Boolean.parseBoolean(value));
                } else if (name.equals("inappbrowserstorageenabled")) {
                    action.getIntent().putExtra(name, Boolean.parseBoolean(value));
                } else if (name.equals("disallowoverscroll")) {
                    action.getIntent().putExtra(name, Boolean.parseBoolean(value));
                } else {
                    action.getIntent().putExtra(name, value);
                }
            }
        }
        if (this.preferencesBundleExtras == null) {
            this.preferencesBundleExtras = action.getIntent().getExtras();
        }
    }
}
