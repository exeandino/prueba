package de.appplant.cordova.plugin.localnotification;

import de.appplant.cordova.plugin.notification.Notification;

public class ClearReceiver extends de.appplant.cordova.plugin.notification.ClearReceiver {
    public void onClear(Notification notification) {
        super.onClear(notification);
        LocalNotification.fireEvent("clear", notification);
    }
}
