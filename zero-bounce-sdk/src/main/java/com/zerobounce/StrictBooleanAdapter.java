package com.zerobounce;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Reads a boolean the way every ZeroBounce SDK does: {@code true}/{@code false},
 * the strings {@code "true"}/{@code "false"} in any case, and {@code 1}/{@code 0}
 * as a number or a string. Anything else is an error that names the field,
 * rather than Gson's default, which reads {@code "yes"} and {@code "1"} as false
 * and fails on a bare {@code 1} with a parser trace.
 */
final class StrictBooleanAdapter extends TypeAdapter<Boolean> {

    @Override
    public void write(JsonWriter out, Boolean value) throws IOException {
        out.value(value);
    }

    @Override
    public Boolean read(JsonReader in) throws IOException {
        String path = in.getPath();
        JsonToken token = in.peek();
        switch (token) {
            case NULL:
                in.nextNull();
                return null;
            case BOOLEAN:
                return in.nextBoolean();
            case STRING:
            case NUMBER:
                String text = in.nextString();
                Boolean parsed = fromText(text);
                if (parsed == null) {
                    throw invalid(path, token == JsonToken.STRING ? "\"" + text + "\"" : text);
                }
                return parsed;
            default:
                in.skipValue();
                throw invalid(path, token == JsonToken.BEGIN_ARRAY ? "an array" : "an object");
        }
    }

    /** The same rules for a value already read into a tree. */
    static @Nullable Boolean fromElement(@Nullable JsonElement element, String field) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            Boolean parsed = fromText(primitive.getAsString());
            if (parsed != null) {
                return parsed;
            }
            throw invalid(field, primitive.isString() ? "\"" + primitive.getAsString() + "\"" : primitive.getAsString());
        }
        throw invalid(field, element.isJsonArray() ? "an array" : "an object");
    }

    private static @Nullable Boolean fromText(String text) {
        String value = text.trim();
        if (value.equalsIgnoreCase("true") || value.equals("1")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equals("0")) {
            return false;
        }
        return null;
    }

    private static JsonParseException invalid(String path, String value) {
        String field = path.startsWith("$.") ? path.substring(2) : path;
        // Cut at a code point, so a surrogate pair is never split (P3).
        String shown = value.codePointCount(0, value.length()) > 40
                ? value.substring(0, value.offsetByCodePoints(0, 40)) + "..." : value;
        return new JsonParseException("ZeroBounce returned " + shown + " for " + field + ", which is not a boolean.");
    }
}
