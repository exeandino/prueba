package com.squareup.okhttp.internal.spdy;

import com.squareup.okhttp.internal.NamedRunnable;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.spdy.FrameReader.Handler;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class SpdyConnection implements Closeable {
    static final /* synthetic */ boolean $assertionsDisabled;
    private static final ExecutorService executor;
    final boolean client;
    private final FrameReader frameReader;
    private final FrameWriter frameWriter;
    private final IncomingStreamHandler handler;
    private final String hostName;
    private long idleStartTimeNs;
    private int lastGoodStreamId;
    private int nextPingId;
    private int nextStreamId;
    private Map<Integer, Ping> pings;
    Settings settings;
    private boolean shutdown;
    private final Map<Integer, SpdyStream> streams;
    final Variant variant;

    public static class Builder {
        private boolean client;
        private IncomingStreamHandler handler;
        private String hostName;
        private InputStream in;
        private OutputStream out;
        private Variant variant;

        public Builder(boolean client, Socket socket) throws IOException {
            this("", client, socket.getInputStream(), socket.getOutputStream());
        }

        public Builder(boolean client, InputStream in, OutputStream out) {
            this("", client, in, out);
        }

        public Builder(String hostName, boolean client, Socket socket) throws IOException {
            this(hostName, client, socket.getInputStream(), socket.getOutputStream());
        }

        public Builder(String hostName, boolean client, InputStream in, OutputStream out) {
            this.handler = IncomingStreamHandler.REFUSE_INCOMING_STREAMS;
            this.variant = Variant.SPDY3;
            this.hostName = hostName;
            this.client = client;
            this.in = in;
            this.out = out;
        }

        public Builder handler(IncomingStreamHandler handler) {
            this.handler = handler;
            return this;
        }

        public Builder spdy3() {
            this.variant = Variant.SPDY3;
            return this;
        }

        public Builder http20Draft06() {
            this.variant = Variant.HTTP_20_DRAFT_06;
            return this;
        }

        public SpdyConnection build() {
            return new SpdyConnection();
        }
    }

    /* renamed from: com.squareup.okhttp.internal.spdy.SpdyConnection.1 */
    class C02141 extends NamedRunnable {
        final /* synthetic */ ErrorCode val$errorCode;
        final /* synthetic */ int val$streamId;

        C02141(String x0, Object[] x1, int i, ErrorCode errorCode) {
            this.val$streamId = i;
            this.val$errorCode = errorCode;
            super(x0, x1);
        }

        public void execute() {
            try {
                SpdyConnection.this.writeSynReset(this.val$streamId, this.val$errorCode);
            } catch (IOException e) {
            }
        }
    }

    /* renamed from: com.squareup.okhttp.internal.spdy.SpdyConnection.2 */
    class C02152 extends NamedRunnable {
        final /* synthetic */ int val$deltaWindowSize;
        final /* synthetic */ int val$streamId;

        C02152(String x0, Object[] x1, int i, int i2) {
            this.val$streamId = i;
            this.val$deltaWindowSize = i2;
            super(x0, x1);
        }

        public void execute() {
            try {
                SpdyConnection.this.writeWindowUpdate(this.val$streamId, this.val$deltaWindowSize);
            } catch (IOException e) {
            }
        }
    }

    /* renamed from: com.squareup.okhttp.internal.spdy.SpdyConnection.3 */
    class C02163 extends NamedRunnable {
        final /* synthetic */ int val$payload1;
        final /* synthetic */ int val$payload2;
        final /* synthetic */ Ping val$ping;
        final /* synthetic */ boolean val$reply;

        C02163(String x0, Object[] x1, boolean z, int i, int i2, Ping ping) {
            this.val$reply = z;
            this.val$payload1 = i;
            this.val$payload2 = i2;
            this.val$ping = ping;
            super(x0, x1);
        }

        public void execute() {
            try {
                SpdyConnection.this.writePing(this.val$reply, this.val$payload1, this.val$payload2, this.val$ping);
            } catch (IOException e) {
            }
        }
    }

    private class Reader implements Runnable, Handler {

        /* renamed from: com.squareup.okhttp.internal.spdy.SpdyConnection.Reader.1 */
        class C02171 extends NamedRunnable {
            final /* synthetic */ SpdyStream val$newStream;

            C02171(String x0, Object[] x1, SpdyStream spdyStream) {
                this.val$newStream = spdyStream;
                super(x0, x1);
            }

            public void execute() {
                try {
                    SpdyConnection.this.handler.receive(this.val$newStream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private Reader() {
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void run() {
            /*
            r5 = this;
            r0 = com.squareup.okhttp.internal.spdy.ErrorCode.INTERNAL_ERROR;
            r2 = com.squareup.okhttp.internal.spdy.ErrorCode.INTERNAL_ERROR;
        L_0x0004:
            r3 = com.squareup.okhttp.internal.spdy.SpdyConnection.this;	 Catch:{ IOException -> 0x001a }
            r3 = r3.frameReader;	 Catch:{ IOException -> 0x001a }
            r3 = r3.nextFrame(r5);	 Catch:{ IOException -> 0x001a }
            if (r3 != 0) goto L_0x0004;
        L_0x0010:
            r0 = com.squareup.okhttp.internal.spdy.ErrorCode.NO_ERROR;	 Catch:{ IOException -> 0x001a }
            r2 = com.squareup.okhttp.internal.spdy.ErrorCode.CANCEL;	 Catch:{ IOException -> 0x001a }
            r3 = com.squareup.okhttp.internal.spdy.SpdyConnection.this;	 Catch:{ IOException -> 0x0030 }
            r3.close(r0, r2);	 Catch:{ IOException -> 0x0030 }
        L_0x0019:
            return;
        L_0x001a:
            r1 = move-exception;
            r0 = com.squareup.okhttp.internal.spdy.ErrorCode.PROTOCOL_ERROR;	 Catch:{ all -> 0x0027 }
            r2 = com.squareup.okhttp.internal.spdy.ErrorCode.PROTOCOL_ERROR;	 Catch:{ all -> 0x0027 }
            r3 = com.squareup.okhttp.internal.spdy.SpdyConnection.this;	 Catch:{ IOException -> 0x0025 }
            r3.close(r0, r2);	 Catch:{ IOException -> 0x0025 }
            goto L_0x0019;
        L_0x0025:
            r3 = move-exception;
            goto L_0x0019;
        L_0x0027:
            r3 = move-exception;
            r4 = com.squareup.okhttp.internal.spdy.SpdyConnection.this;	 Catch:{ IOException -> 0x002e }
            r4.close(r0, r2);	 Catch:{ IOException -> 0x002e }
        L_0x002d:
            throw r3;
        L_0x002e:
            r4 = move-exception;
            goto L_0x002d;
        L_0x0030:
            r3 = move-exception;
            goto L_0x0019;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.squareup.okhttp.internal.spdy.SpdyConnection.Reader.run():void");
        }

        public void data(boolean inFinished, int streamId, InputStream in, int length) throws IOException {
            SpdyStream dataStream = SpdyConnection.this.getStream(streamId);
            if (dataStream == null) {
                SpdyConnection.this.writeSynResetLater(streamId, ErrorCode.INVALID_STREAM);
                Util.skipByReading(in, (long) length);
                return;
            }
            dataStream.receiveData(in, length);
            if (inFinished) {
                dataStream.receiveFin();
            }
        }

        public void headers(boolean outFinished, boolean inFinished, int streamId, int associatedStreamId, int priority, List<String> nameValueBlock, HeadersMode headersMode) {
            synchronized (SpdyConnection.this) {
                if (SpdyConnection.this.shutdown) {
                    return;
                }
                SpdyStream stream = SpdyConnection.this.getStream(streamId);
                if (stream != null) {
                    if (headersMode.failIfStreamPresent()) {
                        stream.closeLater(ErrorCode.PROTOCOL_ERROR);
                        SpdyConnection.this.removeStream(streamId);
                        return;
                    }
                    stream.receiveHeaders(nameValueBlock, headersMode);
                    if (inFinished) {
                        stream.receiveFin();
                    }
                } else if (headersMode.failIfStreamAbsent()) {
                    SpdyConnection.this.writeSynResetLater(streamId, ErrorCode.INVALID_STREAM);
                } else if (streamId <= SpdyConnection.this.lastGoodStreamId) {
                } else if (streamId % 2 == SpdyConnection.this.nextStreamId % 2) {
                } else {
                    SpdyStream newStream = new SpdyStream(streamId, SpdyConnection.this, outFinished, inFinished, priority, nameValueBlock, SpdyConnection.this.settings);
                    SpdyConnection.this.lastGoodStreamId = streamId;
                    SpdyConnection.this.streams.put(Integer.valueOf(streamId), newStream);
                    SpdyConnection.executor.submit(new C02171("OkHttp Callback %s stream %d", new Object[]{SpdyConnection.this.hostName, Integer.valueOf(streamId)}, newStream));
                }
            }
        }

        public void rstStream(int streamId, ErrorCode errorCode) {
            SpdyStream rstStream = SpdyConnection.this.removeStream(streamId);
            if (rstStream != null) {
                rstStream.receiveRstStream(errorCode);
            }
        }

        public void settings(boolean clearPrevious, Settings newSettings) {
            SpdyStream[] streamsToNotify = null;
            synchronized (SpdyConnection.this) {
                if (SpdyConnection.this.settings == null || clearPrevious) {
                    SpdyConnection.this.settings = newSettings;
                } else {
                    SpdyConnection.this.settings.merge(newSettings);
                }
                if (!SpdyConnection.this.streams.isEmpty()) {
                    streamsToNotify = (SpdyStream[]) SpdyConnection.this.streams.values().toArray(new SpdyStream[SpdyConnection.this.streams.size()]);
                }
            }
            if (streamsToNotify != null) {
                for (SpdyStream stream : streamsToNotify) {
                    synchronized (stream) {
                        synchronized (SpdyConnection.this) {
                            stream.receiveSettings(SpdyConnection.this.settings);
                        }
                    }
                }
            }
        }

        public void noop() {
        }

        public void ping(boolean reply, int payload1, int payload2) {
            if (reply) {
                Ping ping = SpdyConnection.this.removePing(payload1);
                if (ping != null) {
                    ping.receive();
                    return;
                }
                return;
            }
            SpdyConnection.this.writePingLater(true, payload1, payload2, null);
        }

        public void goAway(int lastGoodStreamId, ErrorCode errorCode) {
            synchronized (SpdyConnection.this) {
                SpdyConnection.this.shutdown = true;
                Iterator<Entry<Integer, SpdyStream>> i = SpdyConnection.this.streams.entrySet().iterator();
                while (i.hasNext()) {
                    Entry<Integer, SpdyStream> entry = (Entry) i.next();
                    if (((Integer) entry.getKey()).intValue() > lastGoodStreamId && ((SpdyStream) entry.getValue()).isLocallyInitiated()) {
                        ((SpdyStream) entry.getValue()).receiveRstStream(ErrorCode.REFUSED_STREAM);
                        i.remove();
                    }
                }
            }
        }

        public void windowUpdate(int streamId, int deltaWindowSize, boolean endFlowControl) {
            if (streamId != 0) {
                SpdyStream stream = SpdyConnection.this.getStream(streamId);
                if (stream != null) {
                    stream.receiveWindowUpdate(deltaWindowSize);
                }
            }
        }

        public void priority(int streamId, int priority) {
        }
    }

    static {
        boolean z;
        if (SpdyConnection.class.desiredAssertionStatus()) {
            z = false;
        } else {
            z = true;
        }
        $assertionsDisabled = z;
        executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue(), Util.daemonThreadFactory("OkHttp SpdyConnection"));
    }

    private SpdyConnection(Builder builder) {
        int i = 1;
        this.streams = new HashMap();
        this.idleStartTimeNs = System.nanoTime();
        this.variant = builder.variant;
        this.client = builder.client;
        this.handler = builder.handler;
        this.frameReader = this.variant.newReader(builder.in, this.client);
        this.frameWriter = this.variant.newWriter(builder.out, this.client);
        this.nextStreamId = builder.client ? 1 : 2;
        if (!builder.client) {
            i = 2;
        }
        this.nextPingId = i;
        this.hostName = builder.hostName;
        new Thread(new Reader(), "Spdy Reader " + this.hostName).start();
    }

    public synchronized int openStreamCount() {
        return this.streams.size();
    }

    private synchronized SpdyStream getStream(int id) {
        return (SpdyStream) this.streams.get(Integer.valueOf(id));
    }

    synchronized SpdyStream removeStream(int streamId) {
        SpdyStream stream;
        stream = (SpdyStream) this.streams.remove(Integer.valueOf(streamId));
        if (stream != null && this.streams.isEmpty()) {
            setIdle(true);
        }
        return stream;
    }

    private synchronized void setIdle(boolean value) {
        this.idleStartTimeNs = value ? System.nanoTime() : Long.MAX_VALUE;
    }

    public synchronized boolean isIdle() {
        return this.idleStartTimeNs != Long.MAX_VALUE;
    }

    public synchronized long getIdleStartTimeNs() {
        return this.idleStartTimeNs;
    }

    public SpdyStream newStream(List<String> requestHeaders, boolean out, boolean in) throws IOException {
        SpdyStream stream;
        boolean outFinished = !out;
        boolean inFinished = !in;
        synchronized (this.frameWriter) {
            int streamId;
            synchronized (this) {
                if (this.shutdown) {
                    throw new IOException("shutdown");
                }
                streamId = this.nextStreamId;
                this.nextStreamId += 2;
                stream = new SpdyStream(streamId, this, outFinished, inFinished, 0, requestHeaders, this.settings);
                if (stream.isOpen()) {
                    this.streams.put(Integer.valueOf(streamId), stream);
                    setIdle(false);
                }
            }
            this.frameWriter.synStream(outFinished, inFinished, streamId, 0, 0, 0, requestHeaders);
        }
        return stream;
    }

    void writeSynReply(int streamId, boolean outFinished, List<String> alternating) throws IOException {
        this.frameWriter.synReply(outFinished, streamId, alternating);
    }

    public void writeData(int streamId, boolean outFinished, byte[] buffer, int offset, int byteCount) throws IOException {
        this.frameWriter.data(outFinished, streamId, buffer, offset, byteCount);
    }

    void writeSynResetLater(int streamId, ErrorCode errorCode) {
        executor.submit(new C02141("OkHttp SPDY Writer %s stream %d", new Object[]{this.hostName, Integer.valueOf(streamId)}, streamId, errorCode));
    }

    void writeSynReset(int streamId, ErrorCode statusCode) throws IOException {
        this.frameWriter.rstStream(streamId, statusCode);
    }

    void writeWindowUpdateLater(int streamId, int deltaWindowSize) {
        executor.submit(new C02152("OkHttp SPDY Writer %s stream %d", new Object[]{this.hostName, Integer.valueOf(streamId)}, streamId, deltaWindowSize));
    }

    void writeWindowUpdate(int streamId, int deltaWindowSize) throws IOException {
        this.frameWriter.windowUpdate(streamId, deltaWindowSize);
    }

    public Ping ping() throws IOException {
        int pingId;
        Ping ping = new Ping();
        synchronized (this) {
            if (this.shutdown) {
                throw new IOException("shutdown");
            }
            pingId = this.nextPingId;
            this.nextPingId += 2;
            if (this.pings == null) {
                this.pings = new HashMap();
            }
            this.pings.put(Integer.valueOf(pingId), ping);
        }
        writePing(false, pingId, 1330343787, ping);
        return ping;
    }

    private void writePingLater(boolean reply, int payload1, int payload2, Ping ping) {
        executor.submit(new C02163("OkHttp SPDY Writer %s ping %08x%08x", new Object[]{this.hostName, Integer.valueOf(payload1), Integer.valueOf(payload2)}, reply, payload1, payload2, ping));
    }

    private void writePing(boolean reply, int payload1, int payload2, Ping ping) throws IOException {
        synchronized (this.frameWriter) {
            if (ping != null) {
                ping.send();
            }
            this.frameWriter.ping(reply, payload1, payload2);
        }
    }

    private synchronized Ping removePing(int id) {
        return this.pings != null ? (Ping) this.pings.remove(Integer.valueOf(id)) : null;
    }

    public void noop() throws IOException {
        this.frameWriter.noop();
    }

    public void flush() throws IOException {
        this.frameWriter.flush();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void shutdown(com.squareup.okhttp.internal.spdy.ErrorCode r4) throws java.io.IOException {
        /*
        r3 = this;
        r2 = r3.frameWriter;
        monitor-enter(r2);
        monitor-enter(r3);	 Catch:{ all -> 0x0018 }
        r1 = r3.shutdown;	 Catch:{ all -> 0x001b }
        if (r1 == 0) goto L_0x000b;
    L_0x0008:
        monitor-exit(r3);	 Catch:{ all -> 0x001b }
        monitor-exit(r2);	 Catch:{ all -> 0x0018 }
    L_0x000a:
        return;
    L_0x000b:
        r1 = 1;
        r3.shutdown = r1;	 Catch:{ all -> 0x001b }
        r0 = r3.lastGoodStreamId;	 Catch:{ all -> 0x001b }
        monitor-exit(r3);	 Catch:{ all -> 0x001b }
        r1 = r3.frameWriter;	 Catch:{ all -> 0x0018 }
        r1.goAway(r0, r4);	 Catch:{ all -> 0x0018 }
        monitor-exit(r2);	 Catch:{ all -> 0x0018 }
        goto L_0x000a;
    L_0x0018:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x0018 }
        throw r1;
    L_0x001b:
        r1 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x001b }
        throw r1;	 Catch:{ all -> 0x0018 }
        */
        throw new UnsupportedOperationException("Method not decompiled: com.squareup.okhttp.internal.spdy.SpdyConnection.shutdown(com.squareup.okhttp.internal.spdy.ErrorCode):void");
    }

    public void close() throws IOException {
        close(ErrorCode.NO_ERROR, ErrorCode.CANCEL);
    }

    private void close(ErrorCode connectionCode, ErrorCode streamCode) throws IOException {
        if ($assertionsDisabled || !Thread.holdsLock(this)) {
            IOException thrown = null;
            try {
                shutdown(connectionCode);
            } catch (IOException e) {
                thrown = e;
            }
            SpdyStream[] streamsToClose = null;
            Ping[] pingsToCancel = null;
            synchronized (this) {
                if (!this.streams.isEmpty()) {
                    streamsToClose = (SpdyStream[]) this.streams.values().toArray(new SpdyStream[this.streams.size()]);
                    this.streams.clear();
                    setIdle(false);
                }
                if (this.pings != null) {
                    pingsToCancel = (Ping[]) this.pings.values().toArray(new Ping[this.pings.size()]);
                    this.pings = null;
                }
            }
            if (streamsToClose != null) {
                for (SpdyStream stream : streamsToClose) {
                    try {
                        stream.close(streamCode);
                    } catch (IOException e2) {
                        if (thrown != null) {
                            thrown = e2;
                        }
                    }
                }
            }
            if (pingsToCancel != null) {
                for (Ping ping : pingsToCancel) {
                    ping.cancel();
                }
            }
            try {
                this.frameReader.close();
            } catch (IOException e22) {
                thrown = e22;
            }
            try {
                this.frameWriter.close();
            } catch (IOException e222) {
                if (thrown == null) {
                    thrown = e222;
                }
            }
            if (thrown != null) {
                throw thrown;
            }
            return;
        }
        throw new AssertionError();
    }

    public void sendConnectionHeader() throws IOException {
        this.frameWriter.connectionHeader();
        this.frameWriter.settings(new Settings());
    }

    public void readConnectionHeader() throws IOException {
        this.frameReader.readConnectionHeader();
    }
}
