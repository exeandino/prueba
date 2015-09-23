package org.apache.cordova.camera;

import android.database.Cursor;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.LOG;

public class FileHelper {
    private static final String LOG_TAG = "FileUtils";
    private static final String _DATA = "_data";

    public static String getRealPath(String uriString, CordovaInterface cordova) {
        String realPath;
        if (uriString.startsWith("content://")) {
            Cursor cursor = cordova.getActivity().managedQuery(Uri.parse(uriString), new String[]{_DATA}, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(_DATA);
            cursor.moveToFirst();
            realPath = cursor.getString(column_index);
            if (realPath != null) {
                return realPath;
            }
            LOG.m14e(LOG_TAG, "Could get real path for URI string %s", uriString);
            return realPath;
        } else if (!uriString.startsWith("file://")) {
            return uriString;
        } else {
            realPath = uriString.substring(7);
            if (!realPath.startsWith("/android_asset/")) {
                return realPath;
            }
            LOG.m14e(LOG_TAG, "Cannot get real path for URI string %s because it is a file:///android_asset/ URI.", uriString);
            return null;
        }
    }

    public static String getRealPath(Uri uri, CordovaInterface cordova) {
        return getRealPath(uri.toString(), cordova);
    }

    public static InputStream getInputStreamFromUriString(String uriString, CordovaInterface cordova) throws IOException {
        if (uriString.startsWith("content")) {
            return cordova.getActivity().getContentResolver().openInputStream(Uri.parse(uriString));
        } else if (!uriString.startsWith("file://")) {
            return new FileInputStream(getRealPath(uriString, cordova));
        } else {
            int question = uriString.indexOf("?");
            if (question > -1) {
                uriString = uriString.substring(0, question);
            }
            if (!uriString.startsWith("file:///android_asset/")) {
                return new FileInputStream(getRealPath(uriString, cordova));
            }
            return cordova.getActivity().getAssets().open(Uri.parse(uriString).getPath().substring(15));
        }
    }

    public static String stripFileProtocol(String uriString) {
        if (uriString.startsWith("file://")) {
            return uriString.substring(7);
        }
        return uriString;
    }

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

    public static String getMimeType(String uriString, CordovaInterface cordova) {
        Uri uri = Uri.parse(uriString);
        if (uriString.startsWith("content://")) {
            return cordova.getActivity().getContentResolver().getType(uri);
        }
        return getMimeTypeForExtension(uri.getPath());
    }
}
