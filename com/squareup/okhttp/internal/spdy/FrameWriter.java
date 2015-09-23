package com.squareup.okhttp.internal.spdy;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public interface FrameWriter extends Closeable {
    void connectionHeader() throws IOException;

    void data(boolean z, int i, byte[] bArr) throws IOException;

    void data(boolean z, int i, byte[] bArr, int i2, int i3) throws IOException;

    void flush() throws IOException;

    void goAway(int i, ErrorCode errorCode) throws IOException;

    void headers(int i, List<String> list) throws IOException;

    void noop() throws IOException;

    void ping(boolean z, int i, int i2) throws IOException;

    void rstStream(int i, ErrorCode errorCode) throws IOException;

    void settings(Settings settings) throws IOException;

    void synReply(boolean z, int i, List<String> list) throws IOException;

    void synStream(boolean z, boolean z2, int i, int i2, int i3, int i4, List<String> list) throws IOException;

    void windowUpdate(int i, int i2) throws IOException;
}
