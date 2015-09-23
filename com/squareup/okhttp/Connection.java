package com.squareup.okhttp;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.squareup.okhttp.internal.Platform;
import com.squareup.okhttp.internal.http.HttpAuthenticator;
import com.squareup.okhttp.internal.http.HttpEngine;
import com.squareup.okhttp.internal.http.HttpTransport;
import com.squareup.okhttp.internal.http.RawHeaders;
import com.squareup.okhttp.internal.http.SpdyTransport;
import com.squareup.okhttp.internal.spdy.SpdyConnection;
import com.squareup.okhttp.internal.spdy.SpdyConnection.Builder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy.Type;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Arrays;
import javax.net.ssl.SSLSocket;

public final class Connection implements Closeable {
    private static final byte[] HTTP_11;
    private static final byte[] NPN_PROTOCOLS;
    private static final byte[] SPDY3;
    private boolean connected;
    private int httpMinorVersion;
    private long idleStartTimeNs;
    private InputStream in;
    private OutputStream out;
    private final Route route;
    private Socket socket;
    private SpdyConnection spdyConnection;

    static {
        NPN_PROTOCOLS = new byte[]{(byte) 6, (byte) 115, (byte) 112, (byte) 100, (byte) 121, (byte) 47, (byte) 51, (byte) 8, (byte) 104, (byte) 116, (byte) 116, (byte) 112, (byte) 47, (byte) 49, (byte) 46, (byte) 49};
        SPDY3 = new byte[]{(byte) 115, (byte) 112, (byte) 100, (byte) 121, (byte) 47, (byte) 51};
        HTTP_11 = new byte[]{(byte) 104, (byte) 116, (byte) 116, (byte) 112, (byte) 47, (byte) 49, (byte) 46, (byte) 49};
    }

    public Connection(Route route) {
        this.connected = false;
        this.httpMinorVersion = 1;
        this.route = route;
    }

    public void connect(int connectTimeout, int readTimeout, TunnelRequest tunnelRequest) throws IOException {
        if (this.connected) {
            throw new IllegalStateException("already connected");
        }
        this.socket = this.route.proxy.type() != Type.HTTP ? new Socket(this.route.proxy) : new Socket();
        Platform.get().connectSocket(this.socket, this.route.inetSocketAddress, connectTimeout);
        this.socket.setSoTimeout(readTimeout);
        this.in = this.socket.getInputStream();
        this.out = this.socket.getOutputStream();
        if (this.route.address.sslSocketFactory != null) {
            upgradeToTls(tunnelRequest);
        } else {
            streamWrapper();
        }
        this.connected = true;
    }

    private void upgradeToTls(TunnelRequest tunnelRequest) throws IOException {
        boolean useNpn;
        Platform platform = Platform.get();
        if (requiresTunnel()) {
            makeTunnel(tunnelRequest);
        }
        this.socket = this.route.address.sslSocketFactory.createSocket(this.socket, this.route.address.uriHost, this.route.address.uriPort, true);
        SSLSocket sslSocket = this.socket;
        if (this.route.modernTls) {
            platform.enableTlsExtensions(sslSocket, this.route.address.uriHost);
        } else {
            platform.supportTlsIntolerantServer(sslSocket);
        }
        if (this.route.modernTls && this.route.address.transports.contains("spdy/3")) {
            useNpn = true;
        } else {
            useNpn = false;
        }
        if (useNpn) {
            platform.setNpnProtocols(sslSocket, NPN_PROTOCOLS);
        }
        sslSocket.startHandshake();
        if (this.route.address.hostnameVerifier.verify(this.route.address.uriHost, sslSocket.getSession())) {
            this.out = sslSocket.getOutputStream();
            this.in = sslSocket.getInputStream();
            streamWrapper();
            if (useNpn) {
                byte[] selectedProtocol = platform.getNpnSelectedProtocol(sslSocket);
                if (selectedProtocol == null) {
                    return;
                }
                if (Arrays.equals(selectedProtocol, SPDY3)) {
                    sslSocket.setSoTimeout(0);
                    this.spdyConnection = new Builder(this.route.address.getUriHost(), true, this.in, this.out).build();
                    this.spdyConnection.sendConnectionHeader();
                    return;
                } else if (!Arrays.equals(selectedProtocol, HTTP_11)) {
                    throw new IOException("Unexpected NPN transport " + new String(selectedProtocol, "ISO-8859-1"));
                } else {
                    return;
                }
            }
            return;
        }
        throw new IOException("Hostname '" + this.route.address.uriHost + "' was not verified");
    }

