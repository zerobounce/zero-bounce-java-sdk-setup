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
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    /**
     * Clients shared by every {@link ZeroBounceSDK} and {@link ZeroBounceWarmup},
     * one per timeout, so calls reuse connections as HttpURLConnection did instead
     * of paying a new TCP and TLS handshake each time. SDK users never close them.
     */
    private static final ConcurrentMap<Integer, CloseableHttpClient> SHARED = new ConcurrentHashMap<>();
    /** Past this many distinct timeouts, a call gets a client of its own, closed after it. */
    private static final int MAX_SHARED_CLIENTS = 8;

    static ConnectionConfig connectionConfig(int timeoutMillis) {
        Timeout timeout = Timeout.ofMilliseconds(timeoutMillis);
        return ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                // Automatic retries are off, so a pooled connection the server has
                // since closed would fail the call: one idle for over a second is
                // checked before it is reused.
                .setValidateAfterInactivity(TimeValue.ofSeconds(1))
                .build();
    }

    static RequestConfig requestConfig(int timeoutMillis) {
        Timeout timeout = Timeout.ofMilliseconds(timeoutMillis);
        return RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectionRequestTimeout(timeout)
                .setResponseTimeout(timeout)
                // How long an idle connection stays pooled when the server does not say.
                .setConnectionKeepAlive(TimeValue.ofSeconds(5))
                .build();
    }

    static CloseableHttpClient newClient(int timeoutMillis) {
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig(timeoutMillis))
                        // No limit on concurrent calls, as before: HttpURLConnection had
                        // none and each Apache 4 call built a client of its own. A shared
                        // pool's default of 5 per host would make the sixth concurrent
                        // call wait.
                        .setMaxConnPerRoute(Integer.MAX_VALUE)
                        .setMaxConnTotal(Integer.MAX_VALUE)
                        .useSystemProperties()
                        .build())
                // The JVM's proxy and TLS settings (https.proxyHost, ProxySelector,
                // javax.net.ssl.*), which HttpURLConnection honoured. The default
                // User-Agent is kept: HttpURLConnection sent one too, and some
                // Cloudflare rules block a request without one.
                .useSystemProperties()
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
    }

    /** The shared client for {@code timeoutMillis}, or null past {@link #MAX_SHARED_CLIENTS}. */
    static CloseableHttpClient sharedClient(int timeoutMillis) {
        CloseableHttpClient client = SHARED.get(timeoutMillis);
        if (client != null || SHARED.size() >= MAX_SHARED_CLIENTS) {
            return client;
        }
        return SHARED.computeIfAbsent(timeoutMillis, SdkHttp::newClient);
    }

    /**
     * Closes and forgets the shared clients, so the next call builds new ones.
     * The offline suites call it whenever they replace {@code HttpClients.custom()}.
     */
    static void closeSharedClients() {
        for (Integer timeout : SHARED.keySet()) {
            CloseableHttpClient client = SHARED.remove(timeout);
            if (client != null) {
                try {
                    client.close();
                } catch (IOException ignored) {
                    // nothing left to release
                }
            }
        }
    }

    private static final HttpClientResponseHandler<Result> READ = response -> {
        HttpEntity entity = response.getEntity();
        Header type = response.getFirstHeader("Content-Type");
        return new Result(response.getCode(), type == null ? "" : type.getValue(),
                entity == null ? new byte[0] : EntityUtils.toByteArray(entity));
    };

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
        CloseableHttpClient shared = sharedClient(timeoutMillis);
        if (shared != null) {
            return shared.execute(request, READ);
        }
        try (CloseableHttpClient client = newClient(timeoutMillis)) {
            return client.execute(request, READ);
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
