package com.squareup.okhttp;

import android.support.v4.app.NotificationCompat.WearableExtender;
import com.squareup.okhttp.internal.Base64;
import com.squareup.okhttp.internal.DiskLruCache;
import com.squareup.okhttp.internal.DiskLruCache.Editor;
import com.squareup.okhttp.internal.DiskLruCache.Snapshot;
import com.squareup.okhttp.internal.StrictLineReader;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.http.HttpEngine;
import com.squareup.okhttp.internal.http.HttpURLConnectionImpl;
import com.squareup.okhttp.internal.http.HttpsEngine;
import com.squareup.okhttp.internal.http.HttpsURLConnectionImpl;
import com.squareup.okhttp.internal.http.RawHeaders;
import com.squareup.okhttp.internal.http.ResponseHeaders;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.ResponseCache;
import java.net.SecureCacheResponse;
import java.net.URI;
import java.net.URLConnection;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;

public final class HttpResponseCache extends ResponseCache {
    private static final int ENTRY_BODY = 1;
    private static final int ENTRY_COUNT = 2;
    private static final int ENTRY_METADATA = 0;
    private static final int VERSION = 201105;
    private final DiskLruCache cache;
    private int hitCount;
    private int networkCount;
    final OkResponseCache okResponseCache;
    private int requestCount;
    private int writeAbortCount;
    private int writeSuccessCount;

    /* renamed from: com.squareup.okhttp.HttpResponseCache.2 */
    static class C01012 extends FilterInputStream {
        final /* synthetic */ Snapshot val$snapshot;

        C01012(InputStream x0, Snapshot snapshot) {
            this.val$snapshot = snapshot;
            super(x0);
        }

        public void close() throws IOException {
            this.val$snapshot.close();
            super.close();
        }
    }

