package com.moodle.moodlemobile;

import android.os.Bundle;
import org.apache.cordova.CordovaActivity;

public class MoodleMobile extends CordovaActivity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.init();
        loadUrl(this.launchUrl);
    }
}
