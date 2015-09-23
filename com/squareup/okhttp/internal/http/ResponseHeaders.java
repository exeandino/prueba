package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.ResponseSource;
import com.squareup.okhttp.internal.Platform;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.http.HeaderParser.CacheControlHandler;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

public final class ResponseHeaders {
    private static final String RECEIVED_MILLIS;
    static final String RESPONSE_SOURCE;
    static final String SELECTED_TRANSPORT;
    private static final String SENT_MILLIS;
    private int ageSeconds;
    private String connection;
    private String contentEncoding;
    private long contentLength;
    private String contentType;
    private String etag;
    private Date expires;
    private final RawHeaders headers;
    private boolean isPublic;
    private Date lastModified;
    private int maxAgeSeconds;
    private boolean mustRevalidate;
    private boolean noCache;
    private boolean noStore;
    private long receivedResponseMillis;
    private int sMaxAgeSeconds;
    private long sentRequestMillis;
    private Date servedDate;
    private String transferEncoding;
    private final URI uri;
    private Set<String> varyFields;

    /* renamed from: com.squareup.okhttp.internal.http.ResponseHeaders.1 */
    class C02121 implements CacheControlHandler {
        C02121() {
        }

        public void handle(String directive, String parameter) {
            if ("no-cache".equalsIgnoreCase(directive)) {
                ResponseHeaders.this.noCache = true;
            } else if ("no-store".equalsIgnoreCase(directive)) {
                ResponseHeaders.this.noStore = true;
            } else if ("max-age".equalsIgnoreCase(directive)) {
                ResponseHeaders.this.maxAgeSeconds = HeaderParser.parseSeconds(parameter);
            } else if ("s-maxage".equalsIgnoreCase(directive)) {
                ResponseHeaders.this.sMaxAgeSeconds = HeaderParser.parseSeconds(parameter);
            } else if ("public".equalsIgnoreCase(directive)) {
                ResponseHeaders.this.isPublic = true;
            } else if ("must-revalidate".equalsIgnoreCase(directive)) {
                ResponseHeaders.this.mustRevalidate = true;
            }
        }
    }

    static {
        SENT_MILLIS = Platform.get().getPrefix() + "-Sent-Millis";
        RECEIVED_MILLIS = Platform.get().getPrefix() + "-Received-Millis";
        RESPONSE_SOURCE = Platform.get().getPrefix() + "-Response-Source";
        SELECTED_TRANSPORT = Platform.get().getPrefix() + "-Selected-Transport";
    }

    public ResponseHeaders(URI uri, RawHeaders headers) {
        this.maxAgeSeconds = -1;
        this.sMaxAgeSeconds = -1;
        this.ageSeconds = -1;
        this.varyFields = Collections.emptySet();
        this.contentLength = -1;
        this.uri = uri;
        this.headers = headers;
        CacheControlHandler handler = new C02121();
        for (int i = 0; i < headers.length(); i++) {
            String fieldName = headers.getFieldName(i);
            String value = headers.getValue(i);
            if ("Cache-Control".equalsIgnoreCase(fieldName)) {
                HeaderParser.parseCacheControl(value, handler);
            } else if ("Date".equalsIgnoreCase(fieldName)) {
                this.servedDate = HttpDate.parse(value);
            } else if ("Expires".equalsIgnoreCase(fieldName)) {
                this.expires = HttpDate.parse(value);
            } else if ("Last-Modified".equalsIgnoreCase(fieldName)) {
                this.lastModified = HttpDate.parse(value);
            } else if ("ETag".equalsIgnoreCase(fieldName)) {
                this.etag = value;
            } else if ("Pragma".equalsIgnoreCase(fieldName)) {
                if ("no-cache".equalsIgnoreCase(value)) {
                    this.noCache = true;
                }
            } else if ("Age".equalsIgnoreCase(fieldName)) {
                this.ageSeconds = HeaderParser.parseSeconds(value);
            } else if ("Vary".equalsIgnoreCase(fieldName)) {
                if (this.varyFields.isEmpty()) {
                    this.varyFields = new TreeSet(String.CASE_INSENSITIVE_ORDER);
                }
                for (String varyField : value.split(",")) {
                    this.varyFields.add(varyField.trim());
                }
            } else if ("Content-Encoding".equalsIgnoreCase(fieldName)) {
                this.contentEncoding = value;
            } else if ("Transfer-Encoding".equalsIgnoreCase(fieldName)) {
                this.transferEncoding = value;
            } else if ("Content-Length".equalsIgnoreCase(fieldName)) {
                try {
                    this.contentLength = Long.parseLong(value);
                } catch (NumberFormatException e) {
                }
            } else if ("Content-Type".equalsIgnoreCase(fieldName)) {
                this.contentType = value;
            } else if ("Connection".equalsIgnoreCase(fieldName)) {
                this.connection = value;
            } else if (SENT_MILLIS.equalsIgnoreCase(fieldName)) {
                this.sentRequestMillis = Long.parseLong(value);
            } else if (RECEIVED_MILLIS.equalsIgnoreCase(fieldName)) {
                this.receivedResponseMillis = Long.parseLong(value);
            }
        }
    }

