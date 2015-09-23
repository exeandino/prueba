package com.squareup.okhttp.internal.spdy;

import android.support.v4.internal.view.SupportMenu;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.http.HttpTransport;
import com.squareup.okhttp.internal.spdy.FrameReader.Handler;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

final class Http20Draft06 implements Variant {
    private static final byte[] CONNECTION_HEADER;
    static final int FLAG_END_FLOW_CONTROL = 1;
    static final int FLAG_END_HEADERS = 4;
    static final int FLAG_END_STREAM = 1;
    static final int FLAG_PONG = 1;
    static final int FLAG_PRIORITY = 8;
    static final int TYPE_CONTINUATION = 10;
    static final int TYPE_DATA = 0;
    static final int TYPE_GOAWAY = 7;
    static final int TYPE_HEADERS = 1;
    static final int TYPE_PING = 6;
    static final int TYPE_PRIORITY = 2;
    static final int TYPE_PUSH_PROMISE = 5;
    static final int TYPE_RST_STREAM = 3;
    static final int TYPE_SETTINGS = 4;
    static final int TYPE_WINDOW_UPDATE = 9;

    static final class Reader implements FrameReader {
        private final boolean client;
        private final Reader hpackReader;
        private final DataInputStream in;

        Reader(InputStream in, boolean client) {
            this.in = new DataInputStream(in);
            this.client = client;
            this.hpackReader = new Reader(this.in, client);
        }

        public void readConnectionHeader() throws IOException {
            if (!this.client) {
                byte[] connectionHeader = new byte[Http20Draft06.CONNECTION_HEADER.length];
                this.in.readFully(connectionHeader);
                if (!Arrays.equals(connectionHeader, Http20Draft06.CONNECTION_HEADER)) {
                    throw ioException("Expected a connection header but was " + Arrays.toString(connectionHeader), new Object[Http20Draft06.TYPE_DATA]);
                }
            }
        }

        public boolean nextFrame(Handler handler) throws IOException {
            try {
                int w1 = this.in.readInt();
                int length = (SupportMenu.CATEGORY_MASK & w1) >> 16;
                int flags = w1 & MotionEventCompat.ACTION_MASK;
                int streamId = this.in.readInt() & Integer.MAX_VALUE;
                switch ((MotionEventCompat.ACTION_POINTER_INDEX_MASK & w1) >> Http20Draft06.FLAG_PRIORITY) {
                    case Http20Draft06.TYPE_DATA /*0*/:
                        readData(handler, flags, length, streamId);
                        return true;
                    case Http20Draft06.TYPE_HEADERS /*1*/:
                        readHeaders(handler, flags, length, streamId);
                        return true;
                    case Http20Draft06.TYPE_PRIORITY /*2*/:
                        readPriority(handler, flags, length, streamId);
                        return true;
                    case Http20Draft06.TYPE_RST_STREAM /*3*/:
                        readRstStream(handler, flags, length, streamId);
                        return true;
                    case Http20Draft06.TYPE_SETTINGS /*4*/:
                        readSettings(handler, flags, length, streamId);
                        return true;
                    case Http20Draft06.TYPE_PUSH_PROMISE /*5*/:
                        readPushPromise(handler, flags, length, streamId);
                        return true;
                    case Http20Draft06.TYPE_PING /*6*/:
                        readPing(handler, flags, length, streamId);
                        return true;
                    case Http20Draft06.TYPE_GOAWAY /*7*/:
                        readGoAway(handler, flags, length, streamId);
                        return true;
                    case Http20Draft06.TYPE_WINDOW_UPDATE /*9*/:
                        readWindowUpdate(handler, flags, length, streamId);
                        return true;
                    default:
                        throw new UnsupportedOperationException("TODO");
                }
            } catch (IOException e) {
                return false;
            }
        }

