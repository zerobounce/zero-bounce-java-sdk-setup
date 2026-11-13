package com.zerobounce;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.SubstituteLoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/** One outbound call, with a per-client replacement transport for offline tests. */
final class SdkHttp {

    /**
     * The HttpClient 5 loggers whose output at DEBUG or TRACE carries a request's URI,
     * headers or body, and so the API key (httpclient5 5.6.5, classic exchanges; httpcore5
     * 5.4.4 writes no logs). {@code MainClientExec} writes "executing GET &lt;uri&gt;", with
     * {@code api_key} in the query; {@code headers} writes the request line and every
     * request header, {@code API-Key} and {@code Authorization} among them; {@code wire}
     * writes every byte sent, bodies included.
     */
    static final List<String> KEY_LOGGERS = List.of(
            "org.apache.hc.client5.http.impl.classic.MainClientExec",
            "org.apache.hc.client5.http.headers",
            "org.apache.hc.client5.http.wire");

    /**
     * Loggers whose DEBUG or TRACE output carries text the server sent (a garbage status
     * line, an authentication challenge), which may echo the key back. Guarded like
     * {@link #KEY_LOGGERS}, and kept apart from them because they write only when the
     * server answers in a particular way: {@code LenientHttpResponseParser} logs a
     * garbage line it is given, and {@code BasicScheme} logs the realm of a Basic challenge.
     */
    static final List<String> SERVER_TEXT_LOGGERS = List.of(
            "org.apache.hc.client5.http.impl.io.LenientHttpResponseParser",
            "org.apache.hc.client5.http.impl.auth.BasicScheme");

    /** The error a call reports, with no request sent, while one of {@link #KEY_LOGGERS} is at DEBUG or TRACE. */
    static final String KEY_LOGGING_REFUSED = "Apache HttpClient debug logging (org.apache.hc) is enabled "
            + "and would write your API key to the log; set org.apache.hc to INFO or above.";

    /** The error a call reports, with no request sent, when SLF4J is still binding its loggers and cannot be checked. */
    static final String KEY_LOGGING_UNCHECKED = "SLF4J is still initialising its logging, so the Apache HttpClient "
            + "logging level could not be checked and no request was sent; retry the call.";

    /** How long a call waits for SLF4J to finish binding before it is refused. */
    private static final long SLF4J_WAIT_MILLIS = 10_000;

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
     * Reads are lock-free; a new timeout is admitted under the map's lock.
     */
    private static final ConcurrentMap<Integer, CloseableHttpClient> SHARED = new ConcurrentHashMap<>();
    /** Past this many distinct timeouts, a call gets a client of its own, closed after it. */
    private static final int MAX_SHARED_CLIENTS = 8;
    /** An idle pooled connection is closed by the shared client's evictor once idle this long. */
    private static final TimeValue MAX_IDLE = TimeValue.ofSeconds(5);

    static ConnectionConfig connectionConfig(int timeoutMillis) {
        Timeout timeout = Timeout.ofMilliseconds(timeoutMillis);
        return ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                // Automatic retries are off, so a pooled connection the server has
                // since closed would fail the call. A zero interval checks every
                // connection for staleness before it is leased, so none is reused dead.
                .setValidateAfterInactivity(TimeValue.ofMilliseconds(0))
                .build();
    }

    static RequestConfig requestConfig(int timeoutMillis) {
        Timeout timeout = Timeout.ofMilliseconds(timeoutMillis);
        return RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectionRequestTimeout(timeout)
                .setResponseTimeout(timeout)
                // How long an idle connection may be reused when the server does not say.
                .setConnectionKeepAlive(MAX_IDLE)
                .build();
    }

    /**
     * @param shared whether the client outlives the call. A shared client also
     *               runs HttpClient's idle-connection evictor, one daemon thread
     *               that closes connections idle for {@link #MAX_IDLE} or past their
     *               keep-alive. The pool itself never closes an idle connection until
     *               it is next used, so without it a burst of concurrent calls would
     *               leave that many sockets open indefinitely.
     */
    static CloseableHttpClient newClient(int timeoutMillis, boolean shared) {
        HttpClientBuilder builder = HttpClients.custom()
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
                .disableCookieManagement();
        if (shared) {
            builder.evictExpiredConnections().evictIdleConnections(MAX_IDLE);
        }
        return builder.build();
    }

    /**
     * The shared client for {@code timeoutMillis}, or null once
     * {@link #MAX_SHARED_CLIENTS} other timeouts have one. The size check and the
     * insert happen under one lock, so concurrent callers cannot pass the limit.
     */
    static CloseableHttpClient sharedClient(int timeoutMillis) {
        CloseableHttpClient client = SHARED.get(timeoutMillis);
        if (client != null) {
            return client;
        }
        synchronized (SHARED) {
            client = SHARED.get(timeoutMillis);
            if (client == null && SHARED.size() < MAX_SHARED_CLIENTS) {
                client = newClient(timeoutMillis, true);
                SHARED.put(timeoutMillis, client);
            }
            return client;
        }
    }

    /**
     * Closes and forgets the shared clients, so the next call builds new ones.
     * The offline suites call it whenever they replace {@code HttpClients.custom()}.
     */
    static void closeSharedClients() {
        synchronized (SHARED) {
            for (CloseableHttpClient client : SHARED.values()) {
                try {
                    client.close();
                } catch (IOException ignored) {
                    // nothing left to release
                }
            }
            SHARED.clear();
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

    /**
     * Refuses to send while one of {@link #KEY_LOGGERS} would write at DEBUG or TRACE.
     * Checked on every call, so a level changed at run time applies to the next one.
     */
    static void refuseWhileKeyLogging() throws IOException {
        awaitLoggerFactory();
        for (String name : guardedLoggers()) {
            Logger logger = LoggerFactory.getLogger(name);
            if (logger.isDebugEnabled() || logger.isTraceEnabled()) {
                throw new IOException(KEY_LOGGING_REFUSED);
            }
        }
    }

    private static List<String> guardedLoggers() {
        List<String> names = new ArrayList<>(KEY_LOGGERS);
        names.addAll(SERVER_TEXT_LOGGERS);
        return names;
    }

    /**
     * SLF4J binds its factory on first use. While that is under way, every other thread
     * is handed a substitute factory, whose loggers report DEBUG enabled, so a call made
     * then could not be checked. Such a call waits for the binding to finish, and is
     * refused only if it does not finish within {@link #SLF4J_WAIT_MILLIS}.
     */
    private static void awaitLoggerFactory() throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SLF4J_WAIT_MILLIS);
        while (LoggerFactory.getILoggerFactory() instanceof SubstituteLoggerFactory) {
            if (System.nanoTime() - deadline >= 0) {
                throw new IOException(KEY_LOGGING_UNCHECKED);
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(KEY_LOGGING_UNCHECKED, e);
            }
        }
    }

    /** The one place every request sent through HttpClient 5 goes, so every path is checked. */
    static Result execute(HttpUriRequestBase request, int timeoutMillis) throws IOException {
        refuseWhileKeyLogging();
        request.setConfig(requestConfig(timeoutMillis));
        CloseableHttpClient shared = sharedClient(timeoutMillis);
        if (shared != null) {
            return shared.execute(request, READ);
        }
        try (CloseableHttpClient client = newClient(timeoutMillis, false)) {
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