    public boolean isContentEncodingGzip() {
        return "gzip".equalsIgnoreCase(this.contentEncoding);
    }

    public void stripContentEncoding() {
        this.contentEncoding = null;
        this.headers.removeAll("Content-Encoding");
    }

    public void stripContentLength() {
        this.contentLength = -1;
        this.headers.removeAll("Content-Length");
    }

    public boolean isChunked() {
        return "chunked".equalsIgnoreCase(this.transferEncoding);
    }

    public boolean hasConnectionClose() {
        return "close".equalsIgnoreCase(this.connection);
    }

    public URI getUri() {
        return this.uri;
    }

    public RawHeaders getHeaders() {
        return this.headers;
    }

    public Date getServedDate() {
        return this.servedDate;
    }

    public Date getLastModified() {
        return this.lastModified;
    }

    public Date getExpires() {
        return this.expires;
    }

    public boolean isNoCache() {
        return this.noCache;
    }

    public boolean isNoStore() {
        return this.noStore;
    }

    public int getMaxAgeSeconds() {
        return this.maxAgeSeconds;
    }

    public int getSMaxAgeSeconds() {
        return this.sMaxAgeSeconds;
    }

    public boolean isPublic() {
        return this.isPublic;
    }

    public boolean isMustRevalidate() {
        return this.mustRevalidate;
    }

    public String getEtag() {
        return this.etag;
    }

    public Set<String> getVaryFields() {
        return this.varyFields;
    }

    public String getContentEncoding() {
        return this.contentEncoding;
    }

    public long getContentLength() {
        return this.contentLength;
    }

    public String getContentType() {
        return this.contentType;
    }

    public String getConnection() {
        return this.connection;
    }

    public void setLocalTimestamps(long sentRequestMillis, long receivedResponseMillis) {
        this.sentRequestMillis = sentRequestMillis;
        this.headers.add(SENT_MILLIS, Long.toString(sentRequestMillis));
        this.receivedResponseMillis = receivedResponseMillis;
        this.headers.add(RECEIVED_MILLIS, Long.toString(receivedResponseMillis));
    }

    public void setResponseSource(ResponseSource responseSource) {
        this.headers.set(RESPONSE_SOURCE, responseSource.toString() + " " + this.headers.getResponseCode());
    }

    public void setTransport(String transport) {
        this.headers.set(SELECTED_TRANSPORT, transport);
    }

    private long computeAge(long nowMillis) {
        long receivedAge;
        long apparentReceivedAge = 0;
        if (this.servedDate != null) {
            apparentReceivedAge = Math.max(0, this.receivedResponseMillis - this.servedDate.getTime());
        }
        if (this.ageSeconds != -1) {
            receivedAge = Math.max(apparentReceivedAge, TimeUnit.SECONDS.toMillis((long) this.ageSeconds));
        } else {
            receivedAge = apparentReceivedAge;
        }
        return (receivedAge + (this.receivedResponseMillis - this.sentRequestMillis)) + (nowMillis - this.receivedResponseMillis);
    }

    private long computeFreshnessLifetime() {
        if (this.maxAgeSeconds != -1) {
            return TimeUnit.SECONDS.toMillis((long) this.maxAgeSeconds);
        }
        long delta;
        if (this.expires != null) {
            delta = this.expires.getTime() - (this.servedDate != null ? this.servedDate.getTime() : this.receivedResponseMillis);
            if (delta <= 0) {
                delta = 0;
            }
            return delta;
        } else if (this.lastModified == null || this.uri.getRawQuery() != null) {
            return 0;
        } else {
            delta = (this.servedDate != null ? this.servedDate.getTime() : this.sentRequestMillis) - this.lastModified.getTime();
            if (delta > 0) {
                return delta / 10;
            }
            return 0;
        }
    }

    private boolean isFreshnessLifetimeHeuristic() {
        return this.maxAgeSeconds == -1 && this.expires == null;
    }

