package de.appplant.cordova.plugin.notification;

public class ClearReceiver extends AbstractClearReceiver {
    public void onClear(Notification notification) {
        notification.clear();
    }
}
