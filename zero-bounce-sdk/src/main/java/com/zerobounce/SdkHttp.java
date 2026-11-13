package com.zerobounce;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One outbound call, with a per-client replacement transport for offline tests. */
final class SdkHttp {

    final String method;
    final String url;
    final String contentType;
    final byte[] body;
    final Map<String, String> fields;
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

    static ConnectionConfig connectionConfig(int timeoutMillis) {
        Timeout timeout = Timeout.ofMilliseconds(timeoutMillis);
        return ConnectionConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build();
    }

    static RequestConfig requestConfig(int timeoutMillis) {
        Timeout timeout = Timeout.ofMilliseconds(timeoutMillis);
        return RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectionRequestTimeout(timeout)
                .setResponseTimeout(timeout)
                .build();
    }

    static CloseableHttpClient newClient(int timeoutMillis) {
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig(timeoutMillis)).build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableDefaultUserAgent()
                .build();
    }

    static Result exchange(String method, URI uri, String contentType, byte[] body,
            Map<String, String> headers, int timeoutMillis) throws IOException {
        HttpUriRequestBase request = new HttpUriRequestBase(method, uri);
        request.setHeader("Accept", "application/json");
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.setHeader(header.getKey(), header.getValue());
            }
        }
        if (body != null) {
            request.setEntity(new ByteArrayEntity(body, contentType == null ? null : ContentType.parse(contentType)));
        }
        return execute(request, timeoutMillis);
    }

    static Result execute(HttpUriRequestBase request, int timeoutMillis) throws IOException {
        request.setConfig(requestConfig(timeoutMillis));
        // Keep the existing per-exchange lifetime: no new close obligation for SDK callers.
        try (CloseableHttpClient client = newClient(timeoutMillis)) {
            return client.execute(request, response -> {
                HttpEntity entity = response.getEntity();
                Header type = response.getFirstHeader("Content-Type");
                return new Result(response.getCode(), type == null ? "" : type.getValue(),
                        entity == null ? new byte[0] : EntityUtils.toByteArray(entity));
            });
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
