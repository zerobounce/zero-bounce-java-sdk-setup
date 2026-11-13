package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A file job's {@code message} is one string in the model, on every file-job answer:
 * the upload and scoring upload, delete and scoring delete, and file status (standard.md
 * section 3). A list of strings is joined with newlines, an empty list is an empty
 * message, and a list with anything but strings is an error with status 200.
 */
class OfflineFileJobMessageTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "file-job-message-key";

    /** Every typed call whose result is a file-job answer, by its success type. */
    private static List<Method> fileJobMethods() {
        Map<Type, Boolean> kinds = Map.of(ZBSendFileResponse.class, true,
                ZBDeleteFileResponse.class, true, ZBFileStatusResponse.class, true);
        List<Method> methods = new ArrayList<>();
        for (Method method : RequestSweep.allRequestMethods()) {
            if (method.getDeclaringClass() != ZeroBounceSDK.class) {
                continue;
            }
            for (Type type : method.getGenericParameterTypes()) {
                if (type instanceof java.lang.reflect.ParameterizedType
                        && ((java.lang.reflect.ParameterizedType) type).getRawType() == ZeroBounceSDK.OnSuccessCallback.class
                        && kinds.containsKey(((java.lang.reflect.ParameterizedType) type).getActualTypeArguments()[0])) {
                    methods.add(method);
                }
            }
        }
        // Upload, scoring upload (x2 each email and domain flavour), delete and file status calls.
        assertTrue(methods.size() >= 12, "file-job calls: " + methods.size());
        return methods;
    }

    private static String messageOf(Object success) {
        if (success instanceof ZBSendFileResponse) {
            return ((ZBSendFileResponse) success).getMessage();
        }
        if (success instanceof ZBDeleteFileResponse) {
            return ((ZBDeleteFileResponse) success).getMessage();
        }
        if (success instanceof ZBFileStatusResponse) {
            return ((ZBFileStatusResponse) success).getMessage();
        }
        return "<not a file-job answer: " + success + ">";
    }

    @Test
    void aMessageListIsJoinedOnEveryFileJobAnswer() throws Exception {
        List<Method> methods = fileJobMethods();
        String[][] cases = {
                {"[\"ok\",\"also\"]", "ok\nalso"},
                {"[\"only\"]", "only"},
                {"[]", ""},
                {"\"plain\"", "plain"},
                {"null", null}};
        List<String> failures = new ArrayList<>();
        for (String[] c : cases) {
            String body = "{\"success\":true,\"message\":" + c[0] + ",\"file_id\":\"x\"}";
            Path dir = Files.createTempDirectory(tempDir, "joined");
            RequestSweep sweep = new RequestSweep(dir);
            try (LoopbackServer server = new LoopbackServer(
                    LoopbackServer.answer(200, "application/json", body.getBytes(StandardCharsets.UTF_8)))) {
                ZeroBounceSDK sdk = server.sdk();
                sdk.initialize(KEY);
                try (var ignored = server.apacheClients()) {
                    for (Method method : methods) {
                        server.clear();
                        RequestSweep.Outcome outcome = sweep.invoke(sdk, method);
                        if (outcome.thrown != null || !outcome.errors.isEmpty() || outcome.successes.size() != 1
                                || !java.util.Objects.equals(c[1], messageOf(outcome.successes.get(0)))) {
                            failures.add(body + " expected " + c[1] + ": " + outcome);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), failures, failures.size() + " failures");
    }

    @Test
    void aMessageListWithAnythingButStringsIsAnErrorWithStatus200() throws Exception {
        List<Method> methods = fileJobMethods();
        List<String> failures = new ArrayList<>();
        for (String message : List.of("[5]", "[\"ok\",5]", "[\"ok\",null]", "[true]", "[{}]", "[[]]")) {
            String body = "{\"success\":true,\"message\":" + message + ",\"file_id\":\"x\"}";
            Path dir = Files.createTempDirectory(tempDir, "bad");
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
}
