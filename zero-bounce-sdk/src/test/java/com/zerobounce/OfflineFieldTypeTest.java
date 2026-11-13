package com.zerobounce;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field types are part of the shape (check E7, standard.md section 3): on a typed
 * call, a field that arrives with a JSON type other than the documented one is an
 * API error with status 200, never coerced ({@code true} read as {@code "true"}),
 * defaulted or dropped. {@code getCredits} needs {@code Credits} as a whole
 * number (a JSON string or a JSON integer), {@code validate} and every
 * {@code validateBatch} row need {@code address} and {@code status} as strings,
 * and a JSON array is not an object. Every example response still decodes.
 */
class OfflineFieldTypeTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "field-type-key";
    private static final FieldNamingPolicy NAMES = FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

    /** The documented example responses, by the success type of the calls they answer. */
    private static Map<Class<?>, List<String>> documented() throws Exception {
        Map<String, Class<?>> kinds = Map.ofEntries(
                Map.entry("credits", ZBCreditsResponse.class),
                Map.entry("apiusage", ZBGetApiUsageResponse.class),
                Map.entry("activity", ZBActivityDataResponse.class),
                Map.entry("validate", ZBValidateResponse.class),
                Map.entry("validatebatch", ZBValidateBatchResponse.class),
                Map.entry("sendfile", ZBSendFileResponse.class),
                Map.entry("filestatus", ZBFileStatusResponse.class),
                Map.entry("deletefile", ZBDeleteFileResponse.class),
                Map.entry("guessformat", ZBEmailFinderResponse.class),
                Map.entry("findemail", ZBFindEmailResponse.class),
                Map.entry("domainsearch", ZBFindDomainResponse.class));
        Map<Class<?>, List<String>> bodies = new LinkedHashMap<>();
        try (InputStream in = OfflineFieldTypeTest.class.getResourceAsStream("/documented-responses.tsv")) {
            assertNotNull(in, "documented-responses.tsv is on the test classpath");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t", 2);
                Class<?> type = kinds.get(fields[0]);
                assertNotNull(type, "unknown kind " + fields[0]);
                bodies.computeIfAbsent(type, k -> new ArrayList<>())
                        .add(JsonParser.parseString(fields[1]).getAsString());
            }
        }
        assertEquals(kinds.size(), bodies.size(), "every kind has an example");
        return bodies;
    }

    private static boolean isDownload(Method method) {
        String name = method.getName().toLowerCase(Locale.ROOT);
        return name.contains("getfile") || name.startsWith("download");
    }

    /** The type a request method hands its success callback. */
    private static Type successType(Method method) {
        for (Type type : method.getGenericParameterTypes()) {
            if (type instanceof ParameterizedType
                    && ((ParameterizedType) type).getRawType() == ZeroBounceSDK.OnSuccessCallback.class) {
                return ((ParameterizedType) type).getActualTypeArguments()[0];
            }
        }
        throw new AssertionError(method + " has no success callback");
    }

    /** Every typed request method: SDK calls that are not downloads and do not return raw JSON. */
    private static List<Method> typedMethods() {
        List<Method> typed = new ArrayList<>();
        for (Method method : RequestSweep.allRequestMethods()) {
            if (method.getDeclaringClass() != ZeroBounceSDK.class || isDownload(method)) {
                continue;
            }
            Type type = successType(method);
            if (type instanceof Class && JsonElement.class.isAssignableFrom((Class<?>) type)) {
                continue;
            }
            typed.add(method);
        }
        assertTrue(typed.size() > 40, "typed calls: " + typed.size());
        return typed;
    }

    private interface Check {
        void check(Method method, String body, RequestSweep.Outcome outcome);
    }

    /** Answers {@code body} to each of {@code methods} and hands each outcome to {@code check}. */
    private void run(List<Method> methods, String body, Check check) throws Exception {
        Path dir = Files.createTempDirectory(tempDir, "run");
        RequestSweep sweep = new RequestSweep(dir);
        try (LoopbackServer server = new LoopbackServer(
                LoopbackServer.answer(200, "application/json", body.getBytes(StandardCharsets.UTF_8)))) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            try (MockedStatic<HttpClients> ignored = server.apacheClients()) {
                for (Method method : methods) {
                    server.clear();
                    check.check(method, body, sweep.invoke(sdk, method));
                }
            }
        }
    }

    private static Check expectError(List<String> failures) {
        return (method, body, outcome) -> {
            if (outcome.thrown != null || !outcome.successes.isEmpty() || outcome.errors.size() != 1
                    || outcome.errors.get(0).getStatusCode() != 200) {
                failures.add(body + " " + outcome);
            }
        };
    }

    private static Check expectSuccess(List<String> failures) {
        return (method, body, outcome) -> {
            if (outcome.thrown != null || outcome.successes.size() != 1 || !outcome.errors.isEmpty()) {
                failures.add(body + " " + outcome);
            }
        };
    }

    /** The JSON name Gson reads a field from. */
    private static String jsonName(Field field) {
        SerializedName name = field.getAnnotation(SerializedName.class);
        return name != null ? name.value() : NAMES.translateName(field);
    }

    private static List<Field> modelFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static boolean isModel(Class<?> type) {
        return type.getPackageName().equals("com.zerobounce") && !type.isEnum();
    }

    /**
     * JSON values of the wrong type for a field of {@code type}, each as a whole
     * value for the field. A model type also gets each of its own fields wrong,
     * one at a time, inside an otherwise empty object.
     */
    private static List<JsonElement> wrongValues(Type type, Field field) {
        Class<?> raw = type instanceof ParameterizedType
                ? (Class<?>) ((ParameterizedType) type).getRawType() : (Class<?>) type;
        List<String> wrong = new ArrayList<>();
        if (raw == String.class) {
            wrong.addAll(List.of("true", "[]", "[5]", "{}"));
            // A score is a number in the API; the SDK keeps it as text, so a number is not wrong there.
            if (field == null || field.getAnnotation(JsonAdapter.class) == null) {
                wrong.add("5");
            }
        } else if (raw.isEnum()) {
            wrong.addAll(List.of("5", "true", "[]", "{}"));
        } else if (raw == Boolean.class || raw == boolean.class) {
            wrong.addAll(List.of("\"yes\"", "[]", "{}"));
        } else if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class
                || raw == double.class || raw == Double.class) {
            wrong.addAll(List.of("true", "\"abc\"", "[]", "{}"));
        } else if (raw == Date.class) {
            wrong.addAll(List.of("true", "5", "[]", "{}"));
        } else if (List.class.isAssignableFrom(raw)) {
            wrong.addAll(List.of("5", "\"x\"", "true", "{}"));
        } else if (Map.class.isAssignableFrom(raw) || isModel(raw)) {
            wrong.addAll(List.of("5", "\"x\"", "true", "[]"));
        } else {
            throw new AssertionError("no wrong values for " + type + (field == null ? "" : " (" + field + ")"));
        }
        List<JsonElement> values = new ArrayList<>();
        for (String text : wrong) {
            values.add(JsonParser.parseString(text));
        }
        if (List.class.isAssignableFrom(raw)) {
            Type element = ((ParameterizedType) type).getActualTypeArguments()[0];
            for (JsonElement inner : wrongValues(element, null)) {
                JsonArray array = new JsonArray();
                array.add(inner);
                values.add(array);
            }
            if (element instanceof Class && isModel((Class<?>) element)) {
                for (JsonElement inner : nestedWrongObjects((Class<?>) element)) {
                    JsonArray array = new JsonArray();
                    array.add(inner);
                    values.add(array);
                }
            }
        } else if (isModel(raw)) {
            values.addAll(nestedWrongObjects(raw));
        }
        return values;
    }

    private static List<JsonElement> nestedWrongObjects(Class<?> model) {
        List<JsonElement> values = new ArrayList<>();
        for (Field inner : modelFields(model)) {
            for (JsonElement value : wrongValues(inner.getGenericType(), inner)) {
                JsonObject object = new JsonObject();
                object.add(jsonName(inner), value);
                values.add(object);
            }
        }
        return values;
    }

    /**
     * Complete by construction: every field of every typed call's result type
     * (found by reflection, nested rows included) is sent once with each JSON type
     * it does not have, inside a body that is otherwise a valid answer, and every
     * typed call reports one error with status 200.
     */
    @Test
    void aFieldOfTheWrongJsonTypeIsAnErrorWithStatus200OnEveryTypedCall() throws Exception {
        Map<Class<?>, List<String>> documented = documented();
        Map<Type, List<Method>> byType = new LinkedHashMap<>();
        for (Method method : typedMethods()) {
            byType.computeIfAbsent(successType(method), k -> new ArrayList<>()).add(method);
        }
        List<String> failures = new ArrayList<>();
        int bodies = 0;
        for (Map.Entry<Type, List<Method>> entry : byType.entrySet()) {
            Type type = entry.getKey();
            boolean list = type instanceof ParameterizedType
                    && ((ParameterizedType) type).getRawType() == List.class;
            Class<?> model = (Class<?>) (list ? ((ParameterizedType) type).getActualTypeArguments()[0] : type);
            String baseText = documented.containsKey(model) ? documented.get(model).get(0) : "{}";
            // The base alone is a valid answer, so each failure below is the wrong field's.
            run(entry.getValue(), list ? "[" + baseText + "]" : baseText, expectSuccess(failures));
            for (Field field : modelFields(model)) {
                for (JsonElement value : wrongValues(field.getGenericType(), field)) {
                    if (model == ZBSendFileResponse.class && field.getName().equals("message")
                            && value.isJsonArray() && value.getAsJsonArray().isEmpty()) {
                        // An upload's message may be an array of strings (an empty one is fine);
                        // [true] and [5] still are not.
                        continue;
                    }
                    JsonObject body = JsonParser.parseString(baseText).getAsJsonObject();
                    body.add(jsonName(field), value);
                    String text = list ? "[" + body + "]" : body.toString();
                    run(entry.getValue(), text, expectError(failures));
                    bodies++;
                }
            }
        }
        assertTrue(bodies > 300, "wrong-typed bodies sent: " + bodies);
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /** The bodies check E7 names, on getCredits and validate. */
    @Test
    void creditsAndValidateNeedTheirFieldsAsStrings() throws Exception {
        List<Method> credits = new ArrayList<>();
        List<Method> validate = new ArrayList<>();
        for (Method method : typedMethods()) {
            if (successType(method) == ZBCreditsResponse.class) {
                credits.add(method);
            } else if (successType(method) == ZBValidateResponse.class) {
                validate.add(method);
            }
        }
        assertEquals(1, credits.size(), "getCredits");
        assertTrue(validate.size() >= 2, "validate overloads: " + validate);
        List<String> failures = new ArrayList<>();
        for (String body : List.of("{}", "{\"Credits\":null}", "{\"Credits\":true}", "{\"Credits\":5.5}",
                "{\"Credits\":5.0}", "{\"Credits\":1e3}", "{\"Credits\":\"abc\"}", "{\"Credits\":\"5.0\"}",
                "{\"Credits\":\"\"}", "{\"Credits\":1.5}", "{\"Credits\":[\"5\"]}", "{\"Credits\":[5]}",
                "{\"Credits\":{}}")) {
            run(credits, body, expectError(failures));
        }
        for (String body : List.of("{\"address\":true,\"status\":\"valid\"}",
                "{\"address\":\"a@b.c\",\"status\":5}", "{\"address\":\"a@b.c\"}", "{\"status\":\"valid\"}",
                "{\"address\":null,\"status\":\"valid\"}", "{\"address\":\"a@b.c\",\"status\":null}",
                "{\"address\":\"a@b.c\",\"status\":\"valid\",\"sub_status\":7}", "{}")) {
            run(validate, body, expectError(failures));
        }
        // Credits is an integer sent as a JSON string (the live API) or as a JSON
        // integer (zerobounce.net's own example, {"Credits":2375323}).
        for (String body : List.of("{\"Credits\":\"-1\"}", "{\"Credits\":\"12345\"}", "{\"Credits\":2375323}",
                "{\"Credits\":-1}", "{\"Credits\":0}")) {
            run(credits, body, expectSuccess(failures));
        }
        // Every validatebatch row needs address and status as strings, and is an object.
        List<Method> batch = new ArrayList<>();
        for (Method method : typedMethods()) {
            if (successType(method) == ZBValidateBatchResponse.class) {
                batch.add(method);
            }
        }
        assertTrue(!batch.isEmpty(), "validateBatch overloads");
        for (String row : List.of("{\"status\":\"valid\"}", "{\"address\":\"a@b.c\"}", "{}",
                "{\"address\":null,\"status\":\"valid\"}", "{\"address\":\"a@b.c\",\"status\":null}",
                "{\"address\":true,\"status\":\"valid\"}", "{\"address\":\"a@b.c\",\"status\":5}",
                "{\"Message\":\"Greylist validation still in progress.\"}", "5", "null", "\"x\"", "[]")) {
            run(batch, "{\"email_batch\":[" + row + "]}", expectError(failures));
            run(batch, "{\"email_batch\":[{\"address\":\"ok@b.c\",\"status\":\"valid\"}," + row + "]}",
                    expectError(failures));
        }
        run(batch, "{\"email_batch\":[{\"address\":\"a@b.c\",\"status\":\"valid\"}]}", expectSuccess(failures));
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * A JSON array where the call's result is an object (check E7): an empty
     * array, an array of one string and a positional array (values in field
     * order) are an error with status 200 on every typed call. A call whose
     * result is a list accepts the empty array, and still rejects the others.
     */
    @Test
    void anArrayInPlaceOfAnObjectIsAnErrorWithStatus200OnEveryTypedCall() throws Exception {
        List<Method> objects = new ArrayList<>();
        List<Method> lists = new ArrayList<>();
        for (Method method : typedMethods()) {
            Type type = successType(method);
            boolean list = type instanceof ParameterizedType && ((ParameterizedType) type).getRawType() == List.class;
            (list ? lists : objects).add(method);
        }
        List<String> failures = new ArrayList<>();
        for (String body : List.of("[]", "[\"x\"]", "[\"a@example.com\",10]", "[true,\"3\"]",
                "[\"a@example.com\",\"valid\"]", "[[]]", "[{}]")) {
            run(objects, body, expectError(failures));
        }
        for (String body : List.of("[\"x\"]", "[\"a@example.com\",10]", "[true,\"3\"]", "[[]]", "[5]")) {
            run(lists, body, expectError(failures));
        }
        run(lists, "[]", expectSuccess(failures));
        assertTrue(objects.size() > 40, "object calls: " + objects.size());
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * An upload's {@code message} may be an array of strings: every item must be
     * a string, not only the first one the SDK exposes.
     */
    @Test
    void everyItemOfAnUploadMessageArrayIsAString() throws Exception {
        List<Method> uploads = new ArrayList<>();
        for (Method method : typedMethods()) {
            if (successType(method) == ZBSendFileResponse.class) {
                uploads.add(method);
            }
        }
        assertTrue(uploads.size() >= 4, "uploads: " + uploads);
        List<String> failures = new ArrayList<>();
        for (String message : List.of("[5]", "[\"ok\",5]", "[\"ok\",null]", "[\"ok\",true]", "[\"ok\",{}]",
                "[\"ok\",[]]")) {
            run(uploads, "{\"success\":true,\"message\":" + message + ",\"file_id\":\"x\"}", expectError(failures));
        }
        run(uploads, "{\"success\":true,\"message\":[\"ok\",\"also\"],\"file_id\":\"x\"}", expectSuccess(failures));
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /** Every documented example response still decodes on every call that returns its type (ACCEPT). */
    @Test
    void everyDocumentedExampleResponseStillDecodes() throws Exception {
        Map<Class<?>, List<String>> documented = documented();
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<Class<?>, List<String>> entry : documented.entrySet()) {
            List<Method> methods = new ArrayList<>();
            for (Method method : typedMethods()) {
                if (successType(method) == entry.getKey()) {
                    methods.add(method);
                }
            }
            assertTrue(!methods.isEmpty(), "a call returns " + entry.getKey());
            for (String body : entry.getValue()) {
                run(methods, body, expectSuccess(failures));
                checked++;
            }
        }
        assertTrue(checked >= 20, "documented bodies: " + checked);
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * Dead key on a 200 (check E2) on the multipart uploads too: {@code error},
     * {@code error_message} and {@code Error} each reach the error callback with
     * status 200 on every SDK call, never the success callback. Each key is
     * swept on its own, so removing the check for any one of them fails here.
     */
    @Test
    void aDeadKeyBodyIsAnErrorWithStatus200OnEveryCall() throws Exception {
        List<Method> methods = new ArrayList<>();
        for (Method method : RequestSweep.allRequestMethods()) {
            if (method.getDeclaringClass() == ZeroBounceSDK.class && !isDownload(method)) {
                methods.add(method);
            }
        }
        List<String> failures = new ArrayList<>();
        for (String key : List.of("error", "error_message", "Error")) {
            String body = "{\"" + key + "\":\"Invalid API key or your account ran out of credits\"}";
            run(methods, body, expectError(failures));
        }
        // An error list holding a number is still an error with its status, never a
        // ClassCastException thrown out of the call.
        run(methods, "{\"error\":[5]}", expectError(failures));
        assertTrue(methods.stream().filter(m -> m.getName().toLowerCase(Locale.ROOT).contains("sendfile")
                || m.getName().toLowerCase(Locale.ROOT).contains("file")).count() >= 10, "uploads swept");
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }
}
