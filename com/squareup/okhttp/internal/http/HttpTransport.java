package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.Connection;
import com.squareup.okhttp.internal.AbstractOutputStream;
import com.squareup.okhttp.internal.Util;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheRequest;
import java.net.ProtocolException;
import java.net.Socket;

public final class HttpTransport implements Transport {
    public static final int DEFAULT_CHUNK_LENGTH = 1024;
    private static final int DISCARD_STREAM_TIMEOUT_MILLIS = 100;
    private final HttpEngine httpEngine;
    private OutputStream requestOut;
    private final InputStream socketIn;
    private final OutputStream socketOut;

    private static class ChunkedInputStream extends AbstractHttpInputStream {
        private static final int NO_CHUNK_YET = -1;
        private int bytesRemainingInChunk;
        private boolean hasMoreChunks;
        private final HttpTransport transport;

        ChunkedInputStream(InputStream is, CacheRequest cacheRequest, HttpTransport transport) throws IOException {
            super(is, transport.httpEngine, cacheRequest);
            this.bytesRemainingInChunk = NO_CHUNK_YET;
            this.hasMoreChunks = true;
            this.transport = transport;
        }

        public int read(byte[] buffer, int offset, int count) throws IOException {
            Util.checkOffsetAndCount(buffer.length, offset, count);
            checkNotClosed();
            if (!this.hasMoreChunks) {
                return NO_CHUNK_YET;
            }
            if (this.bytesRemainingInChunk == 0 || this.bytesRemainingInChunk == NO_CHUNK_YET) {
                readChunkSize();
                if (!this.hasMoreChunks) {
                    return NO_CHUNK_YET;
                }
            }
            int read = this.in.read(buffer, offset, Math.min(count, this.bytesRemainingInChunk));
            if (read == NO_CHUNK_YET) {
                unexpectedEndOfInput();
                throw new IOException("unexpected end of stream");
            }
            this.bytesRemainingInChunk -= read;
            cacheWrite(buffer, offset, read);
            return read;
        }

        private void readChunkSize() throws IOException {
            if (this.bytesRemainingInChunk != NO_CHUNK_YET) {
                Util.readAsciiLine(this.in);
            }
            String chunkSizeString = Util.readAsciiLine(this.in);
            int index = chunkSizeString.indexOf(";");
            if (index != NO_CHUNK_YET) {
                chunkSizeString = chunkSizeString.substring(0, index);
            }
            try {
                this.bytesRemainingInChunk = Integer.parseInt(chunkSizeString.trim(), 16);
                if (this.bytesRemainingInChunk == 0) {
                    this.hasMoreChunks = false;
                    RawHeaders rawResponseHeaders = this.httpEngine.responseHeaders.getHeaders();
                    RawHeaders.readHeaders(this.transport.socketIn, rawResponseHeaders);
                    this.httpEngine.receiveHeaders(rawResponseHeaders);
                    endOfInput();
                }
            } catch (NumberFormatException e) {
                throw new ProtocolException("Expected a hex chunk size but was " + chunkSizeString);
            }
        }

        public int available() throws IOException {
            checkNotClosed();
            if (!this.hasMoreChunks || this.bytesRemainingInChunk == NO_CHUNK_YET) {
                return 0;
            }
            return Math.min(this.in.available(), this.bytesRemainingInChunk);
        }

        public void close() throws IOException {
            if (!this.closed) {
                if (this.hasMoreChunks && !HttpTransport.discardStream(this.httpEngine, this)) {
                    unexpectedEndOfInput();
                }
                this.closed = true;
            }
        }
    }

    private static final class ChunkedOutputStream extends AbstractOutputStream {
        private static final byte[] CRLF;
        private static final byte[] FINAL_CHUNK;
        private static final byte[] HEX_DIGITS;
        private final ByteArrayOutputStream bufferedChunk;
        private final byte[] hex;
        private final int maxChunkLength;
        private final OutputStream socketOut;

        static {
            CRLF = new byte[]{(byte) 13, (byte) 10};
            HEX_DIGITS = new byte[]{(byte) 48, (byte) 49, (byte) 50, (byte) 51, (byte) 52, (byte) 53, (byte) 54, (byte) 55, (byte) 56, (byte) 57, (byte) 97, (byte) 98, (byte) 99, (byte) 100, (byte) 101, (byte) 102};
            FINAL_CHUNK = new byte[]{(byte) 48, (byte) 13, (byte) 10, (byte) 13, (byte) 10};
        }

