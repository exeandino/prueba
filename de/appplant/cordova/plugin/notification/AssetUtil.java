package de.appplant.cordova.plugin.notification;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy.Builder;
import android.util.Log;
import com.squareup.okhttp.internal.http.HttpTransport;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

class AssetUtil {
    private static final String DEFAULT_SOUND = "res://platform_default";
    private static final String STORAGE_FOLDER = "/localnotification";
    private final Context context;

    private AssetUtil(Context context) {
        this.context = context;
    }

    static AssetUtil getInstance(Context context) {
        return new AssetUtil(context);
    }

    Uri parseSound(String path) {
        if (path == null || path.isEmpty()) {
            return Uri.EMPTY;
        }
        if (path.equalsIgnoreCase(DEFAULT_SOUND)) {
            return RingtoneManager.getDefaultUri(2);
        }
        return parse(path);
    }

    Uri parse(String path) {
        if (path.startsWith("res:")) {
            return getUriForResourcePath(path);
        }
        if (path.startsWith("file:///")) {
            return getUriFromPath(path);
        }
        if (path.startsWith("file://")) {
            return getUriFromAsset(path);
        }
        if (path.startsWith("http")) {
            return getUriFromRemote(path);
        }
        return Uri.EMPTY;
    }

    private Uri getUriFromPath(String path) {
        File file = new File(path.replaceFirst("file://", ""));
        if (file.exists()) {
            return Uri.fromFile(file);
        }
        Log.e("Asset", "File not found: " + file.getAbsolutePath());
        return Uri.EMPTY;
    }

    private Uri getUriFromAsset(String path) {
        File dir = this.context.getExternalCacheDir();
        if (dir == null) {
            Log.e("Asset", "Missing external cache dir");
            return Uri.EMPTY;
        }
        String resPath = path.replaceFirst("file:/", "www");
        String fileName = resPath.substring(resPath.lastIndexOf(47) + 1);
        String storage = dir.toString() + STORAGE_FOLDER;
        File file = new File(storage, fileName);
        new File(storage).mkdir();
        try {
            AssetManager assets = this.context.getAssets();
            FileOutputStream outStream = new FileOutputStream(file);
            copyFile(assets.open(resPath), outStream);
            outStream.flush();
            outStream.close();
            return Uri.fromFile(file);
        } catch (Exception e) {
            Log.e("Asset", "File not found: assets/" + resPath);
            e.printStackTrace();
            return Uri.EMPTY;
        }
    }

    private Uri getUriForResourcePath(String path) {
        File dir = this.context.getExternalCacheDir();
        if (dir == null) {
            Log.e("Asset", "Missing external cache dir");
            return Uri.EMPTY;
        }
        String resPath = path.replaceFirst("res://", "");
        int resId = getResIdForDrawable(resPath);
        if (resId == 0) {
            Log.e("Asset", "File not found: " + resPath);
            return Uri.EMPTY;
        }
        String resName = extractResourceName(resPath);
        String extName = extractResourceExtension(resPath);
        String storage = dir.toString() + STORAGE_FOLDER;
        File file = new File(storage, resName + extName);
        new File(storage).mkdir();
        try {
            Resources res = this.context.getResources();
            FileOutputStream outStream = new FileOutputStream(file);
            copyFile(res.openRawResource(resId), outStream);
            outStream.flush();
            outStream.close();
            return Uri.fromFile(file);
        } catch (Exception e) {
            e.printStackTrace();
            return Uri.EMPTY;
        }
    }

    private Uri getUriFromRemote(String path) {
        File dir = this.context.getExternalCacheDir();
        if (dir == null) {
            Log.e("Asset", "Missing external cache dir");
            return Uri.EMPTY;
        }
        String resName = extractResourceName(path);
        String extName = extractResourceExtension(path);
        String storage = dir.toString() + STORAGE_FOLDER;
        File file = new File(storage, resName + extName);
        new File(storage).mkdir();
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(path).openConnection();
            StrictMode.setThreadPolicy(new Builder().permitAll().build());
            connection.setRequestProperty("Connection", "close");
            connection.setConnectTimeout(5000);
            connection.connect();
            InputStream input = connection.getInputStream();
            FileOutputStream outStream = new FileOutputStream(file);
            copyFile(input, outStream);
            outStream.flush();
            outStream.close();
            return Uri.fromFile(file);
        } catch (MalformedURLException e) {
            Log.e("Asset", "Incorrect URL");
            e.printStackTrace();
            return Uri.EMPTY;
        } catch (FileNotFoundException e2) {
            Log.e("Asset", "Failed to create new File from HTTP Content");
            e2.printStackTrace();
            return Uri.EMPTY;
        } catch (IOException e3) {
            Log.e("Asset", "No Input can be created from http Stream");
            e3.printStackTrace();
            return Uri.EMPTY;
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[HttpTransport.DEFAULT_CHUNK_LENGTH];
        while (true) {
            int read = in.read(buffer);
            if (read != -1) {
                out.write(buffer, 0, read);
            } else {
                return;
            }
        }
    }

    int getResIdForDrawable(String resPath) {
        int resId = getResIdForDrawable(getPkgName(), resPath);
        if (resId == 0) {
            return getResIdForDrawable("android", resPath);
        }
        return resId;
    }

    int getResIdForDrawable(String clsName, String resPath) {
        int resId = 0;
        try {
            resId = ((Integer) Class.forName(clsName + ".R$drawable").getDeclaredField(extractResourceName(resPath)).get(Integer.class)).intValue();
        } catch (Exception e) {
        }
        return resId;
    }

    Bitmap getIconFromDrawable(String drawable) {
        Resources res = this.context.getResources();
        int iconId = getResIdForDrawable(getPkgName(), drawable);
        if (iconId == 0) {
            iconId = getResIdForDrawable("android", drawable);
        }
        if (iconId == 0) {
            iconId = 17301569;
        }
        return BitmapFactory.decodeResource(res, iconId);
    }

    Bitmap getIconFromUri(Uri uri) throws IOException {
        return BitmapFactory.decodeStream(this.context.getContentResolver().openInputStream(uri));
    }

    private String extractResourceName(String resPath) {
        String drawable = resPath;
        if (drawable.contains("/")) {
            drawable = drawable.substring(drawable.lastIndexOf(47) + 1);
        }
        if (resPath.contains(".")) {
            return drawable.substring(0, drawable.lastIndexOf(46));
        }
        return drawable;
    }

    private String extractResourceExtension(String resPath) {
        String extName = "png";
        if (resPath.contains(".")) {
            return resPath.substring(resPath.lastIndexOf(46));
        }
        return extName;
    }

    private String getPkgName() {
        return this.context.getPackageName();
    }
}
