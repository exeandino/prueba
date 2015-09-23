package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.internal.AbstractOutputStream;
import com.squareup.okhttp.internal.Util;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ProtocolException;

final class RetryableOutputStream extends AbstractOutputStream {
    private final ByteArrayOutputStream content;
    private final int limit;

    public RetryableOutputStream(int limit) {
        this.limit = limit;
        this.content = new ByteArrayOutputStream(limit);
    }

    public RetryableOutputStream() {
        this.limit = -1;
        this.content = new ByteArrayOutputStream();
    }

    public synchronized void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            if (this.content.size() < this.limit) {
                throw new ProtocolException("content-length promised " + this.limit + " bytes, but received " + this.content.size());
            }
        }
    }

    public synchronized void write(byte[] buffer, int offset, int count) throws IOException {
        checkNotClosed();
        Util.checkOffsetAndCount(buffer.length, offset, count);
        if (this.limit == -1 || this.content.size() <= this.limit - count) {
            this.content.write(buffer, offset, count);
        } else {
            throw new ProtocolException("exceeded content-length limit of " + this.limit + " bytes");
        }
    }

    public synchronized int contentLength() throws IOException {
        close();
        return this.content.size();
    }

    public void writeToSocket(OutputStream socketOut) throws IOException {
        this.content.writeTo(socketOut);
    }
}