    /* renamed from: com.squareup.okhttp.HttpResponseCache.3 */
    static /* synthetic */ class C01023 {
        static final /* synthetic */ int[] $SwitchMap$com$squareup$okhttp$ResponseSource;

        static {
            $SwitchMap$com$squareup$okhttp$ResponseSource = new int[ResponseSource.values().length];
            try {
                $SwitchMap$com$squareup$okhttp$ResponseSource[ResponseSource.CACHE.ordinal()] = HttpResponseCache.ENTRY_BODY;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$squareup$okhttp$ResponseSource[ResponseSource.CONDITIONAL_CACHE.ordinal()] = HttpResponseCache.ENTRY_COUNT;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$squareup$okhttp$ResponseSource[ResponseSource.NETWORK.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
        }
    }

    private final class CacheRequestImpl extends CacheRequest {
        private OutputStream body;
        private OutputStream cacheOut;
        private boolean done;
        private final Editor editor;

        /* renamed from: com.squareup.okhttp.HttpResponseCache.CacheRequestImpl.1 */
        class C01031 extends FilterOutputStream {
            final /* synthetic */ Editor val$editor;
            final /* synthetic */ HttpResponseCache val$this$0;

            C01031(OutputStream x0, HttpResponseCache httpResponseCache, Editor editor) {
                this.val$this$0 = httpResponseCache;
                this.val$editor = editor;
                super(x0);
            }

            public void close() throws IOException {
                synchronized (HttpResponseCache.this) {
                    if (CacheRequestImpl.this.done) {
                        return;
                    }
                    CacheRequestImpl.this.done = true;
                    HttpResponseCache.this.writeSuccessCount = HttpResponseCache.this.writeSuccessCount + HttpResponseCache.ENTRY_BODY;
                    super.close();
                    this.val$editor.commit();
                }
            }

            public void write(byte[] buffer, int offset, int length) throws IOException {
                this.out.write(buffer, offset, length);
            }
        }

        public CacheRequestImpl(Editor editor) throws IOException {
            this.editor = editor;
            this.cacheOut = editor.newOutputStream(HttpResponseCache.ENTRY_BODY);
            this.body = new C01031(this.cacheOut, HttpResponseCache.this, editor);
        }

        public void abort() {
            synchronized (HttpResponseCache.this) {
                if (this.done) {
                    return;
                }
                this.done = true;
                HttpResponseCache.this.writeAbortCount = HttpResponseCache.this.writeAbortCount + HttpResponseCache.ENTRY_BODY;
                Util.closeQuietly(this.cacheOut);
                try {
                    this.editor.abort();
                } catch (IOException e) {
                }
            }
        }

        public OutputStream getBody() throws IOException {
            return this.body;
        }
    }

    private static final class Entry {
        private final String cipherSuite;
        private final Certificate[] localCertificates;
        private final Certificate[] peerCertificates;
        private final String requestMethod;
        private final RawHeaders responseHeaders;
        private final String uri;
        private final RawHeaders varyHeaders;

        public Entry(InputStream in) throws IOException {
            try {
                int i;
                StrictLineReader reader = new StrictLineReader(in, Util.US_ASCII);
                this.uri = reader.readLine();
                this.requestMethod = reader.readLine();
                this.varyHeaders = new RawHeaders();
                int varyRequestHeaderLineCount = reader.readInt();
                for (i = HttpResponseCache.ENTRY_METADATA; i < varyRequestHeaderLineCount; i += HttpResponseCache.ENTRY_BODY) {
                    this.varyHeaders.addLine(reader.readLine());
                }
                this.responseHeaders = new RawHeaders();
                this.responseHeaders.setStatusLine(reader.readLine());
                int responseHeaderLineCount = reader.readInt();
                for (i = HttpResponseCache.ENTRY_METADATA; i < responseHeaderLineCount; i += HttpResponseCache.ENTRY_BODY) {
                    this.responseHeaders.addLine(reader.readLine());
                }
                if (isHttps()) {
                    String blank = reader.readLine();
                    if (blank.length() > 0) {
                        throw new IOException("expected \"\" but was \"" + blank + "\"");
                    }
                    this.cipherSuite = reader.readLine();
                    this.peerCertificates = readCertArray(reader);
                    this.localCertificates = readCertArray(reader);
                } else {
                    this.cipherSuite = null;
                    this.peerCertificates = null;
                    this.localCertificates = null;
                }
                in.close();
            } catch (Throwable th) {
                in.close();
            }
        }

        public Entry(URI uri, RawHeaders varyHeaders, HttpURLConnection httpConnection) throws IOException {
            this.uri = uri.toString();
            this.varyHeaders = varyHeaders;
            this.requestMethod = httpConnection.getRequestMethod();
            this.responseHeaders = RawHeaders.fromMultimap(httpConnection.getHeaderFields(), true);
            SSLSocket sslSocket = getSslSocket(httpConnection);
            if (sslSocket != null) {
                this.cipherSuite = sslSocket.getSession().getCipherSuite();
                Certificate[] peerCertificatesNonFinal = null;
                try {
                    peerCertificatesNonFinal = sslSocket.getSession().getPeerCertificates();
                } catch (SSLPeerUnverifiedException e) {
                }
                this.peerCertificates = peerCertificatesNonFinal;
                this.localCertificates = sslSocket.getSession().getLocalCertificates();
                return;
            }
            this.cipherSuite = null;
            this.peerCertificates = null;
            this.localCertificates = null;
        }

        private SSLSocket getSslSocket(HttpURLConnection httpConnection) {
            HttpEngine engine = httpConnection instanceof HttpsURLConnectionImpl ? ((HttpsURLConnectionImpl) httpConnection).getHttpEngine() : ((HttpURLConnectionImpl) httpConnection).getHttpEngine();
            return engine instanceof HttpsEngine ? ((HttpsEngine) engine).getSslSocket() : null;
        }

        public void writeTo(Editor editor) throws IOException {
            int i;
            Writer writer = new BufferedWriter(new OutputStreamWriter(editor.newOutputStream(HttpResponseCache.ENTRY_METADATA), Util.UTF_8));
            writer.write(this.uri + '\n');
            writer.write(this.requestMethod + '\n');
            writer.write(Integer.toString(this.varyHeaders.length()) + '\n');
            for (i = HttpResponseCache.ENTRY_METADATA; i < this.varyHeaders.length(); i += HttpResponseCache.ENTRY_BODY) {
                writer.write(this.varyHeaders.getFieldName(i) + ": " + this.varyHeaders.getValue(i) + '\n');
            }
            writer.write(this.responseHeaders.getStatusLine() + '\n');
            writer.write(Integer.toString(this.responseHeaders.length()) + '\n');
            for (i = HttpResponseCache.ENTRY_METADATA; i < this.responseHeaders.length(); i += HttpResponseCache.ENTRY_BODY) {
                writer.write(this.responseHeaders.getFieldName(i) + ": " + this.responseHeaders.getValue(i) + '\n');
            }
            if (isHttps()) {
                writer.write(10);
                writer.write(this.cipherSuite + '\n');
                writeCertArray(writer, this.peerCertificates);
                writeCertArray(writer, this.localCertificates);
            }
            writer.close();
        }

        private boolean isHttps() {
            return this.uri.startsWith("https://");
        }

        private Certificate[] readCertArray(StrictLineReader reader) throws IOException {
            int length = reader.readInt();
            if (length == -1) {
                return null;
            }
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                Certificate[] result = new Certificate[length];
                for (int i = HttpResponseCache.ENTRY_METADATA; i < result.length; i += HttpResponseCache.ENTRY_BODY) {
                    result[i] = certificateFactory.generateCertificate(new ByteArrayInputStream(Base64.decode(reader.readLine().getBytes("US-ASCII"))));
                }
                return result;
            } catch (CertificateException e) {
                throw new IOException(e.getMessage());
            }
        }

        private void writeCertArray(Writer writer, Certificate[] certificates) throws IOException {
            if (certificates == null) {
                writer.write("-1\n");
                return;
            }
            try {
                writer.write(Integer.toString(certificates.length) + '\n');
                Certificate[] arr$ = certificates;
                int len$ = arr$.length;
                for (int i$ = HttpResponseCache.ENTRY_METADATA; i$ < len$; i$ += HttpResponseCache.ENTRY_BODY) {
                    writer.write(Base64.encode(arr$[i$].getEncoded()) + '\n');
                }
            } catch (CertificateEncodingException e) {
                throw new IOException(e.getMessage());
            }
        }

        public boolean matches(URI uri, String requestMethod, Map<String, List<String>> requestHeaders) {
            return this.uri.equals(uri.toString()) && this.requestMethod.equals(requestMethod) && new ResponseHeaders(uri, this.responseHeaders).varyMatches(this.varyHeaders.toMultimap(false), requestHeaders);
        }
    }

    static class EntryCacheResponse extends CacheResponse {
        private final Entry entry;
        private final InputStream in;
        private final Snapshot snapshot;

        public EntryCacheResponse(Entry entry, Snapshot snapshot) {
            this.entry = entry;
            this.snapshot = snapshot;
            this.in = HttpResponseCache.newBodyInputStream(snapshot);
        }

        public Map<String, List<String>> getHeaders() {
            return this.entry.responseHeaders.toMultimap(true);
        }

        public InputStream getBody() {
            return this.in;
        }
    }

    static class EntrySecureCacheResponse extends SecureCacheResponse {
        private final Entry entry;
        private final InputStream in;
        private final Snapshot snapshot;

        public EntrySecureCacheResponse(Entry entry, Snapshot snapshot) {
            this.entry = entry;
            this.snapshot = snapshot;
            this.in = HttpResponseCache.newBodyInputStream(snapshot);
        }

        public Map<String, List<String>> getHeaders() {
            return this.entry.responseHeaders.toMultimap(true);
        }

        public InputStream getBody() {
            return this.in;
        }

        public String getCipherSuite() {
            return this.entry.cipherSuite;
        }

        public List<Certificate> getServerCertificateChain() throws SSLPeerUnverifiedException {
            if (this.entry.peerCertificates != null && this.entry.peerCertificates.length != 0) {
                return Arrays.asList((Object[]) this.entry.peerCertificates.clone());
            }
            throw new SSLPeerUnverifiedException(null);
        }

        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            if (this.entry.peerCertificates != null && this.entry.peerCertificates.length != 0) {
                return ((X509Certificate) this.entry.peerCertificates[HttpResponseCache.ENTRY_METADATA]).getSubjectX500Principal();
            }
            throw new SSLPeerUnverifiedException(null);
        }

        public List<Certificate> getLocalCertificateChain() {
            if (this.entry.localCertificates == null || this.entry.localCertificates.length == 0) {
                return null;
            }
            return Arrays.asList((Object[]) this.entry.localCertificates.clone());
        }

        public Principal getLocalPrincipal() {
            if (this.entry.localCertificates == null || this.entry.localCertificates.length == 0) {
                return null;
            }
            return ((X509Certificate) this.entry.localCertificates[HttpResponseCache.ENTRY_METADATA]).getSubjectX500Principal();
        }
    }

