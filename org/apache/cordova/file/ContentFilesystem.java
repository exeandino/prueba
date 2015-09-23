package org.apache.cordova.file;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Images.Media;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CordovaResourceApi.OpenForReadResult;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginManager;
import org.apache.cordova.file.Filesystem.ReadFileCallback;
import org.apache.cordova.globalization.Globalization;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ContentFilesystem extends Filesystem {
    private CordovaInterface cordova;
    private CordovaResourceApi resourceApi;

    public ContentFilesystem(String name, CordovaInterface cordova, CordovaWebView webView) {
        this.name = name;
        this.cordova = cordova;
        Class webViewClass = webView.getClass();
        PluginManager pm = null;
        try {
            pm = (PluginManager) webViewClass.getMethod("getPluginManager", new Class[0]).invoke(webView, new Object[0]);
        } catch (NoSuchMethodException e) {
        } catch (IllegalAccessException e2) {
        } catch (InvocationTargetException e3) {
        }
        if (pm == null) {
            try {
                pm = (PluginManager) webViewClass.getField("pluginManager").get(webView);
            } catch (NoSuchFieldException e4) {
            } catch (IllegalAccessException e5) {
            }
        }
        this.resourceApi = new CordovaResourceApi(webView.getContext(), pm);
    }

    public JSONObject getEntryForLocalURL(LocalFilesystemURL inputURL) throws IOException {
        JSONObject makeEntryForURL;
        if ("/".equals(inputURL.fullPath)) {
            try {
                makeEntryForURL = Filesystem.makeEntryForURL(inputURL, Boolean.valueOf(true), inputURL.URL.toString());
            } catch (JSONException e) {
                throw new IOException();
            }
        }
        Cursor cursor = openCursorForURL(inputURL);
        String filePath = null;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    filePath = filesystemPathForCursor(cursor);
                    if (filePath == null) {
                        filePath = inputURL.URL.toString();
                    } else {
                        filePath = "file://" + filePath;
                    }
                    try {
                        makeEntryForURL = Filesystem.makeEntryForPath(inputURL.fullPath, inputURL.filesystemName, Boolean.valueOf(false), filePath);
                    } catch (JSONException e2) {
                        throw new IOException();
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        throw new FileNotFoundException();
        return makeEntryForURL;
    }

    public JSONObject getFileForLocalURL(LocalFilesystemURL inputURL, String fileName, JSONObject options, boolean directory) throws IOException, TypeMismatchException, JSONException {
        if (options == null || !options.optBoolean("create")) {
            LocalFilesystemURL requestedURL = new LocalFilesystemURL(Uri.withAppendedPath(inputURL.URL, fileName));
            File fp = new File(filesystemPathForURL(requestedURL));
            if (fp.exists()) {
                if (directory) {
                    if (fp.isFile()) {
                        throw new TypeMismatchException("path doesn't exist or is file");
                    }
                } else if (fp.isDirectory()) {
                    throw new TypeMismatchException("path doesn't exist or is directory");
                }
                return Filesystem.makeEntryForPath(requestedURL.fullPath, requestedURL.filesystemName, Boolean.valueOf(directory), Uri.fromFile(fp).toString());
            }
            throw new FileNotFoundException("path does not exist");
        }
        throw new IOException("Cannot create content url");
    }

    public boolean removeFileAtLocalURL(LocalFilesystemURL inputURL) throws NoModificationAllowedException {
        File file = new File(filesystemPathForURL(inputURL));
        try {
            this.cordova.getActivity().getContentResolver().delete(Media.EXTERNAL_CONTENT_URI, "_data = ?", new String[]{filesystemPathForURL(inputURL)});
        } catch (UnsupportedOperationException e) {
        }
        return file.delete();
    }

    public boolean recursiveRemoveFileAtLocalURL(LocalFilesystemURL inputURL) throws NoModificationAllowedException {
        throw new NoModificationAllowedException("Cannot remove content url");
    }

    public JSONArray readEntriesAtLocalURL(LocalFilesystemURL inputURL) throws FileNotFoundException {
        return null;
    }

    public JSONObject getFileMetadataForLocalURL(LocalFilesystemURL inputURL) throws FileNotFoundException {
        Integer size = null;
        Integer lastModified = null;
        Cursor cursor = openCursorForURL(inputURL);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    size = resourceSizeForCursor(cursor);
                    lastModified = lastModifiedDateForCursor(cursor);
                    JSONObject metadata = new JSONObject();
                    try {
                        metadata.put("size", size);
                        metadata.put(Globalization.TYPE, this.resourceApi.getMimeType(inputURL.URL));
                        metadata.put("name", inputURL.filesystemName);
                        metadata.put("fullPath", inputURL.fullPath);
                        metadata.put("lastModifiedDate", lastModified);
                        return metadata;
                    } catch (JSONException e) {
                        return null;
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        throw new FileNotFoundException();
    }

    public JSONObject copyFileToURL(LocalFilesystemURL destURL, String newName, Filesystem srcFs, LocalFilesystemURL srcURL, boolean move) throws IOException, InvalidModificationException, JSONException, NoModificationAllowedException, FileExistsException {
        if (!LocalFilesystem.class.isInstance(srcFs)) {
            return super.copyFileToURL(destURL, newName, srcFs, srcURL, move);
        }
        LocalFilesystemURL destinationURL = makeDestinationURL(newName, srcURL, destURL);
        OutputStream os = this.resourceApi.openOutputStream(destURL.URL);
        OpenForReadResult ofrr = this.resourceApi.openForRead(srcURL.URL);
        if (!move || srcFs.canRemoveFileAtLocalURL(srcURL)) {
            try {
                this.resourceApi.copyResource(ofrr, os);
                if (move) {
                    srcFs.removeFileAtLocalURL(srcURL);
                }
                return Filesystem.makeEntryForURL(destinationURL, Boolean.valueOf(false), destinationURL.URL.toString());
            } catch (IOException e) {
                throw new IOException("Cannot read file at source URL");
            }
        }
        throw new NoModificationAllowedException("Cannot move file at source URL");
    }

    public void readFileAtURL(LocalFilesystemURL inputURL, long start, long end, ReadFileCallback readFileCallback) throws IOException {
        OpenForReadResult ofrr = this.resourceApi.openForRead(inputURL.URL);
        if (end < 0) {
            end = ofrr.length;
        }
        long numBytesToRead = end - start;
        if (start > 0) {
            try {
                ofrr.inputStream.skip(start);
            } catch (Throwable th) {
                ofrr.inputStream.close();
            }
        }
        readFileCallback.handleData(new LimitedInputStream(ofrr.inputStream, numBytesToRead), ofrr.mimeType);
        ofrr.inputStream.close();
    }

    public long writeToFileAtURL(LocalFilesystemURL inputURL, String data, int offset, boolean isBinary) throws NoModificationAllowedException {
        throw new NoModificationAllowedException("Couldn't write to file given its content URI");
    }

    public long truncateFileAtURL(LocalFilesystemURL inputURL, long size) throws NoModificationAllowedException {
        throw new NoModificationAllowedException("Couldn't truncate file given its content URI");
    }

    protected Cursor openCursorForURL(LocalFilesystemURL url) {
        return this.cordova.getActivity().getContentResolver().query(url.URL, null, null, null, null);
    }

    protected String filesystemPathForCursor(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(new String[]{"_data"}[0]);
        if (columnIndex != -1) {
            return cursor.getString(columnIndex);
        }
        return null;
    }

    protected Integer resourceSizeForCursor(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex("_size");
        if (columnIndex != -1) {
            String sizeStr = cursor.getString(columnIndex);
            if (sizeStr != null) {
                return Integer.valueOf(Integer.parseInt(sizeStr, 10));
            }
        }
        return null;
    }

    protected Integer lastModifiedDateForCursor(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(new String[]{"date_modified"}[0]);
        if (columnIndex != -1) {
            String dateStr = cursor.getString(columnIndex);
            if (dateStr != null) {
                return Integer.valueOf(Integer.parseInt(dateStr, 10));
            }
        }
        return null;
    }

    public String filesystemPathForURL(LocalFilesystemURL url) {
        Cursor cursor = openCursorForURL(url);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    String filesystemPathForCursor = filesystemPathForCursor(cursor);
                    if (cursor == null) {
                        return filesystemPathForCursor;
                    }
                    cursor.close();
                    return filesystemPathForCursor;
                }
            } catch (Throwable th) {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        return null;
    }

    public LocalFilesystemURL URLforFilesystemPath(String path) {
        return null;
    }

    public boolean canRemoveFileAtLocalURL(LocalFilesystemURL inputURL) {
        return new File(filesystemPathForURL(inputURL)).exists();
    }

    OutputStream getOutputStreamForURL(LocalFilesystemURL inputURL) throws IOException {
        return this.resourceApi.openOutputStream(inputURL.URL);
    }
}
