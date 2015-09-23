package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.OkAuthenticator;
import com.squareup.okhttp.OkAuthenticator.Challenge;
import com.squareup.okhttp.OkAuthenticator.Credential;
import java.io.IOException;
import java.net.Authenticator;
import java.net.Authenticator.RequestorType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class HttpAuthenticator {
    public static final OkAuthenticator SYSTEM_DEFAULT;

    /* renamed from: com.squareup.okhttp.internal.http.HttpAuthenticator.1 */
    static class C02101 implements OkAuthenticator {
        C02101() {
        }

        public Credential authenticate(Proxy proxy, URL url, List<Challenge> challenges) throws IOException {
            for (Challenge challenge : challenges) {
                if ("Basic".equalsIgnoreCase(challenge.getScheme())) {
                    PasswordAuthentication auth = Authenticator.requestPasswordAuthentication(url.getHost(), getConnectToInetAddress(proxy, url), url.getPort(), url.getProtocol(), challenge.getRealm(), challenge.getScheme(), url, RequestorType.SERVER);
                    if (auth != null) {
                        return Credential.basic(auth.getUserName(), new String(auth.getPassword()));
                    }
                }
            }
            return null;
        }

        public Credential authenticateProxy(Proxy proxy, URL url, List<Challenge> challenges) throws IOException {
            for (Challenge challenge : challenges) {
                if ("Basic".equalsIgnoreCase(challenge.getScheme())) {
                    InetSocketAddress proxyAddress = (InetSocketAddress) proxy.address();
                    PasswordAuthentication auth = Authenticator.requestPasswordAuthentication(proxyAddress.getHostName(), getConnectToInetAddress(proxy, url), proxyAddress.getPort(), url.getProtocol(), challenge.getRealm(), challenge.getScheme(), url, RequestorType.PROXY);
                    if (auth != null) {
                        return Credential.basic(auth.getUserName(), new String(auth.getPassword()));
                    }
                }
            }
            return null;
        }

        private InetAddress getConnectToInetAddress(Proxy proxy, URL url) throws IOException {
            return (proxy == null || proxy.type() == Type.DIRECT) ? InetAddress.getByName(url.getHost()) : ((InetSocketAddress) proxy.address()).getAddress();
        }
    }

    static {
        SYSTEM_DEFAULT = new C02101();
    }

    private HttpAuthenticator() {
    }

    public static boolean processAuthHeader(OkAuthenticator authenticator, int responseCode, RawHeaders responseHeaders, RawHeaders successorRequestHeaders, Proxy proxy, URL url) throws IOException {
        String responseField;
        String requestField;
        if (responseCode == 401) {
            responseField = "WWW-Authenticate";
            requestField = "Authorization";
        } else if (responseCode == 407) {
            responseField = "Proxy-Authenticate";
            requestField = "Proxy-Authorization";
        } else {
            throw new IllegalArgumentException();
        }
        List<Challenge> challenges = parseChallenges(responseHeaders, responseField);
        if (challenges.isEmpty()) {
            return false;
        }
        Credential credential = responseHeaders.getResponseCode() == 407 ? authenticator.authenticateProxy(proxy, url, challenges) : authenticator.authenticate(proxy, url, challenges);
        if (credential == null) {
            return false;
        }
        successorRequestHeaders.set(requestField, credential.getHeaderValue());
        return true;
    }

    private static List<Challenge> parseChallenges(RawHeaders responseHeaders, String challengeHeader) {
        List<Challenge> result = new ArrayList();
        for (int h = 0; h < responseHeaders.length(); h++) {
            if (challengeHeader.equalsIgnoreCase(responseHeaders.getFieldName(h))) {
                String value = responseHeaders.getValue(h);
                int pos = 0;
                while (pos < value.length()) {
                    int tokenStart = pos;
                    pos = HeaderParser.skipUntil(value, pos, " ");
                    String scheme = value.substring(tokenStart, pos).trim();
                    pos = HeaderParser.skipWhitespace(value, pos);
                    if (!value.regionMatches(true, pos, "realm=\"", 0, "realm=\"".length())) {
                        break;
                    }
                    pos += "realm=\"".length();
                    int realmStart = pos;
                    pos = HeaderParser.skipUntil(value, pos, "\"");
                    String realm = value.substring(realmStart, pos);
                    pos = HeaderParser.skipWhitespace(value, HeaderParser.skipUntil(value, pos + 1, ",") + 1);
                    result.add(new Challenge(scheme, realm));
                }
            }
        }
        return result;
    }
}