    /* renamed from: com.squareup.okhttp.HttpResponseCache.1 */
    class C02061 implements OkResponseCache {
        C02061() {
        }

        public CacheResponse get(URI uri, String requestMethod, Map<String, List<String>> requestHeaders) throws IOException {
            return HttpResponseCache.this.get(uri, requestMethod, requestHeaders);
        }

        public CacheRequest put(URI uri, URLConnection connection) throws IOException {
            return HttpResponseCache.this.put(uri, connection);
        }

        public void maybeRemove(String requestMethod, URI uri) throws IOException {
            HttpResponseCache.this.maybeRemove(requestMethod, uri);
        }

        public void update(CacheResponse conditionalCacheHit, HttpURLConnection connection) throws IOException {
            HttpResponseCache.this.update(conditionalCacheHit, connection);
        }

        public void trackConditionalCacheHit() {
            HttpResponseCache.this.trackConditionalCacheHit();
        }

        public void trackResponse(ResponseSource source) {
            HttpResponseCache.this.trackResponse(source);
        }
    }

    public HttpResponseCache(File directory, long maxSize) throws IOException {
        this.okResponseCache = new C02061();
        this.cache = DiskLruCache.open(directory, VERSION, ENTRY_COUNT, maxSize);
    }

    private String uriToKey(URI uri) {
        return Util.hash(uri.toString());
    }

