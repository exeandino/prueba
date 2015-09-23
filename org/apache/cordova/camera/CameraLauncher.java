package org.apache.cordova.camera;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images.Media;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;

public class CameraLauncher extends CordovaPlugin implements MediaScannerConnectionClient {
    private static final int ALLMEDIA = 2;
    private static final int CAMERA = 1;
    private static final int CROP_CAMERA = 100;
    private static final int DATA_URL = 0;
    private static final int FILE_URI = 1;
    private static final String GET_All = "Get All";
    private static final String GET_PICTURE = "Get Picture";
    private static final String GET_VIDEO = "Get Video";
    private static final int JPEG = 0;
    private static final String LOG_TAG = "CameraLauncher";
    private static final int NATIVE_URI = 2;
    private static final int PHOTOLIBRARY = 0;
    private static final int PICTURE = 0;
    private static final int PNG = 1;
    private static final int SAVEDPHOTOALBUM = 2;
    private static final int VIDEO = 1;
    private boolean allowEdit;
    public CallbackContext callbackContext;
    private MediaScannerConnection conn;
    private boolean correctOrientation;
    private Uri croppedUri;
    private int encodingType;
    private Uri imageUri;
    private int mQuality;
    private int mediaType;
    private int numPics;
    private boolean orientationCorrected;
    private boolean saveToPhotoAlbum;
    private Uri scanMe;
    private int targetHeight;
    private int targetWidth;

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        if (!action.equals("takePicture")) {
            return false;
        }
        this.saveToPhotoAlbum = false;
        this.targetHeight = PICTURE;
        this.targetWidth = PICTURE;
        this.encodingType = PICTURE;
        this.mediaType = PICTURE;
        this.mQuality = 80;
        this.mQuality = args.getInt(PICTURE);
        int destType = args.getInt(VIDEO);
        int srcType = args.getInt(SAVEDPHOTOALBUM);
        this.targetWidth = args.getInt(3);
        this.targetHeight = args.getInt(4);
        this.encodingType = args.getInt(5);
        this.mediaType = args.getInt(6);
        this.allowEdit = args.getBoolean(7);
        this.correctOrientation = args.getBoolean(8);
        this.saveToPhotoAlbum = args.getBoolean(9);
        if (this.targetWidth < VIDEO) {
            this.targetWidth = -1;
        }
        if (this.targetHeight < VIDEO) {
            this.targetHeight = -1;
        }
        if (srcType == VIDEO) {
            try {
                takePicture(destType, this.encodingType);
            } catch (IllegalArgumentException e) {
                callbackContext.error("Illegal Argument Exception");
                callbackContext.sendPluginResult(new PluginResult(Status.ERROR));
                return true;
            }
        } else if (srcType == 0 || srcType == SAVEDPHOTOALBUM) {
            getImage(srcType, destType, this.encodingType);
        }
        PluginResult r = new PluginResult(Status.NO_RESULT);
        r.setKeepCallback(true);
        callbackContext.sendPluginResult(r);
        return true;
    }

    private String getTempDirectoryPath() {
        File cache;
        if (Environment.getExternalStorageState().equals("mounted")) {
            cache = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + this.cordova.getActivity().getPackageName() + "/cache/");
        } else {
            cache = this.cordova.getActivity().getCacheDir();
        }
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    public void takePicture(int returnType, int encodingType) {
        this.numPics = queryImgDB(whichContentStore()).getCount();
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        File photo = createCaptureFile(encodingType);
        intent.putExtra("output", Uri.fromFile(photo));
        this.imageUri = Uri.fromFile(photo);
        if (this.cordova != null) {
            this.cordova.startActivityForResult(this, intent, (returnType + 32) + VIDEO);
        }
    }

    private File createCaptureFile(int encodingType) {
        if (encodingType == 0) {
            return new File(getTempDirectoryPath(), ".Pic.jpg");
        }
        if (encodingType == VIDEO) {
            return new File(getTempDirectoryPath(), ".Pic.png");
        }
        throw new IllegalArgumentException("Invalid Encoding Type: " + encodingType);
    }

    public void getImage(int srcType, int returnType, int encodingType) {
        Intent intent = new Intent();
        String title = GET_PICTURE;
        this.croppedUri = null;
        if (this.mediaType == 0) {
            intent.setType("image/*");
            if (this.allowEdit) {
                intent.setAction("android.intent.action.PICK");
                intent.putExtra("crop", "true");
                if (this.targetWidth > 0) {
                    intent.putExtra("outputX", this.targetWidth);
                }
                if (this.targetHeight > 0) {
                    intent.putExtra("outputY", this.targetHeight);
                }
                if (this.targetHeight > 0 && this.targetWidth > 0 && this.targetWidth == this.targetHeight) {
                    intent.putExtra("aspectX", VIDEO);
                    intent.putExtra("aspectY", VIDEO);
                }
                this.croppedUri = Uri.fromFile(createCaptureFile(encodingType));
                intent.putExtra("output", this.croppedUri);
            } else {
                intent.setAction("android.intent.action.GET_CONTENT");
                intent.addCategory("android.intent.category.OPENABLE");
            }
        } else if (this.mediaType == VIDEO) {
            intent.setType("video/*");
            title = GET_VIDEO;
            intent.setAction("android.intent.action.GET_CONTENT");
            intent.addCategory("android.intent.category.OPENABLE");
        } else if (this.mediaType == SAVEDPHOTOALBUM) {
            intent.setType("*/*");
            title = GET_All;
            intent.setAction("android.intent.action.GET_CONTENT");
            intent.addCategory("android.intent.category.OPENABLE");
        }
        if (this.cordova != null) {
            this.cordova.startActivityForResult(this, Intent.createChooser(intent, new String(title)), (((srcType + VIDEO) * 16) + returnType) + VIDEO);
        }
    }

    private void performCrop(Uri picUri) {
        try {
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            cropIntent.setDataAndType(picUri, "image/*");
            cropIntent.putExtra("crop", "true");
            if (this.targetWidth > 0) {
                cropIntent.putExtra("outputX", this.targetWidth);
            }
            if (this.targetHeight > 0) {
                cropIntent.putExtra("outputY", this.targetHeight);
            }
            if (this.targetHeight > 0 && this.targetWidth > 0 && this.targetWidth == this.targetHeight) {
                cropIntent.putExtra("aspectX", VIDEO);
                cropIntent.putExtra("aspectY", VIDEO);
            }
            cropIntent.putExtra("return-data", true);
            if (this.cordova != null) {
                this.cordova.startActivityForResult(this, cropIntent, CROP_CAMERA);
            }
        } catch (ActivityNotFoundException e) {
            Log.e(LOG_TAG, "Crop operation not supported on this device");
            this.callbackContext.success(picUri.toString());
        }
    }

    private void processResultFromCamera(int destType, Intent intent) throws IOException {
        int rotate = PICTURE;
        ExifHelper exif = new ExifHelper();
        try {
            if (this.encodingType == 0) {
                exif.createInFile(getTempDirectoryPath() + "/.Pic.jpg");
                exif.readExifData();
                rotate = exif.getOrientation();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = null;
        Uri uri = null;
        if (destType == 0) {
            bitmap = getScaledBitmap(FileHelper.stripFileProtocol(this.imageUri.toString()));
            if (bitmap == null) {
                bitmap = (Bitmap) intent.getExtras().get("data");
            }
            if (bitmap == null) {
                Log.d(LOG_TAG, "I either have a null image path or bitmap");
                failPicture("Unable to create bitmap!");
                return;
            }
            if (rotate != 0 && this.correctOrientation) {
                bitmap = getRotatedBitmap(rotate, bitmap, exif);
            }
            processPicture(bitmap);
            checkForDuplicateImage(PICTURE);
        } else if (destType == VIDEO || destType == SAVEDPHOTOALBUM) {
            if (this.saveToPhotoAlbum) {
                try {
                    uri = Uri.fromFile(new File(FileHelper.getRealPath(getUriFromMediaStore(), this.cordova)));
                } catch (NullPointerException e2) {
                    uri = null;
                }
            } else {
                uri = Uri.fromFile(new File(getTempDirectoryPath(), System.currentTimeMillis() + ".jpg"));
            }
            if (uri == null) {
                failPicture("Error capturing image - no media storage found.");
                return;
            } else if (this.targetHeight == -1 && this.targetWidth == -1 && this.mQuality == CROP_CAMERA && !this.correctOrientation) {
                writeUncompressedImage(uri);
                this.callbackContext.success(uri.toString());
            } else {
                bitmap = getScaledBitmap(FileHelper.stripFileProtocol(this.imageUri.toString()));
                if (rotate != 0 && this.correctOrientation) {
                    bitmap = getRotatedBitmap(rotate, bitmap, exif);
                }
                OutputStream os = this.cordova.getActivity().getContentResolver().openOutputStream(uri);
                bitmap.compress(CompressFormat.JPEG, this.mQuality, os);
                os.close();
                if (this.encodingType == 0) {
                    String exifPath;
                    if (this.saveToPhotoAlbum) {
                        exifPath = FileHelper.getRealPath(uri, this.cordova);
                    } else {
                        exifPath = uri.getPath();
                    }
                    exif.createOutFile(exifPath);
                    exif.writeExifData();
                }
                if (this.allowEdit) {
                    performCrop(uri);
                } else {
                    this.callbackContext.success(uri.toString());
                }
            }
        } else {
            throw new IllegalStateException();
        }
        cleanup(VIDEO, this.imageUri, uri, bitmap);
    }

    private String ouputModifiedBitmap(Bitmap bitmap, Uri uri) throws IOException {
        String modifiedPath = getTempDirectoryPath() + "/modified.jpg";
        OutputStream os = new FileOutputStream(modifiedPath);
        bitmap.compress(CompressFormat.JPEG, this.mQuality, os);
        os.close();
        String realPath = FileHelper.getRealPath(uri, this.cordova);
        ExifHelper exif = new ExifHelper();
        if (realPath != null && this.encodingType == 0) {
            try {
                exif.createInFile(realPath);
                exif.readExifData();
                if (this.correctOrientation && this.orientationCorrected) {
                    exif.resetOrientation();
                }
                exif.createOutFile(modifiedPath);
                exif.writeExifData();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return modifiedPath;
    }

    private void processResultFromGallery(int destType, Intent intent) {
        Uri uri = intent.getData();
        if (uri == null) {
            if (this.croppedUri != null) {
                uri = this.croppedUri;
            } else {
                failPicture("null data from photo library");
                return;
            }
        }
        if (this.mediaType != 0) {
            this.callbackContext.success(uri.toString());
        } else if (this.targetHeight == -1 && this.targetWidth == -1 && ((destType == VIDEO || destType == SAVEDPHOTOALBUM) && !this.correctOrientation)) {
            this.callbackContext.success(uri.toString());
        } else {
            String uriString = uri.toString();
            String mimeType = FileHelper.getMimeType(uriString, this.cordova);
            if ("image/jpeg".equalsIgnoreCase(mimeType) || "image/png".equalsIgnoreCase(mimeType)) {
                Bitmap bitmap = null;
                try {
                    bitmap = getScaledBitmap(uriString);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (bitmap == null) {
                    Log.d(LOG_TAG, "I either have a null image path or bitmap");
                    failPicture("Unable to create bitmap!");
                    return;
                }
                if (this.correctOrientation) {
                    int rotate = getImageOrientation(uri);
                    if (rotate != 0) {
                        Matrix matrix = new Matrix();
                        matrix.setRotate((float) rotate);
                        try {
                            bitmap = Bitmap.createBitmap(bitmap, PICTURE, PICTURE, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            this.orientationCorrected = true;
                        } catch (OutOfMemoryError e2) {
                            this.orientationCorrected = false;
                        }
                    }
                }
                if (destType == 0) {
                    processPicture(bitmap);
                } else if (destType == VIDEO || destType == SAVEDPHOTOALBUM) {
                    if ((this.targetHeight <= 0 || this.targetWidth <= 0) && !(this.correctOrientation && this.orientationCorrected)) {
                        this.callbackContext.success(uri.toString());
                    } else {
                        try {
                            this.callbackContext.success("file://" + ouputModifiedBitmap(bitmap, uri) + "?" + System.currentTimeMillis());
                        } catch (Exception e3) {
                            e3.printStackTrace();
                            failPicture("Error retrieving image.");
                        }
                    }
                }
                if (bitmap != null) {
                    bitmap.recycle();
                }
                System.gc();
                return;
            }
            Log.d(LOG_TAG, "I either have a null image path or bitmap");
            failPicture("Unable to retrieve path to picture!");
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        OutputStream outputStream;
        FileNotFoundException e;
        IOException e2;
        int srcType = (requestCode / 16) - 1;
        int destType = (requestCode % 16) - 1;
        if (requestCode == CROP_CAMERA) {
            if (resultCode == -1) {
                Bitmap thePic = (Bitmap) intent.getExtras().getParcelable("data");
                if (thePic == null) {
                    failPicture("Crop returned no data.");
                    return;
                }
                File temp_file = new File(getTempDirectoryPath(), System.currentTimeMillis() + ".jpg");
                try {
                    temp_file.createNewFile();
                    OutputStream fOut = new FileOutputStream(temp_file);
                    try {
                        thePic.compress(CompressFormat.JPEG, this.mQuality, fOut);
                        fOut.flush();
                        fOut.close();
                        outputStream = fOut;
                    } catch (FileNotFoundException e3) {
                        e = e3;
                        outputStream = fOut;
                        e.printStackTrace();
                        this.callbackContext.success(Uri.fromFile(temp_file).toString());
                        if (srcType == VIDEO) {
                            if (srcType == 0) {
                            }
                            if (resultCode != -1) {
                            }
                            if (resultCode == 0) {
                                failPicture("Selection did not complete!");
                            } else {
                                failPicture("Selection cancelled.");
                            }
                        } else if (resultCode == -1) {
                            try {
                                processResultFromCamera(destType, intent);
                            } catch (IOException e22) {
                                e22.printStackTrace();
                                failPicture("Error capturing image.");
                                return;
                            }
                        } else if (resultCode == 0) {
                            failPicture("Did not complete!");
                        } else {
                            failPicture("Camera cancelled.");
                        }
                    } catch (IOException e4) {
                        e22 = e4;
                        outputStream = fOut;
                        e22.printStackTrace();
                        this.callbackContext.success(Uri.fromFile(temp_file).toString());
                        if (srcType == VIDEO) {
                            if (srcType == 0) {
                            }
                            if (resultCode != -1) {
                            }
                            if (resultCode == 0) {
                                failPicture("Selection cancelled.");
                            } else {
                                failPicture("Selection did not complete!");
                            }
                        } else if (resultCode == -1) {
                            processResultFromCamera(destType, intent);
                        } else if (resultCode == 0) {
                            failPicture("Camera cancelled.");
                        } else {
                            failPicture("Did not complete!");
                        }
                    }
                } catch (FileNotFoundException e5) {
                    e = e5;
                    e.printStackTrace();
                    this.callbackContext.success(Uri.fromFile(temp_file).toString());
                    if (srcType == VIDEO) {
                        if (srcType == 0) {
                        }
                        if (resultCode != -1) {
                        }
                        if (resultCode == 0) {
                            failPicture("Selection did not complete!");
                        } else {
                            failPicture("Selection cancelled.");
                        }
                    } else if (resultCode == -1) {
                        processResultFromCamera(destType, intent);
                    } else if (resultCode == 0) {
                        failPicture("Did not complete!");
                    } else {
                        failPicture("Camera cancelled.");
                    }
                } catch (IOException e6) {
                    e22 = e6;
                    e22.printStackTrace();
                    this.callbackContext.success(Uri.fromFile(temp_file).toString());
                    if (srcType == VIDEO) {
                        if (srcType == 0) {
                        }
                        if (resultCode != -1) {
                        }
                        if (resultCode == 0) {
                            failPicture("Selection cancelled.");
                        } else {
                            failPicture("Selection did not complete!");
                        }
                    } else if (resultCode == -1) {
                        processResultFromCamera(destType, intent);
                    } else if (resultCode == 0) {
                        failPicture("Camera cancelled.");
                    } else {
                        failPicture("Did not complete!");
                    }
                }
                this.callbackContext.success(Uri.fromFile(temp_file).toString());
            } else if (resultCode == 0) {
                failPicture("Camera cancelled.");
            } else {
                failPicture("Did not complete!");
            }
        }
        if (srcType == VIDEO) {
            if (resultCode == -1) {
                processResultFromCamera(destType, intent);
            } else if (resultCode == 0) {
                failPicture("Camera cancelled.");
            } else {
                failPicture("Did not complete!");
            }
        } else if (srcType == 0 && srcType != SAVEDPHOTOALBUM) {
        } else {
            if (resultCode != -1 && intent != null) {
                processResultFromGallery(destType, intent);
            } else if (resultCode == 0) {
                failPicture("Selection cancelled.");
            } else {
                failPicture("Selection did not complete!");
            }
        }
    }

    private int getImageOrientation(Uri uri) {
        String[] cols = new String[VIDEO];
        cols[PICTURE] = "orientation";
        try {
            Cursor cursor = this.cordova.getActivity().getContentResolver().query(uri, cols, null, null, null);
            if (cursor == null) {
                return PICTURE;
            }
            cursor.moveToPosition(PICTURE);
            int rotate = cursor.getInt(PICTURE);
            cursor.close();
            return rotate;
        } catch (Exception e) {
            return PICTURE;
        }
    }

    private Bitmap getRotatedBitmap(int rotate, Bitmap bitmap, ExifHelper exif) {
        Matrix matrix = new Matrix();
        if (rotate == 180) {
            matrix.setRotate((float) rotate);
        } else {
            matrix.setRotate((float) rotate, ((float) bitmap.getWidth()) / 2.0f, ((float) bitmap.getHeight()) / 2.0f);
        }
        try {
            bitmap = Bitmap.createBitmap(bitmap, PICTURE, PICTURE, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            exif.resetOrientation();
            return bitmap;
        } catch (OutOfMemoryError e) {
            return bitmap;
        }
    }

    private void writeUncompressedImage(Uri uri) throws FileNotFoundException, IOException {
        FileInputStream fis = new FileInputStream(FileHelper.stripFileProtocol(this.imageUri.toString()));
        OutputStream os = this.cordova.getActivity().getContentResolver().openOutputStream(uri);
        byte[] buffer = new byte[AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD];
        while (true) {
            int len = fis.read(buffer);
            if (len != -1) {
                os.write(buffer, PICTURE, len);
            } else {
                os.flush();
                os.close();
                fis.close();
                return;
            }
        }
    }

    private Uri getUriFromMediaStore() {
        ContentValues values = new ContentValues();
        values.put("mime_type", "image/jpeg");
        try {
            return this.cordova.getActivity().getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values);
        } catch (RuntimeException e) {
            LOG.m9d(LOG_TAG, "Can't write to external media storage.");
            try {
                return this.cordova.getActivity().getContentResolver().insert(Media.INTERNAL_CONTENT_URI, values);
            } catch (RuntimeException e2) {
                LOG.m9d(LOG_TAG, "Can't write to internal media storage.");
                return null;
            }
        }
    }

    private Bitmap getScaledBitmap(String imageUrl) throws IOException {
        if (this.targetWidth <= 0 && this.targetHeight <= 0) {
            return BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(imageUrl, this.cordova));
        }
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(imageUrl, this.cordova), null, options);
        if (options.outWidth == 0 || options.outHeight == 0) {
            return null;
        }
        int[] widthHeight = calculateAspectRatio(options.outWidth, options.outHeight);
        options.inJustDecodeBounds = false;
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, this.targetWidth, this.targetHeight);
        Bitmap unscaledBitmap = BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(imageUrl, this.cordova), null, options);
        if (unscaledBitmap != null) {
            return Bitmap.createScaledBitmap(unscaledBitmap, widthHeight[PICTURE], widthHeight[VIDEO], true);
        }
        return null;
    }

    public int[] calculateAspectRatio(int origWidth, int origHeight) {
        int newWidth = this.targetWidth;
        int newHeight = this.targetHeight;
        if (newWidth <= 0 && newHeight <= 0) {
            newWidth = origWidth;
            newHeight = origHeight;
        } else if (newWidth > 0 && newHeight <= 0) {
            newHeight = (newWidth * origHeight) / origWidth;
        } else if (newWidth > 0 || newHeight <= 0) {
            double newRatio = ((double) newWidth) / ((double) newHeight);
            double origRatio = ((double) origWidth) / ((double) origHeight);
            if (origRatio > newRatio) {
                newHeight = (newWidth * origHeight) / origWidth;
            } else if (origRatio < newRatio) {
                newWidth = (newHeight * origWidth) / origHeight;
            }
        } else {
            newWidth = (newHeight * origWidth) / origHeight;
        }
        int[] retval = new int[SAVEDPHOTOALBUM];
        retval[PICTURE] = newWidth;
        retval[VIDEO] = newHeight;
        return retval;
    }

    public static int calculateSampleSize(int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        if (((float) srcWidth) / ((float) srcHeight) > ((float) dstWidth) / ((float) dstHeight)) {
            return srcWidth / dstWidth;
        }
        return srcHeight / dstHeight;
    }

    private Cursor queryImgDB(Uri contentStore) {
        ContentResolver contentResolver = this.cordova.getActivity().getContentResolver();
        String[] strArr = new String[VIDEO];
        strArr[PICTURE] = "_id";
        return contentResolver.query(contentStore, strArr, null, null, null);
    }

    private void cleanup(int imageType, Uri oldImage, Uri newImage, Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.recycle();
        }
        new File(FileHelper.stripFileProtocol(oldImage.toString())).delete();
        checkForDuplicateImage(imageType);
        if (this.saveToPhotoAlbum && newImage != null) {
            scanForGallery(newImage);
        }
        System.gc();
    }

    private void checkForDuplicateImage(int type) {
        int diff = VIDEO;
        Uri contentStore = whichContentStore();
        Cursor cursor = queryImgDB(contentStore);
        int currentNumOfImages = cursor.getCount();
        if (type == VIDEO && this.saveToPhotoAlbum) {
            diff = SAVEDPHOTOALBUM;
        }
        if (currentNumOfImages - this.numPics == diff) {
            cursor.moveToLast();
            int id = Integer.valueOf(cursor.getString(cursor.getColumnIndex("_id"))).intValue();
            if (diff == SAVEDPHOTOALBUM) {
                id--;
            }
            this.cordova.getActivity().getContentResolver().delete(Uri.parse(contentStore + "/" + id), null, null);
            cursor.close();
        }
    }

    private Uri whichContentStore() {
        if (Environment.getExternalStorageState().equals("mounted")) {
            return Media.EXTERNAL_CONTENT_URI;
        }
        return Media.INTERNAL_CONTENT_URI;
    }

    public void processPicture(Bitmap bitmap) {
        ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
        try {
            if (bitmap.compress(CompressFormat.JPEG, this.mQuality, jpeg_data)) {
                this.callbackContext.success(new String(Base64.encode(jpeg_data.toByteArray(), SAVEDPHOTOALBUM)));
            }
        } catch (Exception e) {
            failPicture("Error compressing image.");
        }
    }

    public void failPicture(String err) {
        this.callbackContext.error(err);
    }

    private void scanForGallery(Uri newImage) {
        this.scanMe = newImage;
        if (this.conn != null) {
            this.conn.disconnect();
        }
        this.conn = new MediaScannerConnection(this.cordova.getActivity().getApplicationContext(), this);
        this.conn.connect();
    }

    public void onMediaScannerConnected() {
        try {
            this.conn.scanFile(this.scanMe.toString(), "image/*");
        } catch (IllegalStateException e) {
            LOG.m12e(LOG_TAG, "Can't scan file in MediaScanner after taking picture");
        }
    }

    public void onScanCompleted(String path, Uri uri) {
        this.conn.disconnect();
    }
}
