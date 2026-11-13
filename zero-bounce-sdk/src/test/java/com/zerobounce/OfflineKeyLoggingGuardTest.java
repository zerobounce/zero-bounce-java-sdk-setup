package com.zerobounce;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard in {@link SdkHttp}: no request is sent while one of
 * {@link SdkHttp#KEY_LOGGERS} would write at DEBUG or TRACE, and every public request
 * method is covered, by {@link RequestSweep}, against a real HTTP server on a loopback
 * socket ({@link LoopbackServer}).
 * <p>
 * The tests need an SLF4J binding whose levels can change at run time: slf4j-jdk14
 * (java.util.logging), a test-scope dependency only. Levels are set on JUL loggers, and
 * the records the SDK writes are captured by a JUL handler on the root logger.
 */
class OfflineKeyLoggingGuardTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "guard-key-0123456789";
    private static final String WARMUP_KEY = "guard-warmup-key-0123";
    /** The SMTP password's spellings in a request body: its raw form, and URL-encoded. */
    private static final List<String> PASSWORD_FRAGMENTS = List.of("pa/ss", "pa%2Fss");
    private static final byte[] OK = "{\"Credits\": \"5\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    /**
     * The loggers whose level a test has set, held here so the JVM cannot collect them:
     * java.util.logging keeps only weak references, and a level set on a collected logger is lost.
     */
    private static final Map<String, Logger> LOGGERS = new HashMap<>();
    private static final Logger ROOT = Logger.getLogger("");

    private static void level(String name, Level level) {
        LOGGERS.computeIfAbsent(name, Logger::getLogger).setLevel(level);
    }

    /** The loggers the guard checks: the key loggers and the loggers that echo server text. */
    private static List<String> keyAndServerTextLoggers() {
        List<String> names = new ArrayList<>(SdkHttp.KEY_LOGGERS);
        names.addAll(SdkHttp.SERVER_TEXT_LOGGERS);
        return names;
    }

    /** Everything under org.apache.hc at INFO, the key loggers at INFO too. */
    private static void allAtInfo() {
        level("org.apache.hc", Level.INFO);
        for (String name : keyAndServerTextLoggers()) {
            level(name, Level.INFO);
        }
    }

    /** Everything under org.apache.hc at DEBUG, except the key loggers, which stay at INFO. */
    private static void debugExceptKeyLoggers() {
        level("org.apache.hc", Level.FINE);
        for (String name : keyAndServerTextLoggers()) {
            level(name, Level.INFO);
        }
    }

    @AfterEach
    void restoreLevels() {
        level("org.apache.hc", null);
        for (String name : keyAndServerTextLoggers()) {
            level(name, null);
        }
    }

    /** Collects every record that reaches the root logger, from any logger. */
    private static final class Capture extends Handler {
        final List<LogRecord> list = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(LogRecord record) {
            list.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static Capture capture() {
        Capture capture = new Capture();
        ROOT.addHandler(capture);
        return capture;
    }

    private static void release(Capture capture) {
        ROOT.removeHandler(capture);
    }

    /**
     * A record's text: its message with the arguments filled in (slf4j-jdk14 formats it
     * before it is logged), and its throwable's message.
     */
    private static String text(LogRecord record) {
        String text = record.getMessage();
        if (record.getThrown() != null && record.getThrown().getMessage() != null) {
            text += " " + record.getThrown().getMessage();
        }
        return text;
    }

    private static boolean carriesAKey(String text) {
        return text.contains(KEY) || text.contains(WARMUP_KEY)
                || PASSWORD_FRAGMENTS.stream().anyMatch(text::contains);
    }

    /**
     * Calls every public request method of both clients against {@code server}, with
     * the keys set. Returns what went wrong, one line per call.
     */
    private static List<String> sweep(RequestSweep sweep, LoopbackServer server, Check check) throws Exception {
        ZeroBounceSDK sdk = server.sdk();
        sdk.initialize(KEY);
        ZeroBounceWarmup warmup = server.warmup();
        warmup.initialize(WARMUP_KEY);
        List<String> failures = new ArrayList<>();
        try (MockedStatic<HttpClients> ignored = server.apacheClients()) {
            for (Method method : RequestSweep.allRequestMethods()) {
                server.clear();
                Object client = method.getDeclaringClass() == ZeroBounceWarmup.class ? warmup : sdk;
                RequestSweep.Outcome outcome = sweep.invoke(client, method);
                check.check(outcome, server.requests(), failures);
            }
        }
        return failures;
    }

    private interface Check {
        void check(RequestSweep.Outcome outcome, List<LoopbackServer.Request> requests, List<String> failures);
    }

    /**
     * (a) While one key logger is at DEBUG or TRACE, every public request method refuses
     * the call: no request reaches the server, the error callback gets status 0 with the
     * fixed message and no key, and no download file is written.
     */
    @Test
    void everyRequestMethodIsRefusedWhileAKeyLoggerIsAtDebugOrTrace() throws Exception {
        RequestSweep sweep = new RequestSweep(tempDir);
        List<String> failures = new ArrayList<>();
        for (String logger : SdkHttp.KEY_LOGGERS) {
            for (Level level : List.of(Level.FINE, Level.FINEST)) {
                allAtInfo();
                level(logger, level);
                try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", OK))) {
                    List<String> found = sweep(sweep, server, (outcome, requests, problems) -> {
                        if (outcome.thrown != null) {
                            problems.add(outcome.signature + " threw " + outcome.thrown);
                        }
                        if (!requests.isEmpty()) {
                            problems.add(outcome.signature + " sent a request while " + logger + " was " + level + ": " + requests);
                        }
                        if (!outcome.successes.isEmpty() || outcome.errors.size() != 1) {
                            problems.add(outcome.signature + " did not report exactly one error: " + outcome);
                            return;
                        }
                        ErrorResponse error = outcome.errors.get(0);
                        if (error.getStatusCode() != 0 || !error.getErrors().equals(List.of(SdkHttp.KEY_LOGGING_REFUSED))) {
                            problems.add(outcome.signature + " did not report the refusal: " + outcome);
                        }
                        if (error.getErrors().toString().contains(KEY) || error.getErrors().toString().contains(WARMUP_KEY)) {
                            problems.add(outcome.signature + " put a key in its error");
                        }
                        if (outcome.out != null && new File(outcome.out).exists()) {
                            problems.add(outcome.signature + " wrote " + outcome.out);
                        }
                    });
                    for (String problem : found) {
                        failures.add(logger + " at " + level + ": " + problem);
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * (b) With every key logger at INFO, the calls go out as before: one request per
     * method reaches the server, nothing is refused, and the key is in the request.
     */
    @Test
    void everyRequestMethodSendsWhileEveryKeyLoggerIsAtInfo() throws Exception {
        allAtInfo();
        RequestSweep sweep = new RequestSweep(tempDir);
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", OK))) {
            List<String> failures = sweep(sweep, server, (outcome, requests, problems) -> {
                if (outcome.thrown != null) {
                    problems.add(outcome.signature + " threw " + outcome.thrown);
                }
                if (requests.isEmpty()) {
                    problems.add(outcome.signature + " sent no request: " + outcome);
                }
                if (outcome.errors.stream().anyMatch(e -> e.getErrors().contains(SdkHttp.KEY_LOGGING_REFUSED))) {
                    problems.add(outcome.signature + " was refused at INFO");
                }
            });
            assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
        }
    }

    /** (b) once more, on one call with a known answer: the key goes out and the answer is read. */
    @Test
    void getCreditsWorksWhileEveryKeyLoggerIsAtInfo() throws Exception {
        allAtInfo();
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", OK))) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            List<ZBCreditsResponse> successes = new ArrayList<>();
            List<ErrorResponse> errors = new ArrayList<>();
            try (MockedStatic<HttpClients> ignored = server.apacheClients()) {
                sdk.getCredits(successes::add, errors::add);
            }
            assertTrue(errors.isEmpty(), "getCredits failed: " + errors.stream().map(ErrorResponse::getErrors).collect(Collectors.toList()));
            assertEquals(1, successes.size());
            assertEquals("5", successes.get(0).getCredits());
            assertEquals(1, server.requests().size());
            assertTrue(server.requests().get(0).target.contains("api_key=" + KEY), server.requests().get(0).target);
        }
    }

    /**
     * Completeness: with org.apache.hc at DEBUG and the key loggers at INFO, every
     * request method runs and no other HttpClient logger writes a key or the SMTP password.
     * A new HttpClient logger that does would fail here, and belongs in
     * {@link SdkHttp#KEY_LOGGERS}.
     */
    @Test
    void noOtherHttpClientLoggerWritesAKeyAtDebug() throws Exception {
        debugExceptKeyLoggers();
        RequestSweep sweep = new RequestSweep(tempDir);
        Capture log = capture();
        List<String> failures = new ArrayList<>();
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", OK))) {
            failures.addAll(sweep(sweep, server, (outcome, requests, problems) -> {
                if (requests.isEmpty()) {
                    problems.add(outcome.signature + " sent no request");
                }
            }));
        } finally {
            release(log);
        }
        List<LogRecord> debug = log.list.stream()
                .filter(e -> e.getLoggerName().startsWith("org.apache.hc") && e.getLevel().intValue() >= Level.FINE.intValue())
                .collect(Collectors.toList());
        // Not vacuous: HttpClient did write at DEBUG, from loggers other than the key loggers.
        assertTrue(debug.stream().anyMatch(e -> !SdkHttp.KEY_LOGGERS.contains(e.getLoggerName())),
                "no HttpClient DEBUG output was captured");
        for (LogRecord event : debug) {
            if (!SdkHttp.KEY_LOGGERS.contains(event.getLoggerName()) && carriesAKey(text(event))) {
                failures.add(event.getLoggerName() + " wrote a key or password at " + event.getLevel() + ": " + text(event));
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * Each listed logger, alone at DEBUG, writes the key on a request: the list holds
     * no logger that does nothing, and the guard is needed for each one. The request
     * goes straight through {@link SdkHttp#newClient}, past the guard, to show what
     * the logger would have written.
     */
    @Test
    void eachListedLoggerWritesTheKeyAtDebug() throws Exception {
        for (String logger : SdkHttp.KEY_LOGGERS) {
            allAtInfo();
            level(logger, Level.FINE);
            Capture log = capture();
            try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "text/plain", new byte[0]))) {
                HttpGet get = new HttpGet("http://127.0.0.1:" + server.port() + "/v1/getcredits?api_key=" + KEY);
                get.setHeader("API-Key", KEY);
                try (CloseableHttpClient client = SdkHttp.newClient(5_000, false)) {
                    client.execute(get, response -> response.getCode());
                }
            } finally {
                release(log);
            }
            List<String> written = log.list.stream()
                    .filter(e -> e.getLoggerName().equals(logger) && text(e).contains(KEY))
                    .map(LogRecord::getMessage)
                    .collect(Collectors.toList());
            assertTrue(!written.isEmpty(), logger + " wrote no key at DEBUG");
        }
    }
}
