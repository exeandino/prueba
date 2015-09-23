package org.apache.cordova;

import android.view.View;

public class ScrollEvent {
    public int f0l;
    public int nl;
    public int nt;
    public int f1t;
    private View targetView;

    ScrollEvent(int nx, int ny, int x, int y, View view) {
        this.f0l = x;
        y = this.f1t;
        this.nl = nx;
        this.nt = ny;
        this.targetView = view;
    }

    public int dl() {
        return this.nl - this.f0l;
    }

    public int dt() {
        return this.nt - this.f1t;
    }

    public View getTargetView() {
        return this.targetView;
    }
}
