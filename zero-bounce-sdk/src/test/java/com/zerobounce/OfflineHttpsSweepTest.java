package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every public request method sends over HTTPS, on the SDK's own default base URLs.
 * <p>
 * The methods are found by reflection (every public instance method of
 * {@link ZeroBounceSDK} that takes an {@link ZeroBounceSDK.OnErrorCallback}) and invoked
 * by reflection, overload by overload, with the arguments in {@link RequestSweep}. A
 * public request method missing from it, or an entry that no longer matches a method,
 * fails {@link #everyPublicRequestMethodIsInTheSweep()}, so a new request path cannot
 * skip the sweep.
 */
class OfflineHttpsSweepTest {

    @TempDir
    Path tempDir;

    @Test
    void everyPublicRequestMethodIsInTheSweep() {
        new RequestSweep(tempDir).assertComplete();
    }

    @Test
    void everyPublicRequestMethodSendsOverHttps() {
        RequestSweep sweep = new RequestSweep(tempDir);
        FakeApi api = new FakeApi();
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = api;
        sdk.initialize("https-sweep-key");

        List<String> failures = new ArrayList<>();
        int swept = 0;
        for (Method method : RequestSweep.requestMethods(ZeroBounceSDK.class)) {
            String signature = RequestSweep.signature(method);
            if (!sweep.sdkArgs.containsKey(signature)) {
                continue; // reported by everyPublicRequestMethodIsInTheSweep
            }
            api.clear();
            RequestSweep.Outcome outcome = sweep.invoke(sdk, method);
            if (outcome.thrown != null) {
                failures.add(signature + " threw " + outcome.thrown);
                continue;
            }
            List<FakeApi.RecordedRequest> requests = api.records();
            if (requests.isEmpty()) {
                failures.add(signature + " sent no request" + (outcome.errors.isEmpty() ? "" : ": " + outcome));
            }
            for (FakeApi.RecordedRequest request : requests) {
                if (!request.url.startsWith("https://")) {
                    failures.add(signature + " sent over plain http: " + request.url);
                }
            }
            swept++;
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertFalse(swept == 0, "nothing was swept");
        assertEquals(sweep.sdkArgs.size(), swept, "every sweep entry was invoked");
    }
}
