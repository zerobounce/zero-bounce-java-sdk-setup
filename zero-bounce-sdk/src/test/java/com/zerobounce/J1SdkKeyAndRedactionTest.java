package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The API key is refused when Unicode whitespace surrounds it (a no-break space,
 * an ideographic space, a line or paragraph separator), as it is for a control
 * character; and a secret a server echoes back in another letter case is redacted.
 */
class J1SdkKeyAndRedactionTest {

    private static final String REFUSED = "[api_key has surrounding whitespace or control characters]";

    @Test
    void aKeyWithUnicodeWhitespaceAroundItIsRefusedWithoutARequest() {
        String[] keys = {" abc", "abc ", " abc", "abc ", "　abc", "abc　",
                " abc", "abc ", " abc", " abc"};
        List<String> failures = new ArrayList<>();
        for (String key : keys) {
            AtomicInteger requests = new AtomicInteger();
            ZeroBounceSDK sdk = new ZeroBounceSDK();
            sdk.transport = request -> {
                requests.incrementAndGet();
                return new SdkHttp.Result(200, "application/json", "{\"Credits\":\"5\"}".getBytes(StandardCharsets.UTF_8));
            };
            sdk.initialize(key);
            AtomicReference<ErrorResponse> error = new AtomicReference<>();
            sdk.getCredits(value -> failures.add("sent " + key.codePoints().mapToObj(Integer::toHexString).toList()),
                    error::set);
            if (error.get() == null || !REFUSED.equals(error.get().getErrors().toString())) {
                failures.add("not refused: " + key.codePoints().mapToObj(Integer::toHexString).toList()
                        + " " + error.get());
            }
            if (requests.get() != 0) {
                failures.add("a request was sent with " + key.codePoints().mapToObj(Integer::toHexString).toList());
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void aKeyWithoutSurroundingWhitespaceIsStillAccepted() {
        AtomicInteger requests = new AtomicInteger();
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> {
            requests.incrementAndGet();
            return new SdkHttp.Result(200, "application/json", "{\"Credits\":\"5\"}".getBytes(StandardCharsets.UTF_8));
        };
        sdk.initialize("plain-key");
        AtomicReference<ZBCreditsResponse> value = new AtomicReference<>();
        sdk.getCredits(value::set, e -> { throw new AssertionError(e.getErrors().toString()); });
        assertNotNull(value.get());
        assertEquals(1, requests.get());
    }

    @Test
    void aSecretEchoedInAnotherLetterCaseIsRedacted() {
        String secret = "Sec4tKey-123";
        String echoed = "unsupported content-encoding: sec4tkey-123 and SEC4TKEY-123";
        String redacted = ZeroBounceSDK.redact(echoed, secret);
        assertFalse(redacted.toLowerCase(java.util.Locale.ROOT).contains("sec4tkey-123"), redacted);
        assertTrue(redacted.contains("***"), redacted);
        assertEquals("unsupported content-encoding: *** and ***", redacted);
    }

    @Test
    void aCaseFoldedSecretInAnErrorBodyNeverReachesTheCallback() {
        String secret = "Sec4tKey-123";
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> new SdkHttp.Result(400, "application/json",
                "{\"error\":\"bad value: sec4tkey-123\"}".getBytes(StandardCharsets.UTF_8));
        sdk.initialize(secret);
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        sdk.getCredits(value -> { throw new AssertionError("success"); }, error::set);
        assertNotNull(error.get());
        assertFalse(error.get().getErrors().toString().toLowerCase(java.util.Locale.ROOT).contains("sec4tkey-123"),
                error.get().getErrors().toString());
    }
}