    public boolean isConnected() {
        return this.connected;
    }

    public void close() throws IOException {
        this.socket.close();
    }

    public Route getRoute() {
        return this.route;
    }

    public Socket getSocket() {
        return this.socket;
    }

    public boolean isAlive() {
        return (this.socket.isClosed() || this.socket.isInputShutdown() || this.socket.isOutputShutdown()) ? false : true;
    }

    public boolean isReadable() {
        int readTimeout;
        if (!(this.in instanceof BufferedInputStream) || isSpdy()) {
            return true;
        }
        BufferedInputStream bufferedInputStream = this.in;
        try {
            readTimeout = this.socket.getSoTimeout();
            this.socket.setSoTimeout(1);
            bufferedInputStream.mark(1);
            if (bufferedInputStream.read() == -1) {
                this.socket.setSoTimeout(readTimeout);
                return false;
            }
            bufferedInputStream.reset();
            this.socket.setSoTimeout(readTimeout);
            return true;
        } catch (SocketTimeoutException e) {
            return true;
        } catch (IOException e2) {
            return false;
        } catch (Throwable th) {
            this.socket.setSoTimeout(readTimeout);
        }
    }

    public void resetIdleStartTime() {
        if (this.spdyConnection != null) {
            throw new IllegalStateException("spdyConnection != null");
        }
        this.idleStartTimeNs = System.nanoTime();
    }

    public boolean isIdle() {
        return this.spdyConnection == null || this.spdyConnection.isIdle();
    }

    public boolean isExpired(long keepAliveDurationNs) {
        return getIdleStartTimeNs() < System.nanoTime() - keepAliveDurationNs;
    }

    public long getIdleStartTimeNs() {
        return this.spdyConnection == null ? this.idleStartTimeNs : this.spdyConnection.getIdleStartTimeNs();
    }

    public Object newTransport(HttpEngine httpEngine) throws IOException {
        return this.spdyConnection != null ? new SpdyTransport(httpEngine, this.spdyConnection) : new HttpTransport(httpEngine, this.out, this.in);
    }

    public boolean isSpdy() {
        return this.spdyConnection != null;
    }

    public SpdyConnection getSpdyConnection() {
        return this.spdyConnection;
    }

    public int getHttpMinorVersion() {
        return this.httpMinorVersion;
    }

    public void setHttpMinorVersion(int httpMinorVersion) {
        this.httpMinorVersion = httpMinorVersion;
    }

    public boolean requiresTunnel() {
        return this.route.address.sslSocketFactory != null && this.route.proxy.type() == Type.HTTP;
    }

    public void updateReadTimeout(int newTimeout) throws IOException {
        if (this.connected) {
            this.socket.setSoTimeout(newTimeout);
            return;
        }
        throw new IllegalStateException("updateReadTimeout - not connected");
    }

    private void makeTunnel(TunnelRequest tunnelRequest) throws IOException {
        RawHeaders requestHeaders = tunnelRequest.getRequestHeaders();
        while (true) {
            this.out.write(requestHeaders.toBytes());
            RawHeaders responseHeaders = RawHeaders.fromBytes(this.in);
            switch (responseHeaders.getResponseCode()) {
                case 200:
                    return;
                case 407:
                    RawHeaders requestHeaders2 = new RawHeaders(requestHeaders);
                    if (HttpAuthenticator.processAuthHeader(this.route.address.authenticator, 407, responseHeaders, requestHeaders2, this.route.proxy, new URL("https", tunnelRequest.host, tunnelRequest.port, "/"))) {
                        requestHeaders = requestHeaders2;
                    } else {
                        throw new IOException("Failed to authenticate with proxy");
                    }
                default:
                    throw new IOException("Unexpected response code for CONNECT: " + responseHeaders.getResponseCode());
            }
        }
    }

    private void streamWrapper() throws IOException {
        this.in = new BufferedInputStream(this.in, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
        this.out = new BufferedOutputStream(this.out, AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
    }
}
