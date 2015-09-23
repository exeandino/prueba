package com.squareup.okhttp.internal.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheRequest;

interface Transport {
    OutputStream createRequestBody() throws IOException;

    void flushRequest() throws IOException;

    InputStream getTransferStream(CacheRequest cacheRequest) throws IOException;

    boolean makeReusable(boolean z, OutputStream outputStream, InputStream inputStream);

    ResponseHeaders readResponseHeaders() throws IOException;

    void writeRequestBody(RetryableOutputStream retryableOutputStream) throws IOException;

    void writeRequestHeaders() throws IOException;
}
