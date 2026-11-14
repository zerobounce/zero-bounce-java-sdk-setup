package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Download temp-file permissions and link chains, the empty-body message, and the warmup singleton. */
class J3SdkDownloadAndMessagesTest {

    @TempDir
    Path tempDir;

    private static final byte[] CSV = "email,status\na@example.com,valid\n".getBytes(StandardCharsets.UTF_8);

    private static void save(Path target) {
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        boolean ok = ZeroBounceSDK.saveDownload(target.toFile(), CSV, error::set);
        assertNull(error.get(), () -> String.valueOf(error.get().getErrors()));
        assertTrue(ok);
    }

    @Test
    void theTempFileHasTheTargetsPermissionsWhenItIsFirstOpened() throws Exception {
        Path target = tempDir.resolve("private.csv");
        Files.writeString(target, "old");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
        AtomicReference<String> atOpen = new AtomicReference<>();
        ZeroBounceSDK.FileOpener opener = file -> {
            atOpen.set(PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())));
            return new java.io.FileOutputStream(file);
        };
        assertTrue(ZeroBounceSDK.saveDownload(target.toFile(), CSV, e -> { throw new AssertionError(e.getErrors()); }, opener));
        assertEquals("rw-------", atOpen.get(), "temp file permissions before any byte was written");
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(target)));
    }

    @Test
    void aDanglingLinkChainIsWrittenToTheFinalTargetAndTheLinksStay() throws Exception {
        Path missing = tempDir.resolve("missing.csv");
        Path l2 = tempDir.resolve("l2");
        Path l1 = tempDir.resolve("l1");
        Files.createSymbolicLink(l2, missing.getFileName());
        Files.createSymbolicLink(l1, l2.getFileName());
        save(l1);
        assertTrue(Files.isSymbolicLink(l1));
        assertTrue(Files.isSymbolicLink(l2), "the middle link must not be replaced by a file");
        assertEquals(new String(CSV, StandardCharsets.UTF_8), Files.readString(missing));
        assertEquals(new String(CSV, StandardCharsets.UTF_8), Files.readString(l1));
    }

    @Test
    void aLinkLoopIsRefusedWithAnError() throws Exception {
        Path a = tempDir.resolve("a");
        Path b = tempDir.resolve("b");
        Files.createSymbolicLink(a, b.getFileName());
        Files.createSymbolicLink(b, a.getFileName());
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        assertEquals(false, ZeroBounceSDK.saveDownload(a.toFile(), CSV, error::set));
        assertNotNull(error.get());
        assertTrue(Files.isSymbolicLink(a) && Files.isSymbolicLink(b));
    }

    @Test
    void anEmptyDownloadBodyHasItsOwnMessage() {
        assertEquals("Empty response from ZeroBounce (HTTP 200)", HttpMessages.downloadFailure(200, new byte[0], "text/csv"));
        assertEquals("Empty response from ZeroBounce (HTTP 502)", HttpMessages.downloadFailure(502, null, null));
        assertEquals("Empty response from ZeroBounce (HTTP 200)",
                HttpMessages.downloadFailure(200, " \n".getBytes(StandardCharsets.UTF_8), "text/csv"));
    }

    @Test
    void theWarmupSingletonIsNeverNullAndIsShared() {
        ZeroBounceWarmup first = ZeroBounceWarmup.getInstance();
        assertNotNull(first);
        assertTrue(first == ZeroBounceWarmup.getInstance());
    }
}
