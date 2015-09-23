package com.squareup.okhttp;

import com.squareup.okhttp.Failure.Builder;
import com.squareup.okhttp.Request.Body;
import com.squareup.okhttp.Response.Receiver;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.http.HttpAuthenticator;
import com.squareup.okhttp.internal.http.HttpEngine;
import com.squareup.okhttp.internal.http.HttpTransport;
import com.squareup.okhttp.internal.http.HttpURLConnectionImpl;
import com.squareup.okhttp.internal.http.HttpsEngine;
import com.squareup.okhttp.internal.http.Policy;
import com.squareup.okhttp.internal.http.RawHeaders;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;

final class Job implements Runnable, Policy {
    private final OkHttpClient client;
    private final Dispatcher dispatcher;
    private Request request;
    private final Receiver responseReceiver;

    public Job(Dispatcher dispatcher, OkHttpClient client, Request request, Receiver responseReceiver) {
        this.dispatcher = dispatcher;
        this.client = client;
        this.request = request;
        this.responseReceiver = responseReceiver;
    }

    public int getChunkLength() {
        return this.request.body().contentLength() == -1 ? HttpTransport.DEFAULT_CHUNK_LENGTH : -1;
    }

    public long getFixedContentLength() {
        return this.request.body().contentLength();
    }

    public boolean getUseCaches() {
        return false;
    }

    public HttpURLConnection getHttpConnectionToCache() {
        return null;
    }

    public URL getURL() {
        return this.request.url();
    }

    public long getIfModifiedSince() {
        return 0;
    }

    public boolean usingProxy() {
        return false;
    }

    public void setSelectedProxy(Proxy proxy) {
    }

    Object tag() {
        return this.request.tag();
    }

    public void run() {
        try {
            this.responseReceiver.onResponse(execute());
        } catch (IOException e) {
            this.responseReceiver.onFailure(new Builder().request(this.request).exception(e).build());
        } finally {
            this.dispatcher.finished(this);
        }
    }

    private Response execute() throws IOException {
        Connection connection = null;
        Response redirectedBy = null;
        while (true) {
            HttpEngine engine = newEngine(connection);
            Body body = this.request.body();
            if (body != null) {
                MediaType contentType = body.contentType();
                if (contentType == null) {
                    break;
                } else if (engine.getRequestHeaders().getContentType() == null) {
                    engine.getRequestHeaders().setContentType(contentType.toString());
                }
            }
            engine.sendRequest();
            if (body != null) {
                body.writeTo(engine.getRequestBody());
            }
            engine.readResponse();
            Response response = new Response.Builder(this.request, engine.getResponseCode()).rawHeaders(engine.getResponseHeaders().getHeaders()).body(new RealResponseBody(engine.getResponseHeaders(), engine.getResponseBody())).redirectedBy(redirectedBy).build();
            Request redirect = processResponse(engine, response);
            if (redirect == null) {
                engine.automaticallyReleaseConnectionToPool();
                return response;
            }
            connection = sameConnection(this.request, redirect) ? engine.getConnection() : null;
            redirectedBy = response;
            this.request = redirect;
        }
        throw new IllegalStateException("contentType == null");
    }

    HttpEngine newEngine(Connection connection) throws IOException {
        String protocol = this.request.url().getProtocol();
        RawHeaders requestHeaders = this.request.rawHeaders();
        if (protocol.equals("http")) {
            return new HttpEngine(this.client, this, this.request.method(), requestHeaders, connection, null);
        } else if (protocol.equals("https")) {
            return new HttpsEngine(this.client, this, this.request.method(), requestHeaders, connection, null);
        } else {
            throw new AssertionError();
        }
    }

    private Request processResponse(HttpEngine engine, Response response) throws IOException {
        Request request = response.request();
        Proxy selectedProxy = engine.getConnection() != null ? engine.getConnection().getRoute().getProxy() : this.client.getProxy();
        int responseCode = response.code();
        switch (responseCode) {
            case 300:
            case 301:
            case 302:
            case 303:
            case HttpURLConnectionImpl.HTTP_TEMP_REDIRECT /*307*/:
                String method = request.method();
                if (responseCode == HttpURLConnectionImpl.HTTP_TEMP_REDIRECT && !method.equals("GET") && !method.equals("HEAD")) {
                    return null;
                }
                String location = response.header("Location");
                if (location == null) {
                    return null;
                }
                URL url = new URL(request.url(), location);
                if (url.getProtocol().equals("https") || url.getProtocol().equals("http")) {
                    return this.request.newBuilder().url(url).build();
                }
                return null;
            case 401:
                break;
            case 407:
                if (selectedProxy.type() != Type.HTTP) {
                    throw new ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy");
                }
                break;
            default:
                return null;
        }
        RawHeaders successorRequestHeaders = request.rawHeaders();
        return HttpAuthenticator.processAuthHeader(this.client.getAuthenticator(), response.code(), response.rawHeaders(), successorRequestHeaders, selectedProxy, this.request.url()) ? request.newBuilder().rawHeaders(successorRequestHeaders).build() : null;
    }

    private boolean sameConnection(Request a, Request b) {
        return a.url().getHost().equals(b.url().getHost()) && Util.getEffectivePort(a.url()) == Util.getEffectivePort(b.url()) && a.url().getProtocol().equals(b.url().getProtocol());
    }
}
