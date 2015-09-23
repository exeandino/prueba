package org.apache.cordova;

import android.app.Activity;
import android.content.Intent;
import java.util.concurrent.ExecutorService;

public interface CordovaInterface {
    Activity getActivity();

    ExecutorService getThreadPool();

    Object onMessage(String str, Object obj);

    void setActivityResultCallback(CordovaPlugin cordovaPlugin);

    void startActivityForResult(CordovaPlugin cordovaPlugin, Intent intent, int i);
}
