package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A download over an existing file: the temporary file's name does not grow
 * with the target's name, the existing permissions and a symbolic link survive
 * the rename, a read-only target is refused, and a directory is never replaced.
 */
class J2SdkDownloadTargetTest {

    @TempDir
    Path tempDir;

    private static final String CSV = "email,status\na@example.com,valid\n";

    private static ZeroBounceSDK sdk() {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> new SdkHttp.Result(200, "text/csv", CSV.getBytes(StandardCharsets.UTF_8));
        sdk.initialize("download-target-key");
        return sdk;
    }

    @Test
    void aTargetNameOf255CharactersIsWrittenWithoutATooLongTemporaryName() throws Exception {
        String name = "a".repeat(251) + ".csv";
        assertEquals(255, name.length());
        Path target = tempDir.resolve(name);
        AtomicReference<ZBGetFileResponse> saved = new AtomicReference<>();
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        sdk().getFile("fid", target.toString(), saved::set, error::set);
        assertNull(error.get(), () -> "a 255-character name failed: " + error.get().getErrors());
        assertNotNull(saved.get());
        assertEquals(CSV, Files.readString(target));
    }

    @Test
    void anExistingFilesPermissionsSurviveTheDownload() throws Exception {
        Path target = tempDir.resolve("private.csv");
        Files.writeString(target, "old", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
        AtomicReference<ZBGetFileResponse> saved = new AtomicReference<>();
        sdk().getFile("fid", target.toString(), saved::set, e -> { throw new AssertionError(e.getErrors()); });
        assertNotNull(saved.get());
        assertEquals(CSV, Files.readString(target));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(target)));
    }

    @Test
    void aSymbolicLinkTargetIsWrittenThroughToItsResolvedFile() throws Exception {
        Path real = Files.createDirectories(tempDir.resolve("real")).resolve("out.csv");
        Files.writeString(real, "old", StandardCharsets.UTF_8);
        Path link = tempDir.resolve("link.csv");
        Files.createSymbolicLink(link, real);
        AtomicReference<ZBGetFileResponse> saved = new AtomicReference<>();
        sdk().getFile("fid", link.toString(), saved::set, e -> { throw new AssertionError(e.getErrors()); });
        assertNotNull(saved.get());
        assertTrue(Files.isSymbolicLink(link), "the symbolic link was replaced by a regular file");
        assertEquals(CSV, Files.readString(real));
    }

    @Test
    void aReadOnlyExistingTargetIsRefusedWithAnErrorAndLeftAlone() throws Exception {
        Path target = tempDir.resolve("readonly.csv");
        Files.writeString(target, "keep me", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("r--r--r--"));
        assumeTrue(!Files.isWritable(target), "the test user can write a read-only file");
        try {
            AtomicInteger successes = new AtomicInteger();
            AtomicReference<ErrorResponse> error = new AtomicReference<>();
            sdk().getFile("fid", target.toString(), r -> successes.incrementAndGet(), error::set);
            assertEquals(0, successes.get(), "a read-only file was reported as downloaded");
            assertNotNull(error.get(), "no error for a read-only target");
            assertEquals("keep me", Files.readString(target));
            try (var files = Files.list(tempDir)) {
                assertEquals(List.of("readonly.csv"), files.map(p -> p.getFileName().toString()).toList(),
                        "a temporary file was left behind");
            }
        } finally {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void aDirectoryIsNeverReplacedByAFileWhenTheRenameRuns() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("empty"));
        List<String> errors = new ArrayList<>();
        boolean written = ZeroBounceSDK.saveDownload(dir.toFile(), CSV.getBytes(StandardCharsets.UTF_8),
                e -> errors.add(String.valueOf(e.getErrors())));
        assertFalse(written, "a directory was reported as written");
        assertEquals(1, errors.size());
        assertTrue(Files.isDirectory(dir), "the directory was replaced");
    }
}
