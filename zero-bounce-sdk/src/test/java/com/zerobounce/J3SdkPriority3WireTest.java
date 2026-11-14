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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The six Priority3 uploads on the real HttpClient path: every text field and the api_key part
 * reach the wire with their exact values, and every request asks for JSON.
 */
class J3SdkPriority3WireTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "p3-wire-key";
    private static final byte[] ACCEPTED = ("{\"success\": true, \"message\": \"File Accepted\","
            + " \"file_name\": \"in.csv\", \"file_id\": \"f-1\"}").getBytes(StandardCharsets.UTF_8);
    private static final String CSV = "a,b\n1,2\n";

    private interface Upload {
        void send(ZeroBounceSDK sdk, File file) throws Exception;
    }

    /** The text parts of a multipart body by name; the file part is left out. */
    private static Map<String, String> textParts(LoopbackServer.Request request) {
        String type = request.headers.get("content-type");
        String boundary = type.substring(type.indexOf("boundary=") + 9).replace("\"", "");
        int semicolon = boundary.indexOf(';');
        if (semicolon >= 0) {
            boundary = boundary.substring(0, semicolon);
        }
        Map<String, String> parts = new LinkedHashMap<>();
        String body = new String(request.body, StandardCharsets.UTF_8);
        for (String part : body.split("--" + java.util.regex.Pattern.quote(boundary))) {
            int headEnd = part.indexOf("\r\n\r\n");
            if (headEnd < 0 || !part.contains("name=\"")) {
                continue;
            }
            String head = part.substring(0, headEnd);
            if (head.contains("filename=")) {
                continue;
            }
            int from = head.indexOf("name=\"") + 6;
            String name = head.substring(from, head.indexOf('"', from));
            String value = part.substring(headEnd + 4);
            if (value.endsWith("\r\n")) {
                value = value.substring(0, value.length() - 2);
            }
            parts.put(name, value);
        }
        return parts;
    }

    private void assertWire(String label, Map<String, String> expected, Upload upload) throws Exception {
        File file = tempDir.resolve("in.csv").toFile();
        Files.writeString(file.toPath(), CSV, StandardCharsets.UTF_8);
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", ACCEPTED));
             MockedStatic<HttpClients> ignored = server.apacheClients()) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            upload.send(sdk, file);
            assertEquals(1, server.requests().size(), label);
            LoopbackServer.Request request = server.requests().get(0);
            Map<String, String> want = new LinkedHashMap<>(expected);
            want.put("api_key", KEY);
            assertEquals(want, textParts(request), label + " text parts on the wire");
            assertEquals("application/json", request.headers.get("accept"), label + " Accept header");
        }
    }

    private static ZeroBounceSDK.EmailFinderSendFileOptions finderOptions() {
        return new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2).setLastNameColumn(3)
                .setMiddleNameColumn(4).setFullNameColumn(5).setHasHeaderRow(false);
    }

    private static Map<String, String> finderFields() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("domain_column", "1");
        m.put("has_header_row", "false");
        m.put("first_name_column", "2");
        m.put("last_name_column", "3");
        m.put("middle_name_column", "4");
        m.put("full_name_column", "5");
        return m;
    }

    private static ByteArrayInputStream stream() {
        return new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void emailFinderUploadsSendEveryFieldAndTheKey() throws Exception {
        assertWire("sendEmailFinderFile", finderFields(),
                (sdk, f) -> sdk.sendEmailFinderFile(f, 1, finderOptions(), ok -> { }, e -> { }));
        assertWire("sendEmailFinderFileStream", finderFields(),
                (sdk, f) -> sdk.sendEmailFinderFileStream(stream(), "in.csv", 1, finderOptions(), ok -> { }, e -> { }));
    }

    @Test
    void domainSearchUploadsSendEveryFieldAndTheKey() throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("domain_column", "7");
        fields.put("has_header_row", "true");
        ZeroBounceSDK.DomainSearchSendFileOptions options = new ZeroBounceSDK.DomainSearchSendFileOptions().setHasHeaderRow(true);
        assertWire("sendDomainSearchFile", fields,
                (sdk, f) -> sdk.sendDomainSearchFile(f, 7, options, ok -> { }, e -> { }));
        assertWire("sendDomainSearchFileStream", fields,
                (sdk, f) -> sdk.sendDomainSearchFileStream(stream(), "in.csv", 7, options, ok -> { }, e -> { }));
    }

    @Test
    void listEvaluatorUploadsSendTheFieldAndTheKey() throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("email_address_column", "9");
        assertWire("sendListEvaluatorFile", fields,
                (sdk, f) -> sdk.sendListEvaluatorFile(f, 9, ok -> { }, e -> { }));
        assertWire("sendListEvaluatorFileStream", fields,
                (sdk, f) -> sdk.sendListEvaluatorFileStream(stream(), "in.csv", 9, ok -> { }, e -> { }));
    }

    @Test
    void aPlainRequestAlsoAsksForJson() throws Exception {
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
             MockedStatic<HttpClients> ignored = server.apacheClients()) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            sdk.sendFile(stream(), "in.csv", 1, null, ok -> { }, e -> { });
            assertEquals("application/json", server.requests().get(0).headers.get("accept"));
        }
    }
}
