package com.zerobounce;

import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictBooleanAdapterTest {

    /** Whether {@code text} holds half of a surrogate pair. */
    private static boolean splitsACharacter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    return true;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A long value that is not a boolean is quoted shortened, and the cut never
     * falls inside a character: here an emoji (a surrogate pair) straddles the
     * 40th UTF-16 unit of the quoted value (P3).
     */
    @Test
    void aLongValueIsCutAtACharacterBoundary() {
        String value = "a".repeat(38) + "😀" + "b".repeat(10);
        JsonParseException error = assertThrows(JsonParseException.class,
                () -> StrictBooleanAdapter.fromElement(new JsonPrimitive(value), "free_email"));
        String message = error.getMessage();
        assertTrue(message.contains("..."), message);
        assertTrue(message.contains("free_email"), message);
        assertFalse(splitsACharacter(message), message);
    }
}
