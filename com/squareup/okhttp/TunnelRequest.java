package com.squareup.okhttp;

import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.http.RawHeaders;

public final class TunnelRequest {
    final String host;
    final int port;
    final String proxyAuthorization;
    final String userAgent;

    public TunnelRequest(String host, int port, String userAgent, String proxyAuthorization) {
        if (host == null) {
            throw new NullPointerException("host == null");
        } else if (userAgent == null) {
            throw new NullPointerException("userAgent == null");
        } else {
            this.host = host;
            this.port = port;
            this.userAgent = userAgent;
            this.proxyAuthorization = proxyAuthorization;
        }
    }

    RawHeaders getRequestHeaders() {
        RawHeaders result = new RawHeaders();
        result.setRequestLine("CONNECT " + this.host + ":" + this.port + " HTTP/1.1");
        result.set("Host", this.port == Util.getDefaultPort("https") ? this.host : this.host + ":" + this.port);
        result.set("User-Agent", this.userAgent);
        if (this.proxyAuthorization != null) {
            result.set("Proxy-Authorization", this.proxyAuthorization);
        }
        result.set("Proxy-Connection", "Keep-Alive");
        return result;
    }
}
