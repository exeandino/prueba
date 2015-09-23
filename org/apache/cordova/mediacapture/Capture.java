package org.apache.cordova.mediacapture;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Environment;
import android.provider.MediaStore.Images.Media;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.apache.cordova.file.FileUtils;
import org.apache.cordova.file.LocalFilesystemURL;
import org.apache.cordova.globalization.Globalization;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Capture extends CordovaPlugin {
    private static final String AUDIO_3GPP = "audio/3gpp";
    private static final int CAPTURE_AUDIO = 0;
    private static final int CAPTURE_IMAGE = 1;
    private static final int CAPTURE_INTERNAL_ERR = 0;
    private static final int CAPTURE_NO_MEDIA_FILES = 3;
    private static final int CAPTURE_VIDEO = 2;
    private static final String IMAGE_JPEG = "image/jpeg";
    private static final String LOG_TAG = "Capture";
    private static final String VIDEO_3GPP = "video/3gpp";
    private static final String VIDEO_MP4 = "video/mp4";
    private CallbackContext callbackContext;
    private int duration;
    private long limit;
    private int numPics;
    private JSONArray results;

    /* renamed from: org.apache.cordova.mediacapture.Capture.1 */
    class C01751 implements Runnable {
        final /* synthetic */ Intent val$intent;
        final /* synthetic */ Capture val$that;

        C01751(Intent intent, Capture capture) {
            this.val$intent = intent;
            this.val$that = capture;
        }

        public void run() {
            Capture.this.results.put(Capture.this.createMediaFile(this.val$intent.getData()));
            if (((long) Capture.this.results.length()) >= Capture.this.limit) {
                this.val$that.callbackContext.sendPluginResult(new PluginResult(Status.OK, Capture.this.results));
            } else {
                Capture.this.captureAudio();
            }
        }
    }

    /* renamed from: org.apache.cordova.mediacapture.Capture.2 */
    class C01762 implements Runnable {
        final /* synthetic */ Capture val$that;

        C01762(Capture capture) {
            this.val$that = capture;
        }

        public void run() {
            try {
                Uri uri;
                ContentValues values = new ContentValues();
                values.put("mime_type", Capture.IMAGE_JPEG);
                try {
                    uri = this.val$that.cordova.getActivity().getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values);
                } catch (UnsupportedOperationException e) {
                    LOG.m9d(Capture.LOG_TAG, "Can't write to external media storage.");
                    try {
                        uri = this.val$that.cordova.getActivity().getContentResolver().insert(Media.INTERNAL_CONTENT_URI, values);
                    } catch (UnsupportedOperationException e2) {
                        LOG.m9d(Capture.LOG_TAG, "Can't write to internal media storage.");
                        this.val$that.fail(Capture.this.createErrorObject(Capture.CAPTURE_INTERNAL_ERR, "Error capturing image - no media storage found."));
                        return;
                    }
                }
                FileInputStream fis = new FileInputStream(Capture.this.getTempDirectoryPath() + "/Capture.jpg");
                OutputStream os = this.val$that.cordova.getActivity().getContentResolver().openOutputStream(uri);
                byte[] buffer = new byte[AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD];
                while (true) {
                    int len = fis.read(buffer);
                    if (len == -1) {
                        break;
                    }
                    os.write(buffer, Capture.CAPTURE_INTERNAL_ERR, len);
                }
                os.flush();
                os.close();
                fis.close();
                Capture.this.results.put(Capture.this.createMediaFile(uri));
                Capture.this.checkForDuplicateImage();
                if (((long) Capture.this.results.length()) >= Capture.this.limit) {
                    this.val$that.callbackContext.sendPluginResult(new PluginResult(Status.OK, Capture.this.results));
                } else {
                    Capture.this.captureImage();
                }
            } catch (IOException e3) {
                e3.printStackTrace();
                this.val$that.fail(Capture.this.createErrorObject(Capture.CAPTURE_INTERNAL_ERR, "Error capturing image."));
            }
        }
    }

    /* renamed from: org.apache.cordova.mediacapture.Capture.3 */
    class C01773 implements Runnable {
        final /* synthetic */ Intent val$intent;
        final /* synthetic */ Capture val$that;

        C01773(Intent intent, Capture capture) {
            this.val$intent = intent;
            this.val$that = capture;
        }

        public void run() {
            Uri data = null;
            if (this.val$intent != null) {
                data = this.val$intent.getData();
            }
            if (data == null) {
                data = Uri.fromFile(new File(Capture.this.getTempDirectoryPath(), "Capture.avi"));
            }
            if (data == null) {
                this.val$that.fail(Capture.this.createErrorObject(Capture.CAPTURE_NO_MEDIA_FILES, "Error: data is null"));
                return;
            }
            Capture.this.results.put(Capture.this.createMediaFile(data));
            if (((long) Capture.this.results.length()) >= Capture.this.limit) {
                this.val$that.callbackContext.sendPluginResult(new PluginResult(Status.OK, Capture.this.results));
            } else {
                Capture.this.captureVideo(Capture.this.duration);
            }
        }
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        this.limit = 1;
        this.duration = CAPTURE_INTERNAL_ERR;
        this.results = new JSONArray();
        JSONObject options = args.optJSONObject(CAPTURE_INTERNAL_ERR);
        if (options != null) {
            this.limit = options.optLong("limit", 1);
            this.duration = options.optInt("duration", CAPTURE_INTERNAL_ERR);
        }
        if (action.equals("getFormatData")) {
            callbackContext.success(getFormatData(args.getString(CAPTURE_INTERNAL_ERR), args.getString(CAPTURE_IMAGE)));
            return true;
        } else if (action.equals("captureAudio")) {
            captureAudio();
            return true;
        } else if (action.equals("captureImage")) {
            captureImage();
            return true;
        } else if (!action.equals("captureVideo")) {
            return false;
        } else {
            captureVideo(this.duration);
            return true;
        }
    }

    private JSONObject getFormatData(String filePath, String mimeType) throws JSONException {
        Uri fileUrl;
        if (filePath.startsWith("file:")) {
            fileUrl = Uri.parse(filePath);
        } else {
            fileUrl = Uri.fromFile(new File(filePath));
        }
        JSONObject obj = new JSONObject();
        obj.put("height", CAPTURE_INTERNAL_ERR);
        obj.put("width", CAPTURE_INTERNAL_ERR);
        obj.put("bitrate", CAPTURE_INTERNAL_ERR);
        obj.put("duration", CAPTURE_INTERNAL_ERR);
        obj.put("codecs", "");
        if (mimeType == null || mimeType.equals("") || "null".equals(mimeType)) {
            mimeType = FileHelper.getMimeType(fileUrl, this.cordova);
        }
        Log.d(LOG_TAG, "Mime type = " + mimeType);
        if (mimeType.equals(IMAGE_JPEG) || filePath.endsWith(".jpg")) {
            return getImageData(fileUrl, obj);
        }
        if (mimeType.endsWith(AUDIO_3GPP)) {
            return getAudioVideoData(filePath, obj, false);
        }
        if (mimeType.equals(VIDEO_3GPP) || mimeType.equals(VIDEO_MP4)) {
            return getAudioVideoData(filePath, obj, true);
        }
        return obj;
    }

    private JSONObject getImageData(Uri fileUrl, JSONObject obj) throws JSONException {
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(fileUrl.getPath(), options);
        obj.put("height", options.outHeight);
        obj.put("width", options.outWidth);
        return obj;
    }

    private JSONObject getAudioVideoData(String filePath, JSONObject obj, boolean video) throws JSONException {
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(filePath);
            player.prepare();
            obj.put("duration", player.getDuration() / 1000);
            if (video) {
                obj.put("height", player.getVideoHeight());
                obj.put("width", player.getVideoWidth());
            }
        } catch (IOException e) {
            Log.d(LOG_TAG, "Error: loading video file");
        }
        return obj;
    }

    private void captureAudio() {
        this.cordova.startActivityForResult(this, new Intent("android.provider.MediaStore.RECORD_SOUND"), CAPTURE_INTERNAL_ERR);
    }

    private String getTempDirectoryPath() {
        File cache = this.cordova.getActivity().getCacheDir();
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    private void captureImage() {
        this.numPics = queryImgDB(whichContentStore()).getCount();
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        File photo = new File(getTempDirectoryPath(), "Capture.jpg");
        try {
            createWritableFile(photo);
            intent.putExtra("output", Uri.fromFile(photo));
            this.cordova.startActivityForResult(this, intent, CAPTURE_IMAGE);
        } catch (IOException ex) {
            fail(createErrorObject(CAPTURE_INTERNAL_ERR, ex.toString()));
        }
    }

    private static void createWritableFile(File file) throws IOException {
        file.createNewFile();
        file.setWritable(true, false);
    }

    private void captureVideo(int duration) {
        Intent intent = new Intent("android.media.action.VIDEO_CAPTURE");
        if (VERSION.SDK_INT > 7) {
            intent.putExtra("android.intent.extra.durationLimit", duration);
        }
        this.cordova.startActivityForResult(this, intent, CAPTURE_VIDEO);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == -1) {
            if (requestCode == 0) {
                this.cordova.getThreadPool().execute(new C01751(intent, this));
            } else if (requestCode == CAPTURE_IMAGE) {
                this.cordova.getThreadPool().execute(new C01762(this));
            } else if (requestCode == CAPTURE_VIDEO) {
                this.cordova.getThreadPool().execute(new C01773(intent, this));
            }
        } else if (resultCode == 0) {
            if (this.results.length() > 0) {
                this.callbackContext.sendPluginResult(new PluginResult(Status.OK, this.results));
            } else {
                fail(createErrorObject(CAPTURE_NO_MEDIA_FILES, "Canceled."));
            }
        } else if (this.results.length() > 0) {
            this.callbackContext.sendPluginResult(new PluginResult(Status.OK, this.results));
        } else {
            fail(createErrorObject(CAPTURE_NO_MEDIA_FILES, "Did not complete!"));
        }
    }

    private JSONObject createMediaFile(Uri data) {
        File fp = this.webView.getResourceApi().mapUriToFile(data);
        JSONObject obj = new JSONObject();
        Class webViewClass = this.webView.getClass();
        PluginManager pm = null;
        try {
            pm = (PluginManager) webViewClass.getMethod("getPluginManager", new Class[CAPTURE_INTERNAL_ERR]).invoke(this.webView, new Object[CAPTURE_INTERNAL_ERR]);
        } catch (NoSuchMethodException e) {
        } catch (IllegalAccessException e2) {
        } catch (InvocationTargetException e3) {
        }
        if (pm == null) {
            try {
                pm = (PluginManager) webViewClass.getField("pluginManager").get(this.webView);
            } catch (NoSuchFieldException e4) {
            } catch (IllegalAccessException e5) {
            }
        }
        LocalFilesystemURL url = ((FileUtils) pm.getPlugin("File")).filesystemURLforLocalPath(fp.getAbsolutePath());
        try {
            obj.put("name", fp.getName());
            obj.put("fullPath", fp.toURI().toString());
            if (url != null) {
                obj.put("localURL", url.toString());
            }
            if (!fp.getAbsoluteFile().toString().endsWith(".3gp") && !fp.getAbsoluteFile().toString().endsWith(".3gpp")) {
                obj.put(Globalization.TYPE, FileHelper.getMimeType(Uri.fromFile(fp), this.cordova));
            } else if (data.toString().contains("/audio/")) {
                obj.put(Globalization.TYPE, AUDIO_3GPP);
            } else {
                obj.put(Globalization.TYPE, VIDEO_3GPP);
            }
            obj.put("lastModifiedDate", fp.lastModified());
            obj.put("size", fp.length());
        } catch (JSONException e6) {
            e6.printStackTrace();
        }
        return obj;
    }

    private JSONObject createErrorObject(int code, String message) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("code", code);
            obj.put("message", message);
        } catch (JSONException e) {
        }
        return obj;
    }

    public void fail(JSONObject err) {
        this.callbackContext.error(err);
    }

    private Cursor queryImgDB(Uri contentStore) {
        ContentResolver contentResolver = this.cordova.getActivity().getContentResolver();
        String[] strArr = new String[CAPTURE_IMAGE];
        strArr[CAPTURE_INTERNAL_ERR] = "_id";
        return contentResolver.query(contentStore, strArr, null, null, null);
    }

    private void checkForDuplicateImage() {
        Uri contentStore = whichContentStore();
        Cursor cursor = queryImgDB(contentStore);
        if (cursor.getCount() - this.numPics == CAPTURE_VIDEO) {
            cursor.moveToLast();
            this.cordova.getActivity().getContentResolver().delete(Uri.parse(contentStore + "/" + (Integer.valueOf(cursor.getString(cursor.getColumnIndex("_id"))).intValue() - 1)), null, null);
        }
    }

    private Uri whichContentStore() {
        if (Environment.getExternalStorageState().equals("mounted")) {
            return Media.EXTERNAL_CONTENT_URI;
        }
        return Media.INTERNAL_CONTENT_URI;
    }
}
