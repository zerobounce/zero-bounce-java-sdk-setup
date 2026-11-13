package com.zerobounce;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards in HttpMessages that had no test: the exponent and number shapes of
 * the strict JSON reader, the 1000-character cap on non-JSON error text, the
 * download JSON rule, the JSON media-type rule and the message-only rule.
 */
class J1ParsingHttpMessagesTest {

    /** A JSON object of exactly {@code length} characters with one long string value. */
    private static String jsonOfLength(int length) {
        String prefix = "{\"x\":\"";
        String suffix = "\"}";
        return prefix + "a".repeat(length - prefix.length() - suffix.length()) + suffix;
    }

    private static final String FIXED_NOT_JSON = "ZeroBounce returned a response that is not valid JSON.";

    @Test
    void exponentsNeedDigitsAfterThemAndNumbersNeedDigitsAroundTheDot() {
        List<String> rejected = List.of("1e", "1e+", "1E-", "1E+", "-", "1.", ".5", "-.5", "01", "+1", "1.e5", "1e5.5", "--1");
        for (String text : rejected) {
            assertFalse(HttpMessages.isStrictJson(text), "must reject [" + text + "]");
        }
        List<String> accepted = List.of("1e+5", "1E-5", "0.5e10", "-0", "0", "-12.25E+3", "10");
        for (String text : accepted) {
            assertTrue(HttpMessages.isStrictJson(text), "must accept [" + text + "]");
        }
    }

    @Test
    void aJsonErrorBodyOfMoreThan1000CharactersIsNotEchoed() {
        assertEquals(jsonOfLength(1000), HttpMessages.jsonCallFailure(500, jsonOfLength(1000), null),
                "1000 characters is shown as is");
        assertEquals(FIXED_NOT_JSON, HttpMessages.jsonCallFailure(500, jsonOfLength(1001), null),
                "1001 characters is the fixed message");
    }

    @Test
    void aNonJsonErrorTextOfMoreThan1000CharactersIsNotEchoed() {
        String at1000 = "e".repeat(1000);
        assertEquals(at1000, HttpMessages.jsonCallFailure(500, at1000, "text/plain"));
        assertEquals(FIXED_NOT_JSON, HttpMessages.jsonCallFailure(500, "e".repeat(1001), "text/plain"));
    }

    @Test
    void aStrictJsonBodyOfMoreThan1000CharactersIsShortForThePreferShortMessageChoice() {
        assertFalse(HttpMessages.preferShortMessage(jsonOfLength(1000)), "1000 characters keeps the body");
        assertTrue(HttpMessages.preferShortMessage(jsonOfLength(1001)), "1001 characters prefers the short message");
    }

    @Test
    void anyJsonObjectOrArrayIsAJsonDownloadError() {
        List<String> json = List.of("{}", "[]", "{\"status\":\"error\"}", "[{\"a\":1}]", "{\"success\":false}",
                " \r\n{\"x\":1}\n", "{\"success\":true,\"data\":[]}");
        for (String body : json) {
            assertTrue(HttpMessages.downloadJsonError(body), "[" + body + "]");
        }
        List<String> notJson = List.of("seed,provider\na@b.com,gmail\n", "[abc]", "{bad", "", "12", "\"text\"",
                "{}x", "a,b\n{}\n");
        for (String body : notJson) {
            assertFalse(HttpMessages.downloadJsonError(body), "[" + body + "]");
        }
    }

    @Test
    void aJsonMediaTypeIsApplicationJsonOrAnyPlusJsonSuffix() {
        List<String> json = List.of("application/json", "application/json; charset=UTF-8", "APPLICATION/JSON",
                "application/problem+json", "application/vnd.api+json", "application/ld+json; charset=utf-8");
        for (String type : json) {
            assertTrue(HttpMessages.isJsonMediaType(type), type);
        }
        List<String> notJson = List.of("text/csv", "text/plain", "", "application/jsonp", "application/octet-stream", "text/json-ish");
        for (String type : notJson) {
            assertFalse(HttpMessages.isJsonMediaType(type), type);
        }
        assertFalse(HttpMessages.isJsonMediaType(null));
    }

    @Test
    void aMessageOnlyBodyIsAnErrorWhoseTextIsTheFirstKeyWithText() {
        assertEquals("a\nb", HttpMessages.messageOnlyFailure("{\"message\":[\"a\",\"b\"]}"));
        assertEquals("Invalid API key", HttpMessages.messageOnlyFailure("{\"Message\":\"Invalid API key\"}"));
        assertEquals("Bad domain", HttpMessages.messageOnlyFailure("{\"message\":\"\",\"error\":\"Error: Bad domain\"}"));
        assertEquals("ZeroBounce returned an error with no message text.",
                HttpMessages.messageOnlyFailure("{\"detail\":null}"));
    }

    @Test
    void aBodyThatIsNotOnlyMessageKeysOrIsNotStrictJsonIsNotMessageOnly() {
        assertNull(HttpMessages.messageOnlyFailure("{\"message\":\"x\",\"success\":true}"));
        assertNull(HttpMessages.messageOnlyFailure("{\"message\":\"x\",\"data\":1}"));
        assertNull(HttpMessages.messageOnlyFailure("{message:\"x\"}"), "lenient Gson input is not JSON");
        assertNull(HttpMessages.messageOnlyFailure("{}"));
        assertNull(HttpMessages.messageOnlyFailure("[\"x\"]"));
    }

    @Test
    void validateGreylistWithOnlyTheMessageKeyIsAResult() {
        String greylist = "{\"Message\":\"Greylist validation still in progress...\"}";
        assertNull(HttpMessages.messageOnlyFailure(greylist, true));
        assertNull(HttpMessages.successPayloadError(greylist, true));
        assertEquals("Greylist validation still in progress...", HttpMessages.messageOnlyFailure(greylist, false));
        assertEquals("Greylist validation still in progress...", HttpMessages.successPayloadError(greylist, false));
        assertEquals("Greylist validation still in progress...", HttpMessages.successPayloadError(greylist));
        assertEquals("Bad", HttpMessages.messageOnlyFailure("{\"error\":\"Bad\",\"Message\":\"Greylist\"}", true));
    }
}
