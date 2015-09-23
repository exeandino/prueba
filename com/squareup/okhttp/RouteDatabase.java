package com.squareup.okhttp;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.net.ssl.SSLHandshakeException;

public final class RouteDatabase {
    private final Set<Route> failedRoutes;

    public RouteDatabase() {
        this.failedRoutes = new LinkedHashSet();
    }

    public synchronized void failed(Route failedRoute, IOException failure) {
        this.failedRoutes.add(failedRoute);
        if (!(failure instanceof SSLHandshakeException)) {
            this.failedRoutes.add(failedRoute.flipTlsMode());
        }
    }

    public synchronized void connected(Route route) {
        this.failedRoutes.remove(route);
    }

    public synchronized boolean shouldPostpone(Route route) {
        return this.failedRoutes.contains(route);
    }

    public synchronized int failedRoutesCount() {
        return this.failedRoutes.size();
    }
}
