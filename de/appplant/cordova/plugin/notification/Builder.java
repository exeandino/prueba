package de.appplant.cordova.plugin.notification;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat.BigTextStyle;
import java.util.Random;
import org.json.JSONObject;

public class Builder {
    private Class<?> clearReceiver;
    private Class<?> clickActivity;
    private final Context context;
    private final Options options;
    private Class<?> triggerReceiver;

    public Builder(Context context, JSONObject options) {
        this.clearReceiver = ClearReceiver.class;
        this.clickActivity = ClickActivity.class;
        this.context = context;
        this.options = new Options(context).parse(options);
    }

    public Builder(Options options) {
        this.clearReceiver = ClearReceiver.class;
        this.clickActivity = ClickActivity.class;
        this.context = options.getContext();
        this.options = options;
    }

    public Builder setTriggerReceiver(Class<?> receiver) {
        this.triggerReceiver = receiver;
        return this;
    }

    public Builder setClearReceiver(Class<?> receiver) {
        this.clearReceiver = receiver;
        return this;
    }

    public Builder setClickActivity(Class<?> activity) {
        this.clickActivity = activity;
        return this;
    }

    public Notification build() {
        Uri sound = this.options.getSoundUri();
        android.support.v4.app.NotificationCompat.Builder builder = new android.support.v4.app.NotificationCompat.Builder(this.context).setDefaults(0).setContentTitle(this.options.getTitle()).setContentText(this.options.getText()).setNumber(this.options.getBadgeNumber()).setTicker(this.options.getText()).setSmallIcon(this.options.getSmallIcon()).setLargeIcon(this.options.getIconBitmap()).setAutoCancel(true).setOngoing(this.options.isOngoing().booleanValue()).setStyle(new BigTextStyle().bigText(this.options.getText())).setLights(this.options.getLedColor(), 500, 500);
        if (sound != null) {
            builder.setSound(sound);
        }
        applyDeleteReceiver(builder);
        applyContentReceiver(builder);
        return new Notification(this.context, this.options, builder, this.triggerReceiver);
    }

    private void applyDeleteReceiver(android.support.v4.app.NotificationCompat.Builder builder) {
        if (this.clearReceiver != null) {
            builder.setDeleteIntent(PendingIntent.getBroadcast(this.context, 0, new Intent(this.context, this.clearReceiver).setAction(this.options.getId()).putExtra("NOTIFICATION_OPTIONS", this.options.toString()), 268435456));
        }
    }

    private void applyContentReceiver(android.support.v4.app.NotificationCompat.Builder builder) {
        if (this.clickActivity != null) {
            Intent intent = new Intent(this.context, this.clickActivity).putExtra("NOTIFICATION_OPTIONS", this.options.toString()).setFlags(1073741824);
            builder.setContentIntent(PendingIntent.getActivity(this.context, new Random().nextInt(), intent, 268435456));
        }
    }
}
