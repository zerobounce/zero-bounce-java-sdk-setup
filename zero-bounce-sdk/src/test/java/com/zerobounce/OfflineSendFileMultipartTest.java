package com.zerobounce;

import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The multipart request {@code sendFile} and {@code scoringSendFile} really send,
 * read back by a real HTTP server on a loopback socket: every field's name,
 * value and order, and the file part's name and bytes. The offline transport
 * sees a map of the same fields; this checks the bytes Apache HttpClient puts on
 * the wire.
 */
class OfflineSendFileMultipartTest {

    @TempDir
    Path tempDir;

    private static final String KEY = "multipart-key";
    private static final String CSV = "email,first,last,gender,ip\na@example.com,A,B,f,1.2.3.4\n";
    private static final byte[] ACCEPTED = ("{\"success\": true, \"message\": \"File Accepted\","
            + " \"file_name\": \"in.csv\", \"file_id\": \"f-1\"}").getBytes(StandardCharsets.UTF_8);

    /** One part of a multipart/form-data body. */
    private static final class Part {
        final String name;
        final String fileName;
        final String value;

        Part(String name, String fileName, String value) {
            this.name = name;
            this.fileName = fileName;
            this.value = value;
        }

        @Override
        public String toString() {
            return name + (fileName == null ? "" : "[" + fileName + "]") + "=" + value;
        }
    }

    private static String quoted(String header, String attribute) {
        String marker = attribute + "=\"";
        int start = header.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        return header.substring(start, header.indexOf('"', start));
    }

    private static List<Part> parts(LoopbackServer.Request request) {
        String contentType = request.headers.get("content-type");
        assertTrue(contentType != null && contentType.startsWith("multipart/form-data"), String.valueOf(contentType));
        String boundary = "--" + contentType.substring(contentType.indexOf("boundary=") + 9).replace("\"", "");
        String body = new String(request.body, StandardCharsets.UTF_8);
        List<Part> parts = new ArrayList<>();
        for (String chunk : body.split(java.util.regex.Pattern.quote(boundary))) {
            if (chunk.isEmpty() || chunk.startsWith("--")) {
                continue;
            }
            int split = chunk.indexOf("\r\n\r\n");
            String headers = chunk.substring(0, split);
            String value = chunk.substring(split + 4, chunk.length() - 2);
            String disposition = Arrays.stream(headers.split("\r\n"))
                    .filter(h -> h.toLowerCase(Locale.ROOT).startsWith("content-disposition:"))
                    .findFirst().orElseThrow();
            parts.add(new Part(quoted(disposition, "name"), quoted(disposition, "filename"), value));
        }
        return parts;
    }

    private interface Upload {
        void send(ZeroBounceSDK sdk, File file, List<Object> successes, List<ErrorResponse> errors) throws Exception;
    }

