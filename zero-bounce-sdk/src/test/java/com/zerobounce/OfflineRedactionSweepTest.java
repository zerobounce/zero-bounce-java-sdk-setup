package com.zerobounce;

import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * The API key, and the SMTP password Warmup sends, never reach an error callback,
 * even when the server quotes the request back in its error body: the request URL
 * (with {@code api_key=}), the form, JSON or multipart body, or the key itself.
 * Every public request method of {@link ZeroBounceSDK} and {@link ZeroBounceWarmup}
 * is swept against a real HTTP server on a loopback socket; the methods come from
 * {@link RequestSweep}, which fails when a public request method is missing.
 */
class OfflineRedactionSweepTest {

    @TempDir
    Path tempDir;

    /**
     * A key with characters that URL encoding and JSON escaping change ({@code +},
     * {@code /}, {@code "}, {@code \}, {@code %} and a space) and that Gson's
     * HTML-safe escaping changes ({@code = < > & '}), so each of its spellings
     * (see {@link #forms}) differs from every other.
     */
    private static final String KEY = "echo+key/12\"34%56 7=<>&'\\890";
    /** The SMTP password the Warmup sweep sends to testSmtpConfiguration. */
    private static final String PASSWORD = RequestSweep.SMTP_PASSWORD;

    /** What the server quotes back: the request line, the API-Key header and the body. */
    private static String echo(LoopbackServer.Request request) {
        String body = new String(request.body, StandardCharsets.UTF_8);
        String quoted = request.method + " " + request.target + " API-Key=" + request.headers.get("api-key") + " " + body;
        // Under the SDK's 1000-character cut, so the quote is not replaced by a generic message.
        return quoted.length() > 900 ? quoted.substring(0, 900) : quoted;
    }

    private static String json(String text) {
        return new com.google.gson.Gson().toJson(text);
    }

    /** {@link #json} as PHP's json_encode writes it: every {@code /} as a backslash and a slash. */
    private static String phpJson(String text) {
        return json(text).replace("/", "\\/");
    }

    /** The secret JSON-escaped, as Gson and as PHP write it, without the quotes. */
    private static List<String> jsonForms(String secret) {
        String gson = json(secret);
        String php = phpJson(secret);
        return List.of(gson.substring(1, gson.length() - 1), php.substring(1, php.length() - 1));
    }

    /** The secret JSON-escaped by {@code gson}, without the quotes. */
    private static String escaped(com.google.gson.Gson gson, String text) {
        String quoted = gson.toJson(text);
        return quoted.substring(1, quoted.length() - 1);
    }

    /**
     * The JSON escapes a server can apply: Gson's HTML-safe escaping
     * ({@code \u003c}), its plain escaping, and each with {@code /} as a
     * backslash and a slash (PHP's json_encode).
     */
    private static final List<Function<String, String>> JSON_ESCAPES = List.of(
            text -> escaped(new com.google.gson.Gson(), text),
            text -> escaped(new com.google.gson.Gson(), text).replace("/", "\\/"),
            text -> escaped(new com.google.gson.GsonBuilder().disableHtmlEscaping().create(), text),
            text -> escaped(new com.google.gson.GsonBuilder().disableHtmlEscaping().create(), text)
                    .replace("/", "\\/"));

