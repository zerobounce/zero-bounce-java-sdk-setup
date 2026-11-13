package com.zerobounce;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transport guards on every public request method of {@link ZeroBounceSDK} and
 * {@link ZeroBounceWarmup}, against a real HTTP server on a loopback socket
 * ({@link LoopbackServer}): a body cut short of its {@code Content-Length}, a
 * redirect with a {@code Location}, and connect and read timeouts. The methods come
 * from {@link RequestSweep}, which fails when a public request method is missing.
 */
class OfflineTransportTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "transport-key";
    /** The documented default timeout of both clients, in milliseconds. */
    private static final int DEFAULT_TIMEOUT = 120_000;
    private static final byte[] CSV = "email,status\na@example.com,valid\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] JSON = "{\"success\": true}                ".getBytes(StandardCharsets.UTF_8);

    private static boolean isDownload(Method method) {
        String name = method.getName().toLowerCase(Locale.ROOT);
        return name.contains("getfile") || name.startsWith("download");
    }

    private interface Sweep {
        void check(Method method, RequestSweep.Outcome outcome, List<String> failures) throws Exception;
    }

    /** Calls every public request method of both clients, wired to {@code server}. */
    private void sweep(RequestSweep sweep, LoopbackServer server, Sweep check) throws Exception {
        ZeroBounceSDK sdk = server.sdk();
        sdk.initialize(KEY);
        ZeroBounceWarmup warmup = server.warmup();
        warmup.initialize(KEY);
        List<String> failures = new ArrayList<>();
        int swept = 0;
        try (MockedStatic<HttpClients> ignored = server.apacheClients()) {
            for (Method method : RequestSweep.allRequestMethods()) {
                server.clear();
                Object client = method.getDeclaringClass() == ZeroBounceWarmup.class ? warmup : sdk;
                RequestSweep.Outcome outcome = sweep.invoke(client, method);
                if (outcome.thrown != null) {
                    failures.add(outcome + "");
                    continue;
                }
                if (server.requests().isEmpty()) {
                    failures.add(outcome.signature + " sent no request: " + outcome);
                    continue;
                }
                check.check(method, outcome, failures);
                swept++;
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
        assertEquals(sweep.sdkArgs.size() + sweep.warmupArgs.size(), swept, "every sweep entry was invoked");
    }

    @Test
    void theSweepCoversEveryPublicRequestMethod() {
        new RequestSweep(tempDir).assertComplete();
    }

    /**
     * A 200 that declares 1000 bytes and closes after 33 is a transport failure
     * (status 0) on every path, like Apache's "Premature end of Content-Length
     * delimited message body", and a download leaves no file.
     */
    @Test
    void everyRequestPathTreatsABodyCutShortAsATransportFailure() throws Exception {
        RequestSweep sweep = new RequestSweep(tempDir);
        LoopbackServer.Responder csv = LoopbackServer.cutShort("text/csv", CSV, 1000);
        LoopbackServer.Responder json = LoopbackServer.cutShort("application/json", JSON, 1000);
        try (LoopbackServer server = new LoopbackServer((request, out) -> {
            String target = request.target.toLowerCase(Locale.ROOT);
            (target.contains("getfile") || target.contains("download") ? csv : json).respond(request, out);
        })) {
            sweep(sweep, server, (method, outcome, failures) -> {
                if (!outcome.successes.isEmpty() || outcome.errors.size() != 1) {
                    failures.add(outcome.toString());
                    return;
                }
                ErrorResponse error = outcome.errors.get(0);
                if (error.getStatusCode() != 0 || !error.getErrors().toString().contains("Premature ")) {
                    failures.add(outcome.toString());
                }
                if (isDownload(method) && outcome.out != null && new File(outcome.out).exists()) {
                    failures.add(outcome.signature + " kept a partial file " + outcome.out);
                }
            });
        }
    }

    /**
     * Every path refuses each redirect status with a Location header: the error
     * callback gets the 3xx status, nothing reaches the Location, and no
     * connection is set to follow redirects.
     */
    @Test
    void everyRequestPathRefusesARedirect() throws Exception {
        try (LoopbackServer elsewhere = new LoopbackServer(LoopbackServer.answer(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8)))) {
            for (int status : new int[]{301, 302, 303, 307, 308}) {
                RequestSweep sweep = new RequestSweep(tempDir.resolve(String.valueOf(status)));
                Files.createDirectories(tempDir.resolve(String.valueOf(status)));
                String location = "http://127.0.0.1:" + elsewhere.port() + "/moved";
                try (LoopbackServer server = new LoopbackServer(LoopbackServer.redirect(status, location))) {
                    sweep(sweep, server, (method, outcome, failures) -> {
                        if (!elsewhere.requests().isEmpty()) {
                            failures.add(outcome.signature + " followed a " + status + " to " + elsewhere.requests());
                            elsewhere.clear();
                        }
                        if (server.requests().size() != 1) {
                            failures.add(outcome.signature + " sent " + server.requests().size() + " requests on a " + status);
                        }
                        if (!outcome.successes.isEmpty() || outcome.errors.size() != 1
                                || outcome.errors.get(0).getStatusCode() != status) {
                            failures.add(status + " " + outcome);
                        }
                        for (RequestConfig config : List.copyOf(server.apacheConfigs)) {
                            if (config.isRedirectsEnabled()) {
                                failures.add(outcome.signature + " opened a connection that follows redirects");
                            }
                        }
                    });
                }
            }
        }
    }

    /**
     * Every request the SDK sends has the documented default connect and read
     * timeout, 120 s, on the default settings. HttpClient 5 configures connection
     * establishment on the connection manager and response/lease timeouts on
     * each request. The exact value, so a changed or dropped default fails (T2).
     */
    @Test
    void everyRequestPathSetsConnectAndReadTimeouts() throws Exception {
        assertEquals(DEFAULT_TIMEOUT, SdkHttp.connectionConfig(DEFAULT_TIMEOUT).getConnectTimeout().toMilliseconds());
        assertEquals(DEFAULT_TIMEOUT, SdkHttp.connectionConfig(DEFAULT_TIMEOUT).getSocketTimeout().toMilliseconds());
        RequestSweep sweep = new RequestSweep(tempDir);
        LoopbackServer.Responder csv = LoopbackServer.answer(200, "text/csv", CSV);
        LoopbackServer.Responder json = LoopbackServer.answer(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        try (LoopbackServer server = new LoopbackServer((request, out) -> {
            String target = request.target.toLowerCase(Locale.ROOT);
            (target.contains("getfile") || target.contains("download") ? csv : json).respond(request, out);
        })) {
            sweep(sweep, server, (method, outcome, failures) -> {
                List<RequestConfig> configs = List.copyOf(server.apacheConfigs);
                if (configs.size() < server.requests().size()) {
                    failures.add(outcome.signature + " sent a request the sweep could not inspect");
                }
                for (RequestConfig config : configs) {
                    if (config == null || config.getResponseTimeout().toMilliseconds() != DEFAULT_TIMEOUT
                            || config.getConnectionRequestTimeout().toMilliseconds() != DEFAULT_TIMEOUT) {
                        failures.add(outcome.signature + " Apache config " + config);
                    }
                }
            });
        }
    }

    /**
     * The real client honours the JVM proxy settings, as HttpURLConnection did:
     * with {@code http.proxyHost} set, a request for another host goes to the
     * proxy, in absolute form, instead of straight to that host.
     */
    @Test
    void theJvmProxySettingsAreHonoured() throws Exception {
        String host = System.getProperty("http.proxyHost");
        String port = System.getProperty("http.proxyPort");
        try (LoopbackServer proxy = new LoopbackServer(LoopbackServer.answer(200, "application/json",
                "{}".getBytes(StandardCharsets.UTF_8)))) {
            SdkHttp.closeSharedClients();
            System.setProperty("http.proxyHost", "127.0.0.1");
            System.setProperty("http.proxyPort", String.valueOf(proxy.port()));
            String url = "http://proxied.invalid/v2/getcredits?api_key=" + KEY;
            SdkHttp.Result result = SdkHttp.exchange("GET", URI.create(url), null, null, null, 5_000);
            assertEquals(200, result.status);
            assertEquals(1, proxy.requests().size());
            assertEquals(url, proxy.requests().get(0).target);
        } finally {
            restoreProperty("http.proxyHost", host);
            restoreProperty("http.proxyPort", port);
            SdkHttp.closeSharedClients();
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    /**
     * Requests carry the HTTP client's own User-Agent, as HttpURLConnection sent
     * its own: a request with none is one some Cloudflare rules block.
     */
    @Test
    void requestsCarryTheHttpClientsUserAgent() throws Exception {
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json",
                "{}".getBytes(StandardCharsets.UTF_8)))) {
            SdkHttp.exchange("GET", URI.create("http://127.0.0.1:" + server.port() + "/v2/getcredits"),
                    null, null, null, 5_000);
            String agent = server.requests().get(0).headers.get("user-agent");
            assertTrue(agent != null && agent.startsWith("Apache-HttpClient/"), String.valueOf(agent));
        }
    }

    /**
     * Calls with the same timeout share one client, and so its connection pool,
     * across calls and instances. Past eight distinct timeouts a call gets a
     * client of its own instead of growing the set without bound.
     */
    @Test
    void callsWithTheSameTimeoutShareOneClient() {
        SdkHttp.closeSharedClients();
        try {
            assertSame(SdkHttp.sharedClient(1_234), SdkHttp.sharedClient(1_234));
            assertNotSame(SdkHttp.sharedClient(1_234), SdkHttp.sharedClient(4_321));
            for (int timeout = 1; timeout <= 6; timeout++) {
                assertNotNull(SdkHttp.sharedClient(timeout), "shared client " + timeout);
            }
            assertNull(SdkHttp.sharedClient(99_999), "a ninth distinct timeout");
        } finally {
            SdkHttp.closeSharedClients();
        }
    }

    /**
     * Callers that arrive together with distinct timeouts cannot push the shared
     * clients past eight: the size check and the insert are one step.
     */
    @Test
    void concurrentCallersCannotPassTheSharedClientLimit() throws Exception {
        SdkHttp.closeSharedClients();
        int callers = 64;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> admitted = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                int timeout = 10_000 + i;
                admitted.add(pool.submit(() -> {
                    start.await();
                    return SdkHttp.sharedClient(timeout) != null;
                }));
            }
            start.countDown();
            int shared = 0;
            for (Future<Boolean> result : admitted) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    shared++;
                }
            }
            assertEquals(8, shared, "distinct timeouts given a shared client");
        } finally {
            pool.shutdownNow();
            SdkHttp.closeSharedClients();
        }
    }

    @Test
    void everyRequestPathReports429And503WithoutRetrying() throws Exception {
        for (int status : new int[]{429, 503}) {
            Path dir = tempDir.resolve(String.valueOf(status));
            Files.createDirectories(dir);
            RequestSweep sweep = new RequestSweep(dir);
            try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(status,
                    "application/json", "{\"error\":\"Try again later\"}".getBytes(StandardCharsets.UTF_8)))) {
                sweep(sweep, server, (method, outcome, failures) -> {
                    if (server.requests().size() != 1 || !outcome.successes.isEmpty()
                            || outcome.errors.size() != 1 || outcome.errors.get(0).getStatusCode() != status) {
                        failures.add(status + " " + outcome + " requests=" + server.requests().size());
                    }
                });
            }
        }
    }

    @Test
    void jsonFormsAndAuthenticationReachTheServerUnchanged() throws Exception {
        String key = "key+with/slash";
        String email = "té+tag@example.com";
        List<ErrorResponse> errors = new ArrayList<>();
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(400,
                "application/json", "{\"error\":\"offline fixture\"}".getBytes(StandardCharsets.UTF_8)));
             MockedStatic<HttpClients> ignored = server.apacheClients()) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(key);
            sdk.validateBatch(List.of(new ZBValidateBatchData(email, null)), r -> {
                throw new AssertionError("expected fixture error");
            }, errors::add);
            LoopbackServer.Request json = server.requests().get(0);
            assertEquals("POST", json.method);
            assertEquals("application/json; charset=UTF-8", json.headers.get("content-type"));
            assertEquals("{\"api_key\":\"" + key + "\",\"email_batch\":[{\"email_address\":\"" + email + "\"}]}",
                    new String(json.body, StandardCharsets.UTF_8));

            ZBValidateRequestOptions options = new ZBValidateRequestOptions();
            options.setUsePost(true);
            sdk.validateWithOptions(email, options, r -> {
                throw new AssertionError("expected fixture error");
            }, errors::add);
            LoopbackServer.Request form = server.requests().get(1);
            assertEquals("POST", form.method);
            assertEquals("application/x-www-form-urlencoded", form.headers.get("content-type"));
            String body = new String(form.body, StandardCharsets.UTF_8);
            assertTrue(body.contains("api_key=key%2Bwith%2Fslash"), body);
            assertTrue(body.contains("email=t%C3%A9%2Btag%40example.com"), body);

            sdk.createInboxPlacementTest("wire check", null, r -> {
                throw new AssertionError("expected fixture error");
            }, errors::add);
            LoopbackServer.Request bearer = server.requests().get(2);
            assertEquals("Bearer " + key, bearer.headers.get("authorization"));
            assertFalse(bearer.target.contains("api_key"), bearer.target);
            assertEquals("{\"name\":\"wire check\"}", new String(bearer.body, StandardCharsets.UTF_8));

            ZeroBounceWarmup warmup = server.warmup();
            warmup.initialize(key);
            warmup.getSeedCount(r -> {
                throw new AssertionError("expected fixture error");
            }, errors::add);
            LoopbackServer.Request warmupRequest = server.requests().get(3);
            assertEquals(key, warmupRequest.headers.get("api-key"));
            assertFalse(warmupRequest.target.contains(key), warmupRequest.target);
            assertEquals(4, server.requests().size());
            assertEquals(4, errors.size());
            for (ErrorResponse error : errors) {
                assertEquals(400, error.getStatusCode());
            }
        }
    }

    /**
     * A server that accepts the request and never answers ends in a timeout
     * error (status 0) on each kind of transport: the JSON calls, the bulk-root
     * downloads, the Apache download and upload, and Warmup.
     */
    @Test
    void aServerThatNeverAnswersEndsInATimeout() throws Exception {
        try (LoopbackServer server = LoopbackServer.silent()) {
            File in = tempDir.resolve("in.csv").toFile();
            Files.writeString(in.toPath(), "email\na@example.com\n");
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY, 300);
            ZeroBounceWarmup warmup = server.warmup();
            warmup.initialize(KEY);
            warmup.setTimeout(300);

            List<String> results = new ArrayList<>();
            List<ThrowingRunnable> calls = List.of(
                    () -> sdk.getCredits(r -> results.add("getCredits success"), e -> results.add("getCredits " + e.getStatusCode() + " " + e.getErrors())),
                    () -> sdk.addFilter("allow", "email", "a@example.com", r -> results.add("addFilter success"), e -> results.add("addFilter " + e.getStatusCode() + " " + e.getErrors())),
                    () -> sdk.emailFinderGetFile("fid", tempDir.resolve("ef.csv").toString(), r -> results.add("emailFinderGetFile success"), e -> results.add("emailFinderGetFile " + e.getStatusCode() + " " + e.getErrors())),
                    () -> sdk.getFile("fid", tempDir.resolve("gf.csv").toString(), r -> results.add("getFile success"), e -> results.add("getFile " + e.getStatusCode() + " " + e.getErrors())),
                    () -> sdk.sendFile(in, 1, null, r -> results.add("sendFile success"), e -> results.add("sendFile " + e.getStatusCode() + " " + e.getErrors())),
                    () -> warmup.getSeeds(r -> results.add("getSeeds success"), e -> results.add("getSeeds " + e.getStatusCode() + " " + e.getErrors()))
            );
            for (ThrowingRunnable call : calls) {
                int before = results.size();
                // The static HttpClients mock is per thread, so it is opened on
                // the thread that makes the call.
                assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                    try (MockedStatic<HttpClients> ignored = server.apacheClients()) {
                        call.run();
                    }
                }, () -> "no answer within 10 s although the timeout is 300 ms: " + results);
                assertEquals(before + 1, results.size(), results.toString());
                String result = results.get(before);
                assertTrue(result.contains(" 0 [") && result.toLowerCase(Locale.ROOT).contains("timed out"), result);
            }
        }
    }

    /** The public request methods whose success callback receives the body as text. */
    private static List<Method> textResults() {
        List<Method> text = new ArrayList<>();
        for (Method method : RequestSweep.allRequestMethods()) {
            for (java.lang.reflect.Type type : method.getGenericParameterTypes()) {
                if (type instanceof java.lang.reflect.ParameterizedType
                        && ((java.lang.reflect.ParameterizedType) type).getRawType() == ZeroBounceSDK.OnSuccessCallback.class
                        && ((java.lang.reflect.ParameterizedType) type).getActualTypeArguments()[0] == String.class) {
                    text.add(method);
                }
            }
        }
        return text;
    }

    /**
     * A CSV download comes back exactly as it was served: every line, LF or
     * CRLF line endings, a trailing newline or none, and UTF-8 text. 3.0.1
     * read it line by line and joined the lines, so a seed list came back as
     * {@code seed,providerseed1@example.com,gmail}.
     */
    @Test
    void aTextDownloadComesBackByteForByte() throws Exception {
        List<Method> methods = textResults();
        List<String> names = new ArrayList<>();
        methods.forEach(method -> names.add(method.getName()));
        assertTrue(names.containsAll(List.of("downloadSeeds", "downloadStatistics")), names.toString());
        String[] bodies = {
                "seed,provider\nseed1@example.com,gmail\nseed2@example.com,outlook\n",
                "seed,provider\r\nseed1@example.com,gmail\r\nseed2@example.com,outlook\r\n",
                "date,sent\r\n2026-01-01,5\n2026-01-02,7\n\n2026-01-03,José",
        };
        RequestSweep sweep = new RequestSweep(tempDir);
        List<String> failures = new ArrayList<>();
        for (String served : bodies) {
            byte[] bytes = served.getBytes(StandardCharsets.UTF_8);
            try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "text/csv", bytes))) {
                ZeroBounceWarmup warmup = server.warmup();
                warmup.initialize(KEY);
                for (Method method : methods) {
                    RequestSweep.Outcome outcome = sweep.invoke(warmup, method);
                    if (outcome.successes.size() != 1 || !served.equals(outcome.successes.get(0))) {
                        failures.add(outcome.signature + " served " + served.replace("\r", "\\r").replace("\n", "\\n")
                                + " got " + outcome);
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /**
     * A multi-line error body keeps its line breaks on every path: no error
     * message joins two of its lines into one.
     */
    @Test
    void noPathJoinsTheLinesOfABody() throws Exception {
        RequestSweep sweep = new RequestSweep(tempDir);
        byte[] body = "first line\r\nsecond line\nthird line\n".getBytes(StandardCharsets.UTF_8);
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(400, "text/plain", body))) {
            sweep(sweep, server, (method, outcome, failures) -> {
                if (!outcome.successes.isEmpty() || outcome.errors.size() != 1
                        || outcome.errors.get(0).getStatusCode() != 400) {
                    failures.add(outcome.toString());
                    return;
                }
                String text = String.join(" | ", outcome.errors.get(0).getErrors());
                if (text.contains("first linesecond line") || text.contains("second linethird line")) {
                    failures.add(outcome.signature + " joined the lines: " + text);
                }
            });
        }
    }

    /**
     * {@code {"success": false}} on an HTTP 200 is an error with status 200 on
     * every request path, the uploads included (the API answers an unknown file
     * id that way).
     */
    @Test
    void successFalseOnAnHttp200IsAnErrorOnEveryPath() {
        RequestSweep sweep = new RequestSweep(tempDir);
        byte[] body = "{\"success\":false,\"message\":\"File cannot be found.\"}".getBytes(StandardCharsets.UTF_8);
        List<String> paths = new ArrayList<>();
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = call -> {
            paths.add(call.url);
            return new SdkHttp.Result(200, "application/json", body);
        };
        sdk.initialize(KEY);
        List<String> failures = new ArrayList<>();
        for (Method method : RequestSweep.requestMethods(ZeroBounceSDK.class)) {
            paths.clear();
            RequestSweep.Outcome outcome = sweep.invoke(sdk, method);
            if (paths.isEmpty() || outcome.thrown != null || !outcome.successes.isEmpty()
                    || outcome.errors.size() != 1 || outcome.errors.get(0).getStatusCode() != 200
                    || !outcome.errors.get(0).getErrors().toString().contains("File cannot be found.")) {
                failures.add(outcome.toString());
            }
            if (isDownload(method) && outcome.out != null && new File(outcome.out).exists()) {
                failures.add(outcome.signature + " saved the error body as " + outcome.out);
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * A download whose write fails part-way leaves no partial file behind and
     * the file already at the target exactly as it was, and the failure reaches
     * the error callback.
     */
    @Test
    void aDownloadThatFailsPartWayThroughWritingLeavesNoFile() throws Exception {
        File file = tempDir.resolve("partial.csv").toFile();
        Files.writeString(file.toPath(), "OLD RESULTS");
        byte[] body = "email,status\na@example.com,valid\nb@example.com,invalid\n".getBytes(StandardCharsets.UTF_8);
        AtomicLong written = new AtomicLong(-1);
        List<String> errors = new ArrayList<>();

        boolean saved = ZeroBounceSDK.saveDownload(file, body, error -> errors.addAll(error.getErrors()),
                target -> new FilterOutputStream(new FileOutputStream(target)) {
                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        out.write(bytes, offset, length / 2);
                        out.flush();
                        written.set(target.length());
                        throw new IOException("No space left on device");
                    }
                });

        assertFalse(saved);
        assertTrue(written.get() > 0, "the opener wrote part of the file before failing");
        assertEquals(List.of("No space left on device"), errors);
        assertEquals("OLD RESULTS", Files.readString(file.toPath()), "the existing file was left as it was");
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            assertEquals(List.of(file.toPath()), files.collect(java.util.stream.Collectors.toList()),
                    "no partial file was left");
        }
    }

    /**
     * Every download whose disk write fails part-way (a full disk, a file size
     * limit) reports one error and leaves the file already at its target as it
     * was, with no partial file next to it (D4). A download that succeeds
     * replaces the existing file.
     */
    @Test
    void everyDownloadKeepsTheExistingFileWhenTheWriteFails() throws Exception {
        for (boolean failing : new boolean[]{true, false}) {
            Path dir = tempDir.resolve("write-" + failing);
            Files.createDirectories(dir);
            RequestSweep sweep = new RequestSweep(dir);
            sweep.existingOut = "OLD RESULTS";
            LoopbackServer.Responder dl = LoopbackServer.answer(200, "text/csv", CSV);
            LoopbackServer.Responder js = LoopbackServer.answer(200, "application/json", JSON);
            ZeroBounceSDK.FileOpener original = ZeroBounceSDK.fileOpener;
            if (failing) {
                ZeroBounceSDK.fileOpener = target -> new FilterOutputStream(new FileOutputStream(target)) {
                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        out.write(bytes, offset, length / 2);
                        out.flush();
                        throw new IOException("File too large");
                    }
                };
            }
            int[] downloads = {0};
            try (LoopbackServer server = new LoopbackServer((request, out) -> {
                String target = request.target.toLowerCase(Locale.ROOT);
                (target.contains("getfile") ? dl : js).respond(request, out);
            })) {
                sweep(sweep, server, (method, outcome, failures) -> {
                    if (outcome.out == null || !isDownload(method)) {
                        return;
                    }
                    downloads[0]++;
                    Path out = Path.of(outcome.out);
                    String expected = failing ? "OLD RESULTS" : new String(CSV, StandardCharsets.UTF_8);
                    if (failing != (outcome.successes.isEmpty() && outcome.errors.size() == 1)
                            || !expected.equals(Files.readString(out))) {
                        failures.add(outcome + " left " + Files.readString(out));
                    }
                    try (java.util.stream.Stream<Path> files = Files.list(out.getParent())) {
                        files.filter(f -> f.getFileName().toString().endsWith(".part"))
                                .forEach(f -> failures.add(outcome.signature + " left " + f));
                    }
                });
            } finally {
                ZeroBounceSDK.fileOpener = original;
            }
            assertEquals(6, downloads[0], "every download that writes a file");
        }
    }

    /**
     * Answers every JSON call with {@code body} under {@code contentType}, and
     * every download with {@code download} as text/csv, and checks that each call
     * the body is meant for reports one error with status 200 whose message
     * contains {@code expected} (Warmup's JSON calls quote a short text body
     * instead), and that no download leaves a file (E7, D2).
     */
    private void bodySweep(String label, String contentType, String body, byte[] download, String expected)
            throws Exception {
        Files.createDirectories(tempDir.resolve(label));
        RequestSweep sweep = new RequestSweep(tempDir.resolve(label));
        LoopbackServer.Responder dl = LoopbackServer.answer(200, "text/csv", download);
        LoopbackServer.Responder js = LoopbackServer.answer(200, contentType, body.getBytes(StandardCharsets.UTF_8));
        try (LoopbackServer server = new LoopbackServer((request, out) -> {
            String target = request.target.toLowerCase(Locale.ROOT);
            (target.contains("getfile") || target.contains("download") ? dl : js).respond(request, out);
        })) {
            sweep(sweep, server, (method, outcome, failures) -> {
                if (download == CSV && isDownload(method)) {
                    return;
                }
                // Warmup quotes a short text body as its message, as it always has.
                boolean checkMessage = method.getDeclaringClass() == ZeroBounceSDK.class
                        || expected.startsWith("HTML");
                if (!outcome.successes.isEmpty() || outcome.errors.size() != 1
                        || outcome.errors.get(0).getStatusCode() != 200
                        || checkMessage && !outcome.errors.get(0).getErrors().toString().contains(expected)) {
                    failures.add(label + " " + outcome);
                }
                if (isDownload(method) && outcome.out != null && new File(outcome.out).exists()) {
                    failures.add(label + " " + outcome.signature + " wrote " + outcome.out);
                }
            });
        }
    }

    /**
     * An HTML content type alone makes a 2xx an HTML error page, whatever the
     * body: a JSON-looking {@code {"a":1}} on every JSON call (Warmup included)
     * and a CSV-looking body on every download, Warmup's CSV downloads included.
     * Each call reports one error with status 200 and no download leaves a file.
     * The bodies pass every body check, so only the content-type half of the
     * guard refuses them.
     */
    @Test
    void anHtmlContentTypeAloneIsAnErrorOnEveryPath() throws Exception {
        contentTypeSweep("html-type", "text/html; charset=UTF-8", true);
    }

    /**
     * A JSON content type alone makes a download an error, whatever the body:
     * a CSV-looking body under application/json is refused on every download,
     * Warmup's CSV downloads included, and no file is written.
     */
    @Test
    void aJsonContentTypeAloneIsAnErrorOnEveryDownload() throws Exception {
        contentTypeSweep("json-type", "application/json", false);
    }

    private void contentTypeSweep(String label, String contentType, boolean jsonCalls) throws Exception {
        Files.createDirectories(tempDir.resolve(label));
        RequestSweep sweep = new RequestSweep(tempDir.resolve(label));
        LoopbackServer.Responder dl = LoopbackServer.answer(200, contentType, CSV);
        LoopbackServer.Responder js = LoopbackServer.answer(200, jsonCalls ? contentType : "application/json",
                "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        int[] downloads = {0};
        try (LoopbackServer server = new LoopbackServer((request, out) -> {
            String target = request.target.toLowerCase(Locale.ROOT);
            (target.contains("getfile") || target.contains("download") ? dl : js).respond(request, out);
        })) {
            sweep(sweep, server, (method, outcome, failures) -> {
                if (!jsonCalls && !isDownload(method)) {
                    return;
                }
                if (isDownload(method)) {
                    downloads[0]++;
                }
                if (!outcome.successes.isEmpty() || outcome.errors.size() != 1
                        || outcome.errors.get(0).getStatusCode() != 200) {
                    failures.add(label + " " + outcome);
                }
                if (isDownload(method) && outcome.out != null && new File(outcome.out).exists()) {
                    failures.add(label + " " + outcome.signature + " wrote " + outcome.out);
                }
            });
        }
        assertEquals(8, downloads[0], "every download, Warmup's two CSV downloads included");
    }

    /**
     * A 2xx whose body is not JSON is an API error with status 200 on every JSON
     * call, the ten bulk uploads included (they reported status 0). A single word
     * is checked too: Gson's lenient parser reads it as a JSON string, so only
     * the SDK's own first-character check refuses it.
     */
    @Test
    void aNonJsonBodyOnA2xxIsAnErrorWithStatus200OnEveryPath() throws Exception {
        bodySweep("words", "text/plain", "hello there", CSV, "not valid JSON");
        bodySweep("word", "application/json", "hello", CSV, "not valid JSON");
    }

    /**
     * A 2xx body that only a lenient parser reads as JSON is not JSON: unquoted
     * or single-quoted names, a trailing comma, {@code ;} or {@code =} as a
     * separator, NaN and Infinity, a word that starts with a digit, a JSON value
     * with more after it, and a CSV
     * under application/json. Gson reads every one of these leniently, so each
     * is an API error with status 200 on every JSON call, Warmup included.
     */
    @Test
    void aBodyOnlyALenientParserAcceptsIsAnErrorWithStatus200OnEveryPath() throws Exception {
        String[] bodies = {"{a:1}", "{'a':'b'}", "[1,]", "[1;2]", "{\"a\"=1}", "{\"a\":NaN}", "-Infinity",
                "123abc", "{\"a\":1} {\"b\":2}", "[1] x", "email,status\na@b.com,valid\n"};
        for (int i = 0; i < bodies.length; i++) {
            bodySweep("lenient-" + i, "application/json", bodies[i], CSV, "not valid JSON");
        }
    }

    /**
     * An HTML page on a 2xx is reported as an HTML page, with status 200, on
     * every path, downloads included, whatever the content type says: a page
     * found by its doctype alone (no {@code <html>}, {@code <head>} or
     * {@code <body>} tag) under application/json and under text/csv.
     */
    @Test
    void anHtmlBodyOnA2xxIsReportedAsHtmlOnEveryPath() throws Exception {
        String page = "<!DOCTYPE html><title>Blocked</title><p>Try later</p>";
        bodySweep("html", "application/json", page, page.getBytes(StandardCharsets.UTF_8), "HTML error page");
    }

    /**
     * Each marker of the HTML sniff counts on its own (check E, standard.md section 3):
     * a page with {@code <html}, {@code <head} or {@code <body} and no doctype, served
     * under application/json or text/csv, is an HTML error page with status 200 on every
     * path, and a download writes no file. Removing any one marker fails this test.
     */
    @Test
    void eachHtmlMarkerAloneMakesAnHtmlPageOnEveryPath() throws Exception {
        String[] pages = {
                "<html lang=\"en\"><p>Blocked</p></html>",
                "<head><title>Blocked</title></head><p>Try later</p>",
                "<body><p>Blocked. Try later.</p></body>"};
        for (int i = 0; i < pages.length; i++) {
            byte[] bytes = pages[i].getBytes(StandardCharsets.UTF_8);
            bodySweep("marker-json-" + i, "application/json", pages[i], bytes, "HTML error page");
            bodySweep("marker-csv-" + i, "text/csv", pages[i], bytes, "HTML error page");
        }
    }
}