    private String upload(Upload upload) throws Exception {
        File file = tempDir.resolve("in.csv").toFile();
        Files.writeString(file.toPath(), CSV);
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.answer(200, "application/json", ACCEPTED));
             MockedStatic<HttpClients> ignored = server.apacheClients()) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize(KEY);
            List<Object> successes = new ArrayList<>();
            List<ErrorResponse> errors = new ArrayList<>();
            upload.send(sdk, file, successes, errors);
            assertEquals(1, successes.size(), "successes " + successes + " errors " + errors);
            assertEquals(1, server.requests().size());
            LoopbackServer.Request request = server.requests().get(0);
            assertEquals("POST", request.method);
            return request.target + " " + parts(request);
        }
    }

    private static ZeroBounceSDK.SendFileOptions allOptions() {
        return new ZeroBounceSDK.SendFileOptions()
                .setReturnUrl("https://example.com/done")
                .setFirstNameColumn(2)
                .setLastNameColumn(3)
                .setGenderColumn(4)
                .setIpAddressColumn(5)
                .setHasHeaderRow(true)
                .setRemoveDuplicate(false)
                .setAllowPhase2(true);
    }

    private static ZeroBounceSDK.ScoringSendFileOptions allScoringOptions() {
        return new ZeroBounceSDK.ScoringSendFileOptions()
                .setReturnUrl("https://example.com/scored")
                .setHasHeaderRow(true)
                .setRemoveDuplicate(true);
    }

    private static final String VALIDATION_FIELDS = "[api_key=" + KEY + ", email_address_column=1,"
            + " return_url=https://example.com/done, first_name_column=2, last_name_column=3,"
            + " gender_column=4, ip_address_column=5, has_header_row=true, remove_duplicate=false,"
            + " allow_phase_2=true, file[in.csv]=" + CSV + "]";
    private static final String SCORING_FIELDS = "[api_key=" + KEY + ", email_address_column=1,"
            + " return_url=https://example.com/scored, has_header_row=true, remove_duplicate=true,"
            + " file[in.csv]=" + CSV + "]";

    @Test
    void sendFileSendsEveryFieldOnTheWire() throws Exception {
        assertEquals("/v2/sendfile " + VALIDATION_FIELDS, upload((sdk, file, ok, err) ->
                sdk.sendFile(file, 1, allOptions(), ok::add, err::add)));
        assertEquals("/v2/sendfile " + VALIDATION_FIELDS, upload((sdk, file, ok, err) ->
                sdk.sendFile(new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)), "in.csv", 1,
                        allOptions(), ok::add, err::add)));
        // The email column is sent as given (1-based), and only the set options go out.
        String plain = "[api_key=" + KEY + ", email_address_column=3, file[in.csv]=" + CSV + "]";
        assertEquals("/v2/sendfile " + plain, upload((sdk, file, ok, err) ->
                sdk.sendFile(file, 3, null, ok::add, err::add)));
        assertEquals("/v2/sendfile " + plain, upload((sdk, file, ok, err) ->
                sdk.sendFile(new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)), "in.csv", 3,
                        null, ok::add, err::add)));
    }

    @Test
    void scoringSendFileSendsEveryFieldOnTheWire() throws Exception {
        assertEquals("/v2/scoring/sendfile " + SCORING_FIELDS, upload((sdk, file, ok, err) ->
                sdk.scoringSendFile(file, 1, allScoringOptions(), ok::add, err::add)));
        assertEquals("/v2/scoring/sendfile " + SCORING_FIELDS, upload((sdk, file, ok, err) ->
                sdk.scoringSendFile(new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)), "in.csv", 1,
                        allScoringOptions(), ok::add, err::add)));
        String plain = "[api_key=" + KEY + ", email_address_column=2, file[in.csv]=" + CSV + "]";
        assertEquals("/v2/scoring/sendfile " + plain, upload((sdk, file, ok, err) ->
                sdk.scoringSendFile(file, 2, null, ok::add, err::add)));
    }

    /**
     * Options passed with {@code removeDuplicate} unset send no
     * {@code remove_duplicate} (the API defaults it to true); only the set
     * options go out (check R2).
     */
    @Test
    void anUnsetRemoveDuplicateIsNotSentWhenOptionsArePassed() throws Exception {
        String validation = "[api_key=" + KEY + ", email_address_column=1, return_url=https://example.com/done,"
                + " file[in.csv]=" + CSV + "]";
        ZeroBounceSDK.SendFileOptions options = new ZeroBounceSDK.SendFileOptions()
                .setReturnUrl("https://example.com/done");
        assertEquals("/v2/sendfile " + validation, upload((sdk, file, ok, err) ->
                sdk.sendFile(file, 1, options, ok::add, err::add)));
        assertEquals("/v2/sendfile " + validation, upload((sdk, file, ok, err) ->
                sdk.sendFile(new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)), "in.csv", 1,
                        options, ok::add, err::add)));
        // Scoring options have always sent has_header_row (default false); set it here
        // so the check is about remove_duplicate alone.
        String scoring = "[api_key=" + KEY + ", email_address_column=1, return_url=https://example.com/scored,"
                + " has_header_row=true, file[in.csv]=" + CSV + "]";
        ZeroBounceSDK.ScoringSendFileOptions scoringOptions = new ZeroBounceSDK.ScoringSendFileOptions()
                .setReturnUrl("https://example.com/scored")
                .setHasHeaderRow(true);
        assertEquals("/v2/scoring/sendfile " + scoring, upload((sdk, file, ok, err) ->
                sdk.scoringSendFile(file, 1, scoringOptions, ok::add, err::add)));
        assertEquals("/v2/scoring/sendfile " + scoring, upload((sdk, file, ok, err) ->
                sdk.scoringSendFile(new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)), "in.csv", 1,
                        scoringOptions, ok::add, err::add)));
    }

    /** The four overloads above are all there are; a new one must be added here. */
    @Test
    void everySendFileOverloadIsCovered() {
        List<String> overloads = Arrays.stream(ZeroBounceSDK.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getName().equals("sendFile") || m.getName().equals("scoringSendFile"))
                .map(RequestSweep::signature)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of(
                "scoringSendFile(File,int,ScoringSendFileOptions)",
                "scoringSendFile(InputStream,String,int,ScoringSendFileOptions)",
                "sendFile(File,int,SendFileOptions)",
                "sendFile(InputStream,String,int,SendFileOptions)"), overloads);
    }
}
