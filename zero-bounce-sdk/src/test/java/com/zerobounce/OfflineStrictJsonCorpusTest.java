package com.zerobounce;

import com.google.gson.JsonParser;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Strict JSON (check E7) on every JSON call of {@link ZeroBounceSDK} and
 * {@link ZeroBounceWarmup}: each REJECT body of the shared corpus is an API error
 * with status 200, and each ACCEPT body is read as JSON (it may still not be the
 * shape a call expects, which is then an error with status 200, but never "not
 * valid JSON").
 *
 * <p>The corpus, {@code src/test/resources/strict-json-corpus.tsv}, is a
 * byte-identical copy of
 * {@code sdk-docs/skills/zerobounce-sdk/references/strict-json-corpus.tsv} in
 * zerobounce/sdk-docs; copy it again when that file changes. Each line's second
 * field is a JSON-encoded string whose value is the exact response body.
 */
class OfflineStrictJsonCorpusTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "corpus-key";
    private static final byte[] CSV = "email,status\na@example.com,valid\n".getBytes(StandardCharsets.UTF_8);

    private record Entry(boolean accept, String body) {
        @Override
        public String toString() {
            return (accept ? "ACCEPT " : "REJECT ") + new com.google.gson.Gson().toJson(body);
        }
    }

    static List<String[]> corpusLines() throws Exception {
        List<String[]> lines = new ArrayList<>();
        try (InputStream in = OfflineStrictJsonCorpusTest.class.getResourceAsStream("/strict-json-corpus.tsv")) {
            assertNotNull(in, "strict-json-corpus.tsv is on the test classpath");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                lines.add(line.split("\t", 2));
            }
        }
        return lines;
    }

    private static List<Entry> corpus() throws Exception {
        List<Entry> entries = new ArrayList<>();
        for (String[] fields : corpusLines()) {
            assertEquals(2, fields.length, String.join("\\t", fields));
            assertTrue(fields[0].equals("ACCEPT") || fields[0].equals("REJECT"), fields[0]);
            entries.add(new Entry(fields[0].equals("ACCEPT"), JsonParser.parseString(fields[1]).getAsString()));
        }
        assertTrue(entries.size() >= 50, "the corpus has " + entries.size() + " entries");
        return entries;
    }

    private static boolean isDownload(Method method) {
        String name = method.getName().toLowerCase(Locale.ROOT);
        return name.contains("getfile") || name.startsWith("download");
    }

    /**
     * Where an empty 2xx (zero bytes; a blank body is not empty) is a documented
     * success, as the corpus header says: the Inbox Placement DELETE (check E8) and
     * every Warmup call, whose empty 200 is a documented null result.
     */
    private static boolean emptyIsSuccess(Method method) {
        return method.getDeclaringClass() == ZeroBounceWarmup.class
                || method.getName().equals("deleteInboxPlacementTest");
    }

    @Test
    void theStrictCheckAloneAgreesWithTheCorpus() throws Exception {
        List<String> failures = new ArrayList<>();
        for (Entry entry : corpus()) {
            if (HttpMessages.isStrictJson(entry.body) != entry.accept) {
                failures.add(entry.toString());
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    @Test
    void everyJsonCallRejectsEveryRejectBodyAndReadsEveryAcceptBody() throws Exception {
        List<String> failures = new ArrayList<>();
        int index = 0;
        for (Entry entry : corpus()) {
            sweep(index++, entry, failures);
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /** A failure line without the whole (possibly huge) body in it. */
    private static List<String> shortened(List<String> failures) {
        List<String> out = new ArrayList<>();
        for (String failure : failures) {
            out.add(failure.length() <= 600 ? failure
                    : failure.substring(0, 300) + " ... " + failure.substring(failure.length() - 300));
        }
        return out;
    }

    /**
     * The strict check's depth limit: a 2xx body nested 200000 deep is "not valid
     * JSON", an API error with status 200 on every JSON call, and never a
     * StackOverflowError thrown out of the call (or out of the strict check),
     * which is what a recursive reader without the limit does.
     */
    @Test
    void aBodyNestedPastTheDepthLimitIsAnErrorWithStatus200OnEveryJsonCall() throws Exception {
        String deep = "[".repeat(200_000) + "]".repeat(200_000);
        assertFalse(HttpMessages.isStrictJson(deep), "200000 nested arrays are past the limit");
        assertFalse(HttpMessages.isStrictJson("{\"a\":" + deep + "}"), "nested under an object");
        assertTrue(HttpMessages.isStrictJson("[".repeat(500) + "]".repeat(500)), "500 nested arrays are within it");
        List<String> failures = new ArrayList<>();
        sweep(10_000, new Entry(false, deep), failures);
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", shortened(failures)));
    }

    /** The type a request method hands its success callback. */
    private static Type successType(Method method) {
        for (Type type : method.getGenericParameterTypes()) {
            if (type instanceof ParameterizedType
                    && ((ParameterizedType) type).getRawType() == ZeroBounceSDK.OnSuccessCallback.class) {
                return ((ParameterizedType) type).getActualTypeArguments()[0];
            }
        }
        throw new AssertionError(method + " has no success callback");
    }

    /**
     * Wrong shape (check E7): a 2xx body that is strict JSON but not the shape a
     * typed call returns is an API error with status 200 on every typed call,
     * never a default-filled result, an empty list, a null result or an error
     * without a status. Typed calls are found by reflection: every request
     * method of {@link ZeroBounceSDK} that is not a download and does not hand
     * back raw JSON ({@link com.google.gson.JsonElement}). Warmup calls hand back
     * any JSON value (a bare number or array wrapped under "data"), so no JSON
     * value is the wrong shape for them.
     */
    @Test
    void aWrongShapeIsAnErrorWithStatus200OnEveryTypedCall() throws Exception {
        List<String> failures = new ArrayList<>();
        int typed = 0;
        int index = 20_000;
        for (String body : List.of("0", "\"x\"", "true", "null", "[]", "[{}]", "{\"files\":3}")) {
            Path dir = tempDir.resolve("shape-" + index++);
            Files.createDirectories(dir);
            RequestSweep sweep = new RequestSweep(dir);
            LoopbackServer.Responder js = LoopbackServer.answer(200, "application/json",
                    body.getBytes(StandardCharsets.UTF_8));
            try (LoopbackServer server = new LoopbackServer(js)) {
                ZeroBounceSDK sdk = server.sdk();
                sdk.initialize(KEY);
                try (MockedStatic<HttpClients> ignored = server.apacheClients()) {
                    for (Method method : RequestSweep.allRequestMethods()) {
                        if (method.getDeclaringClass() != ZeroBounceSDK.class || isDownload(method)) {
                            continue;
                        }
                        Type type = successType(method);
                        if (type == com.google.gson.JsonElement.class) {
                            continue;
                        }
                        boolean list = type instanceof ParameterizedType
                                && ((ParameterizedType) type).getRawType() == List.class;
                        if (list && body.startsWith("[")) {
                            continue;
                        }
                        if (!list && body.startsWith("{")) {
                            continue;
                        }
                        typed++;
                        server.clear();
                        RequestSweep.Outcome outcome = sweep.invoke(sdk, method);
                        if (outcome.thrown != null || server.requests().isEmpty() || !outcome.successes.isEmpty()
                                || outcome.errors.size() != 1 || outcome.errors.get(0).getStatusCode() != 200) {
                            failures.add(body + " " + outcome);
                        }
                    }
                }
            }
        }
        assertTrue(typed > 200, "typed calls swept: " + typed);
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    private void sweep(int index, Entry entry, List<String> failures) throws Exception {
        Path dir = tempDir.resolve("corpus-" + index);
        Files.createDirectories(dir);
        RequestSweep sweep = new RequestSweep(dir);
        LoopbackServer.Responder dl = LoopbackServer.answer(200, "text/csv", CSV);
        LoopbackServer.Responder js = LoopbackServer.answer(200, "application/json",
                entry.body.getBytes(StandardCharsets.UTF_8));
        try (LoopbackServer server = new LoopbackServer((request, out) -> {
            String target = request.target.toLowerCase(Locale.ROOT);
            (target.contains("getfile") || target.contains("download") ? dl : js).respond(request, out);
        })) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            ZeroBounceWarmup warmup = server.warmup();
            warmup.initialize(KEY);
            int swept = 0;
            try (MockedStatic<HttpClients> ignored = server.apacheClients()) {
                for (Method method : RequestSweep.allRequestMethods()) {
                    server.clear();
                    Object client = method.getDeclaringClass() == ZeroBounceWarmup.class ? warmup : sdk;
                    RequestSweep.Outcome outcome = sweep.invoke(client, method);
                    swept++;
                    if (outcome.thrown != null || server.requests().isEmpty()) {
                        failures.add(entry + " " + outcome);
                        continue;
                    }
                    if (isDownload(method)) {
                        continue;
                    }
                    if (!entry.accept && entry.body.isEmpty() && emptyIsSuccess(method)) {
                        continue;
                    }
                    if (entry.accept) {
                        for (ErrorResponse error : outcome.errors) {
                            String text = String.valueOf(error.getErrors());
                            if (error.getStatusCode() != 200 || text.contains("not valid JSON")
                                    || text.contains("Empty response")) {
                                failures.add(entry + " " + outcome);
                            }
                        }
                        if (outcome.errors.size() + outcome.successes.size() != 1) {
                            failures.add(entry + " expected one outcome: " + outcome);
                        }
                    } else if (!outcome.successes.isEmpty() || outcome.errors.size() != 1
                            || outcome.errors.get(0).getStatusCode() != 200) {
                        failures.add(entry + " " + outcome);
                    }
                }
            }
            assertEquals(sweep.sdkArgs.size() + sweep.warmupArgs.size(), swept, "every sweep entry was invoked");
        }
    }
}
