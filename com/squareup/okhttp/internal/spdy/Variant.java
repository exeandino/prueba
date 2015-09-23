package com.squareup.okhttp.internal.spdy;

import java.io.InputStream;
import java.io.OutputStream;

interface Variant {
    public static final Variant HTTP_20_DRAFT_06;
    public static final Variant SPDY3;

    FrameReader newReader(InputStream inputStream, boolean z);

    FrameWriter newWriter(OutputStream outputStream, boolean z);

    static {
        SPDY3 = new Spdy3();
        HTTP_20_DRAFT_06 = new Http20Draft06();
    }
}
