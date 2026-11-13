package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A message-only body is an error with status 200 on the email finder and domain
 * search, whatever its message value (check E3, standard.md section 3): a
 * non-blank string, a blank string, null, or a list of strings. It is defined by
 * its keys, so the value does not change the answer. Validate's greylist
 * {@code Message} is a result and is covered by OfflineParityTest.
 */
class OfflineMessageOnlyTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "message-only-key";

    /** The public calls whose result is never a bare message: email finder and domain search. */
    private static List<Method> finderAndDomainSearch() {
        List<Method> methods = new ArrayList<>();
        for (Method method : RequestSweep.allRequestMethods()) {
            if (method.getDeclaringClass() != ZeroBounceSDK.class) {
                continue;
            }
            String name = method.getName();
            if (name.equals("guessFormat") || name.equals("findEmail") || name.equals("findDomain")) {
                methods.add(method);
            }
        }
        assertTrue(methods.size() >= 3, "finder and domain-search overloads: " + methods.size());
        return methods;
    }

    @Test
    void aMessageOnlyBodyIsAnErrorWithStatus200WhateverItsMessageValue() throws Exception {
        List<Method> methods = finderAndDomainSearch();
        List<String> bodies = List.of(
                "{\"Message\":\"Invalid API key or your account ran out of credits\"}",
                "{\"message\":\"Not found\"}",
                "{\"message\":\"\"}",
                "{\"message\":\"   \"}",
                "{\"message\":null}",
                "{\"Message\":null}",
                "{\"message\":[]}",
                "{\"message\":[\"a\",\"b\"]}",
                "{\"message\":[\"\"]}",
                "{\"message\":[null]}",
                "{\"message\":5}",
                "{\"message\":true}",
                "{\"message\":null,\"error\":\"\"}",
                "{\"detail\":null}");
        List<String> failures = new ArrayList<>();
        for (String body : bodies) {
            Path dir = Files.createTempDirectory(tempDir, "run");
            RequestSweep sweep = new RequestSweep(dir);
            try (LoopbackServer server = new LoopbackServer(
                    LoopbackServer.answer(200, "application/json", body.getBytes(StandardCharsets.UTF_8)))) {
                ZeroBounceSDK sdk = server.sdk();
                sdk.initialize(KEY);
                try (var ignored = server.apacheClients()) {
                    for (Method method : methods) {
                        server.clear();
                        RequestSweep.Outcome outcome = sweep.invoke(sdk, method);
                        if (outcome.thrown != null || !outcome.successes.isEmpty() || outcome.errors.size() != 1
                                || outcome.errors.get(0).getStatusCode() != 200) {
                            failures.add(body + " " + outcome);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), failures, failures.size() + " failures");
    }

    @Test
    void aMessageOnlyBodyWithTextKeepsItsTextAsTheErrorMessage() throws Exception {
        List<Method> methods = finderAndDomainSearch();
        String[][] cases = {
                {"{\"Message\":\"Invalid API key or your account ran out of credits\"}",
                        "Invalid API key or your account ran out of credits"},
                {"{\"message\":[\"first\",\"second\"]}", "first\nsecond"},
                {"{\"message\":\"\",\"error\":\"Error: Bad domain\"}", "Bad domain"}};
        List<String> failures = new ArrayList<>();
        for (String[] c : cases) {
            Path dir = Files.createTempDirectory(tempDir, "text");
            RequestSweep sweep = new RequestSweep(dir);
            try (LoopbackServer server = new LoopbackServer(
                    LoopbackServer.answer(200, "application/json", c[0].getBytes(StandardCharsets.UTF_8)))) {
                ZeroBounceSDK sdk = server.sdk();
                sdk.initialize(KEY);
                try (var ignored = server.apacheClients()) {
                    for (Method method : methods) {
                        server.clear();
                        RequestSweep.Outcome outcome = sweep.invoke(sdk, method);
                        if (outcome.errors.size() != 1 || !List.of(c[1]).equals(outcome.errors.get(0).getErrors())) {
                            failures.add(c[0] + " expected [" + c[1] + "] " + outcome);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), failures, failures.size() + " failures");
    }

    @Test
    void validateKeepsAMessageOnlyGreylistAnswerAsAResult() throws Exception {
        List<Method> validate = new ArrayList<>();
        for (Method method : RequestSweep.allRequestMethods()) {
            if (method.getDeclaringClass() == ZeroBounceSDK.class && method.getName().equals("validate")) {
                validate.add(method);
            }
        }
        assertTrue(!validate.isEmpty(), "validate overloads");
        Path dir = Files.createTempDirectory(tempDir, "greylist");
        RequestSweep sweep = new RequestSweep(dir);
        List<String> failures = new ArrayList<>();
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json",
                "{\"address\":\"a@example.com\",\"Message\":\"Greylist validation still in progress...\"}"
                        .getBytes(StandardCharsets.UTF_8)))) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            try (var ignored = server.apacheClients()) {
                for (Method method : validate) {
                    server.clear();
                    RequestSweep.Outcome outcome = sweep.invoke(sdk, method);
                    if (outcome.thrown != null || outcome.successes.size() != 1 || !outcome.errors.isEmpty()) {
                        failures.add(method + " " + outcome);
                    }
                }
            }
        }
        assertEquals(List.of(), failures, failures.size() + " failures");
    }
}