        private ChunkedOutputStream(OutputStream socketOut, int maxChunkLength) {
            this.hex = new byte[]{(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 13, (byte) 10};
            this.socketOut = socketOut;
            this.maxChunkLength = Math.max(1, dataLength(maxChunkLength));
            this.bufferedChunk = new ByteArrayOutputStream(maxChunkLength);
        }

        private int dataLength(int dataPlusHeaderLength) {
            int headerLength = 4;
            for (int i = dataPlusHeaderLength - 4; i > 0; i >>= 4) {
                headerLength++;
            }
            return dataPlusHeaderLength - headerLength;
        }

        public synchronized void write(byte[] buffer, int offset, int count) throws IOException {
            checkNotClosed();
            Util.checkOffsetAndCount(buffer.length, offset, count);
            while (count > 0) {
                int numBytesWritten;
                if (this.bufferedChunk.size() > 0 || count < this.maxChunkLength) {
                    numBytesWritten = Math.min(count, this.maxChunkLength - this.bufferedChunk.size());
                    this.bufferedChunk.write(buffer, offset, numBytesWritten);
                    if (this.bufferedChunk.size() == this.maxChunkLength) {
                        writeBufferedChunkToSocket();
                    }
                } else {
                    numBytesWritten = this.maxChunkLength;
                    writeHex(numBytesWritten);
                    this.socketOut.write(buffer, offset, numBytesWritten);
                    this.socketOut.write(CRLF);
                }
                offset += numBytesWritten;
                count -= numBytesWritten;
            }
        }

        private void writeHex(int i) throws IOException {
            int cursor = 8;
            do {
                cursor--;
                this.hex[cursor] = HEX_DIGITS[i & 15];
                i >>>= 4;
            } while (i != 0);
            this.socketOut.write(this.hex, cursor, this.hex.length - cursor);
        }

        public synchronized void flush() throws IOException {
            if (!this.closed) {
                writeBufferedChunkToSocket();
                this.socketOut.flush();
            }
        }

        public synchronized void close() throws IOException {
            if (!this.closed) {
                this.closed = true;
                writeBufferedChunkToSocket();
                this.socketOut.write(FINAL_CHUNK);
            }
        }

        private void writeBufferedChunkToSocket() throws IOException {
            int size = this.bufferedChunk.size();
            if (size > 0) {
                writeHex(size);
                this.bufferedChunk.writeTo(this.socketOut);
                this.bufferedChunk.reset();
                this.socketOut.write(CRLF);
            }
        }
    }

    private static class FixedLengthInputStream extends AbstractHttpInputStream {
        private long bytesRemaining;

        public FixedLengthInputStream(InputStream is, CacheRequest cacheRequest, HttpEngine httpEngine, long length) throws IOException {
            super(is, httpEngine, cacheRequest);
            this.bytesRemaining = length;
            if (this.bytesRemaining == 0) {
                endOfInput();
            }
        }

        public int read(byte[] buffer, int offset, int count) throws IOException {
            Util.checkOffsetAndCount(buffer.length, offset, count);
            checkNotClosed();
            if (this.bytesRemaining == 0) {
                return -1;
            }
            int read = this.in.read(buffer, offset, (int) Math.min((long) count, this.bytesRemaining));
            if (read == -1) {
                unexpectedEndOfInput();
                throw new ProtocolException("unexpected end of stream");
            }
            this.bytesRemaining -= (long) read;
            cacheWrite(buffer, offset, read);
            if (this.bytesRemaining != 0) {
                return read;
            }
            endOfInput();
            return read;
        }

        public int available() throws IOException {
            checkNotClosed();
            return this.bytesRemaining == 0 ? 0 : (int) Math.min((long) this.in.available(), this.bytesRemaining);
        }

        public void close() throws IOException {
            if (!this.closed) {
                if (!(this.bytesRemaining == 0 || HttpTransport.discardStream(this.httpEngine, this))) {
                    unexpectedEndOfInput();
                }
                this.closed = true;
            }
        }
    }

    private static final class FixedLengthOutputStream extends AbstractOutputStream {
        private long bytesRemaining;
        private final OutputStream socketOut;

        private FixedLengthOutputStream(OutputStream socketOut, long bytesRemaining) {
            this.socketOut = socketOut;
            this.bytesRemaining = bytesRemaining;
        }

        public void write(byte[] buffer, int offset, int count) throws IOException {
            checkNotClosed();
            Util.checkOffsetAndCount(buffer.length, offset, count);
            if (((long) count) > this.bytesRemaining) {
                throw new ProtocolException("expected " + this.bytesRemaining + " bytes but received " + count);
            }
            this.socketOut.write(buffer, offset, count);
            this.bytesRemaining -= (long) count;
        }

        public void flush() throws IOException {
            if (!this.closed) {
                this.socketOut.flush();
            }
        }

        public void close() throws IOException {
            if (!this.closed) {
                this.closed = true;
                if (this.bytesRemaining > 0) {
                    throw new ProtocolException("unexpected end of stream");
                }
            }
        }
    }

