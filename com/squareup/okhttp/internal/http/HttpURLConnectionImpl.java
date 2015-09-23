package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.Connection;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.internal.Platform;
import com.squareup.okhttp.internal.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketPermission;
import java.net.URL;
import java.security.Permission;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLHandshakeException;

public class HttpURLConnectionImpl extends HttpURLConnection implements Policy {
    public static final int HTTP_TEMP_REDIRECT = 307;
    private static final int MAX_REDIRECTS = 20;
    final OkHttpClient client;
    private long fixedContentLength;
    protected HttpEngine httpEngine;
    protected IOException httpEngineFailure;
    private final RawHeaders rawRequestHeaders;
    private int redirectionCount;
    private Proxy selectedProxy;

    enum Retry {
        NONE,
        SAME_CONNECTION,
        DIFFERENT_CONNECTION
    }

    public HttpURLConnectionImpl(URL url, OkHttpClient client) {
        super(url);
        this.rawRequestHeaders = new RawHeaders();
        this.fixedContentLength = -1;
        this.client = client;
    }

    public final void connect() throws IOException {
        initHttpEngine();
        do {
        } while (!execute(false));
    }

    public final void disconnect() {
        if (this.httpEngine != null) {
            if (this.httpEngine.hasResponse()) {
                Util.closeQuietly(this.httpEngine.getResponseBody());
            }
            this.httpEngine.release(true);
        }
    }

    public final InputStream getErrorStream() {
        InputStream inputStream = null;
        try {
            HttpEngine response = getResponse();
            if (response.hasResponseBody() && response.getResponseCode() >= 400) {
                inputStream = response.getResponseBody();
            }
        } catch (IOException e) {
        }
        return inputStream;
    }

    public final String getHeaderField(int position) {
        try {
            return getResponse().getResponseHeaders().getHeaders().getValue(position);
        } catch (IOException e) {
            return null;
        }
    }

    public final String getHeaderField(String fieldName) {
        try {
            RawHeaders rawHeaders = getResponse().getResponseHeaders().getHeaders();
            return fieldName == null ? rawHeaders.getStatusLine() : rawHeaders.get(fieldName);
        } catch (IOException e) {
            return null;
        }
    }

    public final String getHeaderFieldKey(int position) {
        try {
            return getResponse().getResponseHeaders().getHeaders().getFieldName(position);
        } catch (IOException e) {
            return null;
        }
    }

