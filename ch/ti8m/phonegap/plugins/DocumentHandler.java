package ch.ti8m.phonegap.plugins;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import com.squareup.okhttp.internal.http.HttpTransport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

public class DocumentHandler extends CordovaPlugin {
    public static final int ERROR_NO_HANDLER_FOR_DATA_TYPE = 53;
    public static final int ERROR_UNKNOWN_ERROR = 1;
    private static final String FILE_PREFIX = "DH_";
    public static final String HANDLE_DOCUMENT_ACTION = "HandleDocumentWithURL";

    private class FileDownloaderAsyncTask extends AsyncTask<Void, Void, File> {
        private final CallbackContext callbackContext;
        private final String url;

        public FileDownloaderAsyncTask(CallbackContext callbackContext, String url) {
            this.callbackContext = callbackContext;
            this.url = url;
        }

        protected File doInBackground(Void... arg0) {
            return DocumentHandler.this.downloadFile(this.url);
        }

        protected void onPostExecute(File result) {
            Context context = DocumentHandler.this.cordova.getActivity().getApplicationContext();
            String mimeType = DocumentHandler.getMimeType(this.url);
            if (mimeType == null) {
                this.callbackContext.error((int) DocumentHandler.ERROR_UNKNOWN_ERROR);
                return;
            }
            try {
                Intent intent = new Intent("android.intent.action.VIEW");
                intent.setDataAndType(Uri.fromFile(result), mimeType);
                intent.setFlags(268435456);
                context.startActivity(intent);
                this.callbackContext.success();
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                this.callbackContext.error((int) DocumentHandler.ERROR_NO_HANDLER_FOR_DATA_TYPE);
            }
        }
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (!HANDLE_DOCUMENT_ACTION.equals(action)) {
            return false;
        }
        String url = args.getJSONObject(0).getString("url");
        System.out.println("Found: " + url);
        new FileDownloaderAsyncTask(callbackContext, url).execute(new Void[0]);
        return true;
    }

    private File downloadFile(String url) {
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            String auth = null;
            if (cookieManager.getCookie(url) != null) {
                auth = cookieManager.getCookie(url).toString();
            }
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            if (auth != null) {
                conn.setRequestProperty("Cookie", auth);
            }
            InputStream reader = conn.getInputStream();
            File f = File.createTempFile(FILE_PREFIX, "." + MimeTypeMap.getFileExtensionFromUrl(url), null);
            f.setReadable(true, false);
            FileOutputStream outStream = new FileOutputStream(f);
            byte[] buffer = new byte[HttpTransport.DEFAULT_CHUNK_LENGTH];
            for (int readBytes = reader.read(buffer); readBytes > 0; readBytes = reader.read(buffer)) {
                outStream.write(buffer, 0, readBytes);
            }
            reader.close();
            outStream.close();
            return f;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getMimeType(String url) {
        String mimeType = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        System.out.println("Mime Type: " + mimeType);
        return mimeType;
    }
}
