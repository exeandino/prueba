package com.squareup.okhttp;

import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.http.RawHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

final class Response {
    private final Body body;
    private final int code;
    private final RawHeaders headers;
    private final Response redirectedBy;
    private final Request request;

    public static abstract class Body {
        private Reader reader;

        public abstract InputStream byteStream() throws IOException;

        public abstract long contentLength();

        public abstract MediaType contentType();

        public abstract boolean ready() throws IOException;

        public final byte[] bytes() throws IOException {
            long contentLength = contentLength();
            if (contentLength > 2147483647L) {
                throw new IOException("Cannot buffer entire body for content length: " + contentLength);
            } else if (contentLength != -1) {
                byte[] bArr = new byte[((int) contentLength)];
                InputStream in = byteStream();
                Util.readFully(in, bArr);
                if (in.read() == -1) {
                    return bArr;
                }
                throw new IOException("Content-Length and stream length disagree");
            } else {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Util.copy(byteStream(), out);
                return out.toByteArray();
            }
        }

        public final Reader charStream() throws IOException {
            if (this.reader == null) {
                this.reader = new InputStreamReader(byteStream(), charset());
            }
            return this.reader;
        }

        public final String string() throws IOException {
            return new String(bytes(), charset().name());
        }

        private Charset charset() {
            MediaType contentType = contentType();
            return contentType != null ? contentType.charset(Util.UTF_8) : Util.UTF_8;
        }
    }

    public static class Builder {
        private Body body;
        private final int code;
        private RawHeaders headers;
        private Response redirectedBy;
        private final Request request;

        public Builder(Request request, int code) {
            this.headers = new RawHeaders();
            if (request == null) {
                throw new IllegalArgumentException("request == null");
            } else if (code <= 0) {
                throw new IllegalArgumentException("code <= 0");
            } else {
                this.request = request;
                this.code = code;
            }
        }

        public Builder header(String name, String value) {
            this.headers.set(name, value);
            return this;
        }

        public Builder addHeader(String name, String value) {
            this.headers.add(name, value);
            return this;
        }

        Builder rawHeaders(RawHeaders rawHeaders) {
            this.headers = new RawHeaders(rawHeaders);
            return this;
        }

        public Builder body(Body body) {
            this.body = body;
            return this;
        }

        public Builder redirectedBy(Response redirectedBy) {
            this.redirectedBy = redirectedBy;
            return this;
        }

        public Response build() {
            if (this.request == null) {
                throw new IllegalStateException("Response has no request.");
            } else if (this.code != -1) {
                return new Response();
            } else {
                throw new IllegalStateException("Response has no code.");
            }
        }
    }

    public interface Receiver {
        void onFailure(Failure failure);

        boolean onResponse(Response response) throws IOException;
    }

    private Response(Builder builder) {
        this.request = builder.request;
        this.code = builder.code;
        this.headers = new RawHeaders(builder.headers);
        this.body = builder.body;
        this.redirectedBy = builder.redirectedBy;
    }

    public Request request() {
        return this.request;
    }

    public int code() {
        return this.code;
    }

    public String header(String name) {
        return header(name, null);
    }

    public String header(String name, String defaultValue) {
        String result = this.headers.get(name);
        return result != null ? result : defaultValue;
    }

    public List<String> headers(String name) {
        return this.headers.values(name);
    }

    public Set<String> headerNames() {
        return this.headers.names();
    }

    public int headerCount() {
        return this.headers.length();
    }

    public String headerName(int index) {
        return this.headers.getFieldName(index);
    }

    RawHeaders rawHeaders() {
        return new RawHeaders(this.headers);
    }

    public String headerValue(int index) {
        return this.headers.getValue(index);
    }

    public Body body() {
        return this.body;
    }

    public Response redirectedBy() {
        return this.redirectedBy;
    }
}