    /** The URL encodings: none, a space as {@code +} (a form) and as {@code %20}. */
    private static final List<Function<String, String>> URL_ENCODINGS = List.of(
            text -> text,
            text -> URLEncoder.encode(text, StandardCharsets.UTF_8),
            text -> URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20"));

    /**
     * Every spelling a server can quote a secret back in, each of which the SDK
     * must redact, composed from the encoders up to two deep: {raw, each JSON
     * escape} x {each URL encoding}, and each JSON escape applied to each
     * JSON-escaped form (a JSON error quoted inside another JSON string).
     * Duplicates are dropped; {@link #everySpellingOfTheTestSecretsIsDistinct}
     * pins how many remain.
     */
    static List<String> forms(String secret) {
        List<String> once = new ArrayList<>(List.of(secret));
        for (Function<String, String> escape : JSON_ESCAPES) {
            once.add(escape.apply(secret));
        }
        java.util.Set<String> forms = new java.util.LinkedHashSet<>();
        for (String base : once) {
            for (Function<String, String> encoding : URL_ENCODINGS) {
                forms.add(encoding.apply(base));
            }
        }
        for (String base : once.subList(1, once.size())) {
            for (Function<String, String> escape : JSON_ESCAPES) {
                forms.add(escape.apply(base));
            }
        }
        return new ArrayList<>(forms);
    }

    @Test
    void everySpellingOfTheTestSecretsIsDistinct() {
        for (String secret : List.of(KEY, PASSWORD)) {
            List<String> forms = forms(secret);
            // 5 bases x 3 URL encodings, and 4 x 4 double escapes less the 4
            // where the plain escape of an HTML-safe one repeats its HTML-safe
            // escape (nothing HTML-sensitive is left to tell them apart).
            assertEquals(15 + 12, forms.size(), forms.toString());
        }
    }

    /** Each spelling alone is replaced, with nothing of it left around the ***. */
    @Test
    void eachSpellingOfASecretIsRedactedWhole() {
        for (String secret : List.of(KEY, PASSWORD)) {
            for (String form : forms(secret)) {
                assertEquals("[***]", ZeroBounceSDK.redact("[" + form + "]", secret), form);
            }
        }
    }

    private static boolean leaks(String text, String secret) {
        if (text == null) {
            return false;
        }
        for (String form : forms(secret)) {
            if (text.contains(form)) {
                return true;
            }
        }
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8).contains(secret);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void sweep(String label, Function<LoopbackServer.Request, LoopbackServer.Responder> responder,
                       boolean everyCallFails) throws Exception {
        Path dir = tempDir.resolve(label);
        Files.createDirectories(dir);
        RequestSweep sweep = new RequestSweep(dir);
        try (LoopbackServer server = new LoopbackServer((request, out) -> responder.apply(request).respond(request, out))) {
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
                        failures.add(label + " " + outcome);
                        continue;
                    }
                    if (server.requests().isEmpty()) {
                        failures.add(label + " " + outcome.signature + " sent no request: " + outcome);
                        continue;
                    }
                    if (everyCallFails && (!outcome.successes.isEmpty() || outcome.errors.size() != 1)) {
                        failures.add(label + " expected one error: " + outcome);
                    }
                    for (ErrorResponse error : outcome.errors) {
                        String text = String.valueOf(error.getErrors()) + " " + error;
                        if (leaks(text, KEY) || leaks(text, PASSWORD)) {
                            failures.add(label + " leaked a secret: " + outcome);
                        }
                        // The quote reached the message, redacted, rather than a generic text.
                        if (label.startsWith("400-forms") && !text.contains("*** | ***")) {
                            failures.add(label + " did not quote the body: " + outcome);
                        }
                    }
                    swept++;
                }
            }
            assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
            assertEquals(sweep.sdkArgs.size() + sweep.warmupArgs.size(), swept, "every sweep entry was invoked");
        }
    }

    @Test
    void theSweepCoversEveryPublicRequestMethod() {
        new RequestSweep(tempDir).assertComplete();
    }

    /** A 400 whose plain-text body quotes the whole request. */
    @Test
    void aPlainTextErrorQuotingTheRequestNeverCarriesTheKey() throws Exception {
        sweep("400-text", request -> LoopbackServer.answer(400, "text/plain",
                ("Bad request: " + echo(request)).getBytes(StandardCharsets.UTF_8)), true);
    }

    /**
     * A 400 whose plain-text body quotes the key in every spelling of
     * {@link #forms}, and on testSmtpConfiguration the SMTP password in every
     * spelling too: each is redacted on every path, so dropping any one
     * spelling from the SDK fails this test.
     */
    @Test
    void everySpellingOfASecretInAnErrorBodyIsRedacted() throws Exception {
        // The spellings go out a few at a time, so each body stays under the
        // SDK's 1000-character cut and is quoted, not replaced by a generic message.
        int chunk = 6;
        int count = forms(KEY).size();
        for (int from = 0; from < count; from += chunk) {
            int start = from;
            int end = Math.min(count, from + chunk);
            for (String secret : List.of(KEY, PASSWORD)) {
                String body = "Bad request: " + String.join(" | ", forms(secret).subList(start, end));
                assertTrue(body.length() < 1000, body);
            }
            sweep("400-forms-" + start, request -> {
                String secret = request.target.contains("test-smtp") ? PASSWORD : KEY;
                return LoopbackServer.answer(400, "text/plain", ("Bad request: "
                        + String.join(" | ", forms(secret).subList(start, end))).getBytes(StandardCharsets.UTF_8));
            }, true);
        }
    }

    /** A 400 JSON error with several fields, each of which quotes the key or the request. */
    @Test
    void aJsonErrorQuotingTheRequestNeverCarriesTheKey() throws Exception {
        sweep("400-json", request -> LoopbackServer.answer(400, "application/json",
                ("{\"success\": false, \"message\": " + json("Invalid API key " + KEY)
                        + ", \"detail\": " + json(echo(request)) + "}").getBytes(StandardCharsets.UTF_8)), true);
    }

    /**
     * A 400 JSON error written by a server that escapes {@code /} as a backslash
     * and a slash (PHP's json_encode), quoting the key and the request.
     */
    @Test
    void aJsonErrorWithEscapedSlashesNeverCarriesTheKey() throws Exception {
        sweep("400-php-json", request -> LoopbackServer.answer(400, "application/json",
                ("{\"error\": " + phpJson("Invalid API key " + KEY + " for " + echo(request))
                        + "}").getBytes(StandardCharsets.UTF_8)), true);
    }

    /**
     * An HTTP 200 that carries an API error naming the key: a dead-key answer on
     * the JSON calls, and a JSON error body instead of a file on the downloads
     * (sent as text/plain, which the API has done). Warmup reads a 200 as its
     * result, so only the errors that do come back are checked.
     */
    @Test
    void anErrorInASuccessBodyNeverCarriesTheKey() throws Exception {
        sweep("200-error", request -> LoopbackServer.answer(200, "text/plain",
                ("{\"success\": false, \"error\": " + json("Invalid API key " + KEY + " for " + request.target)
                        + "}").getBytes(StandardCharsets.UTF_8)), false);
    }

    /**
     * Sweeps every public Warmup request method against a server that answers
     * HTTP 200 with a body Warmup must refuse, and checks that each call reports
     * one error with status 200 that carries neither the key nor the SMTP
     * password in any form.
     */
    private void warmupSweep(String label, Function<LoopbackServer.Request, LoopbackServer.Responder> responder)
            throws Exception {
        Path dir = tempDir.resolve(label);
        Files.createDirectories(dir);
        RequestSweep sweep = new RequestSweep(dir);
        try (LoopbackServer server = new LoopbackServer((request, out) -> responder.apply(request).respond(request, out))) {
            ZeroBounceWarmup warmup = server.warmup();
            warmup.initialize(KEY);
            List<String> failures = new ArrayList<>();
            List<Method> methods = RequestSweep.requestMethods(ZeroBounceWarmup.class);
            for (Method method : methods) {
                server.clear();
                RequestSweep.Outcome outcome = sweep.invoke(warmup, method);
                if (outcome.thrown != null || server.requests().isEmpty() || !outcome.successes.isEmpty()
                        || outcome.errors.size() != 1 || outcome.errors.get(0).getStatusCode() != 200) {
                    failures.add(label + " expected one error with status 200: " + outcome);
                }
                for (ErrorResponse error : outcome.errors) {
                    String text = String.valueOf(error.getErrors()) + " " + error;
                    if (leaks(text, KEY) || leaks(text, PASSWORD)) {
                        failures.add(label + " leaked a secret: " + outcome);
                    }
                }
            }
            assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
            assertEquals(sweep.warmupArgs.size(), methods.size(), "every Warmup sweep entry was invoked");
        }
    }

    /**
     * Warmup builds an error from a 200 body in two places: a JSON call answered
     * with text that is not JSON, and a CSV download answered with JSON. Both
     * bodies here quote the key and the request (URL, API-Key header, body with
     * the SMTP password).
     */
    @Test
    void aWarmupErrorBuiltFromA200BodyNeverCarriesTheKey() throws Exception {
        warmupSweep("warmup-200", request -> request.target.contains("/download")
                ? LoopbackServer.answer(200, "application/json",
                        ("{\"error\": " + json("Invalid API key " + KEY + " for " + echo(request))
                                + ", \"detail\": " + phpJson(KEY + " " + PASSWORD) + "}")
                                .getBytes(StandardCharsets.UTF_8))
                : LoopbackServer.answer(200, "text/plain",
                        ("Invalid API key " + KEY + " for " + echo(request)).getBytes(StandardCharsets.UTF_8)));
    }

    /** An HTML page on a 200 whose title quotes the key, from every Warmup call. */
    @Test
    void aWarmupHtmlPageOnA200NeverCarriesTheKey() throws Exception {
        warmupSweep("warmup-html", request -> LoopbackServer.answer(200, "text/html",
                ("<!DOCTYPE html><html><head><title>Denied " + KEY + " "
                        + URLEncoder.encode(KEY, StandardCharsets.UTF_8) + "</title></head><body>"
                        + echo(request) + "</body></html>").getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * A transport error whose message quotes the request URL and the key (as sent,
     * URL-encoded and JSON-escaped), and for {@code testSmtpConfiguration} the SMTP
     * password, never carries them: every public Warmup request method reports one
     * error with status 0 and neither secret in any form. The methods come from
     * {@link RequestSweep}, so a new one cannot escape.
     */
    @Test
    void aWarmupTransportErrorNeverCarriesTheKey() throws Exception {
        RequestSweep sweep = new RequestSweep(tempDir);
        ZeroBounceWarmup warmup = spy(new ZeroBounceWarmup());
        AtomicReference<String> extra = new AtomicReference<>("");
        List<String> urls = new ArrayList<>();
        doAnswer(invocation -> {
            String url = invocation.getArgument(0);
            urls.add(url);
            throw new java.net.ConnectException("Connection refused: " + url + " API-Key=" + KEY + " "
                    + URLEncoder.encode(KEY, StandardCharsets.UTF_8) + " " + json(KEY) + " " + phpJson(KEY)
                    + extra.get());
        }).when(warmup).createUrlFrom(anyString());
        warmup.initialize(KEY);
        List<String> failures = new ArrayList<>();
        List<Method> methods = RequestSweep.requestMethods(ZeroBounceWarmup.class);
        for (Method method : methods) {
            urls.clear();
            extra.set(method.getName().equals("testSmtpConfiguration")
                    ? " password=" + PASSWORD + " " + json(PASSWORD) + " " + phpJson(PASSWORD) : "");
            RequestSweep.Outcome outcome = sweep.invoke(warmup, method);
            if (urls.isEmpty() || outcome.thrown != null || !outcome.successes.isEmpty()
                    || outcome.errors.size() != 1 || outcome.errors.get(0).getStatusCode() != 0) {
                failures.add("expected one transport error: " + outcome);
            }
            for (ErrorResponse error : outcome.errors) {
                String text = String.valueOf(error.getErrors()) + " " + error;
                if (leaks(text, KEY) || leaks(text, PASSWORD)) {
                    failures.add("leaked a secret: " + outcome);
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
        assertEquals(sweep.warmupArgs.size(), methods.size(), "every Warmup sweep entry was invoked");
    }

    /**
     * What the SDK writes to its logger never carries the key, in any form, with
     * payload logging off or on: response bodies quoting the key and the request
     * (a 400 JSON error, a 200 text error) on every public request method, and
     * each logged exception with its cause chain. With payload logging off no
     * response body is logged at all, the bulk uploads included (S8).
     */
    @Test
    void theLoggerNeverSeesTheKey() throws Exception {
        List<String> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        ZeroBounceSDK.setLogger(new ZBLogger() {
            @Override
            public void debug(String msg) {
                logged.add("debug " + msg);
            }

            @Override
            public void info(String msg) {
                logged.add("info " + msg);
            }

            @Override
            public void warn(String msg) {
                logged.add("warn " + msg);
            }

            @Override
            public void error(String msg, Throwable t) {
                java.io.StringWriter trace = new java.io.StringWriter();
                if (t != null) {
                    t.printStackTrace(new java.io.PrintWriter(trace));
                }
                logged.add("error " + msg + " " + trace);
            }
        });
        try {
            for (boolean payloads : new boolean[]{false, true}) {
                ZeroBounceSDK.setLogPayloads(payloads);
                logged.clear();
                sweep("log-400-" + payloads, request -> LoopbackServer.answer(400, "application/json",
                        ("{\"error\": " + json("BODY-MARKER Invalid API key " + KEY + " " + echo(request))
                                + ", \"detail\": " + phpJson(KEY) + "}").getBytes(StandardCharsets.UTF_8)), true);
                sweep("log-200-" + payloads, request -> LoopbackServer.answer(200, "text/plain",
                        ("BODY-MARKER Invalid API key " + KEY + " " + echo(request)).getBytes(StandardCharsets.UTF_8)),
                        false);
                assertTrue(!logged.isEmpty(), "the SDK logged nothing");
                for (String line : logged) {
                    assertTrue(!leaks(line, KEY), "payloads=" + payloads + " logged the key: " + line);
                    assertTrue(payloads || !line.contains("BODY-MARKER"),
                            "a response body was logged with payload logging off: " + line);
                }
                if (payloads) {
                    assertTrue(logged.stream().anyMatch(line -> line.contains("BODY-MARKER")),
                            "payload logging on logs the bodies");
                }
            }
            // A transport exception, and its cause, quoting the key in every
            // spelling: the logged exception carries neither.
            logged.clear();
            ZeroBounceSDK sdk = spy(new ZeroBounceSDK());
            String quoted = KEY + " " + URLEncoder.encode(KEY, StandardCharsets.UTF_8) + " " + json(KEY) + " " + phpJson(KEY);
            doAnswer(invocation -> {
                throw new java.net.ConnectException("Connection refused: " + invocation.getArgument(0) + " " + quoted) {
                    {
                        initCause(new java.io.IOException("cause " + quoted));
                    }
                };
            }).when(sdk).createUrlFrom(anyString());
            sdk.initialize(KEY);
            List<ErrorResponse> errors = new ArrayList<>();
            sdk.getCredits(r -> fail("success"), errors::add);
            assertEquals(1, errors.size());
            assertTrue(logged.stream().anyMatch(line -> line.startsWith("error ") && line.contains("Caused by")),
                    logged.toString());
            for (String line : logged) {
                assertTrue(!leaks(line, KEY), "logged the key: " + line);
            }
        } finally {
            ZeroBounceSDK.setLogger(null);
            ZeroBounceSDK.setLogPayloads(false);
        }
    }
}
