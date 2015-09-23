package org.apache.cordova.file;

import android.net.Uri;
import java.util.List;

public class LocalFilesystemURL {
    public static final String FILESYSTEM_PROTOCOL = "cdvfile";
    Uri URL;
    String filesystemName;
    String fullPath;

    public LocalFilesystemURL(Uri URL) {
        this.URL = URL;
        this.filesystemName = filesystemNameForLocalURL(URL);
        this.fullPath = fullPathForLocalURL(URL);
    }

    private String fullPathForLocalURL(Uri URL) {
        if (FILESYSTEM_PROTOCOL.equals(URL.getScheme()) && "localhost".equals(URL.getHost())) {
            String path = URL.getPath();
            if (URL.getQuery() != null) {
                path = path + "?" + URL.getQuery();
            }
            return path.substring(path.indexOf(47, 1));
        } else if ("content".equals(URL.getScheme())) {
            return Uri.encode('/' + URL.getHost() + URL.getPath(), "/");
        } else {
            return null;
        }
    }

    private String filesystemNameForLocalURL(Uri URL) {
        if (FILESYSTEM_PROTOCOL.equals(URL.getScheme()) && "localhost".equals(URL.getHost())) {
            List<String> pathComponents = URL.getPathSegments();
            if (pathComponents == null || pathComponents.size() <= 0) {
                return null;
            }
            return (String) pathComponents.get(0);
        } else if ("content".equals(URL.getScheme())) {
            return "content";
        } else {
            return null;
        }
    }

    public LocalFilesystemURL(String strURL) {
        this(Uri.parse(strURL));
    }

    public String toString() {
        return "cdvfile://localhost/" + this.filesystemName + this.fullPath;
    }
}