    public CacheResponse get(URI uri, String requestMethod, Map<String, List<String>> requestHeaders) {
        try {
            Snapshot snapshot = this.cache.get(uriToKey(uri));
            if (snapshot == null) {
                return null;
            }
            Entry entry = new Entry(snapshot.getInputStream(ENTRY_METADATA));
            if (entry.matches(uri, requestMethod, requestHeaders)) {
                return entry.isHttps() ? new EntrySecureCacheResponse(entry, snapshot) : new EntryCacheResponse(entry, snapshot);
            } else {
                snapshot.close();
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        if (!(urlConnection instanceof HttpURLConnection)) {
            return null;
        }
        HttpURLConnection httpConnection = (HttpURLConnection) urlConnection;
        String requestMethod = httpConnection.getRequestMethod();
        if (maybeRemove(requestMethod, uri) || !requestMethod.equals("GET")) {
            return null;
        }
        HttpEngine httpEngine = getHttpEngine(httpConnection);
        if (httpEngine == null) {
            return null;
        }
        ResponseHeaders response = httpEngine.getResponseHeaders();
        if (response.hasVaryAll()) {
            return null;
        }
        Entry entry = new Entry(uri, httpEngine.getRequestHeaders().getHeaders().getAll(response.getVaryFields()), httpConnection);
        try {
            Editor editor = this.cache.edit(uriToKey(uri));
            if (editor == null) {
                return null;
            }
            entry.writeTo(editor);
            return new CacheRequestImpl(editor);
        } catch (IOException e) {
            abortQuietly(null);
            return null;
        }
    }

    private boolean maybeRemove(String requestMethod, URI uri) {
        if (!requestMethod.equals("POST") && !requestMethod.equals("PUT") && !requestMethod.equals("DELETE")) {
            return false;
        }
        try {
            this.cache.remove(uriToKey(uri));
        } catch (IOException e) {
        }
        return true;
    }

    private void update(CacheResponse conditionalCacheHit, HttpURLConnection httpConnection) throws IOException {
        HttpEngine httpEngine = getHttpEngine(httpConnection);
        Entry entry = new Entry(httpEngine.getUri(), httpEngine.getRequestHeaders().getHeaders().getAll(httpEngine.getResponseHeaders().getVaryFields()), httpConnection);
        try {
            Editor editor = (conditionalCacheHit instanceof EntryCacheResponse ? ((EntryCacheResponse) conditionalCacheHit).snapshot : ((EntrySecureCacheResponse) conditionalCacheHit).snapshot).edit();
            if (editor != null) {
                entry.writeTo(editor);
                editor.commit();
            }
        } catch (IOException e) {
            abortQuietly(null);
        }
    }

    private void abortQuietly(Editor editor) {
        if (editor != null) {
            try {
                editor.abort();
            } catch (IOException e) {
            }
        }
    }

    private HttpEngine getHttpEngine(URLConnection httpConnection) {
        if (httpConnection instanceof HttpURLConnectionImpl) {
            return ((HttpURLConnectionImpl) httpConnection).getHttpEngine();
        }
        if (httpConnection instanceof HttpsURLConnectionImpl) {
            return ((HttpsURLConnectionImpl) httpConnection).getHttpEngine();
        }
        return null;
    }

    public void delete() throws IOException {
        this.cache.delete();
    }

    public synchronized int getWriteAbortCount() {
        return this.writeAbortCount;
    }

    public synchronized int getWriteSuccessCount() {
        return this.writeSuccessCount;
    }

    public long getSize() {
        return this.cache.size();
    }

    public long getMaxSize() {
        return this.cache.getMaxSize();
    }

    public void flush() throws IOException {
        this.cache.flush();
    }

    public void close() throws IOException {
        this.cache.close();
    }

    public File getDirectory() {
        return this.cache.getDirectory();
    }

    public boolean isClosed() {
        return this.cache.isClosed();
    }

    private synchronized void trackResponse(ResponseSource source) {
        this.requestCount += ENTRY_BODY;
        switch (C01023.$SwitchMap$com$squareup$okhttp$ResponseSource[source.ordinal()]) {
            case ENTRY_BODY /*1*/:
                this.hitCount += ENTRY_BODY;
                break;
            case ENTRY_COUNT /*2*/:
            case WearableExtender.SIZE_MEDIUM /*3*/:
                this.networkCount += ENTRY_BODY;
                break;
        }
    }

    private synchronized void trackConditionalCacheHit() {
        this.hitCount += ENTRY_BODY;
    }

    public synchronized int getNetworkCount() {
        return this.networkCount;
    }

    public synchronized int getHitCount() {
        return this.hitCount;
    }

    public synchronized int getRequestCount() {
        return this.requestCount;
    }

    private static InputStream newBodyInputStream(Snapshot snapshot) {
        return new C01012(snapshot.getInputStream(ENTRY_BODY), snapshot);
    }
}
