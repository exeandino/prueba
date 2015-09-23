package com.squareup.okhttp.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.net.ssl.SSLSocket;

public class Platform {
    private static final Platform PLATFORM;
    private Constructor<DeflaterOutputStream> deflaterConstructor;

    private static class JettyNpnProvider implements InvocationHandler {
        private final List<String> protocols;
        private String selected;
        private boolean unsupported;

        public JettyNpnProvider(List<String> protocols) {
            this.protocols = protocols;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            Class<?> returnType = method.getReturnType();
            if (args == null) {
                args = Util.EMPTY_STRING_ARRAY;
            }
            if (methodName.equals("supports") && Boolean.TYPE == returnType) {
                return Boolean.valueOf(true);
            }
            if (methodName.equals("unsupported") && Void.TYPE == returnType) {
                this.unsupported = true;
                return null;
            } else if (methodName.equals("protocols") && args.length == 0) {
                return this.protocols;
            } else {
                if (methodName.equals("selectProtocol") && String.class == returnType && args.length == 1 && (args[0] == null || (args[0] instanceof List))) {
                    List<?> serverProtocols = args[0];
                    this.selected = (String) this.protocols.get(0);
                    return this.selected;
                } else if (!methodName.equals("protocolSelected") || args.length != 1) {
                    return method.invoke(this, args);
                } else {
                    this.selected = (String) args[0];
                    return null;
                }
            }
        }
    }

    private static class Android23 extends Platform {
        protected final Class<?> openSslSocketClass;
        private final Method setHostname;
        private final Method setUseSessionTickets;

        private Android23(Class<?> openSslSocketClass, Method setUseSessionTickets, Method setHostname) {
            this.openSslSocketClass = openSslSocketClass;
            this.setUseSessionTickets = setUseSessionTickets;
            this.setHostname = setHostname;
        }

        public void connectSocket(Socket socket, InetSocketAddress address, int connectTimeout) throws IOException {
            try {
                socket.connect(address, connectTimeout);
            } catch (SecurityException se) {
                IOException ioException = new IOException("Exception in connect");
                ioException.initCause(se);
                throw ioException;
            }
        }

        public void enableTlsExtensions(SSLSocket socket, String uriHost) {
            super.enableTlsExtensions(socket, uriHost);
            if (this.openSslSocketClass.isInstance(socket)) {
                try {
                    this.setUseSessionTickets.invoke(socket, new Object[]{Boolean.valueOf(true)});
                    this.setHostname.invoke(socket, new Object[]{uriHost});
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e2) {
                    throw new AssertionError(e2);
                }
            }
        }
    }

    private static class JdkWithJettyNpnPlatform extends Platform {
        private final Class<?> clientProviderClass;
        private final Method getMethod;
        private final Method putMethod;
        private final Class<?> serverProviderClass;

        public JdkWithJettyNpnPlatform(Method putMethod, Method getMethod, Class<?> clientProviderClass, Class<?> serverProviderClass) {
            this.putMethod = putMethod;
            this.getMethod = getMethod;
            this.clientProviderClass = clientProviderClass;
            this.serverProviderClass = serverProviderClass;
        }

        public void setNpnProtocols(SSLSocket socket, byte[] npnProtocols) {
            try {
                List<String> strings = new ArrayList();
                int i = 0;
                while (i < npnProtocols.length) {
                    int i2 = i + 1;
                    int length = npnProtocols[i];
                    strings.add(new String(npnProtocols, i2, length, "US-ASCII"));
                    i = i2 + length;
                }
                Object provider = Proxy.newProxyInstance(Platform.class.getClassLoader(), new Class[]{this.clientProviderClass, this.serverProviderClass}, new JettyNpnProvider(strings));
                this.putMethod.invoke(null, new Object[]{socket, provider});
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);
            } catch (InvocationTargetException e2) {
                throw new AssertionError(e2);
            } catch (IllegalAccessException e3) {
                throw new AssertionError(e3);
            }
        }

