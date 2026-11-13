package com.zerobounce;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redaction is closed under encoding (checks S4 and S7): a secret quoted back in
 * any composition of up to four encodings (the SDK's own request encoding plus
 * three a server can add) (URL with a space as {@code %20}, URL
 * with a space as {@code +}, JSON, JSON with {@code /} as a backslash and a slash)
 * never appears in any decoded view of what the caller gets. The compositions are
 * random, from a fixed seed so a failure repeats; the decoder here is the test's
 * own, not the SDK's.
 */
class OfflineRedactionFuzzTest {

    @TempDir
    Path tempDir;

    /** A key with a space, {@code " / \ + % & = < > '}: every encoding changes it. */
    private static final String KEY = "fz+k/1\"2%3 4=<>&'\\5";
    private static final String PASSWORD = RequestSweep.SMTP_PASSWORD;

    private static String jsonEscape(String text, boolean htmlSafe) {
        com.google.gson.Gson gson = htmlSafe ? new com.google.gson.Gson()
                : new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
        String quoted = gson.toJson(text);
        return quoted.substring(1, quoted.length() - 1);
    }

    private static final List<String> NAMES = List.of("url%20", "url+", "json", "json\\/");

    private static UnaryOperator<String> encoder(int which, boolean htmlSafe) {
        switch (which) {
            case 0:
                return text -> URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
            case 1:
                return text -> URLEncoder.encode(text, StandardCharsets.UTF_8);
            case 2:
                return text -> jsonEscape(text, htmlSafe);
            default:
                return text -> jsonEscape(text, htmlSafe).replace("/", "\\/");
        }
    }

