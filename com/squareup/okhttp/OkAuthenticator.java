package com.squareup.okhttp;

import com.squareup.okhttp.internal.Base64;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;

public interface OkAuthenticator {

    public static final class Challenge {
        private final String realm;
        private final String scheme;

        public Challenge(String scheme, String realm) {
            this.scheme = scheme;
            this.realm = realm;
        }

        public String getScheme() {
            return this.scheme;
        }

        public String getRealm() {
            return this.realm;
        }

        public boolean equals(Object o) {
            return (o instanceof Challenge) && ((Challenge) o).scheme.equals(this.scheme) && ((Challenge) o).realm.equals(this.realm);
        }

        public int hashCode() {
            return this.scheme.hashCode() + (this.realm.hashCode() * 31);
        }

        public String toString() {
            return this.scheme + " realm=\"" + this.realm + "\"";
        }
    }

    public static final class Credential {
        private final String headerValue;

        private Credential(String headerValue) {
            this.headerValue = headerValue;
        }

        public static Credential basic(String userName, String password) {
            try {
                return new Credential("Basic " + Base64.encode((userName + ":" + password).getBytes("ISO-8859-1")));
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError();
            }
        }

        public String getHeaderValue() {
            return this.headerValue;
        }

        public boolean equals(Object o) {
            return (o instanceof Credential) && ((Credential) o).headerValue.equals(this.headerValue);
        }

        public int hashCode() {
            return this.headerValue.hashCode();
        }

        public String toString() {
            return this.headerValue;
        }
    }

    Credential authenticate(Proxy proxy, URL url, List<Challenge> list) throws IOException;

    Credential authenticateProxy(Proxy proxy, URL url, List<Challenge> list) throws IOException;
}
