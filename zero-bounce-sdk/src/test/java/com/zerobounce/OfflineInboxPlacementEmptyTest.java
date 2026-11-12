package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An empty 2xx from the Inbox Placement Tester: a success with a null result on
 * DELETE (the API answers 204 No Content there), and an error carrying its
 * status on every other call. The methods are every public request method whose
 * name mentions InboxPlacement, found by reflection.
 */
class OfflineInboxPlacementEmptyTest {

    @TempDir
    Path tempDir;

    private static List<Method> inboxPlacementMethods() {
        return RequestSweep.requestMethods(ZeroBounceSDK.class).stream()
                .filter(m -> m.getName().contains("InboxPlacement"))
                .collect(Collectors.toList());
    }

    private RequestSweep.Outcome call(Method method, int status, String contentType, byte[] body) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> new SdkHttp.Result(status, contentType, body);
        sdk.initialize("ipt-key");
        return new RequestSweep(tempDir).invoke(sdk, method);
    }

    @Test
    void anEmpty2xxIsASuccessOnDeleteOnlyAndAnErrorWithItsStatusElsewhere() {
        List<Method> methods = inboxPlacementMethods();
        assertTrue(methods.size() >= 8, "found " + methods.size() + " Inbox Placement methods");
        List<String> failures = new ArrayList<>();
        for (Method method : methods) {
            boolean delete = method.getName().startsWith("delete");
            for (int status : new int[]{200, 202, 204}) {
                for (String contentType : new String[]{"application/json", ""}) {
                    RequestSweep.Outcome outcome = call(method, status, contentType, new byte[0]);
                    String label = status + " " + contentType + " " + outcome;
                    if (outcome.thrown != null) {
                        failures.add(label);
                    } else if (delete) {
                        if (outcome.successes.size() != 1 || outcome.successes.get(0) != null
                                || !outcome.errors.isEmpty()) {
                            failures.add("expected a null success: " + label);
                        }
                    } else if (!outcome.successes.isEmpty() || outcome.errors.size() != 1
                            || outcome.errors.get(0).getStatusCode() != status
                            || !outcome.errors.get(0).getErrors().toString().contains("Empty response")) {
                        failures.add("expected an error with status " + status + ": " + label);
                    }
                }
            }
            // An empty non-2xx is an error with its status everywhere, DELETE included.
            for (int status : new int[]{404, 500}) {
                RequestSweep.Outcome outcome = call(method, status, "application/json", new byte[0]);
                if (!outcome.successes.isEmpty() || outcome.errors.size() != 1
                        || outcome.errors.get(0).getStatusCode() != status) {
                    failures.add("expected an error with status " + status + ": " + outcome);
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    @Test
    void aDeleteWithABodyIsStillReadAsJson() throws Exception {
        Method delete = ZeroBounceSDK.class.getMethod("deleteInboxPlacementTest", String.class,
                ZeroBounceSDK.OnSuccessCallback.class, ZeroBounceSDK.OnErrorCallback.class);
        RequestSweep.Outcome outcome = call(delete, 200, "application/json",
                "{\"deleted\": true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1, outcome.successes.size(), outcome.toString());
        assertEquals("{\"deleted\":true}", String.valueOf(outcome.successes.get(0)));
        outcome = call(delete, 200, "text/html",
                "<html><title>Denied</title></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1, outcome.errors.size(), outcome.toString());
        assertEquals(200, outcome.errors.get(0).getStatusCode());
    }
}
