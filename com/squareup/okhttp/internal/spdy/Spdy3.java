package com.squareup.okhttp.internal.spdy;

import android.support.v4.internal.view.SupportMenu;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ExploreByTouchHelper;
import com.squareup.okhttp.internal.Platform;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.spdy.FrameReader.Handler;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.util.List;
import java.util.zip.Deflater;

final class Spdy3 implements Variant {
    static final byte[] DICTIONARY;
    static final int FLAG_FIN = 1;
    static final int FLAG_UNIDIRECTIONAL = 2;
    static final int TYPE_CREDENTIAL = 16;
    static final int TYPE_DATA = 0;
    static final int TYPE_GOAWAY = 7;
    static final int TYPE_HEADERS = 8;
    static final int TYPE_NOOP = 5;
    static final int TYPE_PING = 6;
    static final int TYPE_RST_STREAM = 3;
    static final int TYPE_SETTINGS = 4;
    static final int TYPE_SYN_REPLY = 2;
    static final int TYPE_SYN_STREAM = 1;
    static final int TYPE_WINDOW_UPDATE = 9;
    static final int VERSION = 3;

    static final class Reader implements FrameReader {
        private final boolean client;
        private final DataInputStream in;
        private final NameValueBlockReader nameValueBlockReader;

        Reader(InputStream in, boolean client) {
            this.in = new DataInputStream(in);
            this.nameValueBlockReader = new NameValueBlockReader(in);
            this.client = client;
        }

        public void readConnectionHeader() {
        }

