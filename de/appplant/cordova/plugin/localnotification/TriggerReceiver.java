package de.appplant.cordova.plugin.localnotification;

import de.appplant.cordova.plugin.notification.Builder;
import de.appplant.cordova.plugin.notification.Notification;

public class TriggerReceiver extends de.appplant.cordova.plugin.notification.TriggerReceiver {
    public void onTrigger(Notification notification, boolean updated) {
        super.onTrigger(notification, updated);
        if (!updated) {
            LocalNotification.fireEvent("trigger", notification);
        }
    }

    public Notification buildNotification(Builder builder) {
        return builder.setTriggerReceiver(TriggerReceiver.class).setClickActivity(ClickActivity.class).setClearReceiver(ClearReceiver.class).build();
    }
}
