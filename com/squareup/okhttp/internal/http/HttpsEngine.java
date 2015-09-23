package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.Connection;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.TunnelRequest;
import com.squareup.okhttp.internal.Util;
import java.io.IOException;
import java.net.CacheResponse;
import java.net.SecureCacheResponse;
import java.net.URL;
import javax.net.ssl.SSLSocket;

public final class HttpsEngine extends HttpEngine {
    private SSLSocket sslSocket;

    public HttpsEngine(OkHttpClient client, Policy policy, String method, RawHeaders requestHeaders, Connection connection, RetryableOutputStream requestBody) throws IOException {
        super(client, policy, method, requestHeaders, connection, requestBody);
        this.sslSocket = connection != null ? (SSLSocket) connection.getSocket() : null;
    }

    protected void connected(Connection connection) {
        this.sslSocket = (SSLSocket) connection.getSocket();
        super.connected(connection);
    }

    protected boolean acceptCacheResponseType(CacheResponse cacheResponse) {
        return cacheResponse instanceof SecureCacheResponse;
    }

    protected boolean includeAuthorityInRequestLine() {
        return false;
    }

    public SSLSocket getSslSocket() {
        return this.sslSocket;
    }

    protected TunnelRequest getTunnelConfig() {
        String userAgent = this.requestHeaders.getUserAgent();
        if (userAgent == null) {
            userAgent = HttpEngine.getDefaultUserAgent();
        }
        URL url = this.policy.getURL();
        return new TunnelRequest(url.getHost(), Util.getEffectivePort(url), userAgent, this.requestHeaders.getProxyAuthorization());
    }
}
