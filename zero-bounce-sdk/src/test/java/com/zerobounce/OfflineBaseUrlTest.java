package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The exact URL every base URL produces. The {@link ZBConstants} values end in
 * a slash and the endpoint paths start with one, so a base URL taken as it was
 * sent {@code https://api-eu.zerobounce.net/v2//validate}.
 */
class OfflineBaseUrlTest {

    @TempDir
    Path tempDir;

    private static final ZeroBounceSDK.OnErrorCallback IGNORE_ERROR = error -> {
    };

    private static String withoutQuery(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }

    /** Calls the main-API endpoints once each and returns the URLs they were sent to. */
    private static List<String> mainApiUrls(String baseUrl) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        FakeApi api = new FakeApi();
        sdk.transport = api;
        sdk.initialize("url-key", baseUrl);

        sdk.validate("person@example.com", null, response -> {
        }, IGNORE_ERROR);
        sdk.validateBatch(List.of(new ZBValidateBatchData("ok@example.com", null)), response -> {
        }, IGNORE_ERROR);
        sdk.findEmail("John", "example.com", null, null, "Doe", response -> {
        }, IGNORE_ERROR);
        sdk.getCredits(response -> {
        }, IGNORE_ERROR);
        sdk.scoreEmail("person@example.com", response -> {
        }, IGNORE_ERROR);
        sdk.listFilters(false, response -> {
        }, IGNORE_ERROR);

        List<String> urls = new ArrayList<>();
        for (FakeApi.RecordedRequest request : api.records()) {
            urls.add(withoutQuery(request.url));
        }
        return urls;
    }

    private static List<String> expectedMainApiUrls(String root) {
        return List.of(
                root + "/v2/validate",
                root + "/v2/validatebatch",
                root + "/v2/guessformat",
                root + "/v2/getcredits",
                root + "/v2/scoring",
                root + "/v2/filters/list"
        );
    }

    @Test
    void defaultBaseUrlSendsSingleSlashPaths() {
        assertEquals(expectedMainApiUrls("https://api.zerobounce.net"), mainApiUrls(null));
    }

    @Test
    void zbConstantsSendSingleSlashPaths() {
        assertEquals(expectedMainApiUrls("https://api.zerobounce.net"), mainApiUrls(ZBConstants.API_DEFAULT_URL));
        assertEquals(expectedMainApiUrls("https://api-us.zerobounce.net"), mainApiUrls(ZBConstants.API_USA_URL));
        assertEquals(expectedMainApiUrls("https://api-eu.zerobounce.net"), mainApiUrls(ZBConstants.API_EU_URL));
    }

    @Test
    void customBaseUrlWithOrWithoutTrailingSlashSendsTheSameUrls() {
        for (String baseUrl : List.of(
                "https://api.example.test/v2",
                "https://api.example.test/v2/",
                "https://api.example.test/v2//")) {
            assertEquals(expectedMainApiUrls("https://api.example.test"), mainApiUrls(baseUrl), baseUrl);
        }
    }

    @Test
    void bulkSendFileUrlIsExact() throws Exception {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        FakeApi api = new FakeApi();
        sdk.transport = api;
        sdk.initialize("url-key", ZBConstants.API_EU_URL);

        File csv = tempDir.resolve("emails.csv").toFile();
        Files.writeString(csv.toPath(), "email\na@example.com\n");
        sdk.sendFile(csv, 1, null, response -> {
        }, IGNORE_ERROR);

        assertEquals(1, api.records().size(), api.records().toString());
        assertEquals("https://bulkapi.zerobounce.net/v2/sendfile", withoutQuery(api.records().get(0).url));
    }

    @Test
    void listEvaluatorAndInboxPlacementOverridesWithOrWithoutTrailingSlash() throws Exception {
        for (String suffix : List.of("", "/")) {
            assertOverrideUrls(suffix);
        }
    }

    private void assertOverrideUrls(String suffix) throws Exception {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        FakeApi api = new FakeApi();
        sdk.transport = api;
        sdk.initialize(
                "url-key",
                null,
                "https://bulk.example.test/v2/listevaluator" + suffix,
                "https://bulk.example.test/api/ipt" + suffix
        );

        File csv = tempDir.resolve("list.csv").toFile();
        Files.writeString(csv.toPath(), "email\na@example.com\n");
        sdk.sendListEvaluatorFile(csv, 1, response -> {
        }, IGNORE_ERROR);
        sdk.getListEvaluatorResult("fid", response -> {
        }, IGNORE_ERROR);
        sdk.getInboxPlacementTestTypes(response -> {
        }, IGNORE_ERROR);

        List<String> urls = new ArrayList<>();
        for (FakeApi.RecordedRequest request : api.records()) {
            urls.add(withoutQuery(request.url));
        }
        assertEquals(List.of(
                "https://bulk.example.test/v2/listevaluator/",
                "https://bulk.example.test/v2/listevaluator/fid/",
                "https://bulk.example.test/api/ipt/test-types"
        ), urls, "suffix '" + suffix + "'");
    }

    /** Where getCredits, the list evaluator and the Inbox Placement Tester go, and with which key. */
    private static List<String> sent(ZeroBounceSDK sdk, FakeApi api) {
        api.clear();
        sdk.getCredits(response -> {
        }, IGNORE_ERROR);
        sdk.getListEvaluatorResult("fid", response -> {
        }, IGNORE_ERROR);
        sdk.getInboxPlacementTestTypes(response -> {
        }, IGNORE_ERROR);
        List<String> sent = new ArrayList<>();
        for (FakeApi.RecordedRequest request : api.records()) {
            // The Inbox Placement Tester sends its key in a header the fake does not record.
            sent.add(withoutQuery(request.url) + (request.url.contains("/ipt/") ? "" : " key=" + request.apiKey));
        }
        return sent;
    }

    /**
     * An initialize that throws leaves the client exactly as it was: not the
     * new key, not a base URL checked before the one that failed.
     */
    @Test
    void initializeThatThrowsChangesNothing() {
        List<String> before = List.of(
                "https://api.zerobounce.net/v2/getcredits key=first-key",
                "https://bulkapi.zerobounce.net/v2/listevaluator/fid/ key=first-key",
                "https://bulkapi.zerobounce.net/api/ipt/test-types");
        String eu = "https://api-eu.zerobounce.net/v2";
        String listEvaluator = "https://bulk.example.test/v2/listevaluator";
        List<Runnable> failing = new ArrayList<>();
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        FakeApi api = new FakeApi();
        sdk.transport = api;
        failing.add(() -> sdk.initialize("second-key", "http://api.example.test/v2"));
        failing.add(() -> sdk.initialize("second-key", eu, "http://bulk.example.test/v2/listevaluator", null));
        failing.add(() -> sdk.initialize("second-key", eu, listEvaluator, "http://bulk.example.test/api/ipt"));
        failing.add(() -> sdk.initialize("second-key", null, listEvaluator, "http://bulk.example.test/api/ipt"));
        for (Runnable initialize : failing) {
            sdk.initialize("first-key");
            assertEquals(before, sent(sdk, api));
            assertThrows(IllegalArgumentException.class, initialize::run);
            assertEquals(before, sent(sdk, api));
        }
    }
}
