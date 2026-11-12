package com.zerobounce;

import com.google.gson.JsonParser;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