        private void readHeaders(Handler handler, int flags, int length, int streamId) throws IOException {
            if (streamId == 0) {
                throw ioException("TYPE_HEADERS streamId == 0", new Object[Http20Draft06.TYPE_DATA]);
            }
            boolean inFinished = (flags & Http20Draft06.TYPE_HEADERS) != 0;
            int newStreamId;
            do {
                this.hpackReader.readHeaders(length);
                if ((flags & Http20Draft06.TYPE_SETTINGS) != 0) {
                    this.hpackReader.emitReferenceSet();
                    handler.headers(false, inFinished, streamId, -1, -1, this.hpackReader.getAndReset(), HeadersMode.HTTP_20_HEADERS);
                    return;
                }
                int w1 = this.in.readInt();
                int w2 = this.in.readInt();
                length = (SupportMenu.CATEGORY_MASK & w1) >> 16;
                int newType = (MotionEventCompat.ACTION_POINTER_INDEX_MASK & w1) >> Http20Draft06.FLAG_PRIORITY;
                flags = w1 & MotionEventCompat.ACTION_MASK;
                inFinished = (flags & Http20Draft06.TYPE_HEADERS) != 0;
                newStreamId = w2 & Integer.MAX_VALUE;
                if (newType != Http20Draft06.TYPE_CONTINUATION) {
                    throw ioException("TYPE_CONTINUATION didn't have FLAG_END_HEADERS", new Object[Http20Draft06.TYPE_DATA]);
                }
            } while (newStreamId == streamId);
            throw ioException("TYPE_CONTINUATION streamId changed", new Object[Http20Draft06.TYPE_DATA]);
        }

        private void readData(Handler handler, int flags, int length, int streamId) throws IOException {
            handler.data((flags & Http20Draft06.TYPE_HEADERS) != 0, streamId, this.in, length);
        }

        private void readPriority(Handler handler, int flags, int length, int streamId) throws IOException {
            if (length != Http20Draft06.TYPE_SETTINGS) {
                Object[] objArr = new Object[Http20Draft06.TYPE_HEADERS];
                objArr[Http20Draft06.TYPE_DATA] = Integer.valueOf(length);
                throw ioException("TYPE_PRIORITY length: %d != 4", objArr);
            } else if (streamId == 0) {
                throw ioException("TYPE_PRIORITY streamId == 0", new Object[Http20Draft06.TYPE_DATA]);
            } else {
                handler.priority(streamId, this.in.readInt() & Integer.MAX_VALUE);
            }
        }

        private void readRstStream(Handler handler, int flags, int length, int streamId) throws IOException {
            Object[] objArr;
            if (length != Http20Draft06.TYPE_SETTINGS) {
                objArr = new Object[Http20Draft06.TYPE_HEADERS];
                objArr[Http20Draft06.TYPE_DATA] = Integer.valueOf(length);
                throw ioException("TYPE_RST_STREAM length: %d != 4", objArr);
            } else if (streamId == 0) {
                throw ioException("TYPE_RST_STREAM streamId == 0", new Object[Http20Draft06.TYPE_DATA]);
            } else {
                int errorCodeInt = this.in.readInt();
                ErrorCode errorCode = ErrorCode.fromHttp2(errorCodeInt);
                if (errorCode == null) {
                    objArr = new Object[Http20Draft06.TYPE_HEADERS];
                    objArr[Http20Draft06.TYPE_DATA] = Integer.valueOf(errorCodeInt);
                    throw ioException("TYPE_RST_STREAM unexpected error code: %d", objArr);
                }
                handler.rstStream(streamId, errorCode);
            }
        }

