package org.apache.cordova.mediacapture;

import android.net.Uri;
import android.webkit.MimeTypeMap;
import java.util.Locale;
import org.apache.cordova.CordovaInterface;

public class FileHelper {
    public static String getMimeTypeForExtension(String path) {
        String extension = path;
        int lastDot = extension.lastIndexOf(46);
        if (lastDot != -1) {
            extension = extension.substring(lastDot + 1);
        }
        extension = extension.toLowerCase(Locale.getDefault());
        if (extension.equals("3ga")) {
            return "audio/3gpp";
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    public static String getMimeType(Uri uri, CordovaInterface cordova) {
        if ("content".equals(uri.getScheme())) {
            return cordova.getActivity().getContentResolver().getType(uri);
        }
        return getMimeTypeForExtension(uri.getPath());
    }
}
