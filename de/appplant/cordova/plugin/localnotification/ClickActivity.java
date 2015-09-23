package de.appplant.cordova.plugin.localnotification;

import de.appplant.cordova.plugin.notification.Builder;
import de.appplant.cordova.plugin.notification.Notification;
import de.appplant.cordova.plugin.notification.TriggerReceiver;

public class ClickActivity extends de.appplant.cordova.plugin.notification.ClickActivity {
    public void onClick(Notification notification) {
        LocalNotification.fireEvent("click", notification);
        if (!notification.getOptions().isOngoing().booleanValue()) {
            LocalNotification.fireEvent(notification.isRepeating() ? "clear" : "cancel", notification);
        }
        super.onClick(notification);
    }

    public Notification buildNotification(Builder builder) {
        return builder.setTriggerReceiver(TriggerReceiver.class).build();
    }
}