        private void readSettings(Handler handler, int flags, int length, int streamId) throws IOException {
            if (length % Http20Draft06.FLAG_PRIORITY != 0) {
                Object[] objArr = new Object[Http20Draft06.TYPE_HEADERS];
                objArr[Http20Draft06.TYPE_DATA] = Integer.valueOf(length);
                throw ioException("TYPE_SETTINGS length %% 8 != 0: %s", objArr);
            } else if (streamId != 0) {
                throw ioException("TYPE_SETTINGS streamId != 0", new Object[Http20Draft06.TYPE_DATA]);
            } else {
                Settings settings = new Settings();
                for (int i = Http20Draft06.TYPE_DATA; i < length; i += Http20Draft06.FLAG_PRIORITY) {
                    settings.set(this.in.readInt() & ViewCompat.MEASURED_SIZE_MASK, Http20Draft06.TYPE_DATA, this.in.readInt());
                }
                handler.settings(false, settings);
            }
        }

        private void readPushPromise(Handler handler, int flags, int length, int streamId) {
        }

        private void readPing(Handler handler, int flags, int length, int streamId) throws IOException {
            boolean reply = true;
            if (length != Http20Draft06.FLAG_PRIORITY) {
                Object[] objArr = new Object[Http20Draft06.TYPE_HEADERS];
                objArr[Http20Draft06.TYPE_DATA] = Integer.valueOf(length);
                throw ioException("TYPE_PING length != 8: %s", objArr);
            } else if (streamId != 0) {
                throw ioException("TYPE_PING streamId != 0", new Object[Http20Draft06.TYPE_DATA]);
            } else {
                int payload1 = this.in.readInt();
                int payload2 = this.in.readInt();
                if ((flags & Http20Draft06.TYPE_HEADERS) == 0) {
                    reply = false;
                }
                handler.ping(reply, payload1, payload2);
            }
        }

        private void readGoAway(Handler handler, int flags, int length, int streamId) throws IOException {
            if (length < Http20Draft06.FLAG_PRIORITY) {
                Object[] objArr = new Object[Http20Draft06.TYPE_HEADERS];
                objArr[Http20Draft06.TYPE_DATA] = Integer.valueOf(length);
                throw ioException("TYPE_GOAWAY length < 8: %s", objArr);
            }
            int lastStreamId = this.in.readInt();
            int errorCodeInt = this.in.readInt();
            int opaqueDataLength = length - 8;
            ErrorCode errorCode = ErrorCode.fromHttp2(errorCodeInt);
            if (errorCode == null) {
                objArr = new Object[Http20Draft06.TYPE_HEADERS];
                objArr[Http20Draft06.TYPE_DATA] = Integer.valueOf(errorCodeInt);
                throw ioException("TYPE_RST_STREAM unexpected error code: %d", objArr);
            } else if (Util.skipByReading(this.in, (long) opaqueDataLength) != ((long) opaqueDataLength)) {
                throw new IOException("TYPE_GOAWAY opaque data was truncated");
            } else {
                handler.goAway(lastStreamId, errorCode);
            }
        }

        private void readWindowUpdate(Handler handler, int flags, int length, int streamId) throws IOException {
            handler.windowUpdate(streamId, this.in.readInt() & Integer.MAX_VALUE, (flags & Http20Draft06.TYPE_HEADERS) != 0);
        }

        private static IOException ioException(String message, Object... args) throws IOException {
            throw new IOException(String.format(message, args));
        }

        public void close() throws IOException {
            this.in.close();
        }
    }

    static final class Writer implements FrameWriter {
        private final boolean client;
        private final ByteArrayOutputStream hpackBuffer;
        private final Writer hpackWriter;
        private final DataOutputStream out;

        Writer(OutputStream out, boolean client) {
            this.out = new DataOutputStream(out);
            this.client = client;
            this.hpackBuffer = new ByteArrayOutputStream();
            this.hpackWriter = new Writer(this.hpackBuffer);
        }

        public synchronized void flush() throws IOException {
            this.out.flush();
        }

        public synchronized void connectionHeader() throws IOException {
            if (this.client) {
                this.out.write(Http20Draft06.CONNECTION_HEADER);
            }
        }

        public synchronized void synStream(boolean outFinished, boolean inFinished, int streamId, int associatedStreamId, int priority, int slot, List<String> nameValueBlock) throws IOException {
            if (inFinished) {
                throw new UnsupportedOperationException();
            }
            headers(outFinished, streamId, priority, nameValueBlock);
        }

