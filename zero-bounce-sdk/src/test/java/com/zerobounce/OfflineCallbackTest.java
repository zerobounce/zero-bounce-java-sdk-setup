package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline tests for what the SDK does with the caller's own callbacks, with
 * file downloads that turn out to be JSON errors, with the API key handed to
 * {@code initialize}, with booleans in responses, and with {@code toString}.
 * Every request is answered in process; nothing here reaches the live API.
 */
class OfflineCallbackTest {

    @TempDir
    Path tempDir;

    /** A transport that answers every call with the same response. */
    private static ZeroBounceSDK client(String key, int status, String contentType, String body,
                                        List<String> urls) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = call -> {
            urls.add(call.url);
            return new SdkHttp.Result(status, contentType, body.getBytes(StandardCharsets.UTF_8));
        };
        sdk.initialize(key);
        return sdk;
    }

    private static ZeroBounceSDK client(int status, String contentType, String body) {
        return client("callback-key", status, contentType, body, new ArrayList<>());
    }

    private interface Call {
        @SuppressWarnings("rawtypes")
        void run(ZeroBounceSDK sdk, ZeroBounceSDK.OnSuccessCallback ok, ZeroBounceSDK.OnErrorCallback err)
                throws Exception;
    }

    /** One public method per request path the SDK has. */
    private Map<String, Call> everyRequestPath() {
        File csv = tempDir.resolve("in.csv").toFile();
        try {
            Files.writeString(csv.toPath(), "email\nperson@example.com\n");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String out = tempDir.resolve("out.csv").toString();
        Map<String, Call> calls = new LinkedHashMap<>();
        // sendRequest
        calls.put("validate", (sdk, ok, err) -> sdk.validate("person@example.com", null, ok, err));
        // sendRequest, with the message-only check
        calls.put("findEmail", (sdk, ok, err) -> sdk.findEmail("Ada", "example.com", null, null, null, ok, err));
        // sendFormPostRequest
        calls.put("scoreEmailPost", (sdk, ok, err) -> sdk.scoreEmailPost("person@example.com", ok, err));
        // sendRequestWithMethod
        calls.put("listFilters", (sdk, ok, err) -> sdk.listFilters(false, ok, err));
        // sendRequestWithMethod, through a lambda that converts the payload
        calls.put("listS3Files", (sdk, ok, err) -> sdk.listS3Files(null, null, ok, err));
        // the Apache multipart upload, through the transport
        calls.put("sendFile", (sdk, ok, err) -> sdk.sendFile(csv, 1, null, ok, err));
        calls.put("sendFileStream", (sdk, ok, err) -> sdk.sendFile(
                new ByteArrayInputStream("email\na@example.com\n".getBytes(StandardCharsets.UTF_8)),
                "in.csv", 1, null, ok, err));
        // getfile
        calls.put("getFile", (sdk, ok, err) -> sdk.getFile("fid", out, ok, err));
        // the email finder and domain search bulk paths
        calls.put("emailFinderGetFile", (sdk, ok, err) -> sdk.emailFinderGetFile("fid", out, ok, err));
        calls.put("emailFinderFileStatus", (sdk, ok, err) -> sdk.emailFinderFileStatus("fid", ok, err));
        calls.put("sendListEvaluatorFile", (sdk, ok, err) -> sdk.sendListEvaluatorFile(csv, 1, ok, err));
        return calls;
    }

    /** A body each path accepts as a success. */
    private static String successBody(String name) {
        switch (name) {
            case "getFile":
            case "emailFinderGetFile":
                return "email,status\nperson@example.com,valid\n";
            case "listFilters":
            case "listS3Files":
                return "[]";
            case "findEmail":
                return "{\"email\":\"ada@example.com\",\"domain\":\"example.com\",\"format\":\"first\"}";
            default:
                return "{\"success\":true,\"message\":\"ok\",\"file_id\":\"fid\",\"address\":\"person@example.com\","
                        + "\"email\":\"person@example.com\",\"score\":9,\"status\":\"valid\"}";
        }
    }

    private static String contentType(String name) {
        return name.endsWith("GetFile") || name.equals("getFile") ? "text/csv" : "application/json";
    }

    @Test
    void anExceptionFromTheSuccessCallbackPropagatesOnEveryRequestPath() throws Exception {
        for (Map.Entry<String, Call> entry : everyRequestPath().entrySet()) {
            String name = entry.getKey();
            ZeroBounceSDK sdk = client(200, contentType(name), successBody(name));
            IllegalStateException thrown = new IllegalStateException("from the caller's success callback");
            AtomicInteger errors = new AtomicInteger();
            RuntimeException caught = assertThrows(RuntimeException.class, () -> entry.getValue().run(
                    sdk,
                    value -> {
                        throw thrown;
                    },
                    error -> errors.incrementAndGet()
            ), name);
            assertSame(thrown, caught, name);
            assertEquals(0, errors.get(), name + " reported the caller's exception through the error callback");
        }
    }

    @Test
    void anExceptionFromTheErrorCallbackPropagatesAndIsNotReportedTwice() throws Exception {
        for (Map.Entry<String, Call> entry : everyRequestPath().entrySet()) {
            String name = entry.getKey();
            ZeroBounceSDK sdk = client(500, "application/json", "{\"error\":\"Server error\"}");
            IllegalStateException thrown = new IllegalStateException("from the caller's error callback");
            AtomicInteger errors = new AtomicInteger();
            RuntimeException caught = assertThrows(RuntimeException.class, () -> entry.getValue().run(
                    sdk,
                    value -> fail(name + " reached the success callback"),
                    error -> {
                        errors.incrementAndGet();
                        throw thrown;
                    }
            ), name);
            assertSame(thrown, caught, name);
            assertEquals(1, errors.get(), name + " called the error callback again with the caller's exception");
        }
    }

    @Test
    void aTransportFailureStillReachesTheErrorCallback() {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = call -> {
            throw new java.io.IOException("connection reset");
        };
        sdk.initialize("transport-key");
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        sdk.validate("person@example.com", null, value -> fail("success"), error::set);
        assertNotNull(error.get());
        assertEquals("[connection reset]", error.get().getErrors().toString());
    }

    // -- getfile and JSON error bodies -----------------------------------------

    @Test
    void getFileRejectsAJsonErrorBodyWhateverTheContentType() throws Exception {
        String[] bodies = {
                "{\"error\":\"File not found\"}",
                "{\"Error\":\"File not found\"}",
                "{\"Message\":\"File not found\"}",
                "{\"message\":\"File not found\"}",
                "{\"error_message\":\"File not found\"}",
                "{\"detail\":\"File not found\"}",
                "{\"success\":false}",
                "{\"success\":\"False\",\"message\":\"File not ready\"}",
                "  {\"error\":\"File not found\"}\n",
        };
        String[] contentTypes = {"text/plain", "application/octet-stream", "", "text/csv; charset=utf-8"};
        int[] statuses = {200, 400};
        for (String body : bodies) {
            for (String contentType : contentTypes) {
                for (int status : statuses) {
                    for (String method : new String[]{"getFile", "scoringGetFile", "emailFinderGetFile",
                            "domainSearchGetFile"}) {
                        String label = method + " " + status + " " + contentType + " " + body.trim();
                        Path out = tempDir.resolve("download-" + Math.abs(label.hashCode()) + ".csv");
                        ZeroBounceSDK sdk = client(status, contentType, body);
                        AtomicReference<ErrorResponse> error = new AtomicReference<>();
                        AtomicReference<Object> value = new AtomicReference<>();
                        ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse> ok = value::set;
                        switch (method) {
                            case "getFile":
                                sdk.getFile("fid", out.toString(), ok, error::set);
                                break;
                            case "scoringGetFile":
                                sdk.scoringGetFile("fid", out.toString(), ok, error::set);
                                break;
                            case "emailFinderGetFile":
                                sdk.emailFinderGetFile("fid", out.toString(), ok, error::set);
                                break;
                            default:
                                sdk.domainSearchGetFile("fid", out.toString(), ok, error::set);
                        }
                        assertNull(value.get(), label + " was saved as a file");
                        assertNotNull(error.get(), label);
                        assertEquals(status, error.get().getStatusCode(), label);
                        assertFalse(error.get().getErrors().isEmpty(), label);
                        assertFalse(Files.exists(out), label + " wrote the error body to disk");
                    }
                }
            }
        }
    }

    @Test
    void getFileRejectsAnyBodySentAsApplicationJson() {
        Path out = tempDir.resolve("json.csv");
        ZeroBounceSDK sdk = client(200, "application/json", "{\"file_id\":\"fid\"}");
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        sdk.getFile("fid", out.toString(), value -> fail("saved"), error::set);
        assertNotNull(error.get());
        assertEquals(200, error.get().getStatusCode());
        assertFalse(Files.exists(out));
    }

    @Test
    void getFileStillSavesACsvWhateverTheContentType() throws Exception {
        for (String contentType : new String[]{"text/plain", "application/octet-stream", "", "text/csv"}) {
            for (String method : new String[]{"getFile", "emailFinderGetFile"}) {
                Path out = tempDir.resolve(method + "-" + contentType.replace('/', '_') + ".csv");
                String csv = "email,status\n\"{a}\"@example.com,valid\n";
                ZeroBounceSDK sdk = client(200, contentType, csv);
                AtomicReference<ZBGetFileResponse> value = new AtomicReference<>();
                ZeroBounceSDK.OnErrorCallback err = e -> fail(method + " " + contentType + ": " + e.getErrors());
                if (method.equals("getFile")) {
                    sdk.getFile("fid", out.toString(), value::set, err);
                } else {
                    sdk.emailFinderGetFile("fid", out.toString(), value::set, err);
                }
                assertNotNull(value.get());
                assertEquals(csv, Files.readString(out));
            }
        }
    }

    // -- the API key handed to initialize ---------------------------------------

    @Test
    void initializeRefusesAKeyWithSurroundingWhitespaceOrControlCharacters() {
        String[] keys = {" key", "key ", "key\n", "\tkey", "ke\u0000y", "ke\ny", "ke\u007fy"};
        for (String key : keys) {
            for (int overload = 0; overload < 4; overload++) {
                List<String> urls = new ArrayList<>();
                ZeroBounceSDK sdk = client("placeholder", 200, "application/json", "{\"Credits\":\"5\"}", urls);
                switch (overload) {
                    case 0:
                        sdk.initialize(key);
                        break;
                    case 1:
                        sdk.initialize(key, 1000);
                        break;
                    case 2:
                        sdk.initialize(key, ZBConstants.API_EU_URL);
                        break;
                    default:
                        sdk.initialize(key, null, null, null);
                }
                AtomicReference<ErrorResponse> error = new AtomicReference<>();
                sdk.getCredits(value -> fail("sent " + key.replace("\n", "\\n")), error::set);
                assertNotNull(error.get());
                assertEquals("[api_key has surrounding whitespace or control characters]",
                        error.get().getErrors().toString());
                assertEquals(0, error.get().getStatusCode());
                assertTrue(urls.isEmpty(), "a request was sent with the refused key");

                // The Inbox Placement Tester sends the key in a header instead.
                AtomicReference<ErrorResponse> iptError = new AtomicReference<>();
                sdk.getInboxPlacementProviders(value -> fail("sent"), iptError::set);
                assertNotNull(iptError.get());
                assertTrue(urls.isEmpty(), "a request was sent with the refused key");
            }
        }
    }

    @Test
    void aCleanKeyAfterARefusedOneIsAccepted() {
        List<String> urls = new ArrayList<>();
        ZeroBounceSDK sdk = client("placeholder", 200, "application/json", "{\"Credits\":\"5\"}", urls);
        sdk.initialize("bad key\n");
        sdk.initialize("good-key");
        AtomicReference<ZBCreditsResponse> value = new AtomicReference<>();
        sdk.getCredits(value::set, e -> fail(e.getErrors().toString()));
        assertNotNull(value.get());
        assertEquals(1, urls.size());
        assertTrue(urls.get(0).contains("api_key=good-key"));
    }

    @Test
    void aKeyWithInnerSpacesIsStillSent() {
        List<String> urls = new ArrayList<>();
        ZeroBounceSDK sdk = client("key a/b", 200, "application/json", "{\"Credits\":\"5\"}", urls);
        sdk.getCredits(value -> {
        }, e -> fail(e.getErrors().toString()));
        assertEquals(1, urls.size());
    }

    // -- booleans in responses ----------------------------------------------------

    private ZBValidateResponse validated(String body) {
        ZeroBounceSDK sdk = client(200, "application/json", body);
        AtomicReference<ZBValidateResponse> value = new AtomicReference<>();
        sdk.validate("person@example.com", null, value::set, e -> fail(e.getErrors().toString()));
        return value.get();
    }

    private ErrorResponse validateFails(String body) {
        ZeroBounceSDK sdk = client(200, "application/json", body);
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        sdk.validate("person@example.com", null, value -> fail("parsed " + body), error::set);
        assertNotNull(error.get(), body);
        return error.get();
    }

    @Test
    void booleansAcceptTheDocumentedSpellings() {
        Object[][] cases = {
                {"true", true}, {"false", false},
                {"\"true\"", true}, {"\"false\"", false},
                {"\"True\"", true}, {"\"False\"", false},
                {"1", true}, {"0", false},
                {"\"1\"", true}, {"\"0\"", false},
        };
        for (Object[] c : cases) {
            String body = "{\"address\":\"a@example.com\",\"status\":\"valid\",\"free_email\":" + c[0]
                    + ",\"mx_found\":" + c[0] + ",\"catchall_domain\":" + c[0] + "}";
            ZBValidateResponse response = validated(body);
            assertEquals(c[1], response.getFreeEmail(), body);
            assertEquals(c[1], response.getMxFound(), body);
            assertEquals(c[1], response.getCatchallDomain(), body);
        }
        ZBValidateResponse nulls = validated("{\"address\":\"a@example.com\",\"catchall_domain\":null}");
        assertNull(nulls.getCatchallDomain());
    }

    @Test
    void anythingElseInABooleanIsAShortError() {
        String[] values = {"\"yes\"", "\"no\"", "\"\"", "2", "-1", "1.5", "\"t\"", "[]", "{}"};
        for (String v : values) {
            String body = "{\"address\":\"a@example.com\",\"status\":\"valid\",\"free_email\":" + v
                    + ",\"padding\":\"" + "x".repeat(2000) + "\"}";
            ErrorResponse error = validateFails(body);
            String message = String.join(" ", error.getErrors());
            assertTrue(message.contains("free_email"), message);
            assertTrue(message.length() < 200, message);
            assertFalse(message.contains("xxxxxxxx"), "the whole body: " + message);
            assertFalse(message.contains("Exception"), "a parser dump: " + message);
            assertFalse(message.contains("github.com/google/gson"), "a parser dump: " + message);
        }
    }

    @Test
    void booleansAreStrictOnEveryModel() throws Exception {
        ZeroBounceSDK sdk = client(200, "application/json",
                "{\"success\":\"yes\",\"message\":\"ok\",\"file_id\":\"fid\"}");
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        sdk.fileStatus("fid", value -> fail("parsed"), error::set);
        assertNotNull(error.get());
        assertTrue(String.join(" ", error.get().getErrors()).contains("success"), error.get().getErrors().toString());

        File csv = tempDir.resolve("strict.csv").toFile();
        AtomicReference<ErrorResponse> sendError = new AtomicReference<>();
        ZeroBounceSDK upload = client(200, "application/json", "{\"success\":\"yes\",\"file_id\":\"fid\"}");
        try {
            Files.writeString(csv.toPath(), "email\na@example.com\n");
            upload.sendFile(csv, 1, null, value -> fail("parsed"), sendError::set);
        } catch (Exception e) {
            fail(e);
        }
        assertNotNull(sendError.get());

        AtomicReference<ZBSendFileResponse> sent = new AtomicReference<>();
        client(200, "application/json", "{\"success\":\"True\",\"file_id\":\"fid\"}")
                .sendFile(csv, 1, null, sent::set, e -> fail(e.getErrors().toString()));
        assertTrue(sent.get().getSuccess());

        AtomicReference<ZBActivityDataResponse> activity = new AtomicReference<>();
        client(200, "application/json", "{\"found\":1,\"active_in_days\":\"3\"}")
                .getActivityData("person@example.com", activity::set, e -> fail(e.getErrors().toString()));
        assertTrue(activity.get().getFound());

        AtomicReference<ErrorResponse> batchError = new AtomicReference<>();
        client(200, "application/json",
                "{\"email_batch\":[{\"address\":\"a@example.com\",\"free_email\":\"yes\"}],\"errors\":[]}")
                .validateBatch(List.of(new ZBValidateBatchData("a@example.com", null)),
                        value -> fail("parsed"), batchError::set);
        assertNotNull(batchError.get());
        assertTrue(String.join(" ", batchError.get().getErrors()).contains("free_email"),
                batchError.get().getErrors().toString());
    }

    // -- toString ------------------------------------------------------------------

    @Test
    void errorResponseToStringNamesTheTypeAndEveryField() {
        ErrorResponse error = new ErrorResponse();
        error.setSuccess(false);
        error.setErrors(new ArrayList<>(List.of("first", "second")));
        error.setStatusCode(403);
        String text = error.toString();
        assertTrue(text.startsWith("ErrorResponse{"), text);
        assertTrue(text.contains("success=false"), text);
        assertTrue(text.contains("errors=[first, second]"), text);
        assertTrue(text.contains("statusCode=403"), text);
    }

    @Test
    void validateBatchResponseToStringNamesTheTypeAndEveryField() {
        ZeroBounceSDK sdk = client(200, "application/json",
                "{\"email_batch\":[{\"address\":\"ok@example.com\",\"status\":\"valid\"}],"
                        + "\"errors\":[{\"email_address\":\"bad@\",\"error\":\"Invalid email\"}]}");
        AtomicReference<ZBValidateBatchResponse> value = new AtomicReference<>();
        sdk.validateBatch(List.of(new ZBValidateBatchData("ok@example.com", null)), value::set,
                e -> fail(e.getErrors().toString()));
        String text = value.get().toString();
        assertTrue(text.startsWith("ZBValidateBatchResponse{"), text);
        assertTrue(text.contains("emailBatch=[ZBValidateResponse{"), text);
        assertTrue(text.contains("address='ok@example.com'"), text);
        assertTrue(text.contains("errors=[{email_address=bad@, error=Invalid email}]"), text);
    }
}
