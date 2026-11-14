package com.zerobounce;

import org.apache.hc.client5.http.impl.classic.MainClientExec;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finding S8 (completeness): the guard lists are hand-kept, and a logger left off them
 * is how the DigestScheme and BearerScheme echoes got through. This scans the httpclient5
 * jar on the test classpath for every class with a static {@link Logger} field, reads each
 * field's logger name, and fails unless every name is guarded ({@link SdkHttp}) or cleared
 * below with a one-line reason. A logger added by a future httpclient5 upgrade therefore
 * fails the build until someone decides which list it belongs on.
 */
class J2TransportLoggerCompletenessTest {

    /**
     * Loggers that write no request or server text at DEBUG or TRACE, with the reason. The
     * reasons were read from each class's log calls and string constants in httpclient5 5.6.5.
     */
    static final Map<String, String> CLEARED = Map.ofEntries(
            entry("org.apache.hc.client5.http.async.methods.IgnoreCompleteExceptionFutureCallback",
                    "async client only; the SDK builds classic clients, so this never runs"),
            entry("org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder",
                    "declares a logger and never calls it"),
            entry("org.apache.hc.client5.http.impl.InMemoryDnsResolver",
                    "host names and addresses only; the SDK uses the system resolver"),
            entry("org.apache.hc.client5.http.impl.async",
                    "async client only (LoggingExceptionCallback); the SDK builds classic clients"),
            entry("org.apache.hc.client5.http.impl.async.AbstractHttpAsyncClientBase",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.AsyncConnectExec",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.AsyncHttpRequestRetryExec",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.AsyncProtocolExec",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.AsyncRedirectExec",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.H2AsyncMainClientExec",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.HttpAsyncMainClientExec",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.InternalAbstractHttpAsyncClient",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.InternalH2AsyncClient",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.InternalHttpAsyncClient",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.MinimalH2AsyncClient",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient",
                    "async client only: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.classic.AbstractBackoff",
                    "no backoff manager is configured, so BackoffStrategyExec is never in the chain"),
            entry("org.apache.hc.client5.http.impl.classic.BackoffStrategyExec",
                    "no backoff manager is configured, so this exec is never in the chain"),
            entry("org.apache.hc.client5.http.impl.classic.CloseableHttpClient",
                    "WARN only, with a fixed message and the exception; no DEBUG request or server text"),
            entry("org.apache.hc.client5.http.impl.classic.ExponentialBackoffManager",
                    "no backoff manager is configured; logs route and connection counts only"),
            entry("org.apache.hc.client5.http.impl.classic.HttpRequestRetryExec",
                    "not in the chain: automatic retries are disabled (disableAutomaticRetries)"),
            entry("org.apache.hc.client5.http.impl.classic.InternalHttpClient",
                    "logs exchange ids and the fixed \"preparing request execution\" message only"),
            entry("org.apache.hc.client5.http.impl.classic.LinearBackoffManager",
                    "no backoff manager is configured; logs route and connection counts only"),
            entry("org.apache.hc.client5.http.impl.classic.MinimalHttpClient",
                    "declares a logger and never calls it"),
            entry("org.apache.hc.client5.http.impl.classic.ProtocolExec",
                    "logs auth states and fixed messages; header names, never header values"),
            entry("org.apache.hc.client5.http.impl.classic.RedirectExec",
                    "not in the chain: redirect handling is disabled (disableRedirectHandling)"),
            entry("org.apache.hc.client5.http.impl.cookie.PublicSuffixDomainFilter",
                    "logs host names only (public suffix list check)"),
            entry("org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager",
                    "single-connection manager, unused by the SDK; logs endpoint ids and routes only"),
            entry("org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator",
                    "logs connection and socket configuration only, no request or response text"),
            entry("org.apache.hc.client5.http.impl.io.DefaultManagedHttpClientConnection",
                    "connection lifecycle and timeouts only; request and response lines go to the guarded headers and wire loggers"),
            entry("org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager",
                    "pool events: endpoint ids, routes and states only"),
            entry("org.apache.hc.client5.http.impl.nio.DefaultAsyncClientConnectionOperator",
                    "async connection pool: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.nio.DefaultManagedAsyncClientConnection",
                    "async connection pool: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.nio.H2SharingConnPool",
                    "async connection pool: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.nio.MultihomeIOSessionRequester",
                    "async connection pool: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager",
                    "async connection pool: the SDK builds classic clients, so this logger is never reached"),
            entry("org.apache.hc.client5.http.protocol.RequestAddCookies",
                    "not in the chain: cookie management is disabled (disableCookieManagement)"),
            entry("org.apache.hc.client5.http.protocol.RequestClientConnControl",
                    "logs the fixed \"route not set\" message only"),
            entry("org.apache.hc.client5.http.protocol.RequestUpgrade",
                    "logs the protocol version and TLS upgrade method only"),
            entry("org.apache.hc.client5.http.protocol.ResponseProcessCookies",
                    "not in the chain: cookie management is disabled (disableCookieManagement)"),
            entry("org.apache.hc.client5.http.psl.PublicSuffixMatcherLoader",
                    "logs a failure to load the public suffix list only"),
            entry("org.apache.hc.client5.http.socket.UnixDomainSocketFactory",
                    "Unix domain sockets are not configured; logs socket support only"),
            entry("org.apache.hc.client5.http.ssl.AbstractClientTlsStrategy",
                    "TLS handshake: certificate names, protocols and cipher suites; no request or response text"),
            entry("org.apache.hc.client5.http.ssl.DefaultHostnameVerifier",
                    "TLS: certificate and host names only"),
            entry("org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory",
                    "TLS handshake: certificate names, protocols and cipher suites; no request or response text"),
            entry("org.apache.hc.client5.http2.flow",
                    "HTTP/2 flow control, reached only by the async client, which the SDK does not create"),
            entry("org.apache.hc.client5.http2.frame",
                    "HTTP/2 framing, reached only by the async client, which the SDK does not create"),
            entry("org.apache.hc.client5.http2.frame.payload",
                    "HTTP/2 framing, reached only by the async client, which the SDK does not create"));