        public synchronized void synReply(boolean outFinished, int streamId, List<String> nameValueBlock) throws IOException {
            headers(outFinished, streamId, -1, nameValueBlock);
        }

        public synchronized void headers(int streamId, List<String> nameValueBlock) throws IOException {
            headers(false, streamId, -1, nameValueBlock);
        }

        private void headers(boolean outFinished, int streamId, int priority, List<String> nameValueBlock) throws IOException {
            this.hpackBuffer.reset();
            this.hpackWriter.writeHeaders(nameValueBlock);
            int length = this.hpackBuffer.size();
            int flags = Http20Draft06.TYPE_SETTINGS;
            if (outFinished) {
                flags = Http20Draft06.TYPE_SETTINGS | Http20Draft06.TYPE_HEADERS;
            }
            if (priority != -1) {
                flags |= Http20Draft06.FLAG_PRIORITY;
            }
            this.out.writeInt((((SupportMenu.USER_MASK & length) << 16) | AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY) | (flags & MotionEventCompat.ACTION_MASK));
            this.out.writeInt(streamId & Integer.MAX_VALUE);
            if (priority != -1) {
                this.out.writeInt(priority & Integer.MAX_VALUE);
            }
            this.hpackBuffer.writeTo(this.out);
        }

        public synchronized void rstStream(int streamId, ErrorCode errorCode) throws IOException {
            throw new UnsupportedOperationException("TODO");
        }

        public void data(boolean outFinished, int streamId, byte[] data) throws IOException {
            data(outFinished, streamId, data, Http20Draft06.TYPE_DATA, data.length);
        }

        public synchronized void data(boolean outFinished, int streamId, byte[] data, int offset, int byteCount) throws IOException {
            int flags = Http20Draft06.TYPE_DATA;
            if (outFinished) {
                flags = Http20Draft06.TYPE_DATA | Http20Draft06.TYPE_HEADERS;
            }
            this.out.writeInt((((SupportMenu.USER_MASK & byteCount) << 16) | Http20Draft06.TYPE_DATA) | (flags & MotionEventCompat.ACTION_MASK));
            this.out.writeInt(Integer.MAX_VALUE & streamId);
            this.out.write(data, offset, byteCount);
        }

        public synchronized void settings(Settings settings) throws IOException {
            this.out.writeInt((((SupportMenu.USER_MASK & (settings.size() * Http20Draft06.FLAG_PRIORITY)) << 16) | HttpTransport.DEFAULT_CHUNK_LENGTH) | Http20Draft06.TYPE_DATA);
            this.out.writeInt(Http20Draft06.TYPE_DATA);
            for (int i = Http20Draft06.TYPE_DATA; i < Http20Draft06.TYPE_CONTINUATION; i += Http20Draft06.TYPE_HEADERS) {
                if (settings.isSet(i)) {
                    this.out.writeInt(ViewCompat.MEASURED_SIZE_MASK & i);
                    this.out.writeInt(settings.get(i));
                }
            }
        }

        public synchronized void noop() throws IOException {
            throw new UnsupportedOperationException();
        }

        public synchronized void ping(boolean reply, int payload1, int payload2) throws IOException {
        }

        public synchronized void goAway(int lastGoodStreamId, ErrorCode errorCode) throws IOException {
        }

        public synchronized void windowUpdate(int streamId, int deltaWindowSize) throws IOException {
        }

        public void close() throws IOException {
            this.out.close();
        }
    }

    Http20Draft06() {
    }

    static {
        try {
            CONNECTION_HEADER = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError();
        }
    }

    public FrameReader newReader(InputStream in, boolean client) {
        return new Reader(in, client);
    }

    public FrameWriter newWriter(OutputStream out, boolean client) {
        return new Writer(out, client);
    }
}
