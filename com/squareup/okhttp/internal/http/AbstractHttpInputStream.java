package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.internal.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheRequest;

abstract class AbstractHttpInputStream extends InputStream {
    private final OutputStream cacheBody;
    private final CacheRequest cacheRequest;
    protected boolean closed;
    protected final HttpEngine httpEngine;
    protected final InputStream in;

    AbstractHttpInputStream(InputStream in, HttpEngine httpEngine, CacheRequest cacheRequest) throws IOException {
        this.in = in;
        this.httpEngine = httpEngine;
        OutputStream cacheBody = cacheRequest != null ? cacheRequest.getBody() : null;
        if (cacheBody == null) {
            cacheRequest = null;
        }
        this.cacheBody = cacheBody;
        this.cacheRequest = cacheRequest;
    }

    public final int read() throws IOException {
        return Util.readSingleByte(this);
    }

    protected final void checkNotClosed() throws IOException {
        if (this.closed) {
            throw new IOException("stream closed");
        }
    }

    protected final void cacheWrite(byte[] buffer, int offset, int count) throws IOException {
        if (this.cacheBody != null) {
            this.cacheBody.write(buffer, offset, count);
        }
    }

    protected final void endOfInput() throws IOException {
        if (this.cacheRequest != null) {
            this.cacheBody.close();
        }
        this.httpEngine.release(false);
    }

    protected final void unexpectedEndOfInput() {
        if (this.cacheRequest != null) {
            this.cacheRequest.abort();
        }
        this.httpEngine.release(true);
    }
}