    public HttpTransport(HttpEngine httpEngine, OutputStream outputStream, InputStream inputStream) {
        this.httpEngine = httpEngine;
        this.socketOut = outputStream;
        this.requestOut = outputStream;
        this.socketIn = inputStream;
    }

    public OutputStream createRequestBody() throws IOException {
        boolean chunked = this.httpEngine.requestHeaders.isChunked();
        if (!(chunked || this.httpEngine.policy.getChunkLength() <= 0 || this.httpEngine.connection.getHttpMinorVersion() == 0)) {
            this.httpEngine.requestHeaders.setChunked();
            chunked = true;
        }
        if (chunked) {
            int chunkLength = this.httpEngine.policy.getChunkLength();
            if (chunkLength == -1) {
                chunkLength = DEFAULT_CHUNK_LENGTH;
            }
            writeRequestHeaders();
            return new ChunkedOutputStream(chunkLength, null);
        }
        long fixedContentLength = this.httpEngine.policy.getFixedContentLength();
        if (fixedContentLength != -1) {
            this.httpEngine.requestHeaders.setContentLength(fixedContentLength);
            writeRequestHeaders();
            return new FixedLengthOutputStream(fixedContentLength, null);
        }
        long contentLength = this.httpEngine.requestHeaders.getContentLength();
        if (contentLength > 2147483647L) {
            throw new IllegalArgumentException("Use setFixedLengthStreamingMode() or setChunkedStreamingMode() for requests larger than 2 GiB.");
        } else if (contentLength == -1) {
            return new RetryableOutputStream();
        } else {
            writeRequestHeaders();
            return new RetryableOutputStream((int) contentLength);
        }
    }

    public void flushRequest() throws IOException {
        this.requestOut.flush();
        this.requestOut = this.socketOut;
    }

    public void writeRequestBody(RetryableOutputStream requestBody) throws IOException {
        requestBody.writeToSocket(this.requestOut);
    }

    public void writeRequestHeaders() throws IOException {
        this.httpEngine.writingRequestHeaders();
        this.requestOut.write(this.httpEngine.requestHeaders.getHeaders().toBytes());
    }

    public ResponseHeaders readResponseHeaders() throws IOException {
        RawHeaders rawHeaders = RawHeaders.fromBytes(this.socketIn);
        this.httpEngine.connection.setHttpMinorVersion(rawHeaders.getHttpMinorVersion());
        this.httpEngine.receiveHeaders(rawHeaders);
        ResponseHeaders headers = new ResponseHeaders(this.httpEngine.uri, rawHeaders);
        headers.setTransport("http/1.1");
        return headers;
    }

    public boolean makeReusable(boolean streamCanceled, OutputStream requestBodyOut, InputStream responseBodyIn) {
        if (streamCanceled) {
            return false;
        }
        if ((requestBodyOut != null && !((AbstractOutputStream) requestBodyOut).isClosed()) || this.httpEngine.requestHeaders.hasConnectionClose()) {
            return false;
        }
        if ((this.httpEngine.responseHeaders != null && this.httpEngine.responseHeaders.hasConnectionClose()) || (responseBodyIn instanceof UnknownLengthHttpInputStream)) {
            return false;
        }
        if (responseBodyIn != null) {
            return discardStream(this.httpEngine, responseBodyIn);
        }
        return true;
    }

    private static boolean discardStream(HttpEngine httpEngine, InputStream responseBodyIn) {
        Connection connection = httpEngine.connection;
        if (connection == null) {
            return false;
        }
        Socket socket = connection.getSocket();
        if (socket == null) {
            return false;
        }
        int socketTimeout;
        try {
            socketTimeout = socket.getSoTimeout();
            socket.setSoTimeout(DISCARD_STREAM_TIMEOUT_MILLIS);
            Util.skipAll(responseBodyIn);
            socket.setSoTimeout(socketTimeout);
            return true;
        } catch (IOException e) {
            return false;
        } catch (Throwable th) {
            socket.setSoTimeout(socketTimeout);
        }
    }

    public InputStream getTransferStream(CacheRequest cacheRequest) throws IOException {
        if (!this.httpEngine.hasResponseBody()) {
            return new FixedLengthInputStream(this.socketIn, cacheRequest, this.httpEngine, 0);
        } else if (this.httpEngine.responseHeaders.isChunked()) {
            return new ChunkedInputStream(this.socketIn, cacheRequest, this);
        } else {
            if (this.httpEngine.responseHeaders.getContentLength() == -1) {
                return new UnknownLengthHttpInputStream(this.socketIn, cacheRequest, this.httpEngine);
            }
            return new FixedLengthInputStream(this.socketIn, cacheRequest, this.httpEngine, this.httpEngine.responseHeaders.getContentLength());
        }
    }
}
