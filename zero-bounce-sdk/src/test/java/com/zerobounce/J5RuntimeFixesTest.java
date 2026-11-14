package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Final-round runtime fixes: html titles, missing fields, empty messages, null arguments, redaction, keys, downloads, timeouts. */
class J5RuntimeFixesTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "j5-runtime-key";
    private static final String NO_TEXT = "ZeroBounce returned an error with no message text.";

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    // 1: html title

    @Test
    void htmlTitleSurvivesCharactersThatChangeLengthWhenLowerCased() {
        String page = "<html><head><meta content=\"" + "İ".repeat(50) + "\"><title>Blocked</title></head></html>";
        assertEquals("ZeroBounce returned an HTML error page (\"Blocked\") instead of an API response.",
                HttpMessages.htmlMessage(page));
        assertTrue(HttpMessages.htmlMessage("<html><title>İstek engellendi</title>").contains("İstek engellendi"));
    }

    @Test
    void htmlTitleIsFoundUnderATurkishDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(HttpMessages.htmlMessage("<HTML><HEAD><TITLE>Blocked</TITLE></HEAD>").contains("\"Blocked\""));
            assertTrue(HttpMessages.looksLikeHtml("x", "TEXT/HTML"));
        } finally {
            Locale.setDefault(before);
        }
    }

    // 2: missing free_email / mx_found read like null

    @Test
    void missingFreeEmailAndMxFoundReadNullOnValidateAndBatchRows() throws Exception {
        String single = "{\"address\":\"a@example.com\",\"status\":\"valid\"}";
        String batch = "{\"email_batch\":[{\"address\":\"a@example.com\",\"status\":\"valid\"}],\"errors\":[]}";
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", bytes(single)))) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            List<ZBValidateResponse> got = new ArrayList<>();
            try (var ignored = server.apacheClients()) {
                sdk.validate("a@example.com", null, got::add, e -> {
                    throw new AssertionError(e.toString());
                });
            }
            assertEquals(1, got.size());
            assertNull(got.get(0).getFreeEmail());
            assertNull(got.get(0).getMxFound());
        }
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", bytes(batch)))) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            List<ZBValidateBatchResponse> got = new ArrayList<>();
            try (var ignored = server.apacheClients()) {
                sdk.validateBatch(List.of(new ZBValidateBatchData("a@example.com", null)), got::add, e -> {
                    throw new AssertionError(e.toString());
                });
            }
            assertEquals(1, got.size());
            assertNull(got.get(0).getEmailBatch().get(0).getFreeEmail());
            assertNull(got.get(0).getEmailBatch().get(0).getMxFound());
        }
    }

    // 3: message-only bodies with no usable text

    @Test
    void aBodyWithNoUsableMessageTextGivesTheFixedMessageOnce() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String body : List.of("{\"error\":{}}", "{\"error\":[]}", "{\"error\":\"Error:\"}",
                "{\"message\":\"[Error]: \"}", "{\"success\":false,\"message\":\"Error:\"}")) {
            try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", bytes(body)))) {
                ZeroBounceSDK sdk = server.sdk();
                sdk.initialize(KEY);
                List<ErrorResponse> errors = new ArrayList<>();
                try (var ignored = server.apacheClients()) {
                    sdk.getCredits(r -> failures.add(body + " succeeded"), errors::add);
                }
                if (errors.size() != 1 || !List.of(NO_TEXT).equals(errors.get(0).getErrors())) {
                    failures.add(body + " -> " + errors);
                }
            }
        }
        assertEquals(List.of(), failures);
    }

    // 4: null arguments

    @Test
    void aNullRequiredArgumentIsAnErrorCallbackAndSendsNothing() throws Exception {
        List<String> failures = new ArrayList<>();
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", bytes("{}")))) {
            RequestSweep sweep = new RequestSweep(tempDir);
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            ZeroBounceWarmup warmup = server.warmup();
            warmup.initialize(KEY);
            int checked = 0;
            try (var ignored = server.apacheClients()) {
                for (Method method : RequestSweep.allRequestMethods()) {
                    Object client = method.getDeclaringClass() == ZeroBounceWarmup.class ? warmup : sdk;
                    Map<String, Supplier<Object[]>> argmap = client == warmup ? sweep.warmupArgs : sweep.sdkArgs;
                    Object[] base = argmap.get(RequestSweep.signature(method)).get();
                    Class<?>[] types = method.getParameterTypes();
                    int p = -1;
                    for (int i = 0, next = 0; i < types.length; i++) {
                        if (types[i] == ZeroBounceSDK.OnSuccessCallback.class || types[i] == ZeroBounceSDK.OnErrorCallback.class) {
                            continue;
                        }
                        next++;
                        p = next - 1;
                        if (base[p] == null || types[i].isPrimitive()) {
                            continue;
                        }
                        // Options objects that a caller may leave out are not required.
                        if (types[i] == ZBValidateOptions.class || types[i] == ZBGetFileOptions.class) {
                            continue;
                        }
                        // Optional: the finders' first name, the export destination, the bucket filter.
                        if (List.of("findEmail(String,String,String,String,String)#0",
                                "guessFormat(String,String,String,String)#1",
                                "exportS3File(String,String)#1", "exportSFTPFile(String,String)#1",
                                "listS3Files(String,String)#0")
                                .contains(RequestSweep.signature(method) + "#" + p)) {
                            continue;
                        }
                        Object[] args = base.clone();
                        args[p] = null;
                        List<Object> ok = new ArrayList<>();
                        List<ErrorResponse> errs = new ArrayList<>();
                        Object[] call = new Object[types.length];
                        int a = 0;
                        for (int j = 0; j < types.length; j++) {
                            if (types[j] == ZeroBounceSDK.OnSuccessCallback.class) {
                                call[j] = (ZeroBounceSDK.OnSuccessCallback<Object>) ok::add;
                            } else if (types[j] == ZeroBounceSDK.OnErrorCallback.class) {
                                call[j] = (ZeroBounceSDK.OnErrorCallback) errs::add;
                            } else {
                                call[j] = args[a++];
                            }
                        }
                        server.clear();
                        String what = RequestSweep.signature(method) + "#" + p;
                        checked++;
                        try {
                            method.invoke(client, call);
                        } catch (InvocationTargetException e) {
                            failures.add(what + " threw " + e.getCause());
                            continue;
                        }
                        if (!server.requests().isEmpty()) {
                            failures.add(what + " sent a request");
                        } else if (errs.size() != 1 || errs.get(0).getStatusCode() != 0 || !ok.isEmpty()) {
                            failures.add(what + " -> " + errs + " ok=" + ok);
                        }
                    }
                }
            }
            assertTrue(checked > 80, "pairs checked: " + checked);
        }
        assertEquals(List.of(), failures, failures.size() + " failures");
    }

    // 5: redaction of a backslash-escaped quote

    @Test
    void aSecretWithAQuoteIsMaskedWhenEchoedWithABackslashEscape() {
        String secret = "pa'ss word";
        String echoed = ZeroBounceSDK.redact("bad header: pa\\'ss word", secret);
        assertFalse(echoed.contains("pa\\'ss"), echoed);
        assertFalse(echoed.contains("pa'ss"), echoed);
        assertTrue(ZeroBounceSDK.exposesSecret("x pa\\'ss word y", secret));
    }

    // 6: keys padded with format characters

    @Test
    void aKeyPaddedWithZeroWidthCharactersIsRefusedInBothClients() throws Exception {
        for (String pad : List.of("​", "﻿", "᠎")) {
            for (String key : List.of(pad + KEY, KEY + pad)) {
                try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", bytes("{}")))) {
                    ZeroBounceSDK sdk = server.sdk();
                    sdk.initialize(key);
                    ZeroBounceWarmup warmup = server.warmup();
                    warmup.initialize(key);
                    List<ErrorResponse> errors = new ArrayList<>();
                    try (var ignored = server.apacheClients()) {
                        sdk.getCredits(r -> errors.add(null), errors::add);
                        warmup.getSeeds(r -> errors.add(null), errors::add);
                    }
                    assertEquals(2, errors.size(), "pad " + (int) pad.charAt(0));
                    for (ErrorResponse e : errors) {
                        assertTrue(e != null && e.getErrors().get(0).contains("surrounding whitespace"), String.valueOf(e));
                    }
                    assertEquals(0, server.requests().size());
                }
            }
        }
    }

    // 7: downloads

    @Test
    void aJsonErrorWithAByteOrderMarkIsNotSavedAsResults() throws Exception {
        String body = "﻿{\"success\":false,\"message\":\"No such file\"}";
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "text/csv", bytes(body)))) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            File out = tempDir.resolve("bom.csv").toFile();
            List<ErrorResponse> errors = new ArrayList<>();
            try (var ignored = server.apacheClients()) {
                sdk.getFile("fid", out.getPath(), r -> errors.add(null), errors::add);
            }
            assertEquals(1, errors.size());
            assertTrue(errors.get(0) != null && errors.get(0).getErrors().toString().contains("No such file"), String.valueOf(errors));
            assertFalse(out.exists());
        }
    }

    @Test
    void aWarmupCsvJsonErrorWithAByteOrderMarkIsAnError() throws Exception {
        String body = "﻿{\"success\":false,\"message\":\"No seeds\"}";
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "text/csv", bytes(body)))) {
            ZeroBounceWarmup warmup = server.warmup();
            warmup.initialize(KEY);
            List<String> ok = new ArrayList<>();
            List<ErrorResponse> errors = new ArrayList<>();
            try (var ignored = server.apacheClients()) {
                warmup.downloadSeeds(ok::add, errors::add);
            }
            assertEquals(List.of(), ok);
            assertEquals(1, errors.size());
        }
    }

    @Test
    void aDanglingRelativeLinkWithDotDotResolvesAgainstTheLinksRealDirectory() throws Exception {
        Path real = Files.createDirectories(tempDir.resolve("real/sub"));
        Path linkDir = tempDir.resolve("linkdir");
        Files.createSymbolicLink(linkDir, real);
        Path link = linkDir.resolve("lnk");
        Files.createSymbolicLink(link, Path.of("../out.csv"));
        List<ErrorResponse> errors = new ArrayList<>();
        assertTrue(ZeroBounceSDK.saveDownload(link.toFile(), bytes("a,b\n"), errors::add), errors.toString());
        assertTrue(Files.exists(tempDir.resolve("real/out.csv")));
        assertFalse(Files.exists(tempDir.resolve("out.csv")));
    }

    @Test
    void aTargetInAReadOnlyDirectoryGivesAClearPermissionError() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")));
        Path dir = Files.createDirectories(tempDir.resolve("ro"));
        Path target = dir.resolve("out.csv");
        Files.writeString(target, "old");
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            for (Path file : List.of(target, dir.resolve("new.csv"))) {
                List<ErrorResponse> errors = new ArrayList<>();
                assertFalse(ZeroBounceSDK.saveDownload(file.toFile(), bytes("a,b\n"), errors::add));
                assertEquals(1, errors.size());
                String message = errors.get(0).getErrors().get(0);
                assertTrue(message.startsWith("Cannot write ") && message.endsWith("(permission denied)"), message);
            }
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void anEmptyOrDirectoryLikeDownloadPathIsRefusedBeforeSending() throws Exception {
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "text/csv", bytes("a,b\n")))) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            String nested = tempDir.resolve("dir/newdir") + File.separator;
            try (var ignored = server.apacheClients()) {
                for (String path : new String[]{"", "  ", nested, tempDir + "/x/"}) {
                    List<ErrorResponse> errors = new ArrayList<>();
                    List<Object> ok = new ArrayList<>();
                    sdk.getFile("fid", path, ok::add, errors::add);
                    sdk.emailFinderGetFile("fid", path, ok::add, errors::add);
                    sdk.domainSearchGetFile("fid", path, ok::add, errors::add);
                    assertEquals(3, errors.size(), "path [" + path + "]");
                    assertEquals(List.of(), ok);
                }
            }
            assertEquals(0, server.requests().size());
            assertFalse(Files.exists(tempDir.resolve("dir")));
        }
    }

    // 8: negative timeouts

    @Test
    void aNegativeTimeoutIsRejectedUpFront() {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        assertThrows(IllegalArgumentException.class, () -> sdk.initialize(KEY, -1));
        sdk.initialize(KEY, 0);
        ZeroBounceWarmup warmup = new ZeroBounceWarmup();
        assertThrows(IllegalArgumentException.class, () -> warmup.setTimeout(-5));
        warmup.setTimeout(0);
    }
}
