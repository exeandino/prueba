package com.squareup.okhttp.internal;

import java.io.IOException;
import java.io.OutputStream;

public abstract class AbstractOutputStream extends OutputStream {
    protected boolean closed;

    public final void write(int data) throws IOException {
        write(new byte[]{(byte) data});
    }

    protected final void checkNotClosed() throws IOException {
        if (this.closed) {
            throw new IOException("stream closed");
        }
    }

    public boolean isClosed() {
        return this.closed;
    }
}
