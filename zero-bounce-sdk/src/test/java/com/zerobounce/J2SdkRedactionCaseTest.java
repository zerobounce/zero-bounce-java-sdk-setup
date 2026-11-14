package com.zerobounce;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A secret a server echoes back in lower case, after being URL-encoded and
 * re-encoded, is still caught by the decoded-view check: the view comparison
 * ignores case, as the first-layer replacement does.
 */
class J2SdkRedactionCaseTest {

    // Decoded three times the echo is "qzj2+k*y/sec=0%a", the key in lower case.
    private static final String KEY = "Qzj2+K*y/Sec=0%a";
    private static final String ECHO = "qzj2%252bk*y%252fsec%253d0%2525a";

    @Test
    void aMixedCaseKeyEchoedInLowerCaseAndEncodedTwiceIsCaught() {
        assertTrue(ZeroBounceSDK.exposesSecret(ECHO, KEY), "decoded view with a different letter case was not caught");
    }

    @Test
    void redactDropsTheWholeTextForThatEcho() {
        assertEquals(ZeroBounceSDK.CREDENTIAL_OMITTED,
                ZeroBounceSDK.redact("server said: " + ECHO, KEY));
    }
}
