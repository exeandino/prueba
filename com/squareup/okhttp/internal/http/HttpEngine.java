package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.Address;
import com.squareup.okhttp.Connection;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkResponseCache;
import com.squareup.okhttp.ResponseSource;
import com.squareup.okhttp.TunnelRequest;
import com.squareup.okhttp.internal.Dns;
import com.squareup.okhttp.internal.Platform;
import com.squareup.okhttp.internal.Util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.Proxy.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

public class HttpEngine {
    private static final CacheResponse GATEWAY_TIMEOUT_RESPONSE;
    public static final int HTTP_CONTINUE = 100;
    private boolean automaticallyReleaseConnectionToPool;
    private CacheRequest cacheRequest;
    private CacheResponse cacheResponse;
    private InputStream cachedResponseBody;
    private ResponseHeaders cachedResponseHeaders;
    protected final OkHttpClient client;
    boolean connected;
    protected Connection connection;
    private boolean connectionReleased;
    protected final String method;
    protected final Policy policy;
    private OutputStream requestBodyOut;
    final RequestHeaders requestHeaders;
    private InputStream responseBodyIn;
    ResponseHeaders responseHeaders;
    private ResponseSource responseSource;
    private InputStream responseTransferIn;
    protected RouteSelector routeSelector;
    long sentRequestMillis;
    private boolean transparentGzip;
    private Transport transport;
    final URI uri;

    /* renamed from: com.squareup.okhttp.internal.http.HttpEngine.1 */
    static class C01131 extends CacheResponse {
        C01131() {
        }

