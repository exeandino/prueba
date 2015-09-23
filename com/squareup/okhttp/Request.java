package com.squareup.okhttp;

import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.http.RawHeaders;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;

final class Request {
    private final Body body;
    private final RawHeaders headers;
    private final String method;
    private final Object tag;
    private final URL url;

    public static abstract class Body {

        /* renamed from: com.squareup.okhttp.Request.Body.1 */
        static class C02071 extends Body {
            final /* synthetic */ byte[] val$content;
            final /* synthetic */ MediaType val$contentType;

            C02071(MediaType mediaType, byte[] bArr) {
                this.val$contentType = mediaType;
                this.val$content = bArr;
            }

            public MediaType contentType() {
                return this.val$contentType;
            }

            public long contentLength() {
                return (long) this.val$content.length;
            }

            public void writeTo(OutputStream out) throws IOException {
                out.write(this.val$content);
            }
        }

        /* renamed from: com.squareup.okhttp.Request.Body.2 */
        static class C02082 extends Body {
            final /* synthetic */ MediaType val$contentType;
            final /* synthetic */ File val$file;

            C02082(MediaType mediaType, File file) {
                this.val$contentType = mediaType;
                this.val$file = file;
            }

            public MediaType contentType() {
                return this.val$contentType;
            }

            public long contentLength() {
                return this.val$file.length();
            }

            public void writeTo(OutputStream out) throws IOException {
                Throwable th;
                long length = contentLength();
                if (length != 0) {
                    Closeable in = null;
                    try {
                        Closeable in2 = new FileInputStream(this.val$file);
                        try {
                            byte[] buffer = new byte[((int) Math.min(8192, length))];
                            while (true) {
                                int c = in2.read(buffer);
                                if (c != -1) {
                                    out.write(buffer, 0, c);
                                } else {
                                    Util.closeQuietly(in2);
                                    return;
                                }
                            }
                        } catch (Throwable th2) {
                            th = th2;
                            in = in2;
                            Util.closeQuietly(in);
                            throw th;
                        }
                    } catch (Throwable th3) {
                        th = th3;
                        Util.closeQuietly(in);
                        throw th;
                    }
                }
            }
        }

        public abstract MediaType contentType();

        public abstract void writeTo(OutputStream outputStream) throws IOException;

        public long contentLength() {
            return -1;
        }

        public static Body create(MediaType contentType, String content) {
            if (contentType.charset() == null) {
                contentType = MediaType.parse(contentType + "; charset=utf-8");
            }
            try {
                return create(contentType, content.getBytes(contentType.charset().name()));
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError();
            }
        }

        public static Body create(MediaType contentType, byte[] content) {
            if (contentType == null) {
                throw new NullPointerException("contentType == null");
            } else if (content != null) {
                return new C02071(contentType, content);
            } else {
                throw new NullPointerException("content == null");
            }
        }

        public static Body create(MediaType contentType, File file) {
            if (contentType == null) {
                throw new NullPointerException("contentType == null");
            } else if (file != null) {
                return new C02082(contentType, file);
            } else {
                throw new NullPointerException("content == null");
            }
        }
    }

    public static class Builder {
        private Body body;
        private RawHeaders headers;
        private String method;
        private Object tag;
        private URL url;

        public Builder(String url) {
            this.method = "GET";
            this.headers = new RawHeaders();
            url(url);
        }

        public Builder(URL url) {
            this.method = "GET";
            this.headers = new RawHeaders();
            url(url);
        }

        public Builder url(String url) {
            try {
                this.url = new URL(url);
                return this;
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Malformed URL: " + url);
            }
        }

        public Builder url(URL url) {
            if (url == null) {
                throw new IllegalStateException("url == null");
            }
            this.url = url;
            return this;
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

        public Builder get() {
            return method("GET", null);
        }

        public Builder head() {
            return method("HEAD", null);
        }

        public Builder post(Body body) {
            return method("POST", body);
        }

        public Builder put(Body body) {
            return method("PUT", body);
        }

        public Builder method(String method, Body body) {
            if (method == null || method.length() == 0) {
                throw new IllegalArgumentException("method == null || method.length() == 0");
            }
            this.method = method;
            this.body = body;
            return this;
        }

        public Builder tag(Object tag) {
            this.tag = tag;
            return this;
        }

        public Request build() {
            return new Request();
        }
    }

    private Request(Builder builder) {
        Object access$400;
        this.url = builder.url;
        this.method = builder.method;
        this.headers = new RawHeaders(builder.headers);
        this.body = builder.body;
        if (builder.tag != null) {
            access$400 = builder.tag;
        } else {
            Request request = this;
        }
        this.tag = access$400;
    }

    public URL url() {
        return this.url;
    }

    public String urlString() {
        return this.url.toString();
    }

    public String method() {
        return this.method;
    }

    public String header(String name) {
        return this.headers.get(name);
    }

    public List<String> headers(String name) {
        return this.headers.values(name);
    }

    public Set<String> headerNames() {
        return this.headers.names();
    }

    RawHeaders rawHeaders() {
        return new RawHeaders(this.headers);
    }

    public int headerCount() {
        return this.headers.length();
    }

    public String headerName(int index) {
        return this.headers.getFieldName(index);
    }

    public String headerValue(int index) {
        return this.headers.getValue(index);
    }

    public Body body() {
        return this.body;
    }

    public Object tag() {
        return this.tag;
    }

    Builder newBuilder() {
        return new Builder(this.url).method(this.method, this.body).rawHeaders(this.headers).tag(this.tag);
    }
}
