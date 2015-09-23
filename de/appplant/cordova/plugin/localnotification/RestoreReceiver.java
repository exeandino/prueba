package de.appplant.cordova.plugin.localnotification;

import de.appplant.cordova.plugin.notification.AbstractRestoreReceiver;
import de.appplant.cordova.plugin.notification.Builder;
import de.appplant.cordova.plugin.notification.Notification;

public class RestoreReceiver extends AbstractRestoreReceiver {
    public void onRestore(Notification notification) {
        notification.schedule();
    }

    public Notification buildNotification(Builder builder) {
        return builder.setTriggerReceiver(TriggerReceiver.class).setClearReceiver(ClearReceiver.class).setClickActivity(ClickActivity.class).build();
    }
}
