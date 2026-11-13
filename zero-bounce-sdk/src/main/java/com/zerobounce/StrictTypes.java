package com.zerobounce;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Field types are part of the shape (check E7): a typed result's field that
 * arrives with another JSON type is an API error with status 200, never coerced
 * ({@code true} read as {@code "true"}), defaulted or dropped. {@code null} is
 * accepted for an optional field. Numbers keep Gson's reading (a number, or a
 * string holding one, as the documented {@code "active_in_days": "180"}); a
 * boolean, an array or an object is already an error there.
 */
final class StrictTypes {

    private StrictTypes() {
    }

    private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

    /** A string field: a JSON string or null, nothing else. */
    static final TypeAdapter<String> STRING = new TypeAdapter<String>() {
        @Override
        public void write(JsonWriter out, String value) throws IOException {
            out.value(value);
        }

        @Override
        public String read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            if (token == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (token == JsonToken.STRING) {
                return in.nextString();
            }
            String path = in.getPath();
            in.skipValue();
            throw wrongType(path, token, "a string");
        }
    };

    /**
     * A field the API sends as a number that this SDK has always exposed as text
     * (the AI score): a JSON number or string, kept as its text; a boolean, an
     * array or an object is an error.
     */
    static final class TextOrNumber extends TypeAdapter<String> {
        @Override
        public void write(JsonWriter out, String value) throws IOException {
            out.value(value);
        }

        @Override
        public String read(JsonReader in) throws IOException {
            JsonToken token = in.peek();
            if (token == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (token == JsonToken.STRING || token == JsonToken.NUMBER) {
                return in.nextString();
            }
            String path = in.getPath();
            in.skipValue();
            throw wrongType(path, token, "a number");
        }
    }

    /**
     * Enums (status, sub-status): a JSON string or null; Gson's own reading of the
     * string is kept. Maps (batch error rows): a JSON object or null, not the
     * array of pairs Gson would otherwise also read as a map.
     */
    static final TypeAdapterFactory ENUMS = new TypeAdapterFactory() {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            boolean isEnum = type.getRawType().isEnum();
            if (!isEnum && !java.util.Map.class.isAssignableFrom(type.getRawType())) {
                return null;
            }
            JsonToken wanted = isEnum ? JsonToken.STRING : JsonToken.BEGIN_OBJECT;
            String expected = isEnum ? "a string" : "an object";
            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    delegate.write(out, value);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    JsonToken token = in.peek();
                    if (token != wanted && token != JsonToken.NULL) {
                        String path = in.getPath();
                        in.skipValue();
                        throw wrongType(path, token, expected);
                    }
                    return delegate.read(in);
                }
            };
        }
    };

    /** The same rule for a value already read into a tree. */
    static @Nullable String text(@Nullable JsonElement element, String field) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        throw new JsonParseException("ZeroBounce returned " + kind(element) + " for " + field
                + ", which is not a string.");
    }

    /**
     * The fields a call cannot answer without: getCredits' {@code Credits} is a
     * whole number, sent as a JSON string ({@code "-1"}, {@code "12345"}) or a
     * JSON integer ({@code 2375323}); validate's {@code address} and
     * {@code status} are strings (except in the greylist answer, which carries
     * {@code Message} instead of a result); and every validatebatch row is an
     * object with {@code address} and {@code status} as strings.
     *
     * @return why {@code body} is not a {@code type} result, or null
     */
    static @Nullable String missingField(Class<?> type, String body) {
        if (type != ZBCreditsResponse.class && type != ZBValidateResponse.class
                && type != ZBValidateBatchResponse.class) {
            return null;
        }
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) {
            return null;
        }
        JsonObject object = parsed.getAsJsonObject();
        if (type == ZBCreditsResponse.class) {
            JsonElement credits = object.get("Credits");
            if (credits == null || !credits.isJsonPrimitive()
                    || !INTEGER.matcher(credits.getAsString()).matches()) {
                return "ZeroBounce returned " + (credits == null ? "no value" : kind(credits))
                        + " for Credits, which is not a whole number.";
            }
            return null;
        }
        if (type == ZBValidateBatchResponse.class) {
            JsonElement rows = object.get("email_batch");
            if (rows == null || !rows.isJsonArray()) {
                return null;
            }
            for (JsonElement row : rows.getAsJsonArray()) {
                if (!row.isJsonObject()) {
                    return "ZeroBounce returned " + kind(row) + " for a row of email_batch, which is not an object.";
                }
                String missing = requireStrings(row.getAsJsonObject(), "email_batch row ");
                if (missing != null) {
                    return missing;
                }
            }
            return null;
        }
        if (object.has("Message")) {
            // The greylist answer: a result that may carry nothing but Message
            // (or the address with it) while the retry is still running.
            return null;
        }
        return requireStrings(object, "");
    }

    /** Why {@code object} lacks {@code address} or {@code status} as a string, or null. */
    private static @Nullable String requireStrings(JsonObject object, String where) {
        for (String field : new String[]{"address", "status"}) {
            JsonElement value = object.get(field);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return "ZeroBounce returned " + (value == null ? "no value" : kind(value)) + " for " + where + field
                        + ", which is not a string.";
            }
        }
        return null;
    }

    private static String kind(JsonElement element) {
        if (element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonArray()) {
            return "an array";
        }
        if (element.isJsonObject()) {
            return "an object";
        }
        if (element.getAsJsonPrimitive().isBoolean()) {
            return "a boolean";
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            return "a number";
        }
        return "a string";
    }

    private static JsonParseException wrongType(String path, JsonToken token, String expected) {
        String field = path.startsWith("$.") ? path.substring(2) : path;
        String found;
        switch (token) {
            case BOOLEAN:
                found = "a boolean";
                break;
            case NUMBER:
                found = "a number";
                break;
            case BEGIN_ARRAY:
                found = "an array";
                break;
            case BEGIN_OBJECT:
                found = "an object";
                break;
            default:
                found = "a string";
        }
        return new JsonParseException("ZeroBounce returned " + found + " for " + field
                + ", which is not " + expected + ".");
    }
}
