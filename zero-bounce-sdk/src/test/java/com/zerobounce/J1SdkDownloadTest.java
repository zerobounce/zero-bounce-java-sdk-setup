package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Downloads: no directories before a failed request, a directory target refused before any request, and the atomic-move fallback. */
class J1SdkDownloadTest {

    @TempDir
    Path tempDir;

    private static final String CSV = "email,status\na@example.com,valid\n";

    private ZeroBounceSDK sdk(AtomicInteger requests, int status, String contentType, String body) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.transport = request -> {
            requests.incrementAndGet();
            return new SdkHttp.Result(status, contentType, body.getBytes(StandardCharsets.UTF_8));
        };
        sdk.initialize("download-key");
        return sdk;
    }

    @Test
    void aFailedGetFileCreatesNoDirectories() {
        Path target = tempDir.resolve("new").resolve("deeper").resolve("out.csv");
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<ErrorResponse> error = new AtomicReference<>();
        sdk(requests, 404, "text/plain", "not found").getFile("fid", target.toString(),
                r -> { throw new AssertionError("success"); }, error::set);
        assertEquals(1, requests.get());
        assertNotNull(error.get());
        assertEquals(404, error.get().getStatusCode());
        assertFalse(Files.exists(tempDir.resolve("new")), "a failed download created directories");
    }

    @Test
    void aSuccessfulGetFileStillCreatesTheDirectoriesAndTheFile() throws Exception {
        Path target = tempDir.resolve("made").resolve("out.csv");
        AtomicReference<ZBGetFileResponse> saved = new AtomicReference<>();
        sdk(new AtomicInteger(), 200, "text/csv", CSV).getFile("fid", target.toString(), saved::set,
                e -> { throw new AssertionError(e.getErrors().toString()); });
        assertNotNull(saved.get());
        assertEquals(CSV, Files.readString(target));
    }

    @Test
    void aDirectoryTargetIsRefusedBeforeAnyRequestOnEveryDownloadPath() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String method : new String[]{"emailFinderGetFile", "domainSearchGetFile", "getFile"}) {
            Path dir = Files.createDirectories(tempDir.resolve(method));
            AtomicInteger requests = new AtomicInteger();
            AtomicReference<ErrorResponse> error = new AtomicReference<>();
            ZeroBounceSDK sdk = sdk(requests, 200, "text/csv", CSV);
            switch (method) {
                case "emailFinderGetFile" -> sdk.emailFinderGetFile("fid", dir.toString(),
                        r -> failures.add("success " + method), error::set);
                case "domainSearchGetFile" -> sdk.domainSearchGetFile("fid", dir.toString(),
                        r -> failures.add("success " + method), error::set);
                default -> sdk.getFile("fid", dir.toString(),
                        r -> failures.add("success " + method), error::set);
            }
            if (requests.get() != 0) {
                failures.add(method + " sent " + requests.get() + " request(s)");
            }
            if (error.get() == null || !error.get().getErrors().toString().contains("Invalid file path")) {
                failures.add(method + " error " + error.get());
            } else if (error.get().getErrors().toString().contains(".part")) {
                failures.add(method + " quoted the temporary file: " + error.get().getErrors());
            }
            if (!Files.isDirectory(dir)) {
                failures.add(method + " replaced the directory");
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void aFileSystemWithoutAtomicRenameFallsBackToAPlainRename() throws Exception {
        ZeroBounceSDK.AtomicMove atomic = ZeroBounceSDK.atomicMove;
        ZeroBounceSDK.atomicMove = (from, to) -> {
            throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "no atomic rename");
        };
        try {
            Path target = tempDir.resolve("fallback").resolve("out.csv");
            AtomicReference<ZBGetFileResponse> saved = new AtomicReference<>();
            sdk(new AtomicInteger(), 200, "text/csv", CSV).getFile("fid", target.toString(), saved::set,
                    e -> { throw new AssertionError(e.getErrors().toString()); });
            assertNotNull(saved.get());
            assertEquals(CSV, Files.readString(target));
            try (var files = Files.list(target.getParent())) {
                assertEquals(List.of("out.csv"), files.map(p -> p.getFileName().toString()).toList());
            }
        } finally {
            ZeroBounceSDK.atomicMove = atomic;
        }
    }
}
