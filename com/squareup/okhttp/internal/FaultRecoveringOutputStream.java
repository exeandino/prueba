package com.squareup.okhttp.internal;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public abstract class FaultRecoveringOutputStream extends AbstractOutputStream {
    private final int maxReplayBufferLength;
    private OutputStream out;
    private ByteArrayOutputStream replayBuffer;

    protected abstract OutputStream replacementStream(IOException iOException) throws IOException;

    public FaultRecoveringOutputStream(int maxReplayBufferLength, OutputStream out) {
        if (maxReplayBufferLength < 0) {
            throw new IllegalArgumentException();
        }
        this.maxReplayBufferLength = maxReplayBufferLength;
        this.replayBuffer = new ByteArrayOutputStream(maxReplayBufferLength);
        this.out = out;
    }

    public final void write(byte[] buffer, int offset, int count) throws IOException {
        if (this.closed) {
            throw new IOException("stream closed");
        }
        Util.checkOffsetAndCount(buffer.length, offset, count);
        do {
            try {
                this.out.write(buffer, offset, count);
                if (this.replayBuffer == null) {
                    return;
                }
                if (this.replayBuffer.size() + count > this.maxReplayBufferLength) {
                    this.replayBuffer = null;
                    return;
                } else {
                    this.replayBuffer.write(buffer, offset, count);
                    return;
                }
            } catch (IOException e) {
                if (!recover(e)) {
                    throw e;
                }
            }
        } while (recover(e));
        throw e;
    }

    public final void flush() throws IOException {
        if (!this.closed) {
            do {
                try {
                    this.out.flush();
                    return;
                } catch (IOException e) {
                    if (!recover(e)) {
                        throw e;
                    }
                }
            } while (recover(e));
            throw e;
        }
    }

    public final void close() throws IOException {
        if (!this.closed) {
            do {
                try {
                    this.out.close();
                    this.closed = true;
                    return;
                } catch (IOException e) {
                    if (!recover(e)) {
                        throw e;
                    }
                }
            } while (recover(e));
            throw e;
        }
    }

    private boolean recover(IOException e) {
        if (this.replayBuffer == null) {
            return false;
        }
        while (true) {
            try {
                break;
            } catch (IOException replacementStreamFailure) {
                Util.closeQuietly((Closeable) null);
                e = replacementStreamFailure;
            }
        }
        OutputStream replacementStream = replacementStream(e);
        if (replacementStream == null) {
            return false;
        }
        replaceStream(replacementStream);
        return true;
    }

    public boolean isRecoverable() {
        return this.replayBuffer != null;
    }

    public final void replaceStream(OutputStream replacementStream) throws IOException {
        if (!isRecoverable()) {
            throw new IllegalStateException();
        } else if (this.out != replacementStream) {
            this.replayBuffer.writeTo(replacementStream);
            Util.closeQuietly(this.out);
            this.out = replacementStream;
        }
    }
}