    /**
     * Classes with a Logger that cannot be loaded here, because an optional dependency is not
     * on the SDK's classpath. Their logger names are unknown; each is cleared by class.
     */
    static final Map<String, String> UNREADABLE = Map.ofEntries();

    /** The httpclient5 jar, found from one of its own classes. */
    private static File jar() throws Exception {
        File jar = new File(MainClientExec.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(jar.isFile() && jar.getName().startsWith("httpclient5-"), "not the httpclient5 jar: " + jar);
        return jar;
    }

    /** Whether the class file's bytes mention the Logger type, without loading the class. */
    private static boolean declaresALogger(String className) throws Exception {
        try (java.io.InputStream in = J2TransportLoggerCompletenessTest.class.getClassLoader()
                .getResourceAsStream(className.replace('.', '/') + ".class")) {
            return in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1)
                    .contains("Lorg/slf4j/Logger;");
        }
    }

    /** Every static Logger field's logger name, with the class that declares it. */
    private static Map<String, String> loggerNames(Set<String> unreadable) throws Exception {
        Map<String, String> names = new TreeMap<>();
        try (JarFile jar = new JarFile(jar())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (!entry.endsWith(".class") || entry.contains("module-info") || entry.startsWith("META-INF")) {
                    continue;
                }
                String className = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
                Class<?> type;
                try {
                    type = Class.forName(className, false, J2TransportLoggerCompletenessTest.class.getClassLoader());
                    List<Field> loggers = new ArrayList<>();
                    for (Field field : type.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) && field.getType() == Logger.class) {
                            loggers.add(field);
                        }
                    }
                    if (loggers.isEmpty()) {
                        continue;
                    }
                    // Initialising the class assigns its loggers; nothing else it does is needed here.
                    Class.forName(className, true, type.getClassLoader());
                    for (Field field : loggers) {
                        field.setAccessible(true);
                        Logger logger = (Logger) field.get(null);
                        assertTrue(logger != null, className + "." + field.getName() + " is null after initialisation");
                        names.putIfAbsent(logger.getName(), className);
                    }
                } catch (LinkageError | ReflectiveOperationException e) {
                    // A class whose optional dependency is absent cannot be read. Its bytes show
                    // whether it declares a Logger; if so it must be listed in UNREADABLE by class.
                    if (declaresALogger(className)) {
                        unreadable.add(className + " (" + e + ")");
                    }
                }
            }
        }
        return names;
    }

    @Test
    void everyStaticLoggerInTheHttpClient5JarIsGuardedOrClearedWithAReason() throws Exception {
        List<String> failures = new ArrayList<>();
        Set<String> unreadable = new TreeSet<>();
        Map<String, String> found = loggerNames(unreadable);

        Set<String> guarded = new TreeSet<>();
        guarded.addAll(SdkHttp.KEY_LOGGERS);
        guarded.addAll(SdkHttp.SERVER_TEXT_LOGGERS);

        assertTrue(found.containsKey("org.apache.hc.client5.http.impl.classic.MainClientExec"),
                "the scan found no MainClientExec logger: " + found.keySet());

        for (Map.Entry<String, String> entry : found.entrySet()) {
            String name = entry.getKey();
            if (!guarded.contains(name) && !CLEARED.containsKey(name)) {
                failures.add("unguarded and not cleared: " + name + " (declared in " + entry.getValue() + ")");
            }
        }
        for (String name : guarded) {
            if (!found.containsKey(name)) {
                failures.add("guarded name is not a logger in httpclient5 5.6.5: " + name);
            }
        }
        for (String className : unreadable) {
            String name = className.replaceAll(" .*", "");
            if (!UNREADABLE.containsKey(name)) {
                failures.add("declares a Logger but cannot be loaded, and is not listed: " + className);
            }
        }
        for (Map.Entry<String, String> entry : UNREADABLE.entrySet()) {
            assertFalse(entry.getValue().isBlank(), "no reason for unreadable " + entry.getKey());
            if (!unreadable.stream().anyMatch(c -> c.startsWith(entry.getKey() + " "))) {
                failures.add("listed as unreadable but not found unreadable: " + entry.getKey());
            }
        }
        for (Map.Entry<String, String> entry : CLEARED.entrySet()) {
            if (!found.containsKey(entry.getKey())) {
                failures.add("cleared name is not a logger in httpclient5 5.6.5: " + entry.getKey());
            }
            assertFalse(entry.getValue().isBlank(), "no reason for " + entry.getKey());
            assertFalse(entry.getValue().contains("\n"), "reason is not one line for " + entry.getKey());
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