        public Map<String, List<String>> getHeaders() throws IOException {
            Map<String, List<String>> result = new HashMap();
            result.put(null, Collections.singletonList("HTTP/1.1 504 Gateway Timeout"));
            return result;
        }

        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(Util.EMPTY_BYTE_ARRAY);
        }
    }

    static {
        GATEWAY_TIMEOUT_RESPONSE = new C01131();
    }

    public HttpEngine(OkHttpClient client, Policy policy, String method, RawHeaders requestHeaders, Connection connection, RetryableOutputStream requestBodyOut) throws IOException {
        this.sentRequestMillis = -1;
        this.client = client;
        this.policy = policy;
        this.method = method;
        this.connection = connection;
        this.requestBodyOut = requestBodyOut;
        try {
            this.uri = Platform.get().toUriLenient(policy.getURL());
            this.requestHeaders = new RequestHeaders(this.uri, new RawHeaders(requestHeaders));
        } catch (URISyntaxException e) {
            throw new IOException(e.getMessage());
        }
    }

    public URI getUri() {
        return this.uri;
    }

    public final void sendRequest() throws IOException {
        if (this.responseSource == null) {
            prepareRawRequestHeaders();
            initResponseSource();
            OkResponseCache responseCache = this.client.getOkResponseCache();
            if (responseCache != null) {
                responseCache.trackResponse(this.responseSource);
            }
            if (this.requestHeaders.isOnlyIfCached() && this.responseSource.requiresConnection()) {
                if (this.responseSource == ResponseSource.CONDITIONAL_CACHE) {
                    Util.closeQuietly(this.cachedResponseBody);
                }
                this.responseSource = ResponseSource.CACHE;
                this.cacheResponse = GATEWAY_TIMEOUT_RESPONSE;
                setResponse(new ResponseHeaders(this.uri, RawHeaders.fromMultimap(this.cacheResponse.getHeaders(), true)), this.cacheResponse.getBody());
            }
            if (this.responseSource.requiresConnection()) {
                sendSocketRequest();
            } else if (this.connection != null) {
                this.client.getConnectionPool().recycle(this.connection);
                this.connection = null;
            }
        }
    }

    private void initResponseSource() throws IOException {
        this.responseSource = ResponseSource.NETWORK;
        if (this.policy.getUseCaches()) {
            OkResponseCache responseCache = this.client.getOkResponseCache();
            if (responseCache != null) {
                CacheResponse candidate = responseCache.get(this.uri, this.method, this.requestHeaders.getHeaders().toMultimap(false));
                if (candidate != null) {
                    Map<String, List<String>> responseHeadersMap = candidate.getHeaders();
                    this.cachedResponseBody = candidate.getBody();
                    if (!acceptCacheResponseType(candidate) || responseHeadersMap == null || this.cachedResponseBody == null) {
                        Util.closeQuietly(this.cachedResponseBody);
                        return;
                    }
                    this.cachedResponseHeaders = new ResponseHeaders(this.uri, RawHeaders.fromMultimap(responseHeadersMap, true));
                    this.responseSource = this.cachedResponseHeaders.chooseResponseSource(System.currentTimeMillis(), this.requestHeaders);
                    if (this.responseSource == ResponseSource.CACHE) {
                        this.cacheResponse = candidate;
                        setResponse(this.cachedResponseHeaders, this.cachedResponseBody);
                    } else if (this.responseSource == ResponseSource.CONDITIONAL_CACHE) {
                        this.cacheResponse = candidate;
                    } else if (this.responseSource == ResponseSource.NETWORK) {
                        Util.closeQuietly(this.cachedResponseBody);
                    } else {
                        throw new AssertionError();
                    }
                }
            }
        }
    }

    private void sendSocketRequest() throws IOException {
        if (this.connection == null) {
            connect();
        }
        if (this.transport != null) {
            throw new IllegalStateException();
        }
        this.transport = (Transport) this.connection.newTransport(this);
        if (hasRequestBody() && this.requestBodyOut == null) {
            this.requestBodyOut = this.transport.createRequestBody();
        }
    }

    protected final void connect() throws IOException {
        if (this.connection == null) {
            if (this.routeSelector == null) {
                String uriHost = this.uri.getHost();
                if (uriHost == null) {
                    throw new UnknownHostException(this.uri.toString());
                }
                SSLSocketFactory sslSocketFactory = null;
                HostnameVerifier hostnameVerifier = null;
                if (this.uri.getScheme().equalsIgnoreCase("https")) {
                    sslSocketFactory = this.client.getSslSocketFactory();
                    hostnameVerifier = this.client.getHostnameVerifier();
                }
                Address address = new Address(uriHost, Util.getEffectivePort(this.uri), sslSocketFactory, hostnameVerifier, this.client.getAuthenticator(), this.client.getProxy(), this.client.getTransports());
                this.routeSelector = new RouteSelector(address, this.uri, this.client.getProxySelector(), this.client.getConnectionPool(), Dns.DEFAULT, this.client.getRoutesDatabase());
            }
            this.connection = this.routeSelector.next(this.method);
            if (!this.connection.isConnected()) {
                this.connection.connect(this.client.getConnectTimeout(), this.client.getReadTimeout(), getTunnelConfig());
                this.client.getConnectionPool().maybeShare(this.connection);
                this.client.getRoutesDatabase().connected(this.connection.getRoute());
            } else if (!this.connection.isSpdy()) {
                this.connection.updateReadTimeout(this.client.getReadTimeout());
            }
            connected(this.connection);
            if (this.connection.getRoute().getProxy() != this.client.getProxy()) {
                this.requestHeaders.getHeaders().setRequestLine(getRequestLine());
            }
        }
    }

    protected void connected(Connection connection) {
        this.policy.setSelectedProxy(connection.getRoute().getProxy());
        this.connected = true;
    }

    public void writingRequestHeaders() {
        if (this.sentRequestMillis != -1) {
            throw new IllegalStateException();
        }
        this.sentRequestMillis = System.currentTimeMillis();
    }

    private void setResponse(ResponseHeaders headers, InputStream body) throws IOException {
        if (this.responseBodyIn != null) {
            throw new IllegalStateException();
        }
        this.responseHeaders = headers;
        if (body != null) {
            initContentStream(body);
        }
    }

    boolean hasRequestBody() {
        return this.method.equals("POST") || this.method.equals("PUT") || this.method.equals("PATCH");
    }

    public final OutputStream getRequestBody() {
        if (this.responseSource != null) {
            return this.requestBodyOut;
        }
        throw new IllegalStateException();
    }

    public final boolean hasResponse() {
        return this.responseHeaders != null;
    }

    public final RequestHeaders getRequestHeaders() {
        return this.requestHeaders;
    }

    public final ResponseHeaders getResponseHeaders() {
        if (this.responseHeaders != null) {
            return this.responseHeaders;
        }
        throw new IllegalStateException();
    }

    public final int getResponseCode() {
        if (this.responseHeaders != null) {
            return this.responseHeaders.getHeaders().getResponseCode();
        }
        throw new IllegalStateException();
    }

    public final InputStream getResponseBody() {
        if (this.responseHeaders != null) {
            return this.responseBodyIn;
        }
        throw new IllegalStateException();
    }

    public final CacheResponse getCacheResponse() {
        return this.cacheResponse;
    }

    public final Connection getConnection() {
        return this.connection;
    }

    protected boolean acceptCacheResponseType(CacheResponse cacheResponse) {
        return true;
    }

    private void maybeCache() throws IOException {
        if (this.policy.getUseCaches()) {
            OkResponseCache responseCache = this.client.getOkResponseCache();
            if (responseCache != null) {
                HttpURLConnection connectionToCache = this.policy.getHttpConnectionToCache();
                if (this.responseHeaders.isCacheable(this.requestHeaders)) {
                    this.cacheRequest = responseCache.put(this.uri, connectionToCache);
                } else {
                    responseCache.maybeRemove(connectionToCache.getRequestMethod(), this.uri);
                }
            }
        }
    }

    public final void automaticallyReleaseConnectionToPool() {
        this.automaticallyReleaseConnectionToPool = true;
        if (this.connection != null && this.connectionReleased) {
            this.client.getConnectionPool().recycle(this.connection);
            this.connection = null;
        }
    }

    public final void release(boolean streamCanceled) {
        if (this.responseBodyIn == this.cachedResponseBody) {
            Util.closeQuietly(this.responseBodyIn);
        }
        if (!this.connectionReleased && this.connection != null) {
            this.connectionReleased = true;
            if (this.transport == null || !this.transport.makeReusable(streamCanceled, this.requestBodyOut, this.responseTransferIn)) {
                Util.closeQuietly(this.connection);
                this.connection = null;
            } else if (this.automaticallyReleaseConnectionToPool) {
                this.client.getConnectionPool().recycle(this.connection);
                this.connection = null;
            }
        }
    }

    private void initContentStream(InputStream transferStream) throws IOException {
        this.responseTransferIn = transferStream;
        if (this.transparentGzip && this.responseHeaders.isContentEncodingGzip()) {
            this.responseHeaders.stripContentEncoding();
            this.responseHeaders.stripContentLength();
            this.responseBodyIn = new GZIPInputStream(transferStream);
            return;
        }
        this.responseBodyIn = transferStream;
    }

    public final boolean hasResponseBody() {
        int responseCode = this.responseHeaders.getHeaders().getResponseCode();
        if (this.method.equals("HEAD")) {
            return false;
        }
        if ((responseCode < HTTP_CONTINUE || responseCode >= 200) && responseCode != 204 && responseCode != 304) {
            return true;
        }
        if (this.responseHeaders.getContentLength() != -1 || this.responseHeaders.isChunked()) {
            return true;
        }
        return false;
    }

    private void prepareRawRequestHeaders() throws IOException {
        this.requestHeaders.getHeaders().setRequestLine(getRequestLine());
        if (this.requestHeaders.getUserAgent() == null) {
            this.requestHeaders.setUserAgent(getDefaultUserAgent());
        }
        if (this.requestHeaders.getHost() == null) {
            this.requestHeaders.setHost(getOriginAddress(this.policy.getURL()));
        }
        if ((this.connection == null || this.connection.getHttpMinorVersion() != 0) && this.requestHeaders.getConnection() == null) {
            this.requestHeaders.setConnection("Keep-Alive");
        }
        if (this.requestHeaders.getAcceptEncoding() == null) {
            this.transparentGzip = true;
            this.requestHeaders.setAcceptEncoding("gzip");
        }
        if (hasRequestBody() && this.requestHeaders.getContentType() == null) {
            this.requestHeaders.setContentType("application/x-www-form-urlencoded");
        }
        long ifModifiedSince = this.policy.getIfModifiedSince();
        if (ifModifiedSince != 0) {
            this.requestHeaders.setIfModifiedSince(new Date(ifModifiedSince));
        }
        CookieHandler cookieHandler = this.client.getCookieHandler();
        if (cookieHandler != null) {
            this.requestHeaders.addCookies(cookieHandler.get(this.uri, this.requestHeaders.getHeaders().toMultimap(false)));
        }
    }

    String getRequestLine() {
        String protocol = (this.connection == null || this.connection.getHttpMinorVersion() != 0) ? "HTTP/1.1" : "HTTP/1.0";
        return this.method + " " + requestString() + " " + protocol;
    }

    private String requestString() {
        URL url = this.policy.getURL();
        if (includeAuthorityInRequestLine()) {
            return url.toString();
        }
        return requestPath(url);
    }

    public static String requestPath(URL url) {
        String fileOnly = url.getFile();
        if (fileOnly == null) {
            return "/";
        }
        if (fileOnly.startsWith("/")) {
            return fileOnly;
        }
        return "/" + fileOnly;
    }

    protected boolean includeAuthorityInRequestLine() {
        if (this.connection == null) {
            return this.policy.usingProxy();
        }
        return this.connection.getRoute().getProxy().type() == Type.HTTP;
    }

    public static String getDefaultUserAgent() {
        String agent = System.getProperty("http.agent");
        return agent != null ? agent : "Java" + System.getProperty("java.version");
    }

    public static String getOriginAddress(URL url) {
        int port = url.getPort();
        String result = url.getHost();
        if (port <= 0 || port == Util.getDefaultPort(url.getProtocol())) {
            return result;
        }
        return result + ":" + port;
    }

    public final void readResponse() throws IOException {
        if (hasResponse()) {
            this.responseHeaders.setResponseSource(this.responseSource);
        } else if (this.responseSource == null) {
            throw new IllegalStateException("readResponse() without sendRequest()");
        } else if (this.responseSource.requiresConnection()) {
            if (this.sentRequestMillis == -1) {
                if (this.requestBodyOut instanceof RetryableOutputStream) {
                    this.requestHeaders.setContentLength((long) ((RetryableOutputStream) this.requestBodyOut).contentLength());
                }
                this.transport.writeRequestHeaders();
            }
            if (this.requestBodyOut != null) {
                this.requestBodyOut.close();
                if (this.requestBodyOut instanceof RetryableOutputStream) {
                    this.transport.writeRequestBody((RetryableOutputStream) this.requestBodyOut);
                }
            }
            this.transport.flushRequest();
            this.responseHeaders = this.transport.readResponseHeaders();
            this.responseHeaders.setLocalTimestamps(this.sentRequestMillis, System.currentTimeMillis());
            this.responseHeaders.setResponseSource(this.responseSource);
            if (this.responseSource == ResponseSource.CONDITIONAL_CACHE) {
                if (this.cachedResponseHeaders.validate(this.responseHeaders)) {
                    release(false);
                    this.responseHeaders = this.cachedResponseHeaders.combine(this.responseHeaders);
                    OkResponseCache responseCache = this.client.getOkResponseCache();
                    responseCache.trackConditionalCacheHit();
                    responseCache.update(this.cacheResponse, this.policy.getHttpConnectionToCache());
                    initContentStream(this.cachedResponseBody);
                    return;
                }
                Util.closeQuietly(this.cachedResponseBody);
            }
            if (hasResponseBody()) {
                maybeCache();
            }
            initContentStream(this.transport.getTransferStream(this.cacheRequest));
        }
    }

    protected TunnelRequest getTunnelConfig() {
        return null;
    }

    public void receiveHeaders(RawHeaders headers) throws IOException {
        CookieHandler cookieHandler = this.client.getCookieHandler();
        if (cookieHandler != null) {
            cookieHandler.put(this.uri, headers.toMultimap(true));
        }
    }
}
