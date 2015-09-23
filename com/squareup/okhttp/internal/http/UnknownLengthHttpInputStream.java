package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.internal.Util;
import java.io.IOException;
import java.io.InputStream;
import java.net.CacheRequest;

final class UnknownLengthHttpInputStream extends AbstractHttpInputStream {
    private boolean inputExhausted;

    UnknownLengthHttpInputStream(InputStream in, CacheRequest cacheRequest, HttpEngine httpEngine) throws IOException {
        super(in, httpEngine, cacheRequest);
    }

    public int read(byte[] buffer, int offset, int count) throws IOException {
        Util.checkOffsetAndCount(buffer.length, offset, count);
        checkNotClosed();
        if (this.in == null || this.inputExhausted) {
            return -1;
        }
        int read = this.in.read(buffer, offset, count);
        if (read == -1) {
            this.inputExhausted = true;
            endOfInput();
            return -1;
        }
        cacheWrite(buffer, offset, read);
        return read;
    }

    public int available() throws IOException {
        checkNotClosed();
        return this.in == null ? 0 : this.in.available();
    }

    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            if (!this.inputExhausted) {
                unexpectedEndOfInput();
            }
        }
    }
}