    public final Map<String, List<String>> getHeaderFields() {
        try {
            return getResponse().getResponseHeaders().getHeaders().toMultimap(true);
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    public final Map<String, List<String>> getRequestProperties() {
        if (!this.connected) {
            return this.rawRequestHeaders.toMultimap(false);
        }
        throw new IllegalStateException("Cannot access request header fields after connection is set");
    }

    public final InputStream getInputStream() throws IOException {
        if (this.doInput) {
            HttpEngine response = getResponse();
            if (getResponseCode() >= 400) {
                throw new FileNotFoundException(this.url.toString());
            }
            InputStream result = response.getResponseBody();
            if (result != null) {
                return result;
            }
            throw new ProtocolException("No response body exists; responseCode=" + getResponseCode());
        }
        throw new ProtocolException("This protocol does not support input");
    }

    public final OutputStream getOutputStream() throws IOException {
        connect();
        OutputStream out = this.httpEngine.getRequestBody();
        if (out == null) {
            throw new ProtocolException("method does not support a request body: " + this.method);
        } else if (!this.httpEngine.hasResponse()) {
            return out;
        } else {
            throw new ProtocolException("cannot write request body after response has been read");
        }
    }

    public final Permission getPermission() throws IOException {
        String hostName = getURL().getHost();
        int hostPort = Util.getEffectivePort(getURL());
        if (usingProxy()) {
            InetSocketAddress proxyAddress = (InetSocketAddress) this.client.getProxy().address();
            hostName = proxyAddress.getHostName();
            hostPort = proxyAddress.getPort();
        }
        return new SocketPermission(hostName + ":" + hostPort, "connect, resolve");
    }

    public final String getRequestProperty(String field) {
        if (field == null) {
            return null;
        }
        return this.rawRequestHeaders.get(field);
    }

    public void setConnectTimeout(int timeoutMillis) {
        this.client.setConnectTimeout((long) timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public int getConnectTimeout() {
        return this.client.getConnectTimeout();
    }

    public void setReadTimeout(int timeoutMillis) {
        this.client.setReadTimeout((long) timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public int getReadTimeout() {
        return this.client.getReadTimeout();
    }

    private void initHttpEngine() throws IOException {
        if (this.httpEngineFailure != null) {
            throw this.httpEngineFailure;
        } else if (this.httpEngine == null) {
            this.connected = true;
            try {
                if (this.doOutput) {
                    if (this.method.equals("GET")) {
                        this.method = "POST";
                    } else if (!(this.method.equals("POST") || this.method.equals("PUT") || this.method.equals("PATCH"))) {
                        throw new ProtocolException(this.method + " does not support writing");
                    }
                }
                this.httpEngine = newHttpEngine(this.method, this.rawRequestHeaders, null, null);
            } catch (IOException e) {
                this.httpEngineFailure = e;
                throw e;
            }
        }
    }

    public HttpURLConnection getHttpConnectionToCache() {
        return this;
    }

    private HttpEngine newHttpEngine(String method, RawHeaders requestHeaders, Connection connection, RetryableOutputStream requestBody) throws IOException {
        if (this.url.getProtocol().equals("http")) {
            return new HttpEngine(this.client, this, method, requestHeaders, connection, requestBody);
        }
        if (this.url.getProtocol().equals("https")) {
            return new HttpsEngine(this.client, this, method, requestHeaders, connection, requestBody);
        }
        throw new AssertionError();
    }

    private HttpEngine getResponse() throws IOException {
        initHttpEngine();
        if (this.httpEngine.hasResponse()) {
            return this.httpEngine;
        }
        int responseCode;
        while (true) {
            if (execute(true)) {
                Retry retry = processResponseHeaders();
                if (retry != Retry.NONE) {
                    String retryMethod = this.method;
                    OutputStream requestBody = this.httpEngine.getRequestBody();
                    responseCode = this.httpEngine.getResponseCode();
                    if (responseCode == 300 || responseCode == 301 || responseCode == 302 || responseCode == 303) {
                        retryMethod = "GET";
                        requestBody = null;
                    }
                    if (requestBody != null && !(requestBody instanceof RetryableOutputStream)) {
                        break;
                    }
                    if (retry == Retry.DIFFERENT_CONNECTION) {
                        this.httpEngine.automaticallyReleaseConnectionToPool();
                    }
                    this.httpEngine.release(false);
                    this.httpEngine = newHttpEngine(retryMethod, this.rawRequestHeaders, this.httpEngine.getConnection(), (RetryableOutputStream) requestBody);
                    if (requestBody == null) {
                        this.httpEngine.getRequestHeaders().removeContentLength();
                    }
                } else {
                    this.httpEngine.automaticallyReleaseConnectionToPool();
                    return this.httpEngine;
                }
            }
        }
        throw new HttpRetryException("Cannot retry streamed HTTP body", responseCode);
    }

    private boolean execute(boolean readResponse) throws IOException {
        try {
            this.httpEngine.sendRequest();
            if (readResponse) {
                this.httpEngine.readResponse();
            }
            return true;
        } catch (IOException e) {
            if (handleFailure(e)) {
                return false;
            }
            throw e;
        }
    }

    private boolean handleFailure(IOException e) throws IOException {
        RouteSelector routeSelector = this.httpEngine.routeSelector;
        if (!(routeSelector == null || this.httpEngine.connection == null)) {
            routeSelector.connectFailed(this.httpEngine.connection, e);
        }
        OutputStream requestBody = this.httpEngine.getRequestBody();
        boolean canRetryRequestBody;
        if (requestBody == null || (requestBody instanceof RetryableOutputStream)) {
            canRetryRequestBody = true;
        } else {
            canRetryRequestBody = false;
        }
        if (!(routeSelector == null && this.httpEngine.connection == null) && ((routeSelector == null || routeSelector.hasNext()) && isRecoverable(e) && canRetryRequestBody)) {
            this.httpEngine.release(true);
            this.httpEngine = newHttpEngine(this.method, this.rawRequestHeaders, null, (RetryableOutputStream) requestBody);
            this.httpEngine.routeSelector = routeSelector;
            return true;
        }
        this.httpEngineFailure = e;
        return false;
    }

    private boolean isRecoverable(IOException e) {
        boolean sslFailure;
        if ((e instanceof SSLHandshakeException) && (e.getCause() instanceof CertificateException)) {
            sslFailure = true;
        } else {
            sslFailure = false;
        }
        return (sslFailure || (e instanceof ProtocolException)) ? false : true;
    }

    public HttpEngine getHttpEngine() {
        return this.httpEngine;
    }

    private Retry processResponseHeaders() throws IOException {
        Proxy selectedProxy = this.httpEngine.connection != null ? this.httpEngine.connection.getRoute().getProxy() : this.client.getProxy();
        int responseCode = getResponseCode();
        switch (responseCode) {
            case 300:
            case 301:
            case 302:
            case 303:
            case HTTP_TEMP_REDIRECT /*307*/:
                if (!getInstanceFollowRedirects()) {
                    return Retry.NONE;
                }
                int i = this.redirectionCount + 1;
                this.redirectionCount = i;
                if (i > MAX_REDIRECTS) {
                    throw new ProtocolException("Too many redirects: " + this.redirectionCount);
                } else if (responseCode == HTTP_TEMP_REDIRECT && !this.method.equals("GET") && !this.method.equals("HEAD")) {
                    return Retry.NONE;
                } else {
                    String location = getHeaderField("Location");
                    if (location == null) {
                        return Retry.NONE;
                    }
                    URL previousUrl = this.url;
                    this.url = new URL(previousUrl, location);
                    if (!this.url.getProtocol().equals("https") && !this.url.getProtocol().equals("http")) {
                        return Retry.NONE;
                    }
                    boolean sameProtocol = previousUrl.getProtocol().equals(this.url.getProtocol());
                    if (!sameProtocol && !this.client.getFollowProtocolRedirects()) {
                        return Retry.NONE;
                    }
                    boolean sameHost = previousUrl.getHost().equals(this.url.getHost());
                    boolean samePort = Util.getEffectivePort(previousUrl) == Util.getEffectivePort(this.url);
                    if (sameHost && samePort && sameProtocol) {
                        return Retry.SAME_CONNECTION;
                    }
                    return Retry.DIFFERENT_CONNECTION;
                }
            case 401:
                break;
            case 407:
                if (selectedProxy.type() != Type.HTTP) {
                    throw new ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy");
                }
                break;
            default:
                return Retry.NONE;
        }
        return HttpAuthenticator.processAuthHeader(this.client.getAuthenticator(), getResponseCode(), this.httpEngine.getResponseHeaders().getHeaders(), this.rawRequestHeaders, selectedProxy, this.url) ? Retry.SAME_CONNECTION : Retry.NONE;
    }

    public final long getFixedContentLength() {
        return this.fixedContentLength;
    }

    public final int getChunkLength() {
        return this.chunkLength;
    }

    public final boolean usingProxy() {
        if (this.selectedProxy != null) {
            return isValidNonDirectProxy(this.selectedProxy);
        }
        return isValidNonDirectProxy(this.client.getProxy());
    }

    private static boolean isValidNonDirectProxy(Proxy proxy) {
        return (proxy == null || proxy.type() == Type.DIRECT) ? false : true;
    }

    public String getResponseMessage() throws IOException {
        return getResponse().getResponseHeaders().getHeaders().getResponseMessage();
    }

    public final int getResponseCode() throws IOException {
        return getResponse().getResponseCode();
    }

    public final void setRequestProperty(String field, String newValue) {
        if (this.connected) {
            throw new IllegalStateException("Cannot set request property after connection is made");
        } else if (field == null) {
            throw new NullPointerException("field == null");
        } else if (newValue == null) {
            Platform.get().logW("Ignoring header " + field + " because its value was null.");
        } else if ("X-Android-Transports".equals(field)) {
            setTransports(newValue, false);
        } else {
            this.rawRequestHeaders.set(field, newValue);
        }
    }

    public final void addRequestProperty(String field, String value) {
        if (this.connected) {
            throw new IllegalStateException("Cannot add request property after connection is made");
        } else if (field == null) {
            throw new NullPointerException("field == null");
        } else if (value == null) {
            Platform.get().logW("Ignoring header " + field + " because its value was null.");
        } else if ("X-Android-Transports".equals(field)) {
            setTransports(value, true);
        } else {
            this.rawRequestHeaders.add(field, value);
        }
    }

    private void setTransports(String transportsString, boolean append) {
        List<String> transportsList = new ArrayList();
        if (append) {
            transportsList.addAll(this.client.getTransports());
        }
        for (String transport : transportsString.split(",", -1)) {
            transportsList.add(transport);
        }
        this.client.setTransports(transportsList);
    }

    public void setFixedLengthStreamingMode(int contentLength) {
        setFixedLengthStreamingMode((long) contentLength);
    }

    public void setFixedLengthStreamingMode(long contentLength) {
        if (this.connected) {
            throw new IllegalStateException("Already connected");
        } else if (this.chunkLength > 0) {
            throw new IllegalStateException("Already in chunked mode");
        } else if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength < 0");
        } else {
            this.fixedContentLength = contentLength;
            this.fixedContentLength = (int) Math.min(contentLength, 2147483647L);
        }
    }

    public final void setSelectedProxy(Proxy proxy) {
        this.selectedProxy = proxy;
    }
}
