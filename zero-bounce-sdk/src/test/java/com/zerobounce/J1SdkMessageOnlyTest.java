package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A message-only 2xx body is an error with status 200 and its text on every
 * typed call of {@link ZeroBounceSDK}: file jobs (send, status, delete), S3 and
 * SFTP jobs, the list evaluator and the finder and domain search. The exceptions
 * are validate (its {@code {"Message": ...}} greylist answer is a result), the
 * raw-JSON filter calls and the Inbox Placement Tester (JSON-element results whose
 * documented success can be a message) and downloads, whose bodies are refused
 * as downloads anyway.
 */
class J1SdkMessageOnlyTest {

    @TempDir
    Path tempDir;

    private static final Set<String> EXEMPT = Set.of(
            "validate", "validateWithOptions", "addFilter", "deleteFilter", "listFilters",
            "getInboxPlacementProviders", "getInboxPlacementTestTypes", "createInboxPlacementTest",
            "getInboxPlacementTest", "listInboxPlacementTests", "deleteInboxPlacementTest",
            "getInboxPlacementWebhook", "setInboxPlacementWebhook", "getListEvaluatorResult");

    private RequestSweep.Outcome call(Method method, String body) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> new SdkHttp.Result(200, "application/json",
                body.getBytes(StandardCharsets.UTF_8));
        sdk.initialize("message-only-key");
        return new RequestSweep(tempDir).invoke(sdk, method);
    }

    private static List<Method> typedCalls() {
        List<Method> methods = new ArrayList<>();
        for (Method method : RequestSweep.requestMethods(ZeroBounceSDK.class)) {
            if (!EXEMPT.contains(method.getName()) && !method.getName().toLowerCase().contains("getfile")
                    && !method.getName().startsWith("download")) {
                methods.add(method);
            }
        }
        return methods;
    }

    @Test
    void aMessageOnlyBodyIsAnErrorWithStatus200OnEveryTypedFileJobAndSdkCall() {
        List<Method> methods = typedCalls();
        assertTrue(methods.size() >= 40, "typed calls: " + methods.size());
        String[][] cases = {
                {"{\"Message\":\"Invalid API key or your account ran out of credits\"}",
                        "Invalid API key or your account ran out of credits"},
                {"{\"message\":[\"first\",\"second\"]}", "first\nsecond"},
                {"{\"message\":null}", null}};
        List<String> failures = new ArrayList<>();
        for (String[] c : cases) {
            for (Method method : methods) {
                RequestSweep.Outcome outcome = call(method, c[0]);
                List<String> errors = outcome.errors.isEmpty() ? List.of() : outcome.errors.get(0).getErrors();
                // A null expected text is the fixed "no message text" error.
                boolean textOk = c[1] == null ? errors.size() == 1 && errors.get(0).contains("no message text")
                        : List.of(c[1]).equals(errors);
                if (outcome.thrown != null || !outcome.successes.isEmpty() || outcome.errors.size() != 1
                        || outcome.errors.get(0).getStatusCode() != 200 || !textOk) {
                    failures.add(c[0] + " " + outcome);
                }
            }
        }
        assertEquals(List.of(), failures, failures.size() + " failures");
    }

    @Test
    void theExemptCallsKeepTheirMessageOnlyResults() {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> new SdkHttp.Result(200, "application/json",
                "{\"Message\":\"Greylist validation still in progress...\"}".getBytes(StandardCharsets.UTF_8));
        sdk.initialize("message-only-key");
        RequestSweep sweep = new RequestSweep(tempDir);
        List<String> failures = new ArrayList<>();
        for (Method method : RequestSweep.requestMethods(ZeroBounceSDK.class)) {
            if (!method.getName().equals("validate") && !method.getName().equals("validateWithOptions")) {
                continue;
            }
            RequestSweep.Outcome outcome = sweep.invoke(sdk, method);
            if (outcome.thrown != null || outcome.successes.size() != 1 || !outcome.errors.isEmpty()) {
                failures.add("validate greylist: " + outcome);
            }
        }
        RequestSweep.Outcome filter = call(
                RequestSweep.requestMethods(ZeroBounceSDK.class).stream()
                        .filter(m -> m.getName().equals("addFilter")).findFirst().orElseThrow(),
                "{\"Message\":\"Filter has been added.\"}");
        if (filter.successes.size() != 1 || !filter.errors.isEmpty()) {
            failures.add("addFilter: " + filter);
        }
        assertEquals(List.of(), failures);
    }
}
