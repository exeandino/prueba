package com.squareup.okhttp.internal.http;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.squareup.okhttp.internal.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class RawHeaders {
    private static final Comparator<String> FIELD_NAME_COMPARATOR;
    private int httpMinorVersion;
    private final List<String> namesAndValues;
    private String requestLine;
    private int responseCode;
    private String responseMessage;
    private String statusLine;

    /* renamed from: com.squareup.okhttp.internal.http.RawHeaders.1 */
    static class C01161 implements Comparator<String> {
        C01161() {
        }

        public int compare(String a, String b) {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return -1;
            }
            if (b == null) {
                return 1;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a, b);
        }
    }

    static {
        FIELD_NAME_COMPARATOR = new C01161();
    }

    public RawHeaders() {
        this.namesAndValues = new ArrayList(20);
        this.httpMinorVersion = 1;
        this.responseCode = -1;
    }

    public RawHeaders(RawHeaders copyFrom) {
        this.namesAndValues = new ArrayList(20);
        this.httpMinorVersion = 1;
        this.responseCode = -1;
        this.namesAndValues.addAll(copyFrom.namesAndValues);
        this.requestLine = copyFrom.requestLine;
        this.statusLine = copyFrom.statusLine;
        this.httpMinorVersion = copyFrom.httpMinorVersion;
        this.responseCode = copyFrom.responseCode;
        this.responseMessage = copyFrom.responseMessage;
    }

    public void setRequestLine(String requestLine) {
        this.requestLine = requestLine.trim();
    }

    public void setStatusLine(String statusLine) throws IOException {
        if (this.responseMessage != null) {
            throw new IllegalStateException("statusLine is already set");
        }
        boolean hasMessage = statusLine.length() > 13;
        if (!statusLine.startsWith("HTTP/1.") || statusLine.length() < 12 || statusLine.charAt(8) != ' ' || (hasMessage && statusLine.charAt(12) != ' ')) {
            throw new ProtocolException("Unexpected status line: " + statusLine);
        }
        int httpMinorVersion = statusLine.charAt(7) - 48;
        if (httpMinorVersion < 0 || httpMinorVersion > 9) {
            throw new ProtocolException("Unexpected status line: " + statusLine);
        }
        try {
            int responseCode = Integer.parseInt(statusLine.substring(9, 12));
            this.responseMessage = hasMessage ? statusLine.substring(13) : "";
            this.responseCode = responseCode;
            this.statusLine = statusLine;
            this.httpMinorVersion = httpMinorVersion;
        } catch (NumberFormatException e) {
            throw new ProtocolException("Unexpected status line: " + statusLine);
        }
    }

    public void addSpdyRequestHeaders(String method, String path, String version, String host, String scheme) {
        add(":method", method);
        add(":scheme", scheme);
        add(":path", path);
        add(":version", version);
        add(":host", host);
    }

    public String getStatusLine() {
        return this.statusLine;
    }

    public int getHttpMinorVersion() {
        return this.httpMinorVersion != -1 ? this.httpMinorVersion : 1;
    }

    public int getResponseCode() {
        return this.responseCode;
    }

    public String getResponseMessage() {
        return this.responseMessage;
    }

    public void addLine(String line) {
        int index = line.indexOf(":", 1);
        if (index != -1) {
            addLenient(line.substring(0, index), line.substring(index + 1));
        } else if (line.startsWith(":")) {
            addLenient("", line.substring(1));
        } else {
            addLenient("", line);
        }
    }

    public void add(String fieldName, String value) {
        if (fieldName == null) {
            throw new IllegalArgumentException("fieldname == null");
        } else if (value == null) {
            throw new IllegalArgumentException("value == null");
        } else if (fieldName.length() != 0 && fieldName.indexOf(0) == -1 && value.indexOf(0) == -1) {
            addLenient(fieldName, value);
        } else {
            throw new IllegalArgumentException("Unexpected header: " + fieldName + ": " + value);
        }
    }

    private void addLenient(String fieldName, String value) {
        this.namesAndValues.add(fieldName);
        this.namesAndValues.add(value.trim());
    }

    public void removeAll(String fieldName) {
        for (int i = 0; i < this.namesAndValues.size(); i += 2) {
            if (fieldName.equalsIgnoreCase((String) this.namesAndValues.get(i))) {
                this.namesAndValues.remove(i);
                this.namesAndValues.remove(i);
            }
        }
    }

    public void addAll(String fieldName, List<String> headerFields) {
        for (String value : headerFields) {
            add(fieldName, value);
        }
    }

    public void set(String fieldName, String value) {
        removeAll(fieldName);
        add(fieldName, value);
    }

    public int length() {
        return this.namesAndValues.size() / 2;
    }

    public String getFieldName(int index) {
        int fieldNameIndex = index * 2;
        if (fieldNameIndex < 0 || fieldNameIndex >= this.namesAndValues.size()) {
            return null;
        }
        return (String) this.namesAndValues.get(fieldNameIndex);
    }

    public Set<String> names() {
        TreeSet<String> result = new TreeSet(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < length(); i++) {
            result.add(getFieldName(i));
        }
        return Collections.unmodifiableSet(result);
    }

    public String getValue(int index) {
        int valueIndex = (index * 2) + 1;
        if (valueIndex < 0 || valueIndex >= this.namesAndValues.size()) {
            return null;
        }
        return (String) this.namesAndValues.get(valueIndex);
    }

    public String get(String fieldName) {
        for (int i = this.namesAndValues.size() - 2; i >= 0; i -= 2) {
            if (fieldName.equalsIgnoreCase((String) this.namesAndValues.get(i))) {
                return (String) this.namesAndValues.get(i + 1);
            }
        }
        return null;
    }

    public List<String> values(String name) {
        List<String> result = null;
        for (int i = 0; i < length(); i++) {
            if (name.equalsIgnoreCase(getFieldName(i))) {
                if (result == null) {
                    result = new ArrayList(2);
                }
                result.add(getValue(i));
            }
        }
        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }

    public RawHeaders getAll(Set<String> fieldNames) {
        RawHeaders result = new RawHeaders();
        for (int i = 0; i < this.namesAndValues.size(); i += 2) {
            String fieldName = (String) this.namesAndValues.get(i);
            if (fieldNames.contains(fieldName)) {
                result.add(fieldName, (String) this.namesAndValues.get(i + 1));
            }
        }
        return result;
    }

    public byte[] toBytes() throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder(AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
        result.append(this.requestLine).append("\r\n");
        for (int i = 0; i < this.namesAndValues.size(); i += 2) {
            result.append((String) this.namesAndValues.get(i)).append(": ").append((String) this.namesAndValues.get(i + 1)).append("\r\n");
        }
        result.append("\r\n");
        return result.toString().getBytes("ISO-8859-1");
    }

    public static RawHeaders fromBytes(InputStream in) throws IOException {
        RawHeaders headers;
        do {
            headers = new RawHeaders();
            headers.setStatusLine(Util.readAsciiLine(in));
            readHeaders(in, headers);
        } while (headers.getResponseCode() == 100);
        return headers;
    }

    public static void readHeaders(InputStream in, RawHeaders out) throws IOException {
        while (true) {
            String line = Util.readAsciiLine(in);
            if (line.length() != 0) {
                out.addLine(line);
            } else {
                return;
            }
        }
    }

    public Map<String, List<String>> toMultimap(boolean response) {
        Map<String, List<String>> result = new TreeMap(FIELD_NAME_COMPARATOR);
        for (int i = 0; i < this.namesAndValues.size(); i += 2) {
            String fieldName = (String) this.namesAndValues.get(i);
            String value = (String) this.namesAndValues.get(i + 1);
            List<String> allValues = new ArrayList();
            List<String> otherValues = (List) result.get(fieldName);
            if (otherValues != null) {
                allValues.addAll(otherValues);
            }
            allValues.add(value);
            result.put(fieldName, Collections.unmodifiableList(allValues));
        }
        if (response && this.statusLine != null) {
            result.put(null, Collections.unmodifiableList(Collections.singletonList(this.statusLine)));
        } else if (this.requestLine != null) {
            result.put(null, Collections.unmodifiableList(Collections.singletonList(this.requestLine)));
        }
        return Collections.unmodifiableMap(result);
    }

    public static RawHeaders fromMultimap(Map<String, List<String>> map, boolean response) throws IOException {
        if (response) {
            RawHeaders result = new RawHeaders();
            for (Entry<String, List<String>> entry : map.entrySet()) {
                String fieldName = (String) entry.getKey();
                List<String> values = (List) entry.getValue();
                if (fieldName != null) {
                    for (String value : values) {
                        result.addLenient(fieldName, value);
                    }
                } else if (!values.isEmpty()) {
                    result.setStatusLine((String) values.get(values.size() - 1));
                }
            }
            return result;
        }
        throw new UnsupportedOperationException();
    }

    public List<String> toNameValueBlock() {
        Set<String> names = new HashSet();
        List<String> result = new ArrayList();
        for (int i = 0; i < this.namesAndValues.size(); i += 2) {
            String name = ((String) this.namesAndValues.get(i)).toLowerCase(Locale.US);
            String value = (String) this.namesAndValues.get(i + 1);
            if (!(name.equals("connection") || name.equals("host") || name.equals("keep-alive") || name.equals("proxy-connection") || name.equals("transfer-encoding"))) {
                if (names.add(name)) {
                    result.add(name);
                    result.add(value);
                } else {
                    for (int j = 0; j < result.size(); j += 2) {
                        if (name.equals(result.get(j))) {
                            result.set(j + 1, ((String) result.get(j + 1)) + "\u0000" + value);
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    public static RawHeaders fromNameValueBlock(List<String> nameValueBlock) throws IOException {
        if (nameValueBlock.size() % 2 != 0) {
            throw new IllegalArgumentException("Unexpected name value block: " + nameValueBlock);
        }
        String status = null;
        String version = null;
        RawHeaders result = new RawHeaders();
        for (int i = 0; i < nameValueBlock.size(); i += 2) {
            String name = (String) nameValueBlock.get(i);
            String values = (String) nameValueBlock.get(i + 1);
            int start = 0;
            while (start < values.length()) {
                int end = values.indexOf(0, start);
                if (end == -1) {
                    end = values.length();
                }
                String value = values.substring(start, end);
                if (":status".equals(name)) {
                    status = value;
                } else if (":version".equals(name)) {
                    version = value;
                } else {
                    result.namesAndValues.add(name);
                    result.namesAndValues.add(value);
                }
                start = end + 1;
            }
        }
        if (status == null) {
            throw new ProtocolException("Expected ':status' header not present");
        } else if (version == null) {
            throw new ProtocolException("Expected ':version' header not present");
        } else {
            result.setStatusLine(version + " " + status);
            return result;
        }
    }
}
