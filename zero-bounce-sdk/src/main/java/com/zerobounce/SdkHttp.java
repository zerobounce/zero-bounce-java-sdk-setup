package com.zerobounce;

import java.io.IOException;
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
