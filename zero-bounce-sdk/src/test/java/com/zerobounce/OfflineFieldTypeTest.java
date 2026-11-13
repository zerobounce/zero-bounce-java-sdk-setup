package com.zerobounce;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.apache.hc.client5.http.impl.classic.HttpClients;
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
import java.util.Set;

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

    /** JSON names a call cannot answer without (null is an error for these). */
    private static final Set<String> REQUIRED = Set.of("Credits", "address", "status");

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
            // A score is a number in the API; the SDK keeps it as text, so a whole number is not wrong there.
            if (field == null || field.getAnnotation(JsonAdapter.class) == null) {
                wrong.add("5");
            } else if (field.getAnnotation(JsonAdapter.class).value() == StrictTypes.IntegerText.class) {
                wrong.addAll(List.of("5.5", "5.0", "1e2", "\"5.9\"", "\"abc\"", "\"\"", "2147483648",
                        "\"2147483648\"", "-2147483649"));
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
                    if ((model == ZBSendFileResponse.class || model == ZBDeleteFileResponse.class
                            || model == ZBFileStatusResponse.class) && field.getName().equals("message")
                            && value.isJsonArray() && value.getAsJsonArray().isEmpty()) {
                        // A file job's message may be an array of strings (an empty one is fine);
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
     * {@code "success"} is a JSON boolean. The string {@code "false"} (any case) is not
     * a failure report read as false, nor a success: on every ZeroBounceSDK request
     * method, JSON and download alike, it is an error with status 200 (standard.md
     * section 3, E4). The Warmup client has its own test.
     */
    @Test
    void aSuccessStringFalseIsAnErrorWithStatus200OnEveryRequestMethod() throws Exception {
        List<Method> methods = new ArrayList<>();
        for (Method method : RequestSweep.allRequestMethods()) {
            if (method.getDeclaringClass() == ZeroBounceSDK.class) {
                methods.add(method);
            }
        }
        assertTrue(methods.size() > 40, "request methods: " + methods.size());
        List<String> failures = new ArrayList<>();
        for (String body : List.of("{\"success\":\"false\",\"message\":\"nope\"}",
                "{\"success\":\"False\",\"message\":\"nope\"}", "{\"success\":\"false\"}",
                "{\"success\":\"FALSE\",\"error\":\"nope\"}")) {
            run(methods, body, expectError(failures));
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * An upload's message may be an array of strings: the SDK joins the items with a
     * newline into its one string (standard.md section 3). An empty array is an empty message.
     */
    @Test
    void anUploadMessageListIsJoinedWithNewlines() throws Exception {
        List<Method> uploads = new ArrayList<>();
        for (Method method : typedMethods()) {
            if (successType(method) == ZBSendFileResponse.class) {
                uploads.add(method);
            }
        }
        List<String> failures = new ArrayList<>();
        String[][] cases = {{"[\"ok\",\"also\"]", "ok\nalso"}, {"[\"only\"]", "only"}, {"[]", ""}};
        for (String[] c : cases) {
            run(uploads, "{\"success\":true,\"message\":" + c[0] + ",\"file_id\":\"x\"}", (method, body, outcome) -> {
                Object sent = outcome.successes.size() == 1 ? outcome.successes.get(0) : null;
                String text = sent instanceof ZBSendFileResponse ? ((ZBSendFileResponse) sent).getMessage() : null;
                if (outcome.thrown != null || !outcome.errors.isEmpty() || !c[1].equals(text)) {
                    failures.add(body + " expected " + c[1].replace("\n", "\\n") + ": " + outcome);
                }
            });
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * {@code null} is accepted for an optional field (standard.md section 3): on every
     * typed call, each top-level field and each validateBatch row field set to null, one
     * at a time, leaves an otherwise valid answer a success. The fields a call cannot
     * answer without ({@code Credits}, {@code address}, {@code status}) are excluded.
     */
    @Test
    void nullInAnOptionalFieldIsNotAnError() throws Exception {
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
            for (Field field : modelFields(model)) {
                if (REQUIRED.contains(jsonName(field))) {
                    continue;
                }
                JsonObject body = JsonParser.parseString(baseText).getAsJsonObject();
                body.add(jsonName(field), JsonNull.INSTANCE);
                String text = list ? "[" + body + "]" : body.toString();
                run(entry.getValue(), text, expectSuccess(failures));
                bodies++;
            }
            if (model == ZBValidateBatchResponse.class) {
                for (Field field : modelFields(ZBValidateBatchData.class)) {
                    if (REQUIRED.contains(jsonName(field))) {
                        continue;
                    }
                    JsonObject row = JsonParser.parseString(
                            "{\"address\":\"a@b.c\",\"status\":\"valid\"}").getAsJsonObject();
                    row.add(jsonName(field), JsonNull.INSTANCE);
                    run(entry.getValue(), "{\"email_batch\":[" + row + "]}", expectSuccess(failures));
                    bodies++;
                }
            }
        }
        assertTrue(bodies > 100, "null bodies sent: " + bodies);
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * The AI score is an integer in the API and is kept as text (check E7): a JSON
     * integer, or a string holding one, within the int range is a result; a
     * fraction, an exponent, a non-number or an out-of-range value is an error with
     * status 200 on both scoring calls.
     */
    @Test
    void theScoreIsAnIntegerOnEveryScoringCall() throws Exception {
        List<Method> scoring = new ArrayList<>();
        for (Method method : typedMethods()) {
            if (successType(method) == ZBScoreEmailResponse.class) {
                scoring.add(method);
            }
        }
        assertTrue(scoring.size() >= 3, "scoring overloads: " + scoring);
        List<String> failures = new ArrayList<>();
        for (String body : List.of("{\"score\":5.5}", "{\"score\":5.0}", "{\"score\":1e2}",
                "{\"score\":1e400}", "{\"score\":\"5.9\"}", "{\"score\":\"abc\"}", "{\"score\":\"\"}",
                "{\"score\":\" 9\"}", "{\"score\":true}", "{\"score\":[9]}", "{\"score\":{}}",
                "{\"score\":2147483648}", "{\"score\":\"2147483648\"}", "{\"score\":\"+9\"}")) {
            run(scoring, body, expectError(failures));
        }
        for (String body : List.of("{\"score\":9}", "{\"score\":\"9\"}", "{\"score\":\"-1\"}",
                "{\"score\":0}", "{\"score\":null}", "{\"score\":2147483647}", "{\"score\":-2147483648}")) {
            run(scoring, body, expectSuccess(failures));
        }
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

    /**
     * The names of a model's fields its source marks {@code @NotNull}. The annotation is
     * CLASS-retained, so the source is read (a header scan of the model's own file).
     */
    private static java.util.Set<String> notNullFields(Class<?> model) throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/zerobounce", model.getSimpleName() + ".java"),
                StandardCharsets.UTF_8);
        java.util.Set<String> names = new java.util.TreeSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "@NotNull\\s+(?:@[\\w.]+(?:\\([^)]*\\))?\\s+)*(?:private\\s+)?[^=;(]+?\\s(\\w+)\\s*[=;]").matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** The value a field holds in a fresh model: its empty value for a field the public type never leaves null. */
    private static Object defaultOf(Class<?> model, Field field) throws Exception {
        java.lang.reflect.Constructor<?> constructor = model.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object fresh = constructor.newInstance();
        field.setAccessible(true);
        return field.get(fresh);
    }

    /**
     * The value a {@code @NotNull} field reads as when the JSON leaves it out or sends null:
     * the empty value of its type, whatever a fresh model holds (a fresh model that starts
     * the field as null must still read as the empty value).
     */
    private static Object emptyValueOf(Class<?> model, Field field) throws Exception {
        Object fresh = defaultOf(model, field);
        if (fresh != null) {
            return fresh;
        }
        if (field.getType() == String.class) {
            return "";
        }
        if (field.getType() == boolean.class || field.getType() == Boolean.class) {
            return false;
        }
        if (List.class.isAssignableFrom(field.getType())) {
            return new ArrayList<>();
        }
        throw new AssertionError("no empty value for " + model.getSimpleName() + "." + field.getName());
    }

    /**
     * standard.md section 3: where the public type is not nullable, a JSON null reads
     * as that type's empty value ({@code ""}, {@code false}, an empty list), never as
     * null and never as an error. Every {@code @NotNull} field of every typed model, and
     * of the nested {@link DomainFormat} rows, is sent as null once; the decoded value must
     * be the empty value the field starts with.
     */
    @Test
    void aNullInANotNullFieldReadsAsItsEmptyValue() throws Exception {
        sweepNotNullFields(false);
    }

    /**
     * standard.md section 3: a missing optional field reads exactly like a null one. Every
     * {@code @NotNull} field of every typed model (and of the nested rows) is left out of
     * an otherwise valid answer once; the decoded value must be the field's empty value.
     */
    @Test
    void aMissingNotNullFieldReadsAsItsEmptyValue() throws Exception {
        sweepNotNullFields(true);
    }

    private void sweepNotNullFields(boolean absent) throws Exception {
        Map<Class<?>, List<String>> documented = documented();
        Map<Type, List<Method>> byType = new LinkedHashMap<>();
        for (Method method : typedMethods()) {
            byType.computeIfAbsent(successType(method), k -> new ArrayList<>()).add(method);
        }
        List<String> failures = new ArrayList<>();
        int checks = 0;
        for (Map.Entry<Type, List<Method>> entry : byType.entrySet()) {
            Type type = entry.getKey();
            boolean list = type instanceof ParameterizedType
                    && ((ParameterizedType) type).getRawType() == List.class;
            Class<?> model = (Class<?>) (list ? ((ParameterizedType) type).getActualTypeArguments()[0] : type);
            String baseText = documented.containsKey(model) ? documented.get(model).get(0) : "{}";
            for (Field field : modelFields(model)) {
                // Only a field the public type marks @NotNull is checked; a @Nullable one stays null.
                if (REQUIRED.contains(jsonName(field)) || !notNullFields(model).contains(field.getName())) {
                    continue;
                }
                Object expected = emptyValueOf(model, field);
                JsonObject body = JsonParser.parseString(baseText).getAsJsonObject();
                if (absent) {
                    body.remove(jsonName(field));
                } else {
                    body.add(jsonName(field), JsonNull.INSTANCE);
                }
                String text = list ? "[" + body + "]" : body.toString();
                run(entry.getValue(), text, (method, sent, outcome) -> {
                    Object decoded = outcome.successes.size() == 1 ? outcome.successes.get(0) : null;
                    if (decoded instanceof List) {
                        decoded = ((List<?>) decoded).isEmpty() ? null : ((List<?>) decoded).get(0);
                    }
                    Object actual = null;
                    try {
                        actual = decoded == null ? null : readField(decoded, field);
                    } catch (Exception e) {
                        failures.add(sent + " " + outcome + " " + e);
                        return;
                    }
                    if (outcome.thrown != null || !outcome.errors.isEmpty() || !java.util.Objects.equals(expected, actual)) {
                        failures.add(sent + " " + field.getName() + " expected " + expected + " got " + actual
                                + " " + outcome);
                    }
                });
                checks++;
            }
        }
        // The nested rows of the finder and domain search answers.
        for (String body : List.of("{\"format\":null,\"confidence\":null}")) {
            for (Field field : modelFields(DomainFormat.class)) {
                if (!notNullFields(DomainFormat.class).contains(field.getName())) {
                    continue;
                }
                String text = "{\"other_domain_formats\":[" + body + "]}";
                List<Method> finders = new ArrayList<>();
                for (Method method : typedMethods()) {
                    if (successType(method) == ZBFindDomainResponse.class
                            || successType(method) == ZBEmailFinderResponse.class) {
                        finders.add(method);
                    }
                }
                JsonObject row = new JsonObject();
                if (!absent) {
                    row.add(jsonName(field), JsonNull.INSTANCE);
                }
                row.addProperty("confidence".equals(jsonName(field)) ? "format" : "confidence", "x");
                String rowText = "{\"other_domain_formats\":[" + row + "]}";
                String expectedField = "";
                run(finders, rowText, (method, sent, outcome) -> {
                    Object decoded = outcome.successes.size() == 1 ? outcome.successes.get(0) : null;
                    Object actual = null;
                    try {
                        Object rows = decoded == null ? null : readField(decoded, otherDomainFormatsField(decoded));
                        Object first = rows instanceof List && !((List<?>) rows).isEmpty()
                                ? ((List<?>) rows).get(0) : null;
                        actual = first == null ? null : readField(first, field);
                    } catch (Exception e) {
                        failures.add(sent + " " + outcome + " " + e);
                        return;
                    }
                    if (outcome.thrown != null || !outcome.errors.isEmpty() || !expectedField.equals(actual)) {
                        failures.add(sent + " DomainFormat." + field.getName() + " expected \"\" got " + actual
                                + " " + outcome);
                    }
                });
                checks++;
            }
        }
        assertTrue(checks > 25, "null checks: " + checks);
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    private static Object readField(Object target, Field field) throws Exception {
        Field own = target.getClass().getDeclaredField(field.getName());
        own.setAccessible(true);
        return own.get(target);
    }

    private static Field otherDomainFormatsField(Object target) throws Exception {
        return target.getClass().getDeclaredField("otherDomainFormats");
    }

    /**
     * A null {@code email_batch} is an empty batch, not a null list (standard.md section 3:
     * the public type is not nullable, so null reads as its empty value).
     */
    @Test
    void aNullEmailBatchIsAnEmptyBatch() throws Exception {
        List<Method> batch = new ArrayList<>();
        for (Method method : typedMethods()) {
            if (successType(method) == ZBValidateBatchResponse.class) {
                batch.add(method);
            }
        }
        List<String> failures = new ArrayList<>();
        run(batch, "{\"email_batch\":null}", (method, body, outcome) -> {
            Object sent = outcome.successes.size() == 1 ? outcome.successes.get(0) : null;
            if (outcome.thrown != null || !outcome.errors.isEmpty() || !(sent instanceof ZBValidateBatchResponse)
                    || !((ZBValidateBatchResponse) sent).getEmailBatch().isEmpty()) {
                failures.add(body + " " + outcome);
            }
        });
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /**
     * A date field the API sends as text that no known format reads (check E7 and the date
     * closure in standard.md section 3) is an error with status 200 on every typed call that
     * returns it, not a null date and not a success.
     */
    @Test
    void anUnparseableDateIsAnErrorWithStatus200OnEveryTypedCall() throws Exception {
        Map<Class<?>, List<String>> documented = documented();
        Map<Type, List<Method>> byType = new LinkedHashMap<>();
        for (Method method : typedMethods()) {
            byType.computeIfAbsent(successType(method), k -> new ArrayList<>()).add(method);
        }
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<Type, List<Method>> entry : byType.entrySet()) {
            Type type = entry.getKey();
            boolean list = type instanceof ParameterizedType
                    && ((ParameterizedType) type).getRawType() == List.class;
            Class<?> model = (Class<?>) (list ? ((ParameterizedType) type).getActualTypeArguments()[0] : type);
            String baseText = documented.containsKey(model) ? documented.get(model).get(0) : "{}";
            for (Field field : modelFields(model)) {
                if (field.getType() != Date.class) {
                    continue;
                }
                JsonObject body = JsonParser.parseString(baseText).getAsJsonObject();
                body.addProperty(jsonName(field), "not a date");
                String text = list ? "[" + body + "]" : body.toString();
                run(entry.getValue(), text, expectError(failures));
                checked++;
            }
        }
        assertTrue(checked >= 2, "date fields checked: " + checked);
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }
}
