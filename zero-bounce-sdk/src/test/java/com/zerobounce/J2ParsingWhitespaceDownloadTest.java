package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A whitespace-only 200 on a results download is an empty response: an error,
 * and no file is written. Every getfile path is checked, as each one reaches
 * {@link HttpMessages#downloadFailure}.
 */
class J2ParsingWhitespaceDownloadTest {

    @TempDir
    Path tempDir;

    private static final String[] BLANK_BODIES = {" ", "\n", " \t\r\n ", "\t"};

    private ZeroBounceSDK sdk(AtomicInteger requests, String body) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> {
            requests.incrementAndGet();
            return new SdkHttp.Result(200, "text/csv", body.getBytes(StandardCharsets.UTF_8));
        };
        sdk.initialize("download-key");
        return sdk;
    }

    /** Runs one download path and reports what it did. */
    private List<String> runPath(String method, String body, Path target) {
        List<String> problems = new ArrayList<>();
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        AtomicReference<ZBGetFileResponse> success = new AtomicReference<>();
        ZeroBounceSDK sdk = sdk(requests, body);
        ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse> ok = success::set;
        ZeroBounceSDK.OnErrorCallback fail = error::set;
        switch (method) {
            case "getFile" -> sdk.getFile("fid", target.toString(), ok, fail);
            case "scoringGetFile" -> sdk.scoringGetFile("fid", target.toString(), ok, fail);
            case "emailFinderGetFile" -> sdk.emailFinderGetFile("fid", target.toString(), ok, fail);
            case "domainSearchGetFile" -> sdk.domainSearchGetFile("fid", target.toString(), ok, fail);
            default -> throw new IllegalArgumentException(method);
        }
        if (requests.get() != 1) {
            problems.add(method + " sent " + requests.get() + " request(s)");
        }
        if (success.get() != null) {
            problems.add(method + " reported success for a blank body");
        }
        if (error.get() == null) {
            problems.add(method + " reported no error for a blank body");
        } else if (error.get().getStatusCode() != 200) {
            problems.add(method + " error status " + error.get().getStatusCode());
        }
        if (Files.exists(target)) {
            problems.add(method + " wrote a file for a blank body");
        }
        return problems;
    }

    @Test
    void aWhitespaceOnlyTwoHundredIsAnErrorAndSavesNothingOnEveryGetFilePath() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String method : new String[]{"getFile", "scoringGetFile", "emailFinderGetFile", "domainSearchGetFile"}) {
            for (String body : BLANK_BODIES) {
                Path target = tempDir.resolve(method + "-" + body.length() + "-" + body.hashCode() + ".csv");
                for (String problem : runPath(method, body, target)) {
                    problems.add(problem + " for body " + body.replace("\n", "\\n").replace("\r", "\\r")
                            .replace("\t", "\\t"));
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void aWhitespaceOnlyTwoHundredLeavesNoDirectoryOrTempFileBehind() throws Exception {
        Path target = tempDir.resolve("nested").resolve("out.csv");
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        sdk(new AtomicInteger(), "  \n ").getFile("fid", target.toString(),
                r -> { throw new AssertionError("success"); }, error::set);
        assertNotNull(error.get());
        assertFalse(Files.exists(tempDir.resolve("nested")), "a blank download created directories");
        assertFalse(Files.exists(target));
    }
}
