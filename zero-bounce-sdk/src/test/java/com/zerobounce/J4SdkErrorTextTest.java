package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text and the shape of API errors in a 2xx (or error-status) body, on every
 * typed call: dead-key bodies that carry another key, a string or number
 * {@code success: false}, the validate greylist answer, the batch "all" error,
 * the {@code [Error]:} prefix, and a message-only body on an error status.
 */
class J4SdkErrorTextTest {

    @TempDir
    Path tempDir;

    private static final Set<String> EXEMPT = Set.of(
            "addFilter", "deleteFilter", "listFilters",
            "getInboxPlacementProviders", "getInboxPlacementTestTypes", "createInboxPlacementTest",
            "getInboxPlacementTest", "listInboxPlacementTests", "deleteInboxPlacementTest",
            "getInboxPlacementWebhook", "setInboxPlacementWebhook", "getListEvaluatorResult");

    private RequestSweep.Outcome call(Method method, int status, String body) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> new SdkHttp.Result(status, "application/json",
                body.getBytes(StandardCharsets.UTF_8));
        sdk.initialize("error-text-key");
        return new RequestSweep(tempDir).invoke(sdk, method);
    }

    /** Typed calls, validate included (its answer is a result only for the greylist text). */
    private static List<Method> typedCalls() {
        List<Method> methods = new ArrayList<>();
        for (Method method : RequestSweep.requestMethods(ZeroBounceSDK.class)) {
            String name = method.getName();
            if (!EXEMPT.contains(name) && !name.toLowerCase().contains("getfile") && !name.startsWith("download")) {
                methods.add(method);
            }
        }
        return methods;
    }

    private static List<Method> named(String... names) {
        List<Method> methods = new ArrayList<>();
        for (Method method : RequestSweep.requestMethods(ZeroBounceSDK.class)) {
            if (Set.of(names).contains(method.getName())) {
                methods.add(method);
            }
        }
        assertTrue(methods.size() >= names.length, methods.toString());
        return methods;
    }

    /** Every typed call answers {@code body} with one error of {@code status}; {@code check} judges its text. */
    private void assertErrorOnEveryCall(String body, int status, List<Method> methods,
            java.util.function.Predicate<List<String>> check) {
        assertTrue(methods.size() >= 2, "calls: " + methods.size());
        List<String> failures = new ArrayList<>();
        for (Method method : methods) {
            RequestSweep.Outcome outcome = call(method, status, body);
            List<String> errors = outcome.errors.isEmpty() ? List.of() : outcome.errors.get(0).getErrors();
            if (outcome.thrown != null || !outcome.successes.isEmpty() || outcome.errors.size() != 1
                    || outcome.errors.get(0).getStatusCode() != status || !check.test(errors)) {
                failures.add(body + " " + outcome);
            }
        }
        assertEquals(List.of(), failures, failures.size() + " failures of " + methods.size());
    }

    // -- 1: the dead keys, with a key that is not a message key --------------

    @Test
    void errorKeyWithAnotherKeyIsAnErrorOnEveryTypedCall() {
        assertErrorOnEveryCall("{\"error\":\"Invalid API key\",\"Credits\":\"5\"}", 200, typedCalls(),
                errors -> errors.contains("Invalid API key"));
    }

    @Test
    void errorMessageKeyWithAnotherKeyIsAnErrorOnEveryTypedCall() {
        assertErrorOnEveryCall("{\"error_message\":\"Invalid API key\",\"Credits\":\"5\"}", 200, typedCalls(),
                errors -> errors.contains("Invalid API key"));
    }

    @Test
    void capitalErrorKeyWithAnotherKeyIsAnErrorOnEveryTypedCall() {
        assertErrorOnEveryCall("{\"Error\":\"Invalid API key\",\"Credits\":\"5\"}", 200, typedCalls(),
                errors -> errors.contains("Invalid API key"));
    }

    // -- 4: success false as a string or number ------------------------------

    @Test
    void aStringOrNumberFalseSuccessIsItsMessageOrAFixedText() {
        String[][] cases = {
                {"{\"success\":0,\"message\":\"nope\"}", "nope"},
                {"{\"success\":\"0\",\"message\":\"nope\"}", "nope"},
                {"{\"success\":\"False\",\"message\":\"nope\"}", "nope"},
                {"{\"success\":0}", "Request not processed successfully."},
                {"{\"success\":\"0\"}", "Request not processed successfully."},
                {"{\"success\":\"False\"}", "Request not processed successfully."}};
        for (String[] c : cases) {
            assertErrorOnEveryCall(c[0], 200, typedCalls(), errors -> List.of(c[1]).equals(errors));
        }
    }

    // -- 3: the validate greylist answer is a result only with text ----------

    @Test
    void aValidateMessageThatIsNotTextIsAnError() {
        List<Method> validate = named("validate", "validateWithOptions");
        for (String body : new String[]{"{\"Message\":null}", "{\"Message\":\"\"}", "{\"Message\":\"  \"}",
                "{\"Message\":5}", "{\"Message\":true}"}) {
            assertErrorOnEveryCall(body, 200, validate,
                    errors -> errors.size() == 1 && errors.get(0).contains("no message text"));
        }
    }

    @Test
    void aNullMessageWithNoAddressOrStatusIsNotAValidateResult() {
        // Not message-only (another key), so it reaches the result check.
        String body = "{\"Message\":null,\"other\":1}";
        assertEquals("ZeroBounce returned no value for address, which is not a string.",
                StrictTypes.missingField(ZBValidateResponse.class, body));
        assertNull(StrictTypes.missingField(ZBValidateResponse.class,
                "{\"Message\":\"Greylist validation still in progress...\",\"other\":1}"));
    }

    @Test
    void theGreylistTextIsStillAValidateResult() {
        for (Method method : named("validate", "validateWithOptions")) {
            RequestSweep.Outcome outcome = call(method, 200,
                    "{\"Message\":\"Greylist validation still in progress...\"}");
            assertEquals(1, outcome.successes.size(), outcome.toString());
            assertTrue(outcome.errors.isEmpty(), outcome.toString());
        }
    }

    // -- 6: N32, N41, N11 ----------------------------------------------------

    @Test
    void aBatchAllErrorWithABlankMessageHasTheDefaultText() {
        String fallback = "Invalid API Key or your account ran out of credits";
        for (String error : new String[]{"\"\"", "\"   \"", "null"}) {
            String body = "{\"email_batch\":[],\"errors\":[{\"email_address\":\"all\",\"error\":" + error + "}]}";
            assertEquals(fallback, HttpMessages.batchAccountError(body), body);
        }
        assertEquals(fallback, HttpMessages.batchAccountError(
                "{\"email_batch\":[],\"errors\":[{\"email_address\":\"all\"}]}"));
        assertErrorOnEveryCall("{\"email_batch\":[],\"errors\":[{\"email_address\":\"all\",\"error\":\"\"}]}", 200,
                named("validateBatch", "validateBatchWithOptions"), errors -> List.of(fallback).equals(errors));
    }

    @Test
    void theBracketedErrorPrefixIsStripped() {
        assertEquals("Invalid key", HttpMessages.stripPrefix("[Error]: Invalid key"));
        assertEquals("Invalid key", HttpMessages.stripPrefix("[Error]:Invalid key"));
        assertEquals("Invalid key", HttpMessages.stripPrefix("Error: [Error]: Error: Invalid key"));
        assertEquals("Invalid key", HttpMessages.stripPrefix("  [Error]:   [Error]: Invalid key"));
        assertEquals("Invalid key", HttpMessages.stripPrefix("Error: Invalid key"));
        assertEquals("[Err] x", HttpMessages.stripPrefix("[Err] x"));
        assertErrorOnEveryCall("{\"error\":\"[Error]: Invalid key\"}", 200, typedCalls(),
                errors -> List.of("Invalid key").equals(errors));
    }

    @Test
    void aMessageOnlyBodyOnAnErrorStatusIsReportedByItsText() {
        // Two message keys: echoing the body's keys would list "second" too.
        assertErrorOnEveryCall("{\"message\":\"first\",\"detail\":\"second\"}", 500, typedCalls(),
                errors -> List.of("first").equals(errors));
        assertTrue(HttpMessages.preferShortMessage("{\"message\":\"first\",\"detail\":\"second\"}"));
        // No text: the keys are not short-circuited.
        assertEquals(false, HttpMessages.preferShortMessage("{\"message\":null}"));
    }

    @Test
    void aMessageOnlyDownloadBodyIsReportedByItsText() {
        ErrorResponse error = ZeroBounceSDK.downloadError(500,
                "{\"message\":\"first\",\"detail\":\"second\"}".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertEquals(List.of("first"), error.getErrors());
    }

    // -- 5: validateS3file's own remove_duplicate ----------------------------

    @Test
    void validateAndScoreS3FileSendRemoveDuplicateByThatName() {
        for (boolean value : new boolean[]{true, false}) {
            ZeroBounceSDK sdk = new ZeroBounceSDK();
            FakeApi api = new FakeApi();
            sdk.transport = api;
            sdk.initialize("s3-key");
            AtomicReference<ErrorResponse> error = new AtomicReference<>();
            sdk.validateS3File(new ZeroBounceSDK.ValidateS3FileOptions()
                    .setFileName("emails.csv").setReturnUrl("https://example.com/done")
                    .setRemoveDuplicate(value), r -> { }, error::set);
            sdk.scoreS3File(new ZeroBounceSDK.ScoreS3FileOptions()
                    .setFileName("emails.csv").setReturnUrl("https://example.com/done")
                    .setRemoveDuplicate(value), r -> { }, error::set);
            assertNull(error.get());
            List<FakeApi.RecordedRequest> sent = api.records();
            assertEquals(2, sent.size());
            assertEquals("/validateS3file", sent.get(0).path);
            assertEquals("/scoreS3file", sent.get(1).path);
            for (FakeApi.RecordedRequest request : sent) {
                assertEquals(String.valueOf(value), request.field("remove_duplicate"), request.path);
            }
        }
    }
}