        public byte[] getNpnSelectedProtocol(SSLSocket socket) {
            byte[] bArr = null;
            try {
                JettyNpnProvider provider = (JettyNpnProvider) Proxy.getInvocationHandler(this.getMethod.invoke(null, new Object[]{socket}));
                if (!provider.unsupported && provider.selected == null) {
                    Logger.getLogger("com.squareup.okhttp.OkHttpClient").log(Level.INFO, "NPN callback dropped so SPDY is disabled. Is npn-boot on the boot class path?");
                } else if (!provider.unsupported) {
                    bArr = provider.selected.getBytes("US-ASCII");
                }
                return bArr;
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError();
            } catch (InvocationTargetException e2) {
                throw new AssertionError();
            } catch (IllegalAccessException e3) {
                throw new AssertionError();
            }
        }
    }

    private static class Android41 extends Android23 {
        private final Method getNpnSelectedProtocol;
        private final Method setNpnProtocols;

        private Android41(Class<?> openSslSocketClass, Method setUseSessionTickets, Method setHostname, Method setNpnProtocols, Method getNpnSelectedProtocol) {
            super(setUseSessionTickets, setHostname, null);
            this.setNpnProtocols = setNpnProtocols;
            this.getNpnSelectedProtocol = getNpnSelectedProtocol;
        }

        public void setNpnProtocols(SSLSocket socket, byte[] npnProtocols) {
            if (this.openSslSocketClass.isInstance(socket)) {
                try {
                    this.setNpnProtocols.invoke(socket, new Object[]{npnProtocols});
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                } catch (InvocationTargetException e2) {
                    throw new RuntimeException(e2);
                }
            }
        }

        public byte[] getNpnSelectedProtocol(SSLSocket socket) {
            if (!this.openSslSocketClass.isInstance(socket)) {
                return null;
            }
            try {
                return (byte[]) this.getNpnSelectedProtocol.invoke(socket, new Object[0]);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e2) {
                throw new AssertionError(e2);
            }
        }
    }

    static {
        PLATFORM = findPlatform();
    }

    public static Platform get() {
        return PLATFORM;
    }

    public String getPrefix() {
        return "OkHttp";
    }

    public void logW(String warning) {
        System.out.println(warning);
    }

    public void tagSocket(Socket socket) throws SocketException {
    }

    public void untagSocket(Socket socket) throws SocketException {
    }

    public URI toUriLenient(URL url) throws URISyntaxException {
        return url.toURI();
    }

    public void enableTlsExtensions(SSLSocket socket, String uriHost) {
    }

    public void supportTlsIntolerantServer(SSLSocket socket) {
        socket.setEnabledProtocols(new String[]{"SSLv3"});
    }

    public byte[] getNpnSelectedProtocol(SSLSocket socket) {
        return null;
    }

    public void setNpnProtocols(SSLSocket socket, byte[] npnProtocols) {
    }

    public void connectSocket(Socket socket, InetSocketAddress address, int connectTimeout) throws IOException {
        socket.connect(address, connectTimeout);
    }

    public OutputStream newDeflaterOutputStream(OutputStream out, Deflater deflater, boolean syncFlush) {
        try {
            Constructor<DeflaterOutputStream> constructor = this.deflaterConstructor;
            if (constructor == null) {
                constructor = DeflaterOutputStream.class.getConstructor(new Class[]{OutputStream.class, Deflater.class, Boolean.TYPE});
                this.deflaterConstructor = constructor;
            }
            return (OutputStream) constructor.newInstance(new Object[]{out, deflater, Boolean.valueOf(syncFlush)});
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("Cannot SPDY; no SYNC_FLUSH available");
        } catch (InvocationTargetException e2) {
            throw (e2.getCause() instanceof RuntimeException ? (RuntimeException) e2.getCause() : new RuntimeException(e2.getCause()));
        } catch (InstantiationException e3) {
            throw new RuntimeException(e3);
        } catch (IllegalAccessException e4) {
            throw new AssertionError();
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private static com.squareup.okhttp.internal.Platform findPlatform() {
        /*
        r0 = "com.android.org.conscrypt.OpenSSLSocketImpl";
        r1 = java.lang.Class.forName(r0);	 Catch:{ ClassNotFoundException -> 0x0040, NoSuchMethodException -> 0x00cc }
    L_0x0006:
        r0 = "setUseSessionTickets";
        r6 = 1;
        r6 = new java.lang.Class[r6];	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        r15 = 0;
        r16 = java.lang.Boolean.TYPE;	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        r6[r15] = r16;	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        r2 = r1.getMethod(r0, r6);	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        r0 = "setHostname";
        r6 = 1;
        r6 = new java.lang.Class[r6];	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        r15 = 0;
        r16 = java.lang.String.class;
        r6[r15] = r16;	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        r3 = r1.getMethod(r0, r6);	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        r0 = "setNpnProtocols";
        r6 = 1;
        r6 = new java.lang.Class[r6];	 Catch:{ NoSuchMethodException -> 0x0048, ClassNotFoundException -> 0x0050 }
        r15 = 0;
        r16 = byte[].class;
        r6[r15] = r16;	 Catch:{ NoSuchMethodException -> 0x0048, ClassNotFoundException -> 0x0050 }
        r4 = r1.getMethod(r0, r6);	 Catch:{ NoSuchMethodException -> 0x0048, ClassNotFoundException -> 0x0050 }
        r0 = "getNpnSelectedProtocol";
        r6 = 0;
        r6 = new java.lang.Class[r6];	 Catch:{ NoSuchMethodException -> 0x0048, ClassNotFoundException -> 0x0050 }
        r5 = r1.getMethod(r0, r6);	 Catch:{ NoSuchMethodException -> 0x0048, ClassNotFoundException -> 0x0050 }
        r0 = new com.squareup.okhttp.internal.Platform$Android41;	 Catch:{ NoSuchMethodException -> 0x0048, ClassNotFoundException -> 0x0050 }
        r6 = 0;
        r0.<init>(r2, r3, r4, r5, r6);	 Catch:{ NoSuchMethodException -> 0x0048, ClassNotFoundException -> 0x0050 }
    L_0x003f:
        return r0;
    L_0x0040:
        r9 = move-exception;
        r0 = "org.apache.harmony.xnet.provider.jsse.OpenSSLSocketImpl";
        r1 = java.lang.Class.forName(r0);	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        goto L_0x0006;
    L_0x0048:
        r9 = move-exception;
        r0 = new com.squareup.okhttp.internal.Platform$Android23;	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        r6 = 0;
        r0.<init>(r2, r3, r6);	 Catch:{ ClassNotFoundException -> 0x0050, NoSuchMethodException -> 0x00cc }
        goto L_0x003f;
    L_0x0050:
        r0 = move-exception;
    L_0x0051:
        r11 = "org.eclipse.jetty.npn.NextProtoNego";
        r10 = java.lang.Class.forName(r11);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = new java.lang.StringBuilder;	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0.<init>();	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = r0.append(r11);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r6 = "$Provider";
        r0 = r0.append(r6);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = r0.toString();	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r12 = java.lang.Class.forName(r0);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = new java.lang.StringBuilder;	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0.<init>();	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = r0.append(r11);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r6 = "$ClientProvider";
        r0 = r0.append(r6);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = r0.toString();	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r7 = java.lang.Class.forName(r0);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = new java.lang.StringBuilder;	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0.<init>();	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = r0.append(r11);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r6 = "$ServerProvider";
        r0 = r0.append(r6);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = r0.toString();	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r14 = java.lang.Class.forName(r0);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = "put";
        r6 = 2;
        r6 = new java.lang.Class[r6];	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r15 = 0;
        r16 = javax.net.ssl.SSLSocket.class;
        r6[r15] = r16;	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r15 = 1;
        r6[r15] = r12;	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r13 = r10.getMethod(r0, r6);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = "get";
        r6 = 1;
        r6 = new java.lang.Class[r6];	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r15 = 0;
        r16 = javax.net.ssl.SSLSocket.class;
        r6[r15] = r16;	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r8 = r10.getMethod(r0, r6);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0 = new com.squareup.okhttp.internal.Platform$JdkWithJettyNpnPlatform;	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        r0.<init>(r13, r8, r7, r14);	 Catch:{ ClassNotFoundException -> 0x00c2, NoSuchMethodException -> 0x00ca }
        goto L_0x003f;
    L_0x00c2:
        r0 = move-exception;
    L_0x00c3:
        r0 = new com.squareup.okhttp.internal.Platform;
        r0.<init>();
        goto L_0x003f;
    L_0x00ca:
        r0 = move-exception;
        goto L_0x00c3;
    L_0x00cc:
        r0 = move-exception;
        goto L_0x0051;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.squareup.okhttp.internal.Platform.findPlatform():com.squareup.okhttp.internal.Platform");
    }
}
