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

    /** A key with characters that URL encoding changes, so the encoded form differs. */
    private static final String KEY = "echo+key/1234567890";
    /** The SMTP password the Warmup sweep sends to testSmtpConfiguration. */
    private static final String PASSWORD = "smtp-pass";

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

    private static boolean leaks(String text, String secret) {
        if (text == null) {
            return false;
        }
        if (text.contains(secret) || text.contains(URLEncoder.encode(secret, StandardCharsets.UTF_8))) {
            return true;
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

    /** A 400 JSON error with several fields, each of which quotes the key or the request. */
    @Test
    void aJsonErrorQuotingTheRequestNeverCarriesTheKey() throws Exception {
        sweep("400-json", request -> LoopbackServer.answer(400, "application/json",
                ("{\"success\": false, \"message\": " + json("Invalid API key " + KEY)
                        + ", \"detail\": " + json(echo(request)) + "}").getBytes(StandardCharsets.UTF_8)), true);
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
                    + URLEncoder.encode(KEY, StandardCharsets.UTF_8) + " " + json(KEY) + extra.get());
        }).when(warmup).createUrlFrom(anyString());
        warmup.initialize(KEY);
        List<String> failures = new ArrayList<>();
        List<Method> methods = RequestSweep.requestMethods(ZeroBounceWarmup.class);
        for (Method method : methods) {
            urls.clear();
            extra.set(method.getName().equals("testSmtpConfiguration") ? " password=" + PASSWORD : "");
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
}
