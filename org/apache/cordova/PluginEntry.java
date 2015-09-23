package org.apache.cordova;

import java.util.List;

public class PluginEntry {
    public boolean onload;
    public CordovaPlugin plugin;
    public String pluginClass;
    public String service;
    private List<String> urlFilters;

    public PluginEntry(String service, CordovaPlugin plugin) {
        this(service, plugin.getClass().getName(), true, plugin, null);
    }

    public PluginEntry(String service, String pluginClass, boolean onload) {
        this(service, pluginClass, onload, null, null);
    }

    @Deprecated
    public PluginEntry(String service, String pluginClass, boolean onload, List<String> urlFilters) {
        this.service = service;
        this.pluginClass = pluginClass;
        this.onload = onload;
        this.urlFilters = urlFilters;
        this.plugin = null;
    }

    private PluginEntry(String service, String pluginClass, boolean onload, CordovaPlugin plugin, List<String> urlFilters) {
        this.service = service;
        this.pluginClass = pluginClass;
        this.onload = onload;
        this.urlFilters = urlFilters;
        this.plugin = plugin;
    }

    public List<String> getUrlFilters() {
        return this.urlFilters;
    }
}
