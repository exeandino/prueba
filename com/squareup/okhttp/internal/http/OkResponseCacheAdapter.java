package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.OkResponseCache;
import com.squareup.okhttp.ResponseSource;
import java.io.IOException;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

public final class OkResponseCacheAdapter implements OkResponseCache {
    private final ResponseCache responseCache;

    public OkResponseCacheAdapter(ResponseCache responseCache) {
        this.responseCache = responseCache;
    }

    public CacheResponse get(URI uri, String requestMethod, Map<String, List<String>> requestHeaders) throws IOException {
        return this.responseCache.get(uri, requestMethod, requestHeaders);
    }

    public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        return this.responseCache.put(uri, urlConnection);
    }

    public void maybeRemove(String requestMethod, URI uri) throws IOException {
    }

    public void update(CacheResponse conditionalCacheHit, HttpURLConnection connection) throws IOException {
    }

    public void trackConditionalCacheHit() {
    }

    public void trackResponse(ResponseSource source) {
    }
}
