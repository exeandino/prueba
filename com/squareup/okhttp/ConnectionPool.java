package com.squareup.okhttp;

import com.squareup.okhttp.internal.Platform;
import com.squareup.okhttp.internal.Util;
import java.io.Closeable;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConnectionPool {
    private static final long DEFAULT_KEEP_ALIVE_DURATION_MS = 300000;
    private static final int MAX_CONNECTIONS_TO_CLEANUP = 2;
    private static final ConnectionPool systemDefault;
    private final LinkedList<Connection> connections;
    private final Callable<Void> connectionsCleanupCallable;
    private final ExecutorService executorService;
    private final long keepAliveDurationNs;
    private final int maxIdleConnections;

    /* renamed from: com.squareup.okhttp.ConnectionPool.1 */
    class C00981 implements Callable<Void> {
        C00981() {
        }

        public Void call() throws Exception {
            List<Connection> expiredConnections = new ArrayList(ConnectionPool.MAX_CONNECTIONS_TO_CLEANUP);
            int idleConnectionCount = 0;
            synchronized (ConnectionPool.this) {
                ListIterator<Connection> i = ConnectionPool.this.connections.listIterator(ConnectionPool.this.connections.size());
                while (i.hasPrevious()) {
                    Connection connection = (Connection) i.previous();
                    if (!connection.isAlive() || connection.isExpired(ConnectionPool.this.keepAliveDurationNs)) {
                        i.remove();
                        expiredConnections.add(connection);
                        if (expiredConnections.size() == ConnectionPool.MAX_CONNECTIONS_TO_CLEANUP) {
                            break;
                        }
                    } else if (connection.isIdle()) {
                        idleConnectionCount++;
                    }
                }
                i = ConnectionPool.this.connections.listIterator(ConnectionPool.this.connections.size());
                while (i.hasPrevious() && idleConnectionCount > ConnectionPool.this.maxIdleConnections) {
                    connection = (Connection) i.previous();
                    if (connection.isIdle()) {
                        expiredConnections.add(connection);
                        i.remove();
                        idleConnectionCount--;
                    }
                }
            }
            for (Closeable expiredConnection : expiredConnections) {
                Util.closeQuietly(expiredConnection);
            }
            return null;
        }
    }

    /* renamed from: com.squareup.okhttp.ConnectionPool.2 */
    class C00992 implements Runnable {
        C00992() {
        }

        public void run() {
        }
    }

    static {
        String keepAlive = System.getProperty("http.keepAlive");
        String keepAliveDuration = System.getProperty("http.keepAliveDuration");
        String maxIdleConnections = System.getProperty("http.maxConnections");
        long keepAliveDurationMs = keepAliveDuration != null ? Long.parseLong(keepAliveDuration) : DEFAULT_KEEP_ALIVE_DURATION_MS;
        if (keepAlive != null && !Boolean.parseBoolean(keepAlive)) {
            systemDefault = new ConnectionPool(0, keepAliveDurationMs);
        } else if (maxIdleConnections != null) {
            systemDefault = new ConnectionPool(Integer.parseInt(maxIdleConnections), keepAliveDurationMs);
        } else {
            systemDefault = new ConnectionPool(5, keepAliveDurationMs);
        }
    }

    public ConnectionPool(int maxIdleConnections, long keepAliveDurationMs) {
        this.connections = new LinkedList();
        this.executorService = new ThreadPoolExecutor(0, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue(), Util.daemonThreadFactory("OkHttp ConnectionPool"));
        this.connectionsCleanupCallable = new C00981();
        this.maxIdleConnections = maxIdleConnections;
        this.keepAliveDurationNs = (keepAliveDurationMs * 1000) * 1000;
    }

    List<Connection> getConnections() {
        List arrayList;
        waitForCleanupCallableToRun();
        synchronized (this) {
            arrayList = new ArrayList(this.connections);
        }
        return arrayList;
    }

    private void waitForCleanupCallableToRun() {
        try {
            this.executorService.submit(new C00992()).get();
        } catch (Exception e) {
            throw new AssertionError();
        }
    }

    public static ConnectionPool getDefault() {
        return systemDefault;
    }

    public synchronized int getConnectionCount() {
        return this.connections.size();
    }

    public synchronized int getSpdyConnectionCount() {
        int total;
        total = 0;
        Iterator i$ = this.connections.iterator();
        while (i$.hasNext()) {
            if (((Connection) i$.next()).isSpdy()) {
                total++;
            }
        }
        return total;
    }

    public synchronized int getHttpConnectionCount() {
        int total;
        total = 0;
        Iterator i$ = this.connections.iterator();
        while (i$.hasNext()) {
            if (!((Connection) i$.next()).isSpdy()) {
                total++;
            }
        }
        return total;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public synchronized com.squareup.okhttp.Connection get(com.squareup.okhttp.Address r9) {
        /*
        r8 = this;
        monitor-enter(r8);
        r2 = 0;
        r4 = r8.connections;	 Catch:{ all -> 0x0087 }
        r5 = r8.connections;	 Catch:{ all -> 0x0087 }
        r5 = r5.size();	 Catch:{ all -> 0x0087 }
        r3 = r4.listIterator(r5);	 Catch:{ all -> 0x0087 }
    L_0x000e:
        r4 = r3.hasPrevious();	 Catch:{ all -> 0x0087 }
        if (r4 == 0) goto L_0x0052;
    L_0x0014:
        r0 = r3.previous();	 Catch:{ all -> 0x0087 }
        r0 = (com.squareup.okhttp.Connection) r0;	 Catch:{ all -> 0x0087 }
        r4 = r0.getRoute();	 Catch:{ all -> 0x0087 }
        r4 = r4.getAddress();	 Catch:{ all -> 0x0087 }
        r4 = r4.equals(r9);	 Catch:{ all -> 0x0087 }
        if (r4 == 0) goto L_0x000e;
    L_0x0028:
        r4 = r0.isAlive();	 Catch:{ all -> 0x0087 }
        if (r4 == 0) goto L_0x000e;
    L_0x002e:
        r4 = java.lang.System.nanoTime();	 Catch:{ all -> 0x0087 }
        r6 = r0.getIdleStartTimeNs();	 Catch:{ all -> 0x0087 }
        r4 = r4 - r6;
        r6 = r8.keepAliveDurationNs;	 Catch:{ all -> 0x0087 }
        r4 = (r4 > r6 ? 1 : (r4 == r6 ? 0 : -1));
        if (r4 >= 0) goto L_0x000e;
    L_0x003d:
        r3.remove();	 Catch:{ all -> 0x0087 }
        r4 = r0.isSpdy();	 Catch:{ all -> 0x0087 }
        if (r4 != 0) goto L_0x0051;
    L_0x0046:
        r4 = com.squareup.okhttp.internal.Platform.get();	 Catch:{ SocketException -> 0x0068 }
        r5 = r0.getSocket();	 Catch:{ SocketException -> 0x0068 }
        r4.tagSocket(r5);	 Catch:{ SocketException -> 0x0068 }
    L_0x0051:
        r2 = r0;
    L_0x0052:
        if (r2 == 0) goto L_0x005f;
    L_0x0054:
        r4 = r2.isSpdy();	 Catch:{ all -> 0x0087 }
        if (r4 == 0) goto L_0x005f;
    L_0x005a:
        r4 = r8.connections;	 Catch:{ all -> 0x0087 }
        r4.addFirst(r2);	 Catch:{ all -> 0x0087 }
    L_0x005f:
        r4 = r8.executorService;	 Catch:{ all -> 0x0087 }
        r5 = r8.connectionsCleanupCallable;	 Catch:{ all -> 0x0087 }
        r4.submit(r5);	 Catch:{ all -> 0x0087 }
        monitor-exit(r8);
        return r2;
    L_0x0068:
        r1 = move-exception;
        com.squareup.okhttp.internal.Util.closeQuietly(r0);	 Catch:{ all -> 0x0087 }
        r4 = com.squareup.okhttp.internal.Platform.get();	 Catch:{ all -> 0x0087 }
        r5 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0087 }
        r5.<init>();	 Catch:{ all -> 0x0087 }
        r6 = "Unable to tagSocket(): ";
        r5 = r5.append(r6);	 Catch:{ all -> 0x0087 }
        r5 = r5.append(r1);	 Catch:{ all -> 0x0087 }
        r5 = r5.toString();	 Catch:{ all -> 0x0087 }
        r4.logW(r5);	 Catch:{ all -> 0x0087 }
        goto L_0x000e;
    L_0x0087:
        r4 = move-exception;
        monitor-exit(r8);
        throw r4;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.squareup.okhttp.ConnectionPool.get(com.squareup.okhttp.Address):com.squareup.okhttp.Connection");
    }

    public void recycle(Connection connection) {
        if (!connection.isSpdy()) {
            if (connection.isAlive()) {
                try {
                    Platform.get().untagSocket(connection.getSocket());
                    synchronized (this) {
                        this.connections.addFirst(connection);
                        connection.resetIdleStartTime();
                    }
                    this.executorService.submit(this.connectionsCleanupCallable);
                    return;
                } catch (SocketException e) {
                    Platform.get().logW("Unable to untagSocket(): " + e);
                    Util.closeQuietly((Closeable) connection);
                    return;
                }
            }
            Util.closeQuietly((Closeable) connection);
        }
    }

    public void maybeShare(Connection connection) {
        this.executorService.submit(this.connectionsCleanupCallable);
        if (connection.isSpdy() && connection.isAlive()) {
            synchronized (this) {
                this.connections.addFirst(connection);
            }
        }
    }

    public void evictAll() {
        synchronized (this) {
            List<Connection> connections = new ArrayList(this.connections);
            this.connections.clear();
        }
        for (Closeable connection : connections) {
            Util.closeQuietly(connection);
        }
    }
}
