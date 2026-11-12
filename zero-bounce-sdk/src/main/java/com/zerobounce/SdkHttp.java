package com.zerobounce;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One outbound call. {@link Transport} replaces the network for a single {@link ZeroBounceSDK}.
 */
final class SdkHttp {

    final String method;
    final String url;
    final String contentType;
    final byte[] body;
    final Map<String, String> fields;
    /** The real clients disable redirects. The offline suite asserts this stays false. */
    final boolean followRedirects;

    SdkHttp(String method, String url, String contentType, byte[] body, Map<String, String> fields) {
        this.method = method;
        this.url = url;
        this.contentType = contentType;
        this.body = body == null ? new byte[0] : body;
        this.fields = fields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.followRedirects = false;
    }

    /**
     * The body of an {@link HttpURLConnection} response, checked against its
     * {@code Content-Length}. HttpURLConnection ends the stream quietly when the
     * connection closes mid-body, so a body cut short would otherwise read as a
     * complete one. Reaching the end with fewer bytes than declared throws the
     * same message as Apache HttpClient ("Premature end of Content-Length
     * delimited message body"), so the caller reports a transport failure with
     * status 0 and saves nothing.
     *
     * @return the stream to read, or null when the response has no body stream
     */
    static InputStream checkedBody(HttpURLConnection con, InputStream stream) {
        if (stream == null) {
            return null;
        }
        long declared = con.getContentLengthLong();
        if (declared < 0) {
            return stream;
        }
        return new FilterInputStream(stream) {
            private long received;

            @Override
            public int read() throws IOException {
                int b = super.read();
                if (b < 0) {
                    checkComplete();
                } else {
                    received++;
                }
                return b;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int n = super.read(buffer, offset, length);
                if (n < 0) {
                    checkComplete();
                } else {
                    received += n;
                }
                return n;
            }

            private void checkComplete() throws IOException {
                if (received < declared) {
                    throw new IOException("Premature end of Content-Length delimited message body (expected: "
                            + declared + "; received: " + received + ")");
                }
            }
        };
    }

    /**
     * A response body exactly as it arrived, decoded as UTF-8, line breaks
     * and all. Reading it line by line and joining the lines dropped every
     * line break, which turned a CSV download into a single line.
     */
    static String readText(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    interface Transport {
        Result exchange(SdkHttp call) throws IOException;
    }

    static final class Result {
        final int status;
        final String contentType;
        final byte[] body;

        Result(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType == null ? "" : contentType;
            this.body = body == null ? new byte[0] : body;
        }
    }
}