    public boolean isCacheable(RequestHeaders request) {
        int responseCode = this.headers.getResponseCode();
        if (responseCode != 200 && responseCode != 203 && responseCode != 300 && responseCode != 301 && responseCode != 410) {
            return false;
        }
        if ((!request.hasAuthorization() || this.isPublic || this.mustRevalidate || this.sMaxAgeSeconds != -1) && !this.noStore) {
            return true;
        }
        return false;
    }

    public boolean hasVaryAll() {
        return this.varyFields.contains("*");
    }

    public boolean varyMatches(Map<String, List<String>> cachedRequest, Map<String, List<String>> newRequest) {
        for (String field : this.varyFields) {
            if (!Util.equal(cachedRequest.get(field), newRequest.get(field))) {
                return false;
            }
        }
        return true;
    }

    public ResponseSource chooseResponseSource(long nowMillis, RequestHeaders request) {
        if (!isCacheable(request)) {
            return ResponseSource.NETWORK;
        }
        if (request.isNoCache() || request.hasConditions()) {
            return ResponseSource.NETWORK;
        }
        long ageMillis = computeAge(nowMillis);
        long freshMillis = computeFreshnessLifetime();
        if (request.getMaxAgeSeconds() != -1) {
            freshMillis = Math.min(freshMillis, TimeUnit.SECONDS.toMillis((long) request.getMaxAgeSeconds()));
        }
        long minFreshMillis = 0;
        if (request.getMinFreshSeconds() != -1) {
            minFreshMillis = TimeUnit.SECONDS.toMillis((long) request.getMinFreshSeconds());
        }
        long maxStaleMillis = 0;
        if (!(this.mustRevalidate || request.getMaxStaleSeconds() == -1)) {
            maxStaleMillis = TimeUnit.SECONDS.toMillis((long) request.getMaxStaleSeconds());
        }
        if (this.noCache || ageMillis + minFreshMillis >= freshMillis + maxStaleMillis) {
            if (this.lastModified != null) {
                request.setIfModifiedSince(this.lastModified);
            } else if (this.servedDate != null) {
                request.setIfModifiedSince(this.servedDate);
            }
            if (this.etag != null) {
                request.setIfNoneMatch(this.etag);
            }
            if (request.hasConditions()) {
                return ResponseSource.CONDITIONAL_CACHE;
            }
            return ResponseSource.NETWORK;
        }
        if (ageMillis + minFreshMillis >= freshMillis) {
            this.headers.add("Warning", "110 HttpURLConnection \"Response is stale\"");
        }
        if (ageMillis > 86400000 && isFreshnessLifetimeHeuristic()) {
            this.headers.add("Warning", "113 HttpURLConnection \"Heuristic expiration\"");
        }
        return ResponseSource.CACHE;
    }

    public boolean validate(ResponseHeaders networkResponse) {
        if (networkResponse.headers.getResponseCode() == 304) {
            return true;
        }
        if (this.lastModified == null || networkResponse.lastModified == null || networkResponse.lastModified.getTime() >= this.lastModified.getTime()) {
            return false;
        }
        return true;
    }

    public ResponseHeaders combine(ResponseHeaders network) throws IOException {
        int i;
        RawHeaders result = new RawHeaders();
        result.setStatusLine(this.headers.getStatusLine());
        for (i = 0; i < this.headers.length(); i++) {
            String fieldName = this.headers.getFieldName(i);
            String value = this.headers.getValue(i);
            if (!("Warning".equals(fieldName) && value.startsWith("1")) && (!isEndToEnd(fieldName) || network.headers.get(fieldName) == null)) {
                result.add(fieldName, value);
            }
        }
        for (i = 0; i < network.headers.length(); i++) {
            fieldName = network.headers.getFieldName(i);
            if (isEndToEnd(fieldName)) {
                result.add(fieldName, network.headers.getValue(i));
            }
        }
        return new ResponseHeaders(this.uri, result);
    }

    private static boolean isEndToEnd(String fieldName) {
        return ("Connection".equalsIgnoreCase(fieldName) || "Keep-Alive".equalsIgnoreCase(fieldName) || "Proxy-Authenticate".equalsIgnoreCase(fieldName) || "Proxy-Authorization".equalsIgnoreCase(fieldName) || "TE".equalsIgnoreCase(fieldName) || "Trailers".equalsIgnoreCase(fieldName) || "Transfer-Encoding".equalsIgnoreCase(fieldName) || "Upgrade".equalsIgnoreCase(fieldName)) ? false : true;
    }
}
