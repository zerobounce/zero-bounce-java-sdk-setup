package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Warmup CSV downloads: a JSON body or a JSON media type is an error with its
 * status, whatever the content type says it is (text/csv included).
 */
class J1ParsingWarmupCsvTest {

    private ZeroBounceWarmup warmupServing(int status, String contentType, String body) throws Exception {
        ZeroBounceWarmup warmup = spy(new ZeroBounceWarmup());
        warmup.initialize("warmup-key");
        doReturn(new SdkHttp.Result(status, contentType, body.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .when(warmup).exchangeHttp(anyString(), anyString(), nullable(String.class),
                        nullable(byte[].class), nullable(java.util.Map.class));
        return warmup;
    }

    private static final class Outcome {
        final List<String> successes = new ArrayList<>();
        final List<ErrorResponse> errors = new ArrayList<>();
    }

    private Outcome download(ZeroBounceWarmup warmup, boolean seeds) {
        Outcome outcome = new Outcome();
        ZeroBounceSDK.OnErrorCallback collect = outcome.errors::add;
        ZeroBounceSDK.OnSuccessCallback<String> ok = outcome.successes::add;
        if (seeds) {
            warmup.downloadSeeds(ok, collect);
        } else {
            warmup.downloadStatistics("a@b.com", ok, collect);
        }
        return outcome;
    }

    @Test
    void aJsonBodyServedAsCsvIsAnErrorOnBothDownloads() throws Exception {
        String[] bodies = {
                "{\"success\":false,\"message\":\"File not found\"}",
                "{}",
                "[]",
                "{\"status\":\"error\"}",
                "{\"success\":true,\"data\":[]}",
        };
        List<String> failures = new ArrayList<>();
        for (String body : bodies) {
            for (boolean seeds : new boolean[]{true, false}) {
                Outcome outcome = download(warmupServing(200, "text/csv", body), seeds);
                if (!outcome.successes.isEmpty() || outcome.errors.size() != 1 || outcome.errors.get(0).getStatusCode() != 200) {
                    failures.add(body + " seeds=" + seeds + " " + outcome.successes + " " + outcome.errors.size());
                }
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void theFileNotFoundTextIsTheErrorMessage() throws Exception {
        Outcome outcome = download(warmupServing(200, "text/csv", "{\"success\":false,\"message\":\"File not found\"}"), true);
        assertTrue(outcome.successes.isEmpty());
        assertTrue(outcome.errors.get(0).getErrors().toString().contains("File not found"), outcome.errors.get(0).getErrors().toString());
    }

    @Test
    void aJsonMediaTypeIsAnErrorEvenWithACsvLookingBody() throws Exception {
        for (String type : new String[]{"application/problem+json", "application/json; charset=utf-8", "application/vnd.api+json"}) {
            Outcome outcome = download(warmupServing(200, type, "seed,provider\na@b.com,gmail\n"), true);
            assertTrue(outcome.successes.isEmpty(), type);
            assertEquals(1, outcome.errors.size(), type);
            assertEquals(200, outcome.errors.get(0).getStatusCode(), type);
        }
    }

    @Test
    void aRealCsvStillComesBackAsText() throws Exception {
        String csv = "seed,provider\na@b.com,gmail\n";
        Outcome outcome = download(warmupServing(200, "text/csv", csv), true);
        assertEquals(List.of(csv), outcome.successes);
        assertTrue(outcome.errors.isEmpty());
    }
}
