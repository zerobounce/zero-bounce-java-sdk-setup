package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Warmup key padded with Unicode whitespace is refused, as the main client refuses it. */
class J1WarmupKeyWhitespaceTest {

    @Test
    void unicodeWhitespaceAroundTheWarmupKeyIsRefused() throws Exception {
        List<String> accepted = new ArrayList<>();
        for (char c : new char[]{' ', ' ', ' ', '　', ' ', '\t'}) {
            for (String key : new String[]{c + "warmkey1234", "warmkey1234" + c}) {
                ZeroBounceWarmup warmup = new ZeroBounceWarmup();
                warmup.initialize(key);
                java.lang.reflect.Field f = ZeroBounceWarmup.class.getDeclaredField("setupProblem");
                f.setAccessible(true);
                Object problem = f.get(warmup);
                if (!"api_key has surrounding whitespace or control characters".equals(problem)) {
                    accepted.add(Integer.toHexString(c) + " -> " + problem);
                }
            }
        }
        assertTrue(accepted.isEmpty(), "accepted: " + accepted);
        java.lang.reflect.Field f = ZeroBounceWarmup.class.getDeclaredField("setupProblem");
        f.setAccessible(true);
        ZeroBounceWarmup ok = new ZeroBounceWarmup();
        ok.initialize("warmkey1234");
        assertEquals(null, f.get(ok));
    }
}