    /** One random composition of up to four encodings, and its name. */
    private static String[] compose(Random random, String secret) {
        int layers = 1 + random.nextInt(4);
        String value = secret;
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < layers; i++) {
            int which = random.nextInt(4);
            boolean htmlSafe = random.nextBoolean();
            value = encoder(which, htmlSafe).apply(value);
            name.append(name.length() == 0 ? "" : " then ").append(NAMES.get(which)).append(htmlSafe ? "(html)" : "");
        }
        return new String[]{value, name.toString()};
    }

    // -- the test's own decoders ------------------------------------------------

    private static String percentDecode(String text, boolean plusIsSpace) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < raw.length; i++) {
            byte b = raw[i];
            if (b == '%' && i + 2 < raw.length && hex(raw[i + 1]) >= 0 && hex(raw[i + 2]) >= 0) {
                bytes.write(hex(raw[i + 1]) * 16 + hex(raw[i + 2]));
                i += 2;
            } else if (b == '+' && plusIsSpace) {
                bytes.write(' ');
            } else {
                bytes.write(b);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static int hex(byte b) {
        return Character.digit((char) b, 16);
    }

    private static String jsonUnescape(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char e = text.charAt(i + 1);
            int at = "\"\\/bfnrt".indexOf(e);
            if (at >= 0) {
                out.append("\"\\/\b\f\n\r\t".charAt(at));
                i++;
            } else if (e == 'u' && i + 5 < text.length()) {
                try {
                    out.append((char) Integer.parseInt(text.substring(i + 2, i + 6), 16));
                    i += 5;
                } catch (NumberFormatException ignored) {
                    out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The text and every view of it decoded up to four times, in any order. */
    static Set<String> views(String text) {
        Set<String> all = new LinkedHashSet<>(List.of(text));
        List<String> layer = List.of(text);
        for (int depth = 0; depth < 4; depth++) {
            List<String> next = new ArrayList<>();
            for (String view : layer) {
                for (String decoded : List.of(percentDecode(view, false), percentDecode(view, true), jsonUnescape(view))) {
                    if (all.add(decoded)) {
                        next.add(decoded);
                    }
                }
            }
            layer = next;
        }
        return all;
    }

    private static boolean exposes(String text, String... secrets) {
        if (text == null) {
            return false;
        }
        for (String view : views(text)) {
            for (String secret : secrets) {
                if (view.contains(secret)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void theTestDecoderFindsEveryComposition() {
        Random random = new Random(20261112L);
        for (int i = 0; i < 500; i++) {
            for (String secret : List.of(KEY, PASSWORD)) {
                String[] encoded = compose(random, secret);
                assertTrue(exposes("x " + encoded[0] + " y", secret), encoded[1]);
            }
        }
    }

    @Test
    void noCompositionOfEncodingsSurvivesRedaction() {
        Random random = new Random(20261112L);
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            for (String secret : List.of(KEY, PASSWORD)) {
                String[] encoded = compose(random, secret);
                String message = ZeroBounceSDK.redact("Bad request: " + encoded[0] + " (quoted)", KEY, PASSWORD);
                if (exposes(message, secret)) {
                    failures.add(encoded[1] + ": " + message);
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    /** Every %XX in {@code text} with lower-case hex digits. */
    private static String lowerHex(String text) {
        StringBuilder out = new StringBuilder(text);
        for (int i = 0; i + 2 < out.length(); i++) {
            if (out.charAt(i) == '%') {
                out.setCharAt(i + 1, Character.toLowerCase(out.charAt(i + 1)));
                out.setCharAt(i + 2, Character.toLowerCase(out.charAt(i + 2)));
            }
        }
        return out.toString();
    }

    /** Every UTF-16 unit of {@code text} as a JSON u-escape (backslash, u, four hex digits), lower-case hex. */
    private static String unicodeEscaped(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            out.append(String.format("\\u%04x", (int) text.charAt(i)));
        }
        return out.toString();
    }

    /** A secret outside the BMP, so its JSON u-escaped spelling is a surrogate pair. */
    private static final String ASTRAL = "pw\uD83D\uDE00x/\"9";
    /** A secret whose only special character is a space, so its URL spelling has no %. */
    private static final String SPACED = "sp ace7";
    /** Secrets with a 2-byte and a 3-byte UTF-8 character (ASTRAL has a 4-byte one). */
    private static final String TWO_BYTE = "pw\u00e9x7";
    private static final String THREE_BYTE = "pw\u20acx7";

    /**
     * Each decode step of the check on its own, so removing one fails here and not
     * only by chance in the random compositions: percent-decoding with lower-case
     * hex and after an invalid UTF-8 run, {@code +} as a space, JSON unescaping
     * incl. u-escapes (backslash, u, four hex digits) and a surrogate pair, a view with no {@code %} that
     * still needs {@code +} decoded, four layers, and a NUL (raw or {@code %00})
     * before the secret, which must not end the check.
     */
    @Test
    void everyDecodeStepIsChecked() {
        UnaryOperator<String> url20 = encoder(0, false);
        UnaryOperator<String> urlPlus = encoder(1, false);
        UnaryOperator<String> json = encoder(2, false);
        String[][] cases = {
                {"lower-case hex", lowerHex(url20.apply(KEY))},
                {"invalid UTF-8 run first", "%FF%C3%28 " + url20.apply(urlPlus.apply(KEY))},
                {"invalid UTF-8 run, lower-case hex", "%ff%c3 " + lowerHex(urlPlus.apply(url20.apply(KEY)))},
                {"+ under \\u escapes", unicodeEscaped(urlPlus.apply(KEY))},
                {"\\u escapes", unicodeEscaped(KEY)},
                {"surrogate pair", unicodeEscaped(ASTRAL)},
                {"surrogate pair under URL", url20.apply(unicodeEscaped(ASTRAL))},
                {"+ without %", unicodeEscaped(urlPlus.apply(SPACED))},
                {"four layers", url20.apply(json.apply(urlPlus.apply(json.apply(url20.apply(KEY)))))},
                {"four layers, \\u inside", urlPlus.apply(url20.apply(unicodeEscaped(url20.apply(KEY))))},
                {"NUL first", "a\u0000b " + lowerHex(url20.apply(KEY))},
                {"%00 first", "%00 " + lowerHex(url20.apply(url20.apply(KEY)))},
                {"%00 first, JSON", "%00\\u0000 " + unicodeEscaped(KEY)},
                // Percent-decoding works on bytes, then reads them as UTF-8: one
                // case per UTF-8 sequence length, so decoding each %XX as its own
                // character (or dropping multi-byte runs) fails here.
                {"2-byte UTF-8 under %", lowerHex(url20.apply(TWO_BYTE))},
                {"3-byte UTF-8 under %", lowerHex(url20.apply(THREE_BYTE))},
                {"4-byte UTF-8 under %", lowerHex(url20.apply(ASTRAL))},
                {"4-byte UTF-8 under two % layers", lowerHex(url20.apply(url20.apply(ASTRAL)))},
        };
        List<String> failures = new ArrayList<>();
        for (String[] c : cases) {
            String body = "Bad request: " + c[1] + " (quoted)";
            assertTrue(exposes(body, KEY, ASTRAL, SPACED, TWO_BYTE, THREE_BYTE),
                    c[0] + ": the test's decoder finds the secret in " + body);
            String message = ZeroBounceSDK.redact(body, KEY, ASTRAL, SPACED, TWO_BYTE, THREE_BYTE);
            if (exposes(message, KEY, ASTRAL, SPACED, TWO_BYTE, THREE_BYTE)) {
                failures.add(c[0] + ": " + message);
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    @Test
    void aMessageWithoutASecretIsKept() {
        assertEquals("Bad request: %41 \\u0041 + plain", ZeroBounceSDK.redact("Bad request: %41 \\u0041 + plain", KEY));
    }

    /**
     * Every public request method, answered with an error that quotes the key (and,
     * on testSmtpConfiguration, the SMTP password) in a random composition of
     * encodings: as a 400 text body and as a JSON error on a 200. Nothing the
     * error callback gets exposes a secret in any decoded view.
     */
    @Test
    void everyRequestPathDropsAnEncodedSecret() throws Exception {
        Random random = new Random(1112L);
        for (int round = 0; round < 6; round++) {
            String[] key = compose(random, KEY);
            String[] password = compose(random, PASSWORD);
            boolean jsonOn200 = round % 2 == 1;
            sweep("fuzz-" + round + " key " + key[1] + ", password " + password[1], request -> {
                String quoted = request.target.contains("test-smtp") ? password[0] + " " + key[0] : key[0];
                return jsonOn200
                        ? LoopbackServer.answer(200, "application/json",
                                ("{\"error\": \"" + jsonEscape("Invalid API key " + quoted, false) + "\"}")
                                        .getBytes(StandardCharsets.UTF_8))
                        : LoopbackServer.answer(400, "text/plain",
                                ("Bad request: " + quoted).getBytes(StandardCharsets.UTF_8));
            }, round);
        }
    }

    private void sweep(String label, java.util.function.Function<LoopbackServer.Request, LoopbackServer.Responder> responder,
                       int round) throws Exception {
        Path dir = tempDir.resolve("fuzz-" + round);
        Files.createDirectories(dir);
        RequestSweep sweep = new RequestSweep(dir);
        try (LoopbackServer server = new LoopbackServer((request, out) -> responder.apply(request).respond(request, out))) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            ZeroBounceWarmup warmup = server.warmup();
            warmup.initialize(KEY);
            List<String> failures = new ArrayList<>();
            int swept = 0;
            try (MockedStatic<HttpClients> ignored = server.apacheClients()) {
                for (Method method : RequestSweep.allRequestMethods()) {
                    server.clear();
                    Object client = method.getDeclaringClass() == ZeroBounceWarmup.class ? warmup : sdk;
                    RequestSweep.Outcome outcome = sweep.invoke(client, method);
                    swept++;
                    if (outcome.thrown != null || server.requests().isEmpty()) {
                        failures.add(label + " " + outcome);
                        continue;
                    }
                    for (ErrorResponse error : outcome.errors) {
                        for (Object message : error.getErrors()) {
                            if (exposes(String.valueOf(message), KEY, PASSWORD)) {
                                failures.add(label + " exposed a secret: " + outcome);
                            }
                        }
                        if (exposes(error.toString(), KEY, PASSWORD)) {
                            failures.add(label + " toString exposed a secret: " + outcome);
                        }
                    }
                }
            }
            assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
            assertEquals(sweep.sdkArgs.size() + sweep.warmupArgs.size(), swept, "every sweep entry was invoked");
        }
    }
}
