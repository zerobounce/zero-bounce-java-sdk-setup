package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An empty 2xx is an error with its status on every public call of
 * {@link ZeroBounceSDK}, a 204 included, except the Inbox Placement delete, the
 * one documented empty success (standard.md section 3). It is never a null
 * success and never a null-pointer crash (listS3Files used to throw).
 */
class J1SdkEmptySuccessTest {

    @TempDir
    Path tempDir;

    private RequestSweep.Outcome call(Method method, int status, String body) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> new SdkHttp.Result(status, "application/json",
                body.getBytes(StandardCharsets.UTF_8));
        sdk.initialize("empty-2xx-key");
        return new RequestSweep(tempDir).invoke(sdk, method);
    }

    @Test
    void everyPublicCallReportsAnEmpty2xxWithItsStatus() {
        List<Method> methods = RequestSweep.requestMethods(ZeroBounceSDK.class);
        assertTrue(methods.size() >= 60, "found " + methods.size() + " request methods");
        List<String> failures = new ArrayList<>();
        for (Method method : methods) {
            boolean documentedEmpty = method.getName().equals("deleteInboxPlacementTest");
            for (int status : new int[]{200, 204}) {
                for (String body : new String[]{"", "\n  "}) {
                    RequestSweep.Outcome outcome = call(method, status, body);
                    String label = method.getName() + " status " + status + " body " + body.length()
                            + " -> " + outcome;
                    if (outcome.thrown != null) {
                        failures.add("threw: " + label);
                    } else if (documentedEmpty && body.isEmpty()) {
                        if (outcome.successes.size() != 1 || outcome.successes.get(0) != null
                                || !outcome.errors.isEmpty()) {
                            failures.add("expected a null success: " + label);
                        }
                    } else if (!outcome.successes.isEmpty() || outcome.errors.size() != 1
                            || outcome.errors.get(0).getStatusCode() != status) {
                        failures.add("expected an error with status " + status + ": " + label);
                    }
                }
            }
        }
        assertEquals(List.of(), failures, failures.size() + " failures");
    }

    @Test
    void listS3FilesWithA204IsAnErrorNotAnException() {
        Method listS3 = RequestSweep.requestMethods(ZeroBounceSDK.class).stream()
                .filter(m -> m.getName().equals("listS3Files")).findFirst().orElseThrow();
        RequestSweep.Outcome outcome = call(listS3, 204, "");
        assertEquals(null, outcome.thrown);
        assertTrue(outcome.successes.isEmpty(), outcome.toString());
        assertEquals(204, outcome.errors.get(0).getStatusCode());
    }
}
