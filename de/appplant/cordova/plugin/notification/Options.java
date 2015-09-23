package de.appplant.cordova.plugin.notification;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.view.ViewCompat;
import com.google.android.gcm.GCMRegistrar;
import java.util.Date;
import org.json.JSONException;
import org.json.JSONObject;

public class Options {
    static final String EXTRA = "NOTIFICATION_OPTIONS";
    private final AssetUtil assets;
    private final Context context;
    private long interval;
    private JSONObject options;

    public Options(Context context) {
        this.options = new JSONObject();
        this.interval = 0;
        this.context = context;
        this.assets = AssetUtil.getInstance(context);
    }

    public Options parse(JSONObject options) {
        this.options = options;
        parseInterval();
        parseAssets();
        return this;
    }

    private void parseInterval() {
        String every = this.options.optString("every").toLowerCase();
        if (every.isEmpty()) {
            this.interval = 0;
        } else if (every.equals("second")) {
            this.interval = 1000;
        } else if (every.equals("minute")) {
            this.interval = 60000;
        } else if (every.equals("hour")) {
            this.interval = 3600000;
        } else if (every.equals("day")) {
            this.interval = 86400000;
        } else if (every.equals("week")) {
            this.interval = GCMRegistrar.DEFAULT_ON_SERVER_LIFESPAN_MS;
        } else if (every.equals("month")) {
            this.interval = 2678400000L;
        } else if (every.equals("year")) {
            this.interval = 31536000000L;
        } else {
            try {
                this.interval = (long) (Integer.parseInt(every) * 60000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void parseAssets() {
        if (!this.options.has("iconUri")) {
            Uri iconUri = this.assets.parse(this.options.optString("icon", "icon"));
            Uri soundUri = this.assets.parseSound(this.options.optString("sound", null));
            try {
                this.options.put("iconUri", iconUri.toString());
                this.options.put("soundUri", soundUri.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public Context getContext() {
        return this.context;
    }

    JSONObject getDict() {
        return this.options;
    }

    public String getText() {
        return this.options.optString("text", "");
    }

    public long getRepeatInterval() {
        return this.interval;
    }

    public int getBadgeNumber() {
        return this.options.optInt("badge", 0);
    }

    public Boolean isOngoing() {
        return Boolean.valueOf(this.options.optBoolean("ongoing", false));
    }

    public long getTriggerTime() {
        return this.options.optLong("at", 0) * 1000;
    }

    public Date getTriggerDate() {
        return new Date(getTriggerTime());
    }

    public String getId() {
        return this.options.optString("id", "0");
    }

    public int getIdAsInt() {
        try {
            return Integer.parseInt(getId());
        } catch (Exception e) {
            return 0;
        }
    }

    public String getTitle() {
        String title = this.options.optString("title", "");
        if (title.isEmpty()) {
            return this.context.getApplicationInfo().loadLabel(this.context.getPackageManager()).toString();
        }
        return title;
    }

    public int getLedColor() {
        return Integer.parseInt(this.options.optString("led", "000000"), 16) - ViewCompat.MEASURED_STATE_TOO_SMALL;
    }

    public Uri getSoundUri() {
        Uri uri = null;
        try {
            uri = Uri.parse(this.options.optString("soundUri"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }

    public Bitmap getIconBitmap() {
        try {
            return this.assets.getIconFromUri(Uri.parse(this.options.optString("iconUri")));
        } catch (Exception e) {
            return this.assets.getIconFromDrawable(this.options.optString("icon", "icon"));
        }
    }

    public int getSmallIcon() {
        int resId = this.assets.getResIdForDrawable(this.options.optString("smallIcon", ""));
        if (resId == 0) {
            return 17301656;
        }
        return resId;
    }

    public String toString() {
        return this.options.toString();
    }
}