        public boolean nextFrame(Handler handler) throws IOException {
            boolean inFinished = false;
            try {
                boolean control;
                int w1 = this.in.readInt();
                int w2 = this.in.readInt();
                if ((ExploreByTouchHelper.INVALID_ID & w1) != 0) {
                    control = true;
                } else {
                    control = false;
                }
                int flags = (ViewCompat.MEASURED_STATE_MASK & w2) >>> 24;
                int length = w2 & ViewCompat.MEASURED_SIZE_MASK;
                if (control) {
                    int version = (2147418112 & w1) >>> Spdy3.TYPE_CREDENTIAL;
                    int type = w1 & SupportMenu.USER_MASK;
                    if (version != Spdy3.VERSION) {
                        throw new ProtocolException("version != 3: " + version);
                    }
                    switch (type) {
                        case Spdy3.TYPE_SYN_STREAM /*1*/:
                            readSynStream(handler, flags, length);
                            return true;
                        case Spdy3.TYPE_SYN_REPLY /*2*/:
                            readSynReply(handler, flags, length);
                            return true;
                        case Spdy3.VERSION /*3*/:
                            readRstStream(handler, flags, length);
                            return true;
                        case Spdy3.TYPE_SETTINGS /*4*/:
                            readSettings(handler, flags, length);
                            return true;
                        case Spdy3.TYPE_NOOP /*5*/:
                            if (length != 0) {
                                Object[] objArr = new Object[Spdy3.TYPE_SYN_STREAM];
                                objArr[Spdy3.TYPE_DATA] = Integer.valueOf(length);
                                throw ioException("TYPE_NOOP length: %d != 0", objArr);
                            }
                            handler.noop();
                            return true;
                        case Spdy3.TYPE_PING /*6*/:
                            readPing(handler, flags, length);
                            return true;
                        case Spdy3.TYPE_GOAWAY /*7*/:
                            readGoAway(handler, flags, length);
                            return true;
                        case Spdy3.TYPE_HEADERS /*8*/:
                            readHeaders(handler, flags, length);
                            return true;
                        case Spdy3.TYPE_WINDOW_UPDATE /*9*/:
                            readWindowUpdate(handler, flags, length);
                            return true;
                        case Spdy3.TYPE_CREDENTIAL /*16*/:
                            Util.skipByReading(this.in, (long) length);
                            throw new UnsupportedOperationException("TODO");
                        default:
                            throw new IOException("Unexpected frame");
                    }
                }
                int streamId = w1 & Integer.MAX_VALUE;
                if ((flags & Spdy3.TYPE_SYN_STREAM) != 0) {
                    inFinished = true;
                }
                handler.data(inFinished, streamId, this.in, length);
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        private void readSynStream(Handler handler, int flags, int length) throws IOException {
            int w1 = this.in.readInt();
            int w2 = this.in.readInt();
            int s3 = this.in.readShort();
            int streamId = w1 & Integer.MAX_VALUE;
            int associatedStreamId = w2 & Integer.MAX_VALUE;
            int priority = (57344 & s3) >>> 13;
            int slot = s3 & MotionEventCompat.ACTION_MASK;
            List<String> nameValueBlock = this.nameValueBlockReader.readNameValueBlock(length - 10);
            handler.headers((flags & Spdy3.TYPE_SYN_REPLY) != 0, (flags & Spdy3.TYPE_SYN_STREAM) != 0, streamId, associatedStreamId, priority, nameValueBlock, HeadersMode.SPDY_SYN_STREAM);
        }

        private void readSynReply(Handler handler, int flags, int length) throws IOException {
            boolean inFinished;
            int streamId = this.in.readInt() & Integer.MAX_VALUE;
            List<String> nameValueBlock = this.nameValueBlockReader.readNameValueBlock(length - 4);
            if ((flags & Spdy3.TYPE_SYN_STREAM) != 0) {
                inFinished = true;
            } else {
                inFinished = false;
            }
            handler.headers(false, inFinished, streamId, -1, -1, nameValueBlock, HeadersMode.SPDY_REPLY);
        }

        private void readRstStream(Handler handler, int flags, int length) throws IOException {
            if (length != Spdy3.TYPE_HEADERS) {
                Object[] objArr = new Object[Spdy3.TYPE_SYN_STREAM];
                objArr[Spdy3.TYPE_DATA] = Integer.valueOf(length);
                throw ioException("TYPE_RST_STREAM length: %d != 8", objArr);
            }
            int streamId = this.in.readInt() & Integer.MAX_VALUE;
            int errorCodeInt = this.in.readInt();
            ErrorCode errorCode = ErrorCode.fromSpdy3Rst(errorCodeInt);
            if (errorCode == null) {
                objArr = new Object[Spdy3.TYPE_SYN_STREAM];
                objArr[Spdy3.TYPE_DATA] = Integer.valueOf(errorCodeInt);
                throw ioException("TYPE_RST_STREAM unexpected error code: %d", objArr);
            }
            handler.rstStream(streamId, errorCode);
        }

        private void readHeaders(Handler handler, int flags, int length) throws IOException {
            handler.headers(false, false, this.in.readInt() & Integer.MAX_VALUE, -1, -1, this.nameValueBlockReader.readNameValueBlock(length - 4), HeadersMode.SPDY_HEADERS);
        }

        private void readWindowUpdate(Handler handler, int flags, int length) throws IOException {
            if (length != Spdy3.TYPE_HEADERS) {
                Object[] objArr = new Object[Spdy3.TYPE_SYN_STREAM];
                objArr[Spdy3.TYPE_DATA] = Integer.valueOf(length);
                throw ioException("TYPE_WINDOW_UPDATE length: %d != 8", objArr);
            }
            handler.windowUpdate(this.in.readInt() & Integer.MAX_VALUE, this.in.readInt() & Integer.MAX_VALUE, false);
        }

        private void readPing(Handler handler, int flags, int length) throws IOException {
            boolean reply = true;
            if (length != Spdy3.TYPE_SETTINGS) {
                Object[] objArr = new Object[Spdy3.TYPE_SYN_STREAM];
                objArr[Spdy3.TYPE_DATA] = Integer.valueOf(length);
                throw ioException("TYPE_PING length: %d != 4", objArr);
            }
            boolean z;
            int id = this.in.readInt();
            boolean z2 = this.client;
            if (id % Spdy3.TYPE_SYN_REPLY == Spdy3.TYPE_SYN_STREAM) {
                z = true;
            } else {
                z = Spdy3.TYPE_DATA;
            }
            if (z2 != z) {
                reply = false;
            }
            handler.ping(reply, id, Spdy3.TYPE_DATA);
        }

        private void readGoAway(Handler handler, int flags, int length) throws IOException {
            if (length != Spdy3.TYPE_HEADERS) {
                Object[] objArr = new Object[Spdy3.TYPE_SYN_STREAM];
                objArr[Spdy3.TYPE_DATA] = Integer.valueOf(length);
                throw ioException("TYPE_GOAWAY length: %d != 8", objArr);
            }
            int lastGoodStreamId = this.in.readInt() & Integer.MAX_VALUE;
            int errorCodeInt = this.in.readInt();
            ErrorCode errorCode = ErrorCode.fromSpdyGoAway(errorCodeInt);
            if (errorCode == null) {
                objArr = new Object[Spdy3.TYPE_SYN_STREAM];
                objArr[Spdy3.TYPE_DATA] = Integer.valueOf(errorCodeInt);
                throw ioException("TYPE_GOAWAY unexpected error code: %d", objArr);
            }
            handler.goAway(lastGoodStreamId, errorCode);
        }

        private void readSettings(Handler handler, int flags, int length) throws IOException {
            boolean clearPrevious = true;
            int numberOfEntries = this.in.readInt();
            if (length != (numberOfEntries * Spdy3.TYPE_HEADERS) + Spdy3.TYPE_SETTINGS) {
                Object[] objArr = new Object[Spdy3.TYPE_SYN_REPLY];
                objArr[Spdy3.TYPE_DATA] = Integer.valueOf(length);
                objArr[Spdy3.TYPE_SYN_STREAM] = Integer.valueOf(numberOfEntries);
                throw ioException("TYPE_SETTINGS length: %d != 4 + 8 * %d", objArr);
            }
            Settings settings = new Settings();
            for (int i = Spdy3.TYPE_DATA; i < numberOfEntries; i += Spdy3.TYPE_SYN_STREAM) {
                int w1 = this.in.readInt();
                int id = w1 & ViewCompat.MEASURED_SIZE_MASK;
                settings.set(id, (ViewCompat.MEASURED_STATE_MASK & w1) >>> 24, this.in.readInt());
            }
            if ((flags & Spdy3.TYPE_SYN_STREAM) == 0) {
                clearPrevious = false;
            }
            handler.settings(clearPrevious, settings);
        }

        private static IOException ioException(String message, Object... args) throws IOException {
            throw new IOException(String.format(message, args));
        }

        public void close() throws IOException {
            Util.closeAll(this.in, this.nameValueBlockReader);
        }
    }

    static final class Writer implements FrameWriter {
        private final boolean client;
        private final ByteArrayOutputStream nameValueBlockBuffer;
        private final DataOutputStream nameValueBlockOut;
        private final DataOutputStream out;

        Writer(OutputStream out, boolean client) {
            this.out = new DataOutputStream(out);
            this.client = client;
            Deflater deflater = new Deflater();
            deflater.setDictionary(Spdy3.DICTIONARY);
            this.nameValueBlockBuffer = new ByteArrayOutputStream();
            this.nameValueBlockOut = new DataOutputStream(Platform.get().newDeflaterOutputStream(this.nameValueBlockBuffer, deflater, true));
        }

        public synchronized void connectionHeader() {
        }

        public synchronized void flush() throws IOException {
            this.out.flush();
        }

        public synchronized void synStream(boolean outFinished, boolean inFinished, int streamId, int associatedStreamId, int priority, int slot, List<String> nameValueBlock) throws IOException {
            int i = Spdy3.TYPE_DATA;
            synchronized (this) {
                int i2;
                writeNameValueBlockToBuffer(nameValueBlock);
                int length = this.nameValueBlockBuffer.size() + 10;
                if (outFinished) {
                    i2 = Spdy3.TYPE_SYN_STREAM;
                } else {
                    i2 = Spdy3.TYPE_DATA;
                }
                if (inFinished) {
                    i = Spdy3.TYPE_SYN_REPLY;
                }
                int flags = i2 | i;
                this.out.writeInt(-2147287039);
                this.out.writeInt(((flags & MotionEventCompat.ACTION_MASK) << 24) | (ViewCompat.MEASURED_SIZE_MASK & length));
                this.out.writeInt(streamId & Integer.MAX_VALUE);
                this.out.writeInt(associatedStreamId & Integer.MAX_VALUE);
                this.out.writeShort((((priority & Spdy3.TYPE_GOAWAY) << 13) | Spdy3.TYPE_DATA) | (slot & MotionEventCompat.ACTION_MASK));
                this.nameValueBlockBuffer.writeTo(this.out);
                this.out.flush();
            }
        }

        public synchronized void synReply(boolean outFinished, int streamId, List<String> nameValueBlock) throws IOException {
            writeNameValueBlockToBuffer(nameValueBlock);
            int flags = outFinished ? Spdy3.TYPE_SYN_STREAM : Spdy3.TYPE_DATA;
            int length = this.nameValueBlockBuffer.size() + Spdy3.TYPE_SETTINGS;
            this.out.writeInt(-2147287038);
            this.out.writeInt(((flags & MotionEventCompat.ACTION_MASK) << 24) | (ViewCompat.MEASURED_SIZE_MASK & length));
            this.out.writeInt(Integer.MAX_VALUE & streamId);
            this.nameValueBlockBuffer.writeTo(this.out);
            this.out.flush();
        }

        public synchronized void headers(int streamId, List<String> nameValueBlock) throws IOException {
            writeNameValueBlockToBuffer(nameValueBlock);
            int length = this.nameValueBlockBuffer.size() + Spdy3.TYPE_SETTINGS;
            this.out.writeInt(-2147287032);
            this.out.writeInt((ViewCompat.MEASURED_SIZE_MASK & length) | Spdy3.TYPE_DATA);
            this.out.writeInt(Integer.MAX_VALUE & streamId);
            this.nameValueBlockBuffer.writeTo(this.out);
            this.out.flush();
        }

        public synchronized void rstStream(int streamId, ErrorCode errorCode) throws IOException {
            if (errorCode.spdyRstCode == -1) {
                throw new IllegalArgumentException();
            }
            this.out.writeInt(-2147287037);
            this.out.writeInt(Spdy3.TYPE_HEADERS);
            this.out.writeInt(Integer.MAX_VALUE & streamId);
            this.out.writeInt(errorCode.spdyRstCode);
            this.out.flush();
        }

        public synchronized void data(boolean outFinished, int streamId, byte[] data) throws IOException {
            data(outFinished, streamId, data, Spdy3.TYPE_DATA, data.length);
        }

        public synchronized void data(boolean outFinished, int streamId, byte[] data, int offset, int byteCount) throws IOException {
            int flags = outFinished ? Spdy3.TYPE_SYN_STREAM : Spdy3.TYPE_DATA;
            this.out.writeInt(Integer.MAX_VALUE & streamId);
            this.out.writeInt(((flags & MotionEventCompat.ACTION_MASK) << 24) | (ViewCompat.MEASURED_SIZE_MASK & byteCount));
            this.out.write(data, offset, byteCount);
        }

        private void writeNameValueBlockToBuffer(List<String> nameValueBlock) throws IOException {
            this.nameValueBlockBuffer.reset();
            this.nameValueBlockOut.writeInt(nameValueBlock.size() / Spdy3.TYPE_SYN_REPLY);
            for (String s : nameValueBlock) {
                this.nameValueBlockOut.writeInt(s.length());
                this.nameValueBlockOut.write(s.getBytes("UTF-8"));
            }
            this.nameValueBlockOut.flush();
        }

        public synchronized void settings(Settings settings) throws IOException {
            int size = settings.size();
            int length = (size * Spdy3.TYPE_HEADERS) + Spdy3.TYPE_SETTINGS;
            this.out.writeInt(-2147287036);
            this.out.writeInt((length & ViewCompat.MEASURED_SIZE_MASK) | Spdy3.TYPE_DATA);
            this.out.writeInt(size);
            for (int i = Spdy3.TYPE_DATA; i <= 10; i += Spdy3.TYPE_SYN_STREAM) {
                if (settings.isSet(i)) {
                    this.out.writeInt(((settings.flags(i) & MotionEventCompat.ACTION_MASK) << 24) | (i & ViewCompat.MEASURED_SIZE_MASK));
                    this.out.writeInt(settings.get(i));
                }
            }
            this.out.flush();
        }

        public synchronized void noop() throws IOException {
            this.out.writeInt(-2147287035);
            this.out.writeInt(Spdy3.TYPE_DATA);
            this.out.flush();
        }

        public synchronized void ping(boolean reply, int payload1, int payload2) throws IOException {
            boolean payloadIsReply = true;
            synchronized (this) {
                if (this.client == (payload1 % Spdy3.TYPE_SYN_REPLY == Spdy3.TYPE_SYN_STREAM ? true : Spdy3.TYPE_DATA)) {
                    payloadIsReply = false;
                }
                if (reply != payloadIsReply) {
                    throw new IllegalArgumentException("payload != reply");
                }
                this.out.writeInt(-2147287034);
                this.out.writeInt(Spdy3.TYPE_SETTINGS);
                this.out.writeInt(payload1);
                this.out.flush();
            }
        }

        public synchronized void goAway(int lastGoodStreamId, ErrorCode errorCode) throws IOException {
            if (errorCode.spdyGoAwayCode == -1) {
                throw new IllegalArgumentException();
            }
            this.out.writeInt(-2147287033);
            this.out.writeInt(Spdy3.TYPE_HEADERS);
            this.out.writeInt(lastGoodStreamId);
            this.out.writeInt(errorCode.spdyGoAwayCode);
            this.out.flush();
        }

        public synchronized void windowUpdate(int streamId, int deltaWindowSize) throws IOException {
            this.out.writeInt(-2147287031);
            this.out.writeInt(Spdy3.TYPE_HEADERS);
            this.out.writeInt(streamId);
            this.out.writeInt(deltaWindowSize);
            this.out.flush();
        }

        public void close() throws IOException {
            Util.closeAll(this.out, this.nameValueBlockOut);
        }
    }

    Spdy3() {
    }

    static {
        try {
            DICTIONARY = "\u0000\u0000\u0000\u0007options\u0000\u0000\u0000\u0004head\u0000\u0000\u0000\u0004post\u0000\u0000\u0000\u0003put\u0000\u0000\u0000\u0006delete\u0000\u0000\u0000\u0005trace\u0000\u0000\u0000\u0006accept\u0000\u0000\u0000\u000eaccept-charset\u0000\u0000\u0000\u000faccept-encoding\u0000\u0000\u0000\u000faccept-language\u0000\u0000\u0000\raccept-ranges\u0000\u0000\u0000\u0003age\u0000\u0000\u0000\u0005allow\u0000\u0000\u0000\rauthorization\u0000\u0000\u0000\rcache-control\u0000\u0000\u0000\nconnection\u0000\u0000\u0000\fcontent-base\u0000\u0000\u0000\u0010content-encoding\u0000\u0000\u0000\u0010content-language\u0000\u0000\u0000\u000econtent-length\u0000\u0000\u0000\u0010content-location\u0000\u0000\u0000\u000bcontent-md5\u0000\u0000\u0000\rcontent-range\u0000\u0000\u0000\fcontent-type\u0000\u0000\u0000\u0004date\u0000\u0000\u0000\u0004etag\u0000\u0000\u0000\u0006expect\u0000\u0000\u0000\u0007expires\u0000\u0000\u0000\u0004from\u0000\u0000\u0000\u0004host\u0000\u0000\u0000\bif-match\u0000\u0000\u0000\u0011if-modified-since\u0000\u0000\u0000\rif-none-match\u0000\u0000\u0000\bif-range\u0000\u0000\u0000\u0013if-unmodified-since\u0000\u0000\u0000\rlast-modified\u0000\u0000\u0000\blocation\u0000\u0000\u0000\fmax-forwards\u0000\u0000\u0000\u0006pragma\u0000\u0000\u0000\u0012proxy-authenticate\u0000\u0000\u0000\u0013proxy-authorization\u0000\u0000\u0000\u0005range\u0000\u0000\u0000\u0007referer\u0000\u0000\u0000\u000bretry-after\u0000\u0000\u0000\u0006server\u0000\u0000\u0000\u0002te\u0000\u0000\u0000\u0007trailer\u0000\u0000\u0000\u0011transfer-encoding\u0000\u0000\u0000\u0007upgrade\u0000\u0000\u0000\nuser-agent\u0000\u0000\u0000\u0004vary\u0000\u0000\u0000\u0003via\u0000\u0000\u0000\u0007warning\u0000\u0000\u0000\u0010www-authenticate\u0000\u0000\u0000\u0006method\u0000\u0000\u0000\u0003get\u0000\u0000\u0000\u0006status\u0000\u0000\u0000\u0006200 OK\u0000\u0000\u0000\u0007version\u0000\u0000\u0000\bHTTP/1.1\u0000\u0000\u0000\u0003url\u0000\u0000\u0000\u0006public\u0000\u0000\u0000\nset-cookie\u0000\u0000\u0000\nkeep-alive\u0000\u0000\u0000\u0006origin100101201202205206300302303304305306307402405406407408409410411412413414415416417502504505203 Non-Authoritative Information204 No Content301 Moved Permanently400 Bad Request401 Unauthorized403 Forbidden404 Not Found500 Internal Server Error501 Not Implemented503 Service UnavailableJan Feb Mar Apr May Jun Jul Aug Sept Oct Nov Dec 00:00:00 Mon, Tue, Wed, Thu, Fri, Sat, Sun, GMTchunked,text/html,image/png,image/jpg,image/gif,application/xml,application/xhtml+xml,text/plain,text/javascript,publicprivatemax-age=gzip,deflate,sdchcharset=utf-8charset=iso-8859-1,utf-,*,enq=0.".getBytes(Util.UTF_8.name());
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
