package com.squareup.okhttp;

import com.squareup.okhttp.Response.Receiver;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.http.HttpAuthenticator;
import com.squareup.okhttp.internal.http.HttpURLConnectionImpl;
import com.squareup.okhttp.internal.http.HttpsURLConnectionImpl;
import com.squareup.okhttp.internal.http.OkResponseCacheAdapter;
import com.squareup.okhttp.internal.tls.OkHostnameVerifier;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ResponseCache;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public final class OkHttpClient implements URLStreamHandlerFactory {
    private static final List<String> DEFAULT_TRANSPORTS;
    private OkAuthenticator authenticator;
    private int connectTimeout;
    private ConnectionPool connectionPool;
    private CookieHandler cookieHandler;
    private final Dispatcher dispatcher;
    private boolean followProtocolRedirects;
    private HostnameVerifier hostnameVerifier;
    private Proxy proxy;
    private ProxySelector proxySelector;
    private int readTimeout;
    private ResponseCache responseCache;
    private final RouteDatabase routeDatabase;
    private SSLSocketFactory sslSocketFactory;
    private List<String> transports;

    /* renamed from: com.squareup.okhttp.OkHttpClient.1 */
    class C01041 extends URLStreamHandler {
        final /* synthetic */ String val$protocol;

        C01041(String str) {
            this.val$protocol = str;
        }

        protected URLConnection openConnection(URL url) {
            return OkHttpClient.this.open(url);
        }

        protected URLConnection openConnection(URL url, Proxy proxy) {
            return OkHttpClient.this.open(url, proxy);
        }

        protected int getDefaultPort() {
            if (this.val$protocol.equals("http")) {
                return 80;
            }
            if (this.val$protocol.equals("https")) {
                return 443;
            }
            throw new AssertionError();
        }
    }

    static {
        DEFAULT_TRANSPORTS = Util.immutableList(Arrays.asList(new String[]{"spdy/3", "http/1.1"}));
    }

    public OkHttpClient() {
        this.followProtocolRedirects = true;
        this.routeDatabase = new RouteDatabase();
        this.dispatcher = new Dispatcher();
    }

    private OkHttpClient(OkHttpClient copyFrom) {
        this.followProtocolRedirects = true;
        this.routeDatabase = copyFrom.routeDatabase;
        this.dispatcher = copyFrom.dispatcher;
    }

    public void setConnectTimeout(long timeout, TimeUnit unit) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout < 0");
        } else if (unit == null) {
            throw new IllegalArgumentException("unit == null");
        } else {
            long millis = unit.toMillis(timeout);
            if (millis > 2147483647L) {
                throw new IllegalArgumentException("Timeout too large.");
            }
            this.connectTimeout = (int) millis;
        }
    }

    public int getConnectTimeout() {
        return this.connectTimeout;
    }

    public void setReadTimeout(long timeout, TimeUnit unit) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout < 0");
        } else if (unit == null) {
            throw new IllegalArgumentException("unit == null");
        } else {
            long millis = unit.toMillis(timeout);
            if (millis > 2147483647L) {
                throw new IllegalArgumentException("Timeout too large.");
            }
            this.readTimeout = (int) millis;
        }
    }

    public int getReadTimeout() {
        return this.readTimeout;
    }

    public OkHttpClient setProxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public OkHttpClient setProxySelector(ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
        return this;
    }

    public ProxySelector getProxySelector() {
        return this.proxySelector;
    }

    public OkHttpClient setCookieHandler(CookieHandler cookieHandler) {
        this.cookieHandler = cookieHandler;
        return this;
    }

    public CookieHandler getCookieHandler() {
        return this.cookieHandler;
    }

    public OkHttpClient setResponseCache(ResponseCache responseCache) {
        this.responseCache = responseCache;
        return this;
    }

    public ResponseCache getResponseCache() {
        return this.responseCache;
    }

    public OkResponseCache getOkResponseCache() {
        if (this.responseCache instanceof HttpResponseCache) {
            return ((HttpResponseCache) this.responseCache).okResponseCache;
        }
        if (this.responseCache != null) {
            return new OkResponseCacheAdapter(this.responseCache);
        }
        return null;
    }

    public OkHttpClient setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
        return this;
    }

    public SSLSocketFactory getSslSocketFactory() {
        return this.sslSocketFactory;
    }

    public OkHttpClient setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    public HostnameVerifier getHostnameVerifier() {
        return this.hostnameVerifier;
    }

    public OkHttpClient setAuthenticator(OkAuthenticator authenticator) {
        this.authenticator = authenticator;
        return this;
    }

    public OkAuthenticator getAuthenticator() {
        return this.authenticator;
    }

    public OkHttpClient setConnectionPool(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
        return this;
    }

    public ConnectionPool getConnectionPool() {
        return this.connectionPool;
    }

    public OkHttpClient setFollowProtocolRedirects(boolean followProtocolRedirects) {
        this.followProtocolRedirects = followProtocolRedirects;
        return this;
    }

    public boolean getFollowProtocolRedirects() {
        return this.followProtocolRedirects;
    }

    public RouteDatabase getRoutesDatabase() {
        return this.routeDatabase;
    }

    public OkHttpClient setTransports(List<String> transports) {
        transports = Util.immutableList(transports);
        if (!transports.contains("http/1.1")) {
            throw new IllegalArgumentException("transports doesn't contain http/1.1: " + transports);
        } else if (transports.contains(null)) {
            throw new IllegalArgumentException("transports must not contain null");
        } else if (transports.contains("")) {
            throw new IllegalArgumentException("transports contains an empty string");
        } else {
            this.transports = transports;
            return this;
        }
    }

    public List<String> getTransports() {
        return this.transports;
    }

    void enqueue(Request request, Receiver responseReceiver) {
        this.dispatcher.enqueue(copyWithDefaults(), request, responseReceiver);
    }

    void cancel(Object tag) {
        this.dispatcher.cancel(tag);
    }

    public HttpURLConnection open(URL url) {
        return open(url, this.proxy);
    }

    HttpURLConnection open(URL url, Proxy proxy) {
        String protocol = url.getProtocol();
        OkHttpClient copy = copyWithDefaults();
        copy.proxy = proxy;
        if (protocol.equals("http")) {
            return new HttpURLConnectionImpl(url, copy);
        }
        if (protocol.equals("https")) {
            return new HttpsURLConnectionImpl(url, copy);
        }
        throw new IllegalArgumentException("Unexpected protocol: " + protocol);
    }

    private OkHttpClient copyWithDefaults() {
        OkHttpClient result = new OkHttpClient(this);
        result.proxy = this.proxy;
        result.proxySelector = this.proxySelector != null ? this.proxySelector : ProxySelector.getDefault();
        result.cookieHandler = this.cookieHandler != null ? this.cookieHandler : CookieHandler.getDefault();
        result.responseCache = this.responseCache != null ? this.responseCache : ResponseCache.getDefault();
        result.sslSocketFactory = this.sslSocketFactory != null ? this.sslSocketFactory : HttpsURLConnection.getDefaultSSLSocketFactory();
        result.hostnameVerifier = this.hostnameVerifier != null ? this.hostnameVerifier : OkHostnameVerifier.INSTANCE;
        result.authenticator = this.authenticator != null ? this.authenticator : HttpAuthenticator.SYSTEM_DEFAULT;
        result.connectionPool = this.connectionPool != null ? this.connectionPool : ConnectionPool.getDefault();
        result.followProtocolRedirects = this.followProtocolRedirects;
        result.transports = this.transports != null ? this.transports : DEFAULT_TRANSPORTS;
        result.connectTimeout = this.connectTimeout;
        result.readTimeout = this.readTimeout;
        return result;
    }

    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (protocol.equals("http") || protocol.equals("https")) {
            return new C01041(protocol);
        }
        return null;
    }
}
