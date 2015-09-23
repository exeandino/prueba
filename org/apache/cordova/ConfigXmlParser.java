package org.apache.cordova;

import android.app.Activity;
import android.content.res.XmlResourceParser;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;
import org.xmlpull.v1.XmlPullParserException;

public class ConfigXmlParser {
    private static String TAG;
    private Whitelist externalWhitelist;
    private Whitelist internalWhitelist;
    private String launchUrl;
    private ArrayList<PluginEntry> pluginEntries;
    private CordovaPreferences prefs;

    public ConfigXmlParser() {
        this.launchUrl = "file:///android_asset/www/index.html";
        this.prefs = new CordovaPreferences();
        this.internalWhitelist = new Whitelist();
        this.externalWhitelist = new Whitelist();
        this.pluginEntries = new ArrayList(20);
    }

    static {
        TAG = "ConfigXmlParser";
    }

    public Whitelist getInternalWhitelist() {
        return this.internalWhitelist;
    }

    public Whitelist getExternalWhitelist() {
        return this.externalWhitelist;
    }

    public CordovaPreferences getPreferences() {
        return this.prefs;
    }

    public ArrayList<PluginEntry> getPluginEntries() {
        return this.pluginEntries;
    }

    public String getLaunchUrl() {
        return this.launchUrl;
    }

    public void parse(Activity action) {
        int id = action.getResources().getIdentifier("config", "xml", action.getClass().getPackage().getName());
        if (id == 0) {
            id = action.getResources().getIdentifier("config", "xml", action.getPackageName());
            if (id == 0) {
                LOG.m12e(TAG, "res/xml/config.xml is missing!");
                return;
            }
        }
        parse(action.getResources().getXml(id));
    }

    public void parse(XmlResourceParser xml) {
        int eventType = -1;
        String service = "";
        String pluginClass = "";
        String paramType = "";
        boolean onload = false;
        boolean insideFeature = false;
        ArrayList<String> urlMap = null;
        this.internalWhitelist.addWhiteListEntry("file:///*", false);
        this.internalWhitelist.addWhiteListEntry("content:///*", false);
        this.internalWhitelist.addWhiteListEntry("data:*", false);
        while (eventType != 1) {
            if (eventType == 2) {
                String strNode = xml.getName();
                if (strNode.equals("url-filter")) {
                    Log.w(TAG, "Plugin " + service + " is using deprecated tag <url-filter>");
                    if (urlMap == null) {
                        ArrayList<String> arrayList = new ArrayList(2);
                    }
                    urlMap.add(xml.getAttributeValue(null, "value"));
                } else {
                    if (strNode.equals("feature")) {
                        insideFeature = true;
                        service = xml.getAttributeValue(null, "name");
                    } else {
                        if (insideFeature) {
                            if (strNode.equals("param")) {
                                paramType = xml.getAttributeValue(null, "name");
                                if (paramType.equals(NotificationCompatApi21.CATEGORY_SERVICE)) {
                                    service = xml.getAttributeValue(null, "value");
                                } else {
                                    if (!paramType.equals("package")) {
                                        if (!paramType.equals("android-package")) {
                                            if (paramType.equals("onload")) {
                                                onload = "true".equals(xml.getAttributeValue(null, "value"));
                                            }
                                        }
                                    }
                                    pluginClass = xml.getAttributeValue(null, "value");
                                }
                            }
                        }
                        if (strNode.equals("access")) {
                            String origin = xml.getAttributeValue(null, "origin");
                            String subdomains = xml.getAttributeValue(null, "subdomains");
                            boolean external = xml.getAttributeValue(null, "launch-external") != null;
                            if (origin != null) {
                                Whitelist whitelist;
                                boolean z;
                                if (external) {
                                    whitelist = this.externalWhitelist;
                                    if (subdomains != null) {
                                        if (subdomains.compareToIgnoreCase("true") == 0) {
                                            z = true;
                                            whitelist.addWhiteListEntry(origin, z);
                                        }
                                    }
                                    z = false;
                                    whitelist.addWhiteListEntry(origin, z);
                                } else {
                                    if ("*".equals(origin)) {
                                        this.internalWhitelist.addWhiteListEntry("http://*/*", false);
                                        this.internalWhitelist.addWhiteListEntry("https://*/*", false);
                                    } else {
                                        whitelist = this.internalWhitelist;
                                        if (subdomains != null) {
                                            if (subdomains.compareToIgnoreCase("true") == 0) {
                                                z = true;
                                                whitelist.addWhiteListEntry(origin, z);
                                            }
                                        }
                                        z = false;
                                        whitelist.addWhiteListEntry(origin, z);
                                    }
                                }
                            }
                        } else {
                            if (strNode.equals("preference")) {
                                this.prefs.set(xml.getAttributeValue(null, "name").toLowerCase(Locale.ENGLISH), xml.getAttributeValue(null, "value"));
                            } else {
                                if (strNode.equals("content")) {
                                    String src = xml.getAttributeValue(null, "src");
                                    if (src != null) {
                                        setStartUrl(src);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (eventType == 3) {
                if (xml.getName().equals("feature")) {
                    this.pluginEntries.add(new PluginEntry(service, pluginClass, onload, urlMap));
                    service = "";
                    pluginClass = "";
                    insideFeature = false;
                    onload = false;
                    urlMap = null;
                }
            }
            try {
                eventType = xml.next();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e2) {
                e2.printStackTrace();
            }
        }
    }

    private void setStartUrl(String src) {
        if (Pattern.compile("^[a-z-]+://").matcher(src).find()) {
            this.launchUrl = src;
            return;
        }
        if (src.charAt(0) == '/') {
            src = src.substring(1);
        }
        this.launchUrl = "file:///android_asset/www/" + src;
    }
}
