package com.squareup.okhttp.internal.spdy;

import android.support.v4.media.TransportMediator;
import android.support.v4.media.session.PlaybackStateCompat;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import org.apache.cordova.globalization.Globalization;

final class Hpack {
    static final List<HeaderEntry> INITIAL_CLIENT_TO_SERVER_HEADER_TABLE;
    static final int INITIAL_CLIENT_TO_SERVER_HEADER_TABLE_LENGTH = 1262;
    static final List<HeaderEntry> INITIAL_SERVER_TO_CLIENT_HEADER_TABLE;
    static final int INITIAL_SERVER_TO_CLIENT_HEADER_TABLE_LENGTH = 1304;
    static final int PREFIX_5_BITS = 31;
    static final int PREFIX_6_BITS = 63;
    static final int PREFIX_7_BITS = 127;
    static final int PREFIX_8_BITS = 255;

    static class HeaderEntry {
        private final String name;
        private final String value;

        HeaderEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }

        int length() {
            return (this.name.length() + 32) + this.value.length();
        }
    }

    static class Reader {
        private long bufferSize;
        private long bytesLeft;
        private final List<String> emittedHeaders;
        private final List<HeaderEntry> headerTable;
        private final DataInputStream in;
        private final long maxBufferSize;
        private final BitSet referenceSet;

        Reader(DataInputStream in, boolean client) {
            this.maxBufferSize = PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
            this.referenceSet = new BitSet();
            this.emittedHeaders = new ArrayList();
            this.bufferSize = 0;
            this.bytesLeft = 0;
            this.in = in;
            if (client) {
                this.headerTable = new ArrayList(Hpack.INITIAL_SERVER_TO_CLIENT_HEADER_TABLE);
                this.bufferSize = 1304;
                return;
            }
            this.headerTable = new ArrayList(Hpack.INITIAL_CLIENT_TO_SERVER_HEADER_TABLE);
            this.bufferSize = 1262;
        }

        public void readHeaders(int byteCount) throws IOException {
            this.bytesLeft += (long) byteCount;
            while (this.bytesLeft > 0) {
                int b = readByte();
                if ((b & TransportMediator.FLAG_KEY_MEDIA_NEXT) != 0) {
                    readIndexedHeader(readInt(b, Hpack.PREFIX_7_BITS));
                } else if (b == 96) {
                    readLiteralHeaderWithoutIndexingNewName();
                } else if ((b & 224) == 96) {
                    readLiteralHeaderWithoutIndexingIndexedName(readInt(b, Hpack.PREFIX_5_BITS) - 1);
                } else if (b == 64) {
                    readLiteralHeaderWithIncrementalIndexingNewName();
                } else if ((b & 224) == 64) {
                    readLiteralHeaderWithIncrementalIndexingIndexedName(readInt(b, Hpack.PREFIX_5_BITS) - 1);
                } else if (b == 0) {
                    readLiteralHeaderWithSubstitutionIndexingNewName();
                } else if ((b & 192) == 0) {
                    readLiteralHeaderWithSubstitutionIndexingIndexedName(readInt(b, Hpack.PREFIX_6_BITS) - 1);
                } else {
                    throw new AssertionError();
                }
            }
        }

        public void emitReferenceSet() {
            int i = this.referenceSet.nextSetBit(0);
            while (i != -1) {
                this.emittedHeaders.add(getName(i));
                this.emittedHeaders.add(getValue(i));
                i = this.referenceSet.nextSetBit(i + 1);
            }
        }

        public List<String> getAndReset() {
            List<String> result = new ArrayList(this.emittedHeaders);
            this.emittedHeaders.clear();
            return result;
        }

        private void readIndexedHeader(int index) {
            if (this.referenceSet.get(index)) {
                this.referenceSet.clear(index);
            } else {
                this.referenceSet.set(index);
            }
        }

        private void readLiteralHeaderWithoutIndexingIndexedName(int index) throws IOException {
            String name = getName(index);
            String value = readString();
            this.emittedHeaders.add(name);
            this.emittedHeaders.add(value);
        }

        private void readLiteralHeaderWithoutIndexingNewName() throws IOException {
            String name = readString();
            String value = readString();
            this.emittedHeaders.add(name);
            this.emittedHeaders.add(value);
        }

        private void readLiteralHeaderWithIncrementalIndexingIndexedName(int nameIndex) throws IOException {
            insertIntoHeaderTable(this.headerTable.size(), new HeaderEntry(getName(nameIndex), readString()));
        }

        private void readLiteralHeaderWithIncrementalIndexingNewName() throws IOException {
            insertIntoHeaderTable(this.headerTable.size(), new HeaderEntry(readString(), readString()));
        }

        private void readLiteralHeaderWithSubstitutionIndexingIndexedName(int nameIndex) throws IOException {
            insertIntoHeaderTable(readInt(readByte(), Hpack.PREFIX_8_BITS), new HeaderEntry(getName(nameIndex), readString()));
        }

        private void readLiteralHeaderWithSubstitutionIndexingNewName() throws IOException {
            insertIntoHeaderTable(readInt(readByte(), Hpack.PREFIX_8_BITS), new HeaderEntry(readString(), readString()));
        }

        private String getName(int index) {
            return ((HeaderEntry) this.headerTable.get(index)).name;
        }

        private String getValue(int index) {
            return ((HeaderEntry) this.headerTable.get(index)).value;
        }

        private void insertIntoHeaderTable(int index, HeaderEntry entry) {
            int delta = entry.length();
            if (index != this.headerTable.size()) {
                delta -= ((HeaderEntry) this.headerTable.get(index)).length();
            }
            if (((long) delta) > PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM) {
                this.headerTable.clear();
                this.bufferSize = 0;
                this.emittedHeaders.add(entry.name);
                this.emittedHeaders.add(entry.value);
                return;
            }
            while (this.bufferSize + ((long) delta) > PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM) {
                remove(0);
                index--;
            }
            if (index < 0) {
                index = 0;
                this.headerTable.add(0, entry);
            } else if (index == this.headerTable.size()) {
                this.headerTable.add(index, entry);
            } else {
                this.headerTable.set(index, entry);
            }
            this.bufferSize += (long) delta;
            this.referenceSet.set(index);
        }

        private void remove(int index) {
            this.bufferSize -= (long) ((HeaderEntry) this.headerTable.remove(index)).length();
        }

        private int readByte() throws IOException {
            this.bytesLeft--;
            return this.in.readByte() & Hpack.PREFIX_8_BITS;
        }

        int readInt(int firstByte, int prefixMask) throws IOException {
            int prefix = firstByte & prefixMask;
            if (prefix < prefixMask) {
                return prefix;
            }
            int result = prefixMask;
            int shift = 0;
            while (true) {
                int b = readByte();
                if ((b & TransportMediator.FLAG_KEY_MEDIA_NEXT) == 0) {
                    return result + (b << shift);
                }
                result += (b & Hpack.PREFIX_7_BITS) << shift;
                shift += 7;
            }
        }

        public String readString() throws IOException {
            int length = readInt(readByte(), Hpack.PREFIX_8_BITS);
            byte[] encoded = new byte[length];
            this.bytesLeft -= (long) length;
            this.in.readFully(encoded);
            return new String(encoded, "UTF-8");
        }
    }

    static class Writer {
        private final OutputStream out;

        Writer(OutputStream out) {
            this.out = out;
        }

        public void writeHeaders(List<String> nameValueBlock) throws IOException {
            int size = nameValueBlock.size();
            for (int i = 0; i < size; i += 2) {
                this.out.write(96);
                writeString((String) nameValueBlock.get(i));
                writeString((String) nameValueBlock.get(i + 1));
            }
        }

        public void writeInt(int value, int prefixMask, int bits) throws IOException {
            if (value < prefixMask) {
                this.out.write(bits | value);
                return;
            }
            this.out.write(bits | prefixMask);
            value -= prefixMask;
            while (value >= TransportMediator.FLAG_KEY_MEDIA_NEXT) {
                this.out.write((value & Hpack.PREFIX_7_BITS) | TransportMediator.FLAG_KEY_MEDIA_NEXT);
                value >>>= 7;
            }
            this.out.write(value);
        }

        public void writeString(String headerName) throws IOException {
            byte[] bytes = headerName.getBytes("UTF-8");
            writeInt(bytes.length, Hpack.PREFIX_8_BITS, 0);
            this.out.write(bytes);
        }
    }

    static {
        INITIAL_CLIENT_TO_SERVER_HEADER_TABLE = Arrays.asList(new HeaderEntry[]{new HeaderEntry(":scheme", "http"), new HeaderEntry(":scheme", "https"), new HeaderEntry(":host", ""), new HeaderEntry(":path", "/"), new HeaderEntry(":method", "GET"), new HeaderEntry("accept", ""), new HeaderEntry("accept-charset", ""), new HeaderEntry("accept-encoding", ""), new HeaderEntry("accept-language", ""), new HeaderEntry("cookie", ""), new HeaderEntry("if-modified-since", ""), new HeaderEntry("user-agent", ""), new HeaderEntry("referer", ""), new HeaderEntry("authorization", ""), new HeaderEntry("allow", ""), new HeaderEntry("cache-control", ""), new HeaderEntry("connection", ""), new HeaderEntry("content-length", ""), new HeaderEntry("content-type", ""), new HeaderEntry(Globalization.DATE, ""), new HeaderEntry("expect", ""), new HeaderEntry("from", ""), new HeaderEntry("if-match", ""), new HeaderEntry("if-none-match", ""), new HeaderEntry("if-range", ""), new HeaderEntry("if-unmodified-since", ""), new HeaderEntry("max-forwards", ""), new HeaderEntry("proxy-authorization", ""), new HeaderEntry("range", ""), new HeaderEntry("via", "")});
        INITIAL_SERVER_TO_CLIENT_HEADER_TABLE = Arrays.asList(new HeaderEntry[]{new HeaderEntry(":status", "200"), new HeaderEntry("age", ""), new HeaderEntry("cache-control", ""), new HeaderEntry("content-length", ""), new HeaderEntry("content-type", ""), new HeaderEntry(Globalization.DATE, ""), new HeaderEntry("etag", ""), new HeaderEntry("expires", ""), new HeaderEntry("last-modified", ""), new HeaderEntry("server", ""), new HeaderEntry("set-cookie", ""), new HeaderEntry("vary", ""), new HeaderEntry("via", ""), new HeaderEntry("access-control-allow-origin", ""), new HeaderEntry("accept-ranges", ""), new HeaderEntry("allow", ""), new HeaderEntry("connection", ""), new HeaderEntry("content-disposition", ""), new HeaderEntry("content-encoding", ""), new HeaderEntry("content-language", ""), new HeaderEntry("content-location", ""), new HeaderEntry("content-range", ""), new HeaderEntry("link", ""), new HeaderEntry("location", ""), new HeaderEntry("proxy-authenticate", ""), new HeaderEntry("refresh", ""), new HeaderEntry("retry-after", ""), new HeaderEntry("strict-transport-security", ""), new HeaderEntry("transfer-encoding", ""), new HeaderEntry("www-authenticate", "")});
    }

    private Hpack() {
    }
}
