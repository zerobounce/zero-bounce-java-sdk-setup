package com.zerobounce;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * Warmup key handling: the key is never serialised by Gson, and a re-initialise
 * that fails leaves the client as it was.
 */
class J2ParsingWarmupStateTest {

    private static final String KEY = "warmkey-secret-7351";
    private static final String OTHER_HOST = "https://other.example.test";

    private static Object field(ZeroBounceWarmup warmup, String name) throws Exception {
        Field f = ZeroBounceWarmup.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(warmup);
    }

    /** Records the URL and API-Key header of each exchange, answering 200 with an empty object. */
    private static List<String> recordCalls(ZeroBounceWarmup warmup) throws Exception {
        List<String> calls = new ArrayList<>();
        doAnswer(invocation -> {
            String url = invocation.getArgument(1);
            @SuppressWarnings("unchecked")
            Map<String, String> headers = invocation.getArgument(4);
            calls.add(url + " key=" + headers.get("API-Key"));
            return new SdkHttp.Result(200, "application/json", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }).when(warmup).exchangeHttp(anyString(), anyString(), nullable(String.class),
                nullable(byte[].class), nullable(Map.class));
        return calls;
    }

    @Test
    void gsonNeverWritesTheWarmupKey() {
        ZeroBounceWarmup warmup = new ZeroBounceWarmup();
        warmup.initialize(KEY);
        String json = assertDoesNotThrow(() -> new Gson().toJson(warmup));
        assertFalse(json.contains(KEY), "Gson wrote the Warmup key: " + json);
    }

    @Test
    void aNullKeyOnAnInitializedClientChangesNothingAndDoesNotThrowNpe() throws Exception {
        ZeroBounceWarmup warmup = spy(new ZeroBounceWarmup());
        warmup.initialize(KEY, OTHER_HOST);
        Object keyBefore = field(warmup, "apiKey");
        Object rootBefore = field(warmup, "root");
        Object problemBefore = field(warmup, "setupProblem");

        // Reported as a bad key, not a NullPointerException from inside initialize.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> warmup.initialize(null));
        assertEquals("api_key is required", refused.getMessage());

        assertEquals(keyBefore, field(warmup, "apiKey"));
        assertEquals(rootBefore, field(warmup, "root"));
        assertEquals(problemBefore, field(warmup, "setupProblem"));
        assertNull(problemBefore);

        // The client still works with its own key and host.
        List<String> calls = recordCalls(warmup);
        AtomicReference<Object> ok = new AtomicReference<>();
        warmup.listSendingAccounts(ok::set, e -> { throw new AssertionError(e.getErrors().toString()); });
        assertEquals(List.of(OTHER_HOST + "/api/v1/warmup/sending-accounts key=" + KEY), calls);
    }

    @Test
    void aNullKeyOnAFreshClientIsRefusedBeforeAnyStateIsSet() throws Exception {
        ZeroBounceWarmup warmup = new ZeroBounceWarmup();
        assertThrows(IllegalArgumentException.class, () -> warmup.initialize(null));
        assertEquals("ZeroBounceWarmup is not initialized. Call initialize(\"API_KEY\") first",
                field(warmup, "setupProblem"));
        assertNull(field(warmup, "apiKey"));
    }

    @Test
    void aNullBaseUrlIsRefusedBeforeTheKeyIsStored() throws Exception {
        ZeroBounceWarmup warmup = new ZeroBounceWarmup();
        warmup.initialize(KEY, OTHER_HOST);
        List<String> failures = new ArrayList<>();
        try {
            warmup.initialize("replacement-key-99", null);
        } catch (IllegalArgumentException expected) {
            // refused
        } catch (RuntimeException other) {
            failures.add("threw " + other);
        }
        if (!KEY.equals(field(warmup, "apiKey"))) {
            failures.add("key replaced");
        }
        assertEquals(List.of(), failures);
        assertTrue(OTHER_HOST.equals(((String) field(warmup, "root")).replace("/api/v1/warmup", "")));
    }
}
