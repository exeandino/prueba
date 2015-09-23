package de.appplant.cordova.plugin.notification;

public class TriggerReceiver extends AbstractTriggerReceiver {
    public void onTrigger(Notification notification, boolean updated) {
        if (notification.isRepeating()) {
            notification.reschedule();
        }
        notification.show();
    }

    public Notification buildNotification(Builder builder) {
        return builder.build();
    }
}
