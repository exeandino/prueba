package com.squareup.okhttp.internal.spdy;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.squareup.okhttp.internal.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public final class SpdyStream {
    static final /* synthetic */ boolean $assertionsDisabled;
    public static final int WINDOW_UPDATE_THRESHOLD = 32768;
    private final SpdyConnection connection;
    private ErrorCode errorCode;
    private final int id;
    private final SpdyDataInputStream in;
    private final SpdyDataOutputStream out;
    private final int priority;
    private long readTimeoutMillis;
    private final List<String> requestHeaders;
    private List<String> responseHeaders;
    private int writeWindowSize;

    private final class SpdyDataInputStream extends InputStream {
        static final /* synthetic */ boolean $assertionsDisabled;
        private final byte[] buffer;
        private boolean closed;
        private boolean finished;
        private int limit;
        private int pos;
        private int unacknowledgedBytes;

        static {
            $assertionsDisabled = !SpdyStream.class.desiredAssertionStatus() ? true : SpdyStream.$assertionsDisabled;
        }

        private SpdyDataInputStream() {
            this.buffer = new byte[AccessibilityNodeInfoCompat.ACTION_CUT];
            this.pos = -1;
            this.unacknowledgedBytes = 0;
        }

        public int available() throws IOException {
            int i;
            synchronized (SpdyStream.this) {
                checkNotClosed();
                if (this.pos == -1) {
                    i = 0;
                } else if (this.limit > this.pos) {
                    i = this.limit - this.pos;
                } else {
                    i = this.limit + (this.buffer.length - this.pos);
                }
            }
            return i;
        }

        public int read() throws IOException {
            return Util.readSingleByte(this);
        }

        public int read(byte[] b, int offset, int count) throws IOException {
            int i = -1;
            synchronized (SpdyStream.this) {
                Util.checkOffsetAndCount(b.length, offset, count);
                waitUntilReadable();
                checkNotClosed();
                if (this.pos == -1) {
                } else {
                    int bytesToCopy;
                    i = 0;
                    if (this.limit <= this.pos) {
                        bytesToCopy = Math.min(count, this.buffer.length - this.pos);
                        System.arraycopy(this.buffer, this.pos, b, offset, bytesToCopy);
                        this.pos += bytesToCopy;
                        i = 0 + bytesToCopy;
                        if (this.pos == this.buffer.length) {
                            this.pos = 0;
                        }
                    }
                    if (i < count) {
                        bytesToCopy = Math.min(this.limit - this.pos, count - i);
                        System.arraycopy(this.buffer, this.pos, b, offset + i, bytesToCopy);
                        this.pos += bytesToCopy;
                        i += bytesToCopy;
                    }
                    this.unacknowledgedBytes += i;
                    if (this.unacknowledgedBytes >= SpdyStream.WINDOW_UPDATE_THRESHOLD) {
                        SpdyStream.this.connection.writeWindowUpdateLater(SpdyStream.this.id, this.unacknowledgedBytes);
                        this.unacknowledgedBytes = 0;
                    }
                    if (this.pos == this.limit) {
                        this.pos = -1;
                        this.limit = 0;
                    }
                }
            }
            return i;
        }

        private void waitUntilReadable() throws IOException {
            long start = 0;
            long remaining = 0;
            if (SpdyStream.this.readTimeoutMillis != 0) {
                start = System.nanoTime() / 1000000;
                remaining = SpdyStream.this.readTimeoutMillis;
            }
            while (this.pos == -1 && !this.finished && !this.closed && SpdyStream.this.errorCode == null) {
                if (SpdyStream.this.readTimeoutMillis == 0) {
                    SpdyStream.this.wait();
                } else if (remaining > 0) {
                    try {
                        SpdyStream.this.wait(remaining);
                        remaining = (SpdyStream.this.readTimeoutMillis + start) - (System.nanoTime() / 1000000);
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException();
                    }
                } else {
                    throw new SocketTimeoutException();
                }
            }
        }

        void receive(InputStream in, int byteCount) throws IOException {
            if (!$assertionsDisabled && Thread.holdsLock(SpdyStream.this)) {
                throw new AssertionError();
            } else if (byteCount != 0) {
                boolean finished;
                int pos;
                int firstNewByte;
                int limit;
                boolean flowControlError;
                synchronized (SpdyStream.this) {
                    finished = this.finished;
                    pos = this.pos;
                    firstNewByte = this.limit;
                    limit = this.limit;
                    flowControlError = byteCount > this.buffer.length - available() ? true : SpdyStream.$assertionsDisabled;
                }
                if (flowControlError) {
                    Util.skipByReading(in, (long) byteCount);
                    SpdyStream.this.closeLater(ErrorCode.FLOW_CONTROL_ERROR);
                } else if (finished) {
                    Util.skipByReading(in, (long) byteCount);
                } else {
                    if (pos < limit) {
                        int firstCopyCount = Math.min(byteCount, this.buffer.length - limit);
                        Util.readFully(in, this.buffer, limit, firstCopyCount);
                        limit += firstCopyCount;
                        byteCount -= firstCopyCount;
                        if (limit == this.buffer.length) {
                            limit = 0;
                        }
                    }
                    if (byteCount > 0) {
                        Util.readFully(in, this.buffer, limit, byteCount);
                        limit += byteCount;
                    }
                    synchronized (SpdyStream.this) {
                        this.limit = limit;
                        if (this.pos == -1) {
                            this.pos = firstNewByte;
                            SpdyStream.this.notifyAll();
                        }
                    }
                }
            }
        }

        public void close() throws IOException {
            synchronized (SpdyStream.this) {
                this.closed = true;
                SpdyStream.this.notifyAll();
            }
            SpdyStream.this.cancelStreamIfNecessary();
        }

        private void checkNotClosed() throws IOException {
            if (this.closed) {
                throw new IOException("stream closed");
            } else if (SpdyStream.this.errorCode != null) {
                throw new IOException("stream was reset: " + SpdyStream.this.errorCode);
            }
        }
    }

    private final class SpdyDataOutputStream extends OutputStream {
        static final /* synthetic */ boolean $assertionsDisabled;
        private final byte[] buffer;
        private boolean closed;
        private boolean finished;
        private int pos;
        private int unacknowledgedBytes;

        static {
            $assertionsDisabled = !SpdyStream.class.desiredAssertionStatus() ? true : SpdyStream.$assertionsDisabled;
        }

        private SpdyDataOutputStream() {
            this.buffer = new byte[AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD];
            this.pos = 0;
            this.unacknowledgedBytes = 0;
        }

        static /* synthetic */ int access$620(SpdyDataOutputStream x0, int x1) {
            int i = x0.unacknowledgedBytes - x1;
            x0.unacknowledgedBytes = i;
            return i;
        }

        public void write(int b) throws IOException {
            Util.writeSingleByte(this, b);
        }

        public void write(byte[] bytes, int offset, int count) throws IOException {
            if ($assertionsDisabled || !Thread.holdsLock(SpdyStream.this)) {
                Util.checkOffsetAndCount(bytes.length, offset, count);
                checkNotClosed();
                while (count > 0) {
                    if (this.pos == this.buffer.length) {
                        writeFrame(SpdyStream.$assertionsDisabled);
                    }
                    int bytesToCopy = Math.min(count, this.buffer.length - this.pos);
                    System.arraycopy(bytes, offset, this.buffer, this.pos, bytesToCopy);
                    this.pos += bytesToCopy;
                    offset += bytesToCopy;
                    count -= bytesToCopy;
                }
                return;
            }
            throw new AssertionError();
        }

        public void flush() throws IOException {
            if ($assertionsDisabled || !Thread.holdsLock(SpdyStream.this)) {
                checkNotClosed();
                if (this.pos > 0) {
                    writeFrame(SpdyStream.$assertionsDisabled);
                    SpdyStream.this.connection.flush();
                    return;
                }
                return;
            }
            throw new AssertionError();
        }

        public void close() throws IOException {
            if ($assertionsDisabled || !Thread.holdsLock(SpdyStream.this)) {
                synchronized (SpdyStream.this) {
                    if (this.closed) {
                        return;
                    }
                    this.closed = true;
                    if (!SpdyStream.this.out.finished) {
                        writeFrame(true);
                    }
                    SpdyStream.this.connection.flush();
                    SpdyStream.this.cancelStreamIfNecessary();
                    return;
                }
            }
            throw new AssertionError();
        }

        private void writeFrame(boolean outFinished) throws IOException {
            if ($assertionsDisabled || !Thread.holdsLock(SpdyStream.this)) {
                int length = this.pos;
                synchronized (SpdyStream.this) {
                    waitUntilWritable(length, outFinished);
                    this.unacknowledgedBytes += length;
                }
                SpdyStream.this.connection.writeData(SpdyStream.this.id, outFinished, this.buffer, 0, this.pos);
                this.pos = 0;
                return;
            }
            throw new AssertionError();
        }

        private void waitUntilWritable(int count, boolean last) throws IOException {
            do {
                try {
                    if (this.unacknowledgedBytes + count >= SpdyStream.this.writeWindowSize) {
                        SpdyStream.this.wait();
                        if (!last && this.closed) {
                            throw new IOException("stream closed");
                        } else if (this.finished) {
                            throw new IOException("stream finished");
                        }
                    } else {
                        return;
                    }
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            } while (SpdyStream.this.errorCode == null);
            throw new IOException("stream was reset: " + SpdyStream.this.errorCode);
        }

        private void checkNotClosed() throws IOException {
            synchronized (SpdyStream.this) {
                if (this.closed) {
                    throw new IOException("stream closed");
                } else if (this.finished) {
                    throw new IOException("stream finished");
                } else if (SpdyStream.this.errorCode != null) {
                    throw new IOException("stream was reset: " + SpdyStream.this.errorCode);
                }
            }
        }
    }

    static {
        $assertionsDisabled = !SpdyStream.class.desiredAssertionStatus() ? true : $assertionsDisabled;
    }

    SpdyStream(int id, SpdyConnection connection, boolean outFinished, boolean inFinished, int priority, List<String> requestHeaders, Settings settings) {
        this.readTimeoutMillis = 0;
        this.in = new SpdyDataInputStream();
        this.out = new SpdyDataOutputStream();
        this.errorCode = null;
        if (connection == null) {
            throw new NullPointerException("connection == null");
        } else if (requestHeaders == null) {
            throw new NullPointerException("requestHeaders == null");
        } else {
            this.id = id;
            this.connection = connection;
            this.in.finished = inFinished;
            this.out.finished = outFinished;
            this.priority = priority;
            this.requestHeaders = requestHeaders;
            setSettings(settings);
        }
    }

    public synchronized boolean isOpen() {
        boolean z = $assertionsDisabled;
        synchronized (this) {
            if (this.errorCode == null) {
                if (!(this.in.finished || this.in.closed) || (!(this.out.finished || this.out.closed) || this.responseHeaders == null)) {
                    z = true;
                }
            }
        }
        return z;
    }

    public boolean isLocallyInitiated() {
        boolean streamIsClient;
        if (this.id % 2 == 1) {
            streamIsClient = true;
        } else {
            streamIsClient = $assertionsDisabled;
        }
        return this.connection.client == streamIsClient ? true : $assertionsDisabled;
    }

    public SpdyConnection getConnection() {
        return this.connection;
    }

    public List<String> getRequestHeaders() {
        return this.requestHeaders;
    }

    public synchronized List<String> getResponseHeaders() throws IOException {
        long remaining = 0;
        long start = 0;
        if (this.readTimeoutMillis != 0) {
            start = System.nanoTime() / 1000000;
            remaining = this.readTimeoutMillis;
        }
        while (this.responseHeaders == null && this.errorCode == null) {
            try {
                if (this.readTimeoutMillis == 0) {
                    wait();
                } else if (remaining > 0) {
                    wait(remaining);
                    remaining = (this.readTimeoutMillis + start) - (System.nanoTime() / 1000000);
                } else {
                    throw new SocketTimeoutException("Read response header timeout. readTimeoutMillis: " + this.readTimeoutMillis);
                }
            } catch (InterruptedException e) {
                InterruptedIOException rethrow = new InterruptedIOException();
                rethrow.initCause(e);
                throw rethrow;
            }
        }
        if (this.responseHeaders != null) {
        } else {
            throw new IOException("stream was reset: " + this.errorCode);
        }
        return this.responseHeaders;
    }

    public synchronized ErrorCode getErrorCode() {
        return this.errorCode;
    }

    public void reply(List<String> responseHeaders, boolean out) throws IOException {
        if ($assertionsDisabled || !Thread.holdsLock(this)) {
            boolean outFinished = $assertionsDisabled;
            synchronized (this) {
                if (responseHeaders == null) {
                    throw new NullPointerException("responseHeaders == null");
                } else if (isLocallyInitiated()) {
                    throw new IllegalStateException("cannot reply to a locally initiated stream");
                } else if (this.responseHeaders != null) {
                    throw new IllegalStateException("reply already sent");
                } else {
                    this.responseHeaders = responseHeaders;
                    if (!out) {
                        this.out.finished = true;
                        outFinished = true;
                    }
                }
            }
            this.connection.writeSynReply(this.id, outFinished, responseHeaders);
            return;
        }
        throw new AssertionError();
    }

    public void setReadTimeout(long readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public long getReadTimeoutMillis() {
        return this.readTimeoutMillis;
    }

    public InputStream getInputStream() {
        return this.in;
    }

    public OutputStream getOutputStream() {
        synchronized (this) {
            if (this.responseHeaders != null || isLocallyInitiated()) {
            } else {
                throw new IllegalStateException("reply before requesting the output stream");
            }
        }
        return this.out;
    }

    public void close(ErrorCode rstStatusCode) throws IOException {
        if (closeInternal(rstStatusCode)) {
            this.connection.writeSynReset(this.id, rstStatusCode);
        }
    }

    public void closeLater(ErrorCode errorCode) {
        if (closeInternal(errorCode)) {
            this.connection.writeSynResetLater(this.id, errorCode);
        }
    }

    private boolean closeInternal(ErrorCode errorCode) {
        if ($assertionsDisabled || !Thread.holdsLock(this)) {
            synchronized (this) {
                if (this.errorCode != null) {
                    return $assertionsDisabled;
                } else if (this.in.finished && this.out.finished) {
                    return $assertionsDisabled;
                } else {
                    this.errorCode = errorCode;
                    notifyAll();
                    this.connection.removeStream(this.id);
                    return true;
                }
            }
        }
        throw new AssertionError();
    }

    void receiveHeaders(List<String> headers, HeadersMode headersMode) {
        if ($assertionsDisabled || !Thread.holdsLock(this)) {
            ErrorCode errorCode = null;
            boolean open = true;
            synchronized (this) {
                if (this.responseHeaders == null) {
                    if (headersMode.failIfHeadersAbsent()) {
                        errorCode = ErrorCode.PROTOCOL_ERROR;
                    } else {
                        this.responseHeaders = headers;
                        open = isOpen();
                        notifyAll();
                    }
                } else if (headersMode.failIfHeadersPresent()) {
                    errorCode = ErrorCode.STREAM_IN_USE;
                } else {
                    List<String> newHeaders = new ArrayList();
                    newHeaders.addAll(this.responseHeaders);
                    newHeaders.addAll(headers);
                    this.responseHeaders = newHeaders;
                }
            }
            if (errorCode != null) {
                closeLater(errorCode);
                return;
            } else if (!open) {
                this.connection.removeStream(this.id);
                return;
            } else {
                return;
            }
        }
        throw new AssertionError();
    }

    void receiveData(InputStream in, int length) throws IOException {
        if ($assertionsDisabled || !Thread.holdsLock(this)) {
            this.in.receive(in, length);
            return;
        }
        throw new AssertionError();
    }

    void receiveFin() {
        if ($assertionsDisabled || !Thread.holdsLock(this)) {
            boolean open;
            synchronized (this) {
                this.in.finished = true;
                open = isOpen();
                notifyAll();
            }
            if (!open) {
                this.connection.removeStream(this.id);
                return;
            }
            return;
        }
        throw new AssertionError();
    }

    synchronized void receiveRstStream(ErrorCode errorCode) {
        if (this.errorCode == null) {
            this.errorCode = errorCode;
            notifyAll();
        }
    }

    private void setSettings(Settings settings) {
        int i = AccessibilityNodeInfoCompat.ACTION_CUT;
        if ($assertionsDisabled || Thread.holdsLock(this.connection)) {
            if (settings != null) {
                i = settings.getInitialWindowSize(AccessibilityNodeInfoCompat.ACTION_CUT);
            }
            this.writeWindowSize = i;
            return;
        }
        throw new AssertionError();
    }

    void receiveSettings(Settings settings) {
        if ($assertionsDisabled || Thread.holdsLock(this)) {
            setSettings(settings);
            notifyAll();
            return;
        }
        throw new AssertionError();
    }

    synchronized void receiveWindowUpdate(int deltaWindowSize) {
        SpdyDataOutputStream.access$620(this.out, deltaWindowSize);
        notifyAll();
    }

    int getPriority() {
        return this.priority;
    }

    private void cancelStreamIfNecessary() throws IOException {
        if ($assertionsDisabled || !Thread.holdsLock(this)) {
            boolean cancel;
            boolean open;
            synchronized (this) {
                cancel = (!this.in.finished && this.in.closed && (this.out.finished || this.out.closed)) ? true : $assertionsDisabled;
                open = isOpen();
            }
            if (cancel) {
                close(ErrorCode.CANCEL);
                return;
            } else if (!open) {
                this.connection.removeStream(this.id);
                return;
            } else {
                return;
            }
        }
        throw new AssertionError();
    }
}
