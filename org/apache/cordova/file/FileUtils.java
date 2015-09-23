package org.apache.cordova.file;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Base64;
import android.util.Log;
import ch.ti8m.phonegap.plugins.DocumentHandler;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.apache.cordova.file.Filesystem.ReadFileCallback;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FileUtils extends CordovaPlugin {
    public static int ABORT_ERR = 0;
    public static int ENCODING_ERR = 0;
    public static int INVALID_MODIFICATION_ERR = 0;
    public static int INVALID_STATE_ERR = 0;
    private static final String LOG_TAG = "FileUtils";
    public static int NOT_FOUND_ERR;
    public static int NOT_READABLE_ERR;
    public static int NO_MODIFICATION_ALLOWED_ERR;
    public static int PATH_EXISTS_ERR;
    public static int QUOTA_EXCEEDED_ERR;
    public static int SECURITY_ERR;
    public static int SYNTAX_ERR;
    public static int TYPE_MISMATCH_ERR;
    public static int UNKNOWN_ERR;
    private static FileUtils filePlugin;
    private boolean configured;
    private ArrayList<Filesystem> filesystems;

    /* renamed from: org.apache.cordova.file.FileUtils.24 */
    class AnonymousClass24 implements Runnable {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ FileOp val$f;

        AnonymousClass24(FileOp fileOp, CallbackContext callbackContext) {
            this.val$f = fileOp;
            this.val$callbackContext = callbackContext;
        }

        public void run() {
            try {
                this.val$f.run();
            } catch (Exception e) {
                e.printStackTrace();
                if (e instanceof EncodingException) {
                    this.val$callbackContext.error(FileUtils.ENCODING_ERR);
                } else if (e instanceof FileNotFoundException) {
                    this.val$callbackContext.error(FileUtils.NOT_FOUND_ERR);
                } else if (e instanceof FileExistsException) {
                    this.val$callbackContext.error(FileUtils.PATH_EXISTS_ERR);
                } else if (e instanceof NoModificationAllowedException) {
                    this.val$callbackContext.error(FileUtils.NO_MODIFICATION_ALLOWED_ERR);
                } else if (e instanceof InvalidModificationException) {
                    this.val$callbackContext.error(FileUtils.INVALID_MODIFICATION_ERR);
                } else if (e instanceof MalformedURLException) {
                    this.val$callbackContext.error(FileUtils.ENCODING_ERR);
                } else if (e instanceof IOException) {
                    this.val$callbackContext.error(FileUtils.INVALID_MODIFICATION_ERR);
                } else if (e instanceof EncodingException) {
                    this.val$callbackContext.error(FileUtils.ENCODING_ERR);
                } else if (e instanceof TypeMismatchException) {
                    this.val$callbackContext.error(FileUtils.TYPE_MISMATCH_ERR);
                } else {
                    this.val$callbackContext.error(FileUtils.UNKNOWN_ERR);
                }
            }
        }
    }

    private interface FileOp {
        void run() throws Exception;
    }

    /* renamed from: org.apache.cordova.file.FileUtils.10 */
    class AnonymousClass10 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;
        final /* synthetic */ int val$offset;

        AnonymousClass10(String str, int i, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$offset = i;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws FileNotFoundException, IOException, NoModificationAllowedException {
            this.val$callbackContext.sendPluginResult(new PluginResult(Status.OK, (float) FileUtils.this.truncateFile(this.val$fname, (long) this.val$offset)));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.11 */
    class AnonymousClass11 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;

        AnonymousClass11(CallbackContext callbackContext) {
            this.val$callbackContext = callbackContext;
        }

        public void run() throws IOException, JSONException {
            this.val$callbackContext.success(FileUtils.this.requestAllFileSystems());
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.12 */
    class AnonymousClass12 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ int val$fstype;
        final /* synthetic */ long val$size;

        AnonymousClass12(long j, CallbackContext callbackContext, int i) {
            this.val$size = j;
            this.val$callbackContext = callbackContext;
            this.val$fstype = i;
        }

        public void run() throws IOException, JSONException {
            if (this.val$size == 0 || this.val$size <= DirectoryManager.getFreeDiskSpace(true) * PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID) {
                this.val$callbackContext.success(FileUtils.this.requestFileSystem(this.val$fstype));
                return;
            }
            this.val$callbackContext.sendPluginResult(new PluginResult(Status.ERROR, FileUtils.QUOTA_EXCEEDED_ERR));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.13 */
    class AnonymousClass13 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;

        AnonymousClass13(String str, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws IOException, JSONException {
            this.val$callbackContext.success(FileUtils.this.resolveLocalFileSystemURI(this.val$fname));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.14 */
    class AnonymousClass14 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;

        AnonymousClass14(String str, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws FileNotFoundException, JSONException, MalformedURLException {
            this.val$callbackContext.success(FileUtils.this.getFileMetadata(this.val$fname));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.15 */
    class AnonymousClass15 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;

        AnonymousClass15(String str, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws JSONException, IOException {
            this.val$callbackContext.success(FileUtils.this.getParent(this.val$fname));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.16 */
    class AnonymousClass16 implements FileOp {
        final /* synthetic */ JSONArray val$args;
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$dirname;
        final /* synthetic */ String val$path;

        AnonymousClass16(String str, String str2, JSONArray jSONArray, CallbackContext callbackContext) {
            this.val$dirname = str;
            this.val$path = str2;
            this.val$args = jSONArray;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws FileExistsException, IOException, TypeMismatchException, EncodingException, JSONException {
            this.val$callbackContext.success(FileUtils.this.getFile(this.val$dirname, this.val$path, this.val$args.optJSONObject(2), true));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.17 */
    class AnonymousClass17 implements FileOp {
        final /* synthetic */ JSONArray val$args;
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$dirname;
        final /* synthetic */ String val$path;

        AnonymousClass17(String str, String str2, JSONArray jSONArray, CallbackContext callbackContext) {
            this.val$dirname = str;
            this.val$path = str2;
            this.val$args = jSONArray;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws FileExistsException, IOException, TypeMismatchException, EncodingException, JSONException {
            this.val$callbackContext.success(FileUtils.this.getFile(this.val$dirname, this.val$path, this.val$args.optJSONObject(2), false));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.18 */
    class AnonymousClass18 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;

        AnonymousClass18(String str, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws NoModificationAllowedException, InvalidModificationException, MalformedURLException {
            if (FileUtils.this.remove(this.val$fname)) {
                this.val$callbackContext.success();
            } else {
                this.val$callbackContext.error(FileUtils.NO_MODIFICATION_ALLOWED_ERR);
            }
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.19 */
    class AnonymousClass19 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;

        AnonymousClass19(String str, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws FileExistsException, MalformedURLException, NoModificationAllowedException {
            if (FileUtils.this.removeRecursively(this.val$fname)) {
                this.val$callbackContext.success();
            } else {
                this.val$callbackContext.error(FileUtils.NO_MODIFICATION_ALLOWED_ERR);
            }
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.1 */
    class C02181 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;

        C02181(CallbackContext callbackContext) {
            this.val$callbackContext = callbackContext;
        }

        public void run() {
            this.val$callbackContext.sendPluginResult(new PluginResult(Status.OK, DirectoryManager.testSaveLocationExists()));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.20 */
    class AnonymousClass20 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;
        final /* synthetic */ String val$newName;
        final /* synthetic */ String val$newParent;

        AnonymousClass20(String str, String str2, String str3, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$newParent = str2;
            this.val$newName = str3;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws JSONException, NoModificationAllowedException, IOException, InvalidModificationException, EncodingException, FileExistsException {
            this.val$callbackContext.success(FileUtils.this.transferTo(this.val$fname, this.val$newParent, this.val$newName, true));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.21 */
    class AnonymousClass21 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;
        final /* synthetic */ String val$newName;
        final /* synthetic */ String val$newParent;

        AnonymousClass21(String str, String str2, String str3, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$newParent = str2;
            this.val$newName = str3;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws JSONException, NoModificationAllowedException, IOException, InvalidModificationException, EncodingException, FileExistsException {
            this.val$callbackContext.success(FileUtils.this.transferTo(this.val$fname, this.val$newParent, this.val$newName, false));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.22 */
    class AnonymousClass22 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;

        AnonymousClass22(String str, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws FileNotFoundException, JSONException, MalformedURLException {
            this.val$callbackContext.success(FileUtils.this.readEntries(this.val$fname));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.23 */
    class AnonymousClass23 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$localURLstr;

        AnonymousClass23(String str, CallbackContext callbackContext) {
            this.val$localURLstr = str;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws FileNotFoundException, JSONException, MalformedURLException {
            this.val$callbackContext.success(FileUtils.this.filesystemPathForURL(this.val$localURLstr));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.25 */
    class AnonymousClass25 implements ReadFileCallback {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$encoding;
        final /* synthetic */ int val$resultType;

        AnonymousClass25(int i, String str, CallbackContext callbackContext) {
            this.val$resultType = i;
            this.val$encoding = str;
            this.val$callbackContext = callbackContext;
        }

        public void handleData(InputStream inputStream, String contentType) {
            try {
                PluginResult result;
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                byte[] buffer = new byte[AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD];
                while (true) {
                    int bytesRead = inputStream.read(buffer, 0, AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                    if (bytesRead <= 0) {
                        break;
                    }
                    os.write(buffer, 0, bytesRead);
                }
                switch (this.val$resultType) {
                    case DocumentHandler.ERROR_UNKNOWN_ERROR /*1*/:
                        result = new PluginResult(Status.OK, os.toString(this.val$encoding));
                        break;
                    case FragmentManagerImpl.ANIM_STYLE_FADE_EXIT /*6*/:
                        result = new PluginResult(Status.OK, os.toByteArray());
                        break;
                    case PluginResult.MESSAGE_TYPE_BINARYSTRING /*7*/:
                        result = new PluginResult(Status.OK, os.toByteArray(), true);
                        break;
                    default:
                        result = new PluginResult(Status.OK, "data:" + contentType + ";base64," + new String(Base64.encode(os.toByteArray(), 2), "US-ASCII"));
                        break;
                }
                this.val$callbackContext.sendPluginResult(result);
            } catch (IOException e) {
                Log.d(FileUtils.LOG_TAG, e.getLocalizedMessage());
                this.val$callbackContext.sendPluginResult(new PluginResult(Status.IO_EXCEPTION, FileUtils.NOT_READABLE_ERR));
            }
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.2 */
    class C02192 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;

        C02192(CallbackContext callbackContext) {
            this.val$callbackContext = callbackContext;
        }

        public void run() {
            this.val$callbackContext.sendPluginResult(new PluginResult(Status.OK, (float) DirectoryManager.getFreeDiskSpace(false)));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.3 */
    class C02203 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;

        C02203(String str, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$callbackContext = callbackContext;
        }

        public void run() {
            this.val$callbackContext.sendPluginResult(new PluginResult(Status.OK, DirectoryManager.testFileExists(this.val$fname)));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.4 */
    class C02214 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$fname;

        C02214(String str, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$callbackContext = callbackContext;
        }

        public void run() {
            this.val$callbackContext.sendPluginResult(new PluginResult(Status.OK, DirectoryManager.testFileExists(this.val$fname)));
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.5 */
    class C02225 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$encoding;
        final /* synthetic */ int val$end;
        final /* synthetic */ String val$fname;
        final /* synthetic */ int val$start;

        C02225(String str, int i, int i2, CallbackContext callbackContext, String str2) {
            this.val$fname = str;
            this.val$start = i;
            this.val$end = i2;
            this.val$callbackContext = callbackContext;
            this.val$encoding = str2;
        }

        public void run() throws MalformedURLException {
            FileUtils.this.readFileAs(this.val$fname, this.val$start, this.val$end, this.val$callbackContext, this.val$encoding, 1);
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.6 */
    class C02236 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ int val$end;
        final /* synthetic */ String val$fname;
        final /* synthetic */ int val$start;

        C02236(String str, int i, int i2, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$start = i;
            this.val$end = i2;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws MalformedURLException {
            FileUtils.this.readFileAs(this.val$fname, this.val$start, this.val$end, this.val$callbackContext, null, -1);
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.7 */
    class C02247 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ int val$end;
        final /* synthetic */ String val$fname;
        final /* synthetic */ int val$start;

        C02247(String str, int i, int i2, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$start = i;
            this.val$end = i2;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws MalformedURLException {
            FileUtils.this.readFileAs(this.val$fname, this.val$start, this.val$end, this.val$callbackContext, null, 6);
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.8 */
    class C02258 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ int val$end;
        final /* synthetic */ String val$fname;
        final /* synthetic */ int val$start;

        C02258(String str, int i, int i2, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$start = i;
            this.val$end = i2;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws MalformedURLException {
            FileUtils.this.readFileAs(this.val$fname, this.val$start, this.val$end, this.val$callbackContext, null, 7);
        }
    }

    /* renamed from: org.apache.cordova.file.FileUtils.9 */
    class C02269 implements FileOp {
        final /* synthetic */ CallbackContext val$callbackContext;
        final /* synthetic */ String val$data;
        final /* synthetic */ String val$fname;
        final /* synthetic */ Boolean val$isBinary;
        final /* synthetic */ int val$offset;

        C02269(String str, String str2, int i, Boolean bool, CallbackContext callbackContext) {
            this.val$fname = str;
            this.val$data = str2;
            this.val$offset = i;
            this.val$isBinary = bool;
            this.val$callbackContext = callbackContext;
        }

        public void run() throws FileNotFoundException, IOException, NoModificationAllowedException {
            this.val$callbackContext.sendPluginResult(new PluginResult(Status.OK, (float) FileUtils.this.write(this.val$fname, this.val$data, this.val$offset, this.val$isBinary.booleanValue())));
        }
    }

    public FileUtils() {
        this.configured = false;
    }

    static {
        NOT_FOUND_ERR = 1;
        SECURITY_ERR = 2;
        ABORT_ERR = 3;
        NOT_READABLE_ERR = 4;
        ENCODING_ERR = 5;
        NO_MODIFICATION_ALLOWED_ERR = 6;
        INVALID_STATE_ERR = 7;
        SYNTAX_ERR = 8;
        INVALID_MODIFICATION_ERR = 9;
        QUOTA_EXCEEDED_ERR = 10;
        TYPE_MISMATCH_ERR = 11;
        PATH_EXISTS_ERR = 12;
        UNKNOWN_ERR = 1000;
    }

    public void registerFilesystem(Filesystem fs) {
        if (fs != null && filesystemForName(fs.name) == null) {
            this.filesystems.add(fs);
        }
    }

    private Filesystem filesystemForName(String name) {
        Iterator i$ = this.filesystems.iterator();
        while (i$.hasNext()) {
            Filesystem fs = (Filesystem) i$.next();
            if (fs != null && fs.name != null && fs.name.equals(name)) {
                return fs;
            }
        }
        return null;
    }

    protected String[] getExtraFileSystemsPreference(Activity activity) {
        String fileSystemsStr = activity.getIntent().getStringExtra("androidextrafilesystems");
        if (fileSystemsStr == null) {
            fileSystemsStr = "files,files-external,documents,sdcard,cache,cache-external,root";
        }
        return fileSystemsStr.split(",");
    }

    protected void registerExtraFileSystems(String[] filesystems, HashMap<String, String> availableFileSystems) {
        HashSet<String> installedFileSystems = new HashSet();
        for (String fsName : filesystems) {
            if (!installedFileSystems.contains(fsName)) {
                String fsRoot = (String) availableFileSystems.get(fsName);
                if (fsRoot != null) {
                    File newRoot = new File(fsRoot);
                    if (newRoot.mkdirs() || newRoot.isDirectory()) {
                        registerFilesystem(new LocalFilesystem(fsName, this.cordova, fsRoot));
                        installedFileSystems.add(fsName);
                    } else {
                        Log.d(LOG_TAG, "Unable to create root dir for fileystem \"" + fsName + "\", skipping");
                    }
                } else {
                    Log.d(LOG_TAG, "Unrecognized extra filesystem identifier: " + fsName);
                }
            }
        }
    }

    protected HashMap<String, String> getAvailableFileSystems(Activity activity) {
        Context context = activity.getApplicationContext();
        HashMap<String, String> availableFileSystems = new HashMap();
        availableFileSystems.put("files", context.getFilesDir().getAbsolutePath());
        availableFileSystems.put("documents", new File(context.getFilesDir(), "Documents").getAbsolutePath());
        availableFileSystems.put("cache", context.getCacheDir().getAbsolutePath());
        availableFileSystems.put("root", "/");
        if (Environment.getExternalStorageState().equals("mounted")) {
            try {
                availableFileSystems.put("files-external", context.getExternalFilesDir(null).getAbsolutePath());
                availableFileSystems.put("sdcard", Environment.getExternalStorageDirectory().getAbsolutePath());
                availableFileSystems.put("cache-external", context.getExternalCacheDir().getAbsolutePath());
            } catch (NullPointerException e) {
                Log.d(LOG_TAG, "External storage unavailable, check to see if USB Mass Storage Mode is on");
            }
        }
        return availableFileSystems;
    }

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.filesystems = new ArrayList();
        String persistentRoot = null;
        Activity activity = cordova.getActivity();
        String packageName = activity.getPackageName();
        String location = activity.getIntent().getStringExtra("androidpersistentfilelocation");
        if (location == null) {
            location = "compatibility";
        }
        String tempRoot = activity.getCacheDir().getAbsolutePath();
        if ("internal".equalsIgnoreCase(location)) {
            persistentRoot = activity.getFilesDir().getAbsolutePath() + "/files/";
            this.configured = true;
        } else if ("compatibility".equalsIgnoreCase(location)) {
            if (Environment.getExternalStorageState().equals("mounted")) {
                persistentRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
                tempRoot = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + packageName + "/cache/";
            } else {
                persistentRoot = "/data/data/" + packageName;
            }
            this.configured = true;
        }
        if (this.configured) {
            new File(tempRoot).mkdirs();
            new File(persistentRoot).mkdirs();
            registerFilesystem(new LocalFilesystem("temporary", cordova, tempRoot));
            registerFilesystem(new LocalFilesystem("persistent", cordova, persistentRoot));
            registerFilesystem(new ContentFilesystem("content", cordova, webView));
            registerExtraFileSystems(getExtraFileSystemsPreference(activity), getAvailableFileSystems(activity));
            if (filePlugin == null) {
                filePlugin = this;
                return;
            }
            return;
        }
        Log.e(LOG_TAG, "File plugin configuration error: Please set AndroidPersistentFileLocation in config.xml to one of \"internal\" (for new applications) or \"compatibility\" (for compatibility with previous versions)");
        activity.finish();
    }

    public static FileUtils getFilePlugin() {
        return filePlugin;
    }

    private Filesystem filesystemForURL(LocalFilesystemURL localURL) {
        if (localURL == null) {
            return null;
        }
        return filesystemForName(localURL.filesystemName);
    }

    public Uri remapUri(Uri uri) {
        Uri uri2 = null;
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(uri);
            Filesystem fs = filesystemForURL(inputURL);
            if (!(fs == null || fs.filesystemPathForURL(inputURL) == null)) {
                uri2 = Uri.parse("file:///" + fs.filesystemPathForURL(inputURL));
            }
        } catch (IllegalArgumentException e) {
        }
        return uri2;
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (this.configured) {
            if (action.equals("testSaveLocationExists")) {
                threadhelper(new C02181(callbackContext), callbackContext);
            } else {
                if (action.equals("getFreeDiskSpace")) {
                    threadhelper(new C02192(callbackContext), callbackContext);
                } else {
                    if (action.equals("testFileExists")) {
                        threadhelper(new C02203(args.getString(0), callbackContext), callbackContext);
                    } else {
                        if (action.equals("testDirectoryExists")) {
                            threadhelper(new C02214(args.getString(0), callbackContext), callbackContext);
                        } else {
                            if (action.equals("readAsText")) {
                                String encoding = args.getString(1);
                                threadhelper(new C02225(args.getString(0), args.getInt(2), args.getInt(3), callbackContext, encoding), callbackContext);
                            } else {
                                if (action.equals("readAsDataURL")) {
                                    threadhelper(new C02236(args.getString(0), args.getInt(1), args.getInt(2), callbackContext), callbackContext);
                                } else {
                                    if (action.equals("readAsArrayBuffer")) {
                                        threadhelper(new C02247(args.getString(0), args.getInt(1), args.getInt(2), callbackContext), callbackContext);
                                    } else {
                                        if (action.equals("readAsBinaryString")) {
                                            threadhelper(new C02258(args.getString(0), args.getInt(1), args.getInt(2), callbackContext), callbackContext);
                                        } else {
                                            if (action.equals("write")) {
                                                String string = args.getString(0);
                                                threadhelper(new C02269(fname, args.getString(1), args.getInt(2), Boolean.valueOf(args.getBoolean(3)), callbackContext), callbackContext);
                                            } else {
                                                if (action.equals("truncate")) {
                                                    threadhelper(new AnonymousClass10(args.getString(0), args.getInt(1), callbackContext), callbackContext);
                                                } else {
                                                    if (action.equals("requestAllFileSystems")) {
                                                        threadhelper(new AnonymousClass11(callbackContext), callbackContext);
                                                    } else {
                                                        if (action.equals("requestAllPaths")) {
                                                            callbackContext.success(requestAllPaths());
                                                        } else {
                                                            if (action.equals("requestFileSystem")) {
                                                                threadhelper(new AnonymousClass12(args.optLong(1), callbackContext, args.getInt(0)), callbackContext);
                                                            } else {
                                                                if (action.equals("resolveLocalFileSystemURI")) {
                                                                    threadhelper(new AnonymousClass13(args.getString(0), callbackContext), callbackContext);
                                                                } else {
                                                                    if (action.equals("getFileMetadata")) {
                                                                        threadhelper(new AnonymousClass14(args.getString(0), callbackContext), callbackContext);
                                                                    } else {
                                                                        if (action.equals("getParent")) {
                                                                            threadhelper(new AnonymousClass15(args.getString(0), callbackContext), callbackContext);
                                                                        } else {
                                                                            if (action.equals("getDirectory")) {
                                                                                threadhelper(new AnonymousClass16(args.getString(0), args.getString(1), args, callbackContext), callbackContext);
                                                                            } else {
                                                                                if (action.equals("getFile")) {
                                                                                    threadhelper(new AnonymousClass17(args.getString(0), args.getString(1), args, callbackContext), callbackContext);
                                                                                } else {
                                                                                    if (action.equals("remove")) {
                                                                                        threadhelper(new AnonymousClass18(args.getString(0), callbackContext), callbackContext);
                                                                                    } else {
                                                                                        if (action.equals("removeRecursively")) {
                                                                                            threadhelper(new AnonymousClass19(args.getString(0), callbackContext), callbackContext);
                                                                                        } else {
                                                                                            String string2;
                                                                                            if (action.equals("moveTo")) {
                                                                                                string2 = args.getString(0);
                                                                                                threadhelper(new AnonymousClass20(fname, args.getString(1), args.getString(2), callbackContext), callbackContext);
                                                                                            } else {
                                                                                                if (action.equals("copyTo")) {
                                                                                                    string2 = args.getString(0);
                                                                                                    threadhelper(new AnonymousClass21(fname, args.getString(1), args.getString(2), callbackContext), callbackContext);
                                                                                                } else {
                                                                                                    if (action.equals("readEntries")) {
                                                                                                        threadhelper(new AnonymousClass22(args.getString(0), callbackContext), callbackContext);
                                                                                                    } else {
                                                                                                        if (!action.equals("_getLocalFilesystemPath")) {
                                                                                                            return false;
                                                                                                        }
                                                                                                        threadhelper(new AnonymousClass23(args.getString(0), callbackContext), callbackContext);
                                                                                                    }
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }
        callbackContext.sendPluginResult(new PluginResult(Status.ERROR, "File plugin is not configured. Please see the README.md file for details on how to update config.xml"));
        return true;
    }

    public String filesystemPathForURL(String localURLstr) throws MalformedURLException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(localURLstr);
            Filesystem fs = filesystemForURL(inputURL);
            if (fs != null) {
                return fs.filesystemPathForURL(inputURL);
            }
            throw new MalformedURLException("No installed handlers for this URL");
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }

    public LocalFilesystemURL filesystemURLforLocalPath(String localPath) {
        LocalFilesystemURL localURL = null;
        int shortestFullPath = 0;
        Iterator i$ = this.filesystems.iterator();
        while (i$.hasNext()) {
            Filesystem fs = (Filesystem) i$.next();
            if (fs != null) {
                LocalFilesystemURL url = fs.URLforFilesystemPath(localPath);
                if (url != null && (localURL == null || url.fullPath.length() < shortestFullPath)) {
                    localURL = url;
                    shortestFullPath = url.fullPath.length();
                }
            }
        }
        return localURL;
    }

    private void threadhelper(FileOp f, CallbackContext callbackContext) {
        this.cordova.getThreadPool().execute(new AnonymousClass24(f, callbackContext));
    }

    private JSONObject resolveLocalFileSystemURI(String url) throws IOException, JSONException {
        if (url == null) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
        LocalFilesystemURL inputURL;
        if (url.startsWith("file:/")) {
            int pathEnd;
            String path;
            if (!url.startsWith("file://")) {
                url = "file:///" + url.substring(6);
            }
            String decoded = URLDecoder.decode(url, "UTF-8");
            int questionMark = decoded.indexOf("?");
            if (questionMark < 0) {
                pathEnd = decoded.length();
            } else {
                pathEnd = questionMark;
            }
            int thirdSlash = decoded.indexOf("/", 7);
            if (thirdSlash < 0 || thirdSlash > pathEnd) {
                path = "";
            } else {
                path = decoded.substring(thirdSlash, pathEnd);
            }
            inputURL = filesystemURLforLocalPath(path);
        } else {
            inputURL = new LocalFilesystemURL(url);
        }
        try {
            Filesystem fs = filesystemForURL(inputURL);
            if (fs != null) {
                return fs.getEntryForLocalURL(inputURL);
            }
            throw new MalformedURLException("No installed handlers for this URL");
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }

    private JSONArray readEntries(String baseURLstr) throws FileNotFoundException, JSONException, MalformedURLException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(baseURLstr);
            Filesystem fs = filesystemForURL(inputURL);
            if (fs != null) {
                return fs.readEntriesAtLocalURL(inputURL);
            }
            throw new MalformedURLException("No installed handlers for this URL");
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }

    private JSONObject transferTo(String srcURLstr, String destURLstr, String newName, boolean move) throws JSONException, NoModificationAllowedException, IOException, InvalidModificationException, EncodingException, FileExistsException {
        if (srcURLstr == null || destURLstr == null) {
            throw new FileNotFoundException();
        }
        LocalFilesystemURL srcURL = new LocalFilesystemURL(srcURLstr);
        LocalFilesystemURL destURL = new LocalFilesystemURL(destURLstr);
        Filesystem srcFs = filesystemForURL(srcURL);
        Filesystem destFs = filesystemForURL(destURL);
        if (newName == null || !newName.contains(":")) {
            return destFs.copyFileToURL(destURL, newName, srcFs, srcURL, move);
        }
        throw new EncodingException("Bad file name");
    }

    private boolean removeRecursively(String baseURLstr) throws FileExistsException, NoModificationAllowedException, MalformedURLException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(baseURLstr);
            if ("".equals(inputURL.fullPath) || "/".equals(inputURL.fullPath)) {
                throw new NoModificationAllowedException("You can't delete the root directory");
            }
            Filesystem fs = filesystemForURL(inputURL);
            if (fs != null) {
                return fs.recursiveRemoveFileAtLocalURL(inputURL);
            }
            throw new MalformedURLException("No installed handlers for this URL");
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }

    private boolean remove(String baseURLstr) throws NoModificationAllowedException, InvalidModificationException, MalformedURLException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(baseURLstr);
            if ("".equals(inputURL.fullPath) || "/".equals(inputURL.fullPath)) {
                throw new NoModificationAllowedException("You can't delete the root directory");
            }
            Filesystem fs = filesystemForURL(inputURL);
            if (fs != null) {
                return fs.removeFileAtLocalURL(inputURL);
            }
            throw new MalformedURLException("No installed handlers for this URL");
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }

    private JSONObject getFile(String baseURLstr, String path, JSONObject options, boolean directory) throws FileExistsException, IOException, TypeMismatchException, EncodingException, JSONException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(baseURLstr);
            Filesystem fs = filesystemForURL(inputURL);
            if (fs != null) {
                return fs.getFileForLocalURL(inputURL, path, options, directory);
            }
            throw new MalformedURLException("No installed handlers for this URL");
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }

    private JSONObject getParent(String baseURLstr) throws JSONException, IOException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(baseURLstr);
            Filesystem fs = filesystemForURL(inputURL);
            if (fs != null) {
                return fs.getParentForLocalURL(inputURL);
            }
            throw new MalformedURLException("No installed handlers for this URL");
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }

    private JSONObject getFileMetadata(String baseURLstr) throws FileNotFoundException, JSONException, MalformedURLException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(baseURLstr);
            Filesystem fs = filesystemForURL(inputURL);
            if (fs != null) {
                return fs.getFileMetadataForLocalURL(inputURL);
            }
            throw new MalformedURLException("No installed handlers for this URL");
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }

    private JSONObject requestFileSystem(int type) throws IOException, JSONException {
        JSONObject fs = new JSONObject();
        Filesystem rootFs = null;
        try {
            rootFs = (Filesystem) this.filesystems.get(type);
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        if (rootFs == null) {
            throw new IOException("No filesystem of type requested");
        }
        LocalFilesystemURL rootURL = new LocalFilesystemURL("cdvfile://localhost/" + rootFs.name + "/");
        fs.put("name", rootFs.name);
        fs.put("root", rootFs.getEntryForLocalURL(rootURL));
        return fs;
    }

    private JSONArray requestAllFileSystems() throws IOException, JSONException {
        JSONArray ret = new JSONArray();
        Iterator i$ = this.filesystems.iterator();
        while (i$.hasNext()) {
            Filesystem fs = (Filesystem) i$.next();
            ret.put(fs.getEntryForLocalURL(new LocalFilesystemURL("cdvfile://localhost/" + fs.name + "/")));
        }
        return ret;
    }

    private static String toDirUrl(File f) {
        return Uri.fromFile(f).toString() + '/';
    }

    private JSONObject requestAllPaths() throws JSONException {
        Context context = this.cordova.getActivity();
        JSONObject ret = new JSONObject();
        ret.put("applicationDirectory", "file:///android_asset/");
        ret.put("applicationStorageDirectory", toDirUrl(context.getFilesDir().getParentFile()));
        ret.put("dataDirectory", toDirUrl(context.getFilesDir()));
        ret.put("cacheDirectory", toDirUrl(context.getCacheDir()));
        if (Environment.getExternalStorageState().equals("mounted")) {
            try {
                ret.put("externalApplicationStorageDirectory", toDirUrl(context.getExternalFilesDir(null).getParentFile()));
                ret.put("externalDataDirectory", toDirUrl(context.getExternalFilesDir(null)));
                ret.put("externalCacheDirectory", toDirUrl(context.getExternalCacheDir()));
                ret.put("externalRootDirectory", toDirUrl(Environment.getExternalStorageDirectory()));
            } catch (NullPointerException e) {
                Log.d(LOG_TAG, "Unable to access these paths, most liklely due to USB storage");
            }
        }
        return ret;
    }

    public JSONObject getEntryForFile(File file) throws JSONException {
        Iterator i$ = this.filesystems.iterator();
        while (i$.hasNext()) {
            JSONObject entry = ((Filesystem) i$.next()).makeEntryForFile(file);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    @Deprecated
    public static JSONObject getEntry(File file) throws JSONException {
        if (getFilePlugin() != null) {
            return getFilePlugin().getEntryForFile(file);
        }
        return null;
    }

    public void readFileAs(String srcURLstr, int start, int end, CallbackContext callbackContext, String encoding, int resultType) throws MalformedURLException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(srcURLstr);
            Filesystem fs = filesystemForURL(inputURL);
            if (fs == null) {
                throw new MalformedURLException("No installed handlers for this URL");
            }
            fs.readFileAtURL(inputURL, (long) start, (long) end, new AnonymousClass25(resultType, encoding, callbackContext));
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        } catch (FileNotFoundException e2) {
            callbackContext.sendPluginResult(new PluginResult(Status.IO_EXCEPTION, NOT_FOUND_ERR));
        } catch (IOException e3) {
            Log.d(LOG_TAG, e3.getLocalizedMessage());
            callbackContext.sendPluginResult(new PluginResult(Status.IO_EXCEPTION, NOT_READABLE_ERR));
        }
    }

    public long write(String srcURLstr, String data, int offset, boolean isBinary) throws FileNotFoundException, IOException, NoModificationAllowedException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(srcURLstr);
            Filesystem fs = filesystemForURL(inputURL);
            if (fs == null) {
                throw new MalformedURLException("No installed handlers for this URL");
            }
            long x = fs.writeToFileAtURL(inputURL, data, offset, isBinary);
            Log.d("TEST", srcURLstr + ": " + x);
            return x;
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }

    private long truncateFile(String srcURLstr, long size) throws FileNotFoundException, IOException, NoModificationAllowedException {
        try {
            LocalFilesystemURL inputURL = new LocalFilesystemURL(srcURLstr);
            Filesystem fs = filesystemForURL(inputURL);
            if (fs != null) {
                return fs.truncateFileAtURL(inputURL, size);
            }
            throw new MalformedURLException("No installed handlers for this URL");
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Unrecognized filesystem URL");
        }
    }
}
