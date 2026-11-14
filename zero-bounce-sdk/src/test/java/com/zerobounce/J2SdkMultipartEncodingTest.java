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
 * What the real multipart uploads put on the wire: text fields are UTF-8 (not
 * Latin-1), a file name outside Latin-1 is sent only as {@code filename=} in UTF-8
 * (RFC 7578 section 4.2: no {@code filename*}), and every file part declares the
 * same content type on every upload path.
 */
class J2SdkMultipartEncodingTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "multipart-encoding-key";
    private static final byte[] ACCEPTED = ("{\"success\": true, \"message\": \"File Accepted\","
            + " \"file_name\": \"x.csv\", \"file_id\": \"f-1\"}").getBytes(StandardCharsets.UTF_8);
    /** The default binary type of httpmime, which FileBody and the Priority3 stream uploads use. */
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final String RETURN_URL = "https://example.com/return?n=ü€";

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

    /** The header value of the named part's Content-Type, or a marker when the part has none. */
    private static String partContentType(byte[] body, String field) {
        String text = new String(body, StandardCharsets.ISO_8859_1);
        int start = text.indexOf("Content-Disposition: form-data; name=\"" + field + "\"");
        if (start < 0) {
            return "<no " + field + " part>";
        }
        int end = text.indexOf("\r\n\r\n", start);
        for (String line : text.substring(start, end).split("\r\n")) {
            if (line.regionMatches(true, 0, "Content-Type:", 0, 13)) {
                return line.substring(13).trim();
            }
        }
        return "<no content type>";
    }

    /** Runs one upload against a loopback server; the file is named {@code fileName}, and the stream form uses the same name. */
    private LoopbackServer.Request send(String fileName, Upload upload) throws Exception {
        File file = tempDir.resolve(fileName).toFile();
        Files.writeString(file.toPath(), "email\na@example.com\n", StandardCharsets.UTF_8);
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", ACCEPTED));
             MockedStatic<HttpClients> ignored = server.apacheClients()) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            upload.send(sdk, file);
            assertEquals(1, server.requests().size(), fileName);
            return server.requests().get(0);
        }
    }

    private static byte[] stream(File file) throws Exception {
        return Files.readAllBytes(file.toPath());
    }

    @Test
    void aNonAsciiTextFieldGoesOutAsUtf8() throws Exception {
        List<String> failures = new ArrayList<>();
        ZeroBounceSDK.SendFileOptions options = new ZeroBounceSDK.SendFileOptions().setReturnUrl(RETURN_URL);
        ZeroBounceSDK.ScoringSendFileOptions scoring = new ZeroBounceSDK.ScoringSendFileOptions().setReturnUrl(RETURN_URL);
        byte[] utf8 = RETURN_URL.getBytes(StandardCharsets.UTF_8);
        Upload[] uploads = {
                (sdk, file) -> sdk.sendFile(file, 1, options, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendFile(new ByteArrayInputStream(stream(file)), file.getName(), 1, options,
                        ok -> { }, e -> { }),
                (sdk, file) -> sdk.scoringSendFile(file, 1, scoring, ok -> { }, e -> { }),
                (sdk, file) -> sdk.scoringSendFile(new ByteArrayInputStream(stream(file)), file.getName(), 1, scoring,
                        ok -> { }, e -> { }),
        };
        for (int i = 0; i < uploads.length; i++) {
            LoopbackServer.Request request = send("return" + i + ".csv", uploads[i]);
            // LEGACY writes no Content-Type on a text part, so the bytes are the whole contract.
            if (indexOf(request.body, utf8) < 0) {
                failures.add("upload " + i + " did not send the return_url as UTF-8");
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void aFileNameOutsideLatin1IsSentAsPlainUtf8FilenameOnly() throws Exception {
        String name = "résumé€🙂.csv";
        byte[] plain = ("filename=\"" + name + "\"").getBytes(StandardCharsets.UTF_8);
        Upload[] uploads = {
                (sdk, file) -> sdk.sendFile(file, 1, null, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendFile(new ByteArrayInputStream(stream(file)), file.getName(), 1, null,
                        ok -> { }, e -> { }),
                (sdk, file) -> sdk.scoringSendFile(file, 1, null, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendEmailFinderFile(file, 1,
                        new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2), ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendEmailFinderFileStream(new ByteArrayInputStream(stream(file)), file.getName(), 1,
                        new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2), ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendDomainSearchFile(file, 1, null, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendDomainSearchFileStream(new ByteArrayInputStream(stream(file)), file.getName(), 1,
                        null, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendListEvaluatorFile(file, 1, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendListEvaluatorFileStream(new ByteArrayInputStream(stream(file)), file.getName(), 1,
                        ok -> { }, e -> { }),
        };
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < uploads.length; i++) {
            LoopbackServer.Request request = send(name, uploads[i]);
            if (indexOf(request.body, "filename*".getBytes(StandardCharsets.ISO_8859_1)) >= 0) {
                failures.add("upload " + i + " sent filename* (RFC 7578 forbids it in LEGACY mode)");
            }
            if (indexOf(request.body, plain) < 0) {
                failures.add("upload " + i + " did not send filename= as UTF-8 bytes");
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void everyFilePartDeclaresTheSameContentType() throws Exception {
        Upload[] uploads = {
                (sdk, file) -> sdk.sendFile(file, 1, null, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendFile(new ByteArrayInputStream(stream(file)), file.getName(), 1, null,
                        ok -> { }, e -> { }),
                (sdk, file) -> sdk.scoringSendFile(file, 1, null, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendEmailFinderFile(file, 1,
                        new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2), ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendEmailFinderFileStream(new ByteArrayInputStream(stream(file)), file.getName(), 1,
                        new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2), ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendDomainSearchFile(file, 1, null, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendDomainSearchFileStream(new ByteArrayInputStream(stream(file)), file.getName(), 1,
                        null, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendListEvaluatorFile(file, 1, ok -> { }, e -> { }),
                (sdk, file) -> sdk.sendListEvaluatorFileStream(new ByteArrayInputStream(stream(file)), file.getName(), 1,
                        ok -> { }, e -> { }),
        };
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < uploads.length; i++) {
            String type = partContentType(send("file" + i + ".csv", uploads[i]).body, "file");
            if (!OCTET_STREAM.equals(type)) {
                failures.add("upload " + i + " file part is " + type);
            }
        }
        assertEquals(List.of(), failures);
    }
}
