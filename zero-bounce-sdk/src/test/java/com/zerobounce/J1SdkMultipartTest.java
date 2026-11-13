package com.zerobounce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A non-ASCII file name goes out as UTF-8, not Latin-1, on every multipart upload,
 * and the form's top-level content type says so.
 */
class J1SdkMultipartTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "multipart-utf8-key";
    private static final byte[] ACCEPTED = ("{\"success\": true, \"message\": \"File Accepted\","
            + " \"file_name\": \"café.csv\", \"file_id\": \"f-1\"}").getBytes(StandardCharsets.UTF_8);
    private static final byte[] UTF8_NAME = "filename=\"café.csv\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LATIN1_NAME = "filename=\"café.csv\"".getBytes(StandardCharsets.ISO_8859_1);

    private interface Upload {
        void send(ZeroBounceSDK sdk, File file) throws Exception;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private void assertUtf8Name(String label, Upload upload) throws Exception {
        File file = tempDir.resolve("café.csv").toFile();
        Files.writeString(file.toPath(), "email\na@example.com\n", StandardCharsets.UTF_8);
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", ACCEPTED));
             MockedStatic<HttpClients> ignored = server.apacheClients()) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            upload.send(sdk, file);
            assertEquals(1, server.requests().size(), label);
            LoopbackServer.Request request = server.requests().get(0);
            String charset = request.headers.get("content-type");
            assertTrue(charset != null && charset.contains("charset=UTF-8"), label + " content type " + charset);
            assertTrue(indexOf(request.body, UTF8_NAME) >= 0, label + " file name is not UTF-8 on the wire");
            assertEquals(-1, indexOf(request.body, LATIN1_NAME), label + " file name is Latin-1 on the wire");
        }
    }

    @Test
    void everyMultipartUploadSendsANonAsciiFileNameAsUtf8() throws Exception {
        assertUtf8Name("sendFile(File)", (sdk, file) -> sdk.sendFile(file, 1, null, ok -> { }, e -> { }));
        assertUtf8Name("sendFile(InputStream)", (sdk, file) -> sdk.sendFile(
                new ByteArrayInputStream("email\na@example.com\n".getBytes(StandardCharsets.UTF_8)),
                "café.csv", 1, null, ok -> { }, e -> { }));
        assertUtf8Name("scoringSendFile(File)", (sdk, file) -> sdk.scoringSendFile(file, 1, null,
                ok -> { }, e -> { }));
        assertUtf8Name("sendEmailFinderFile", (sdk, file) -> sdk.sendEmailFinderFile(file, 1,
                new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2), ok -> { }, e -> { }));
        assertUtf8Name("sendDomainSearchFile", (sdk, file) -> sdk.sendDomainSearchFile(file, 1, null,
                ok -> { }, e -> { }));
        assertUtf8Name("sendListEvaluatorFile", (sdk, file) -> sdk.sendListEvaluatorFile(file, 1,
                ok -> { }, e -> { }));
    }
}
