package com.zerobounce;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflineApiTest {

    @TempDir
    Path tempDir;

    @Test
    void validateUsesGetAndOmitsUnsetOptions() {
        Probe probe = probe("key-a");
        ZBValidateResponse response = probe.success(
                (ok, err) -> probe.sdk.validate("person@example.com", null, ok, err)
        );
        assertEquals("valid", response.getStatus().name().toLowerCase());
        FakeApi.RecordedRequest request = probe.only();
        assertEquals("GET", request.method);
        assertEquals("api.zerobounce.net", request.host);
        assertEquals("/v2/validate", request.path);
        assertEquals("key-a", request.apiKey);
        assertEquals("person@example.com", request.field("email"));
        assertEquals("", request.field("ip_address"));
        assertNull(request.field("timeout"));
        assertNull(request.field("activity_data"));
        assertNull(request.field("verify_plus"));
        assertFalse(request.followRedirects);
    }

    @Test
    void validateSendsZeroTimeoutFalseFlagsAndPostForm() {
        Probe probe = probe("key a/b");
        ZBValidateRequestOptions options = new ZBValidateRequestOptions();
        options.setIpAddress("203.0.113.5");
        options.setTimeout(0);
        options.setActivityData(false);
        options.setVerifyPlus(true);
        options.setUsePost(true);
        probe.success((ok, err) -> probe.sdk.validateWithOptions("a b+c@example.com", options, ok, err));
        FakeApi.RecordedRequest request = probe.only();
        assertEquals("POST", request.method);
        assertEquals("/v2/validate", request.path);
        assertEquals("api.zerobounce.net", request.host);
        assertEquals("application/x-www-form-urlencoded", request.contentType);
        assertEquals("key a/b", request.apiKey);
        assertEquals("a b+c@example.com", request.field("email"));
        assertEquals("0", request.field("timeout"));
        assertEquals("false", request.field("activity_data"));
        assertEquals("true", request.field("verify_plus"));
        assertTrue(request.rawBody.contains("email="));
        assertFalse(request.rawBody.contains(" "));
        assertFalse(request.url.contains(" "));
    }

    @Test
    void validateBatchStaysOnApiHostAndKeepsPartialErrors() {
        Probe probe = probe("batchitem-key");
        ZBValidateBatchOptions options = new ZBValidateBatchOptions();
        options.setTimeout(30);
        options.setActivityData(false);
        options.setVerifyPlus(true);
        ZBValidateBatchResponse response = probe.success((ok, err) -> probe.sdk.validateBatchWithOptions(
                List.of(new ZBValidateBatchData("ok@example.com", null)),
                options,
                ok,
                err
        ));
        assertEquals(2, response.getEmailBatch().size());
        assertEquals(1, response.getErrors().size());
        assertEquals("bad@example.com", String.valueOf(response.getErrors().get(0).get("email_address")));
        FakeApi.RecordedRequest request = probe.only();
        assertEquals("POST", request.method);
        assertEquals("api.zerobounce.net", request.host);
        assertEquals("/v2/validatebatch", request.path);
        assertNotEquals("bulkapi.zerobounce.net", request.host);
        JsonObject body = JsonParser.parseString(request.rawBody).getAsJsonObject();
        // LIVE: the batch timeout goes out as a JSON *string*; a number is
        // rejected with the account-level error.
        assertTrue(body.get("timeout").getAsJsonPrimitive().isString());
        assertEquals("30", body.get("timeout").getAsString());
        assertFalse(body.get("activity_data").getAsBoolean());
        assertTrue(body.get("verify_plus").getAsBoolean());
        assertFalse(body.has("ip_address"));
    }

    @Test
    void validateBatchAccountErrorIsAnError() {
        Probe probe = probe("batchall-key");
        ErrorResponse error = probe.failure((ok, err) -> probe.sdk.validateBatch(
                List.of(new ZBValidateBatchData("a@example.com", "203.0.113.8")),
                ok,
                err
        ));
        assertShort(error, "batchall-key");
        assertTrue(error.getErrors().stream().anyMatch(message -> message.contains("Invalid API Key")));
    }

    @Test
    void accountLevelSuccessBodiesAreErrors() {
        Probe invalid = probe("badkey-1");
        ErrorResponse invalidKey = invalid.failure((ok, err) -> invalid.sdk.getCredits(ok, err));
        assertShort(invalidKey, "badkey-1");
        assertTrue(invalidKey.getErrors().get(0).contains("Invalid API Key"));

        Probe credits = probe("nocredits-1");
        ErrorResponse noCredits = credits.failure((ok, err) -> credits.sdk.validate("a@example.com", "", ok, err));
        assertShort(noCredits, "nocredits-1");
        assertTrue(noCredits.getErrors().get(0).contains("credits"));
    }

    @Test
    void htmlEmptyAndMalformedBodiesStayShort() throws Exception {
        Probe denied = probe("html403-secret");
        ErrorResponse accessDenied = denied.failure((ok, err) -> denied.sdk.getCredits(ok, err));
        assertShort(accessDenied, "html403-secret");
        assertTrue(accessDenied.getErrors().get(0).contains("Access denied"));

        Probe htmlOk = probe("html200-secret");
        Path download = tempDir.resolve("html.csv");
        ErrorResponse htmlFile = htmlOk.failure((ok, err) -> htmlOk.sdk.getFile("fid", download.toString(), ok, err));
        assertShort(htmlFile, "html200-secret");
        assertFalse(Files.exists(download));

        Probe finder = probe("html200-finder");
        Path finderFile = tempDir.resolve("finder.csv");
        ErrorResponse finderError = finder.failure((ok, err) -> finder.sdk.emailFinderGetFile("fid", finderFile.toString(), ok, err));
        assertShort(finderError, "html200-finder");
        assertFalse(Files.exists(finderFile));

        Probe domain = probe("html403-domain");
        Path domainFile = tempDir.resolve("domain.csv");
        ErrorResponse domainError = domain.failure((ok, err) -> domain.sdk.domainSearchGetFile("fid", domainFile.toString(), ok, err));
        assertShort(domainError, "html403-domain");
        assertFalse(Files.exists(domainFile));

        Probe empty = probe("empty-key");
        ErrorResponse emptyError = empty.failure((ok, err) -> empty.sdk.scoreEmail("a@example.com", ok, err));
        assertShort(emptyError, "empty-key");
        assertTrue(emptyError.getErrors().get(0).toLowerCase().contains("empty"));

        Probe malformed = probe("malformed-key");
        ErrorResponse malformedError = malformed.failure((ok, err) -> malformed.sdk.getActivityData("a@example.com", ok, err));
        assertShort(malformedError, "malformed-key");
        assertFalse(malformedError.getErrors().get(0).contains("JsonSyntax"));
        assertFalse(malformedError.getErrors().get(0).contains("IllegalStateException"));

        Probe prefixed = probe("errprefix-key");
        ErrorResponse prefixedError = prefixed.failure((ok, err) -> prefixed.sdk.getCredits(ok, err));
        assertEquals(List.of("boom"), prefixedError.getErrors());

        Probe cloudflare = probe("cf-json-secret");
        ErrorResponse cloudflareError = cloudflare.failure((ok, err) -> cloudflare.sdk.getCredits(ok, err));
        assertShort(cloudflareError, "cf-json-secret");
        String cloudflareText = String.join("\n", cloudflareError.getErrors());
        assertTrue(cloudflareText.contains("Access denied"));
        assertFalse(cloudflareText.contains("developers.cloudflare.com"));
    }

    @Test
    void findersRejectBlankInputAndOmitUnsetFields() {
        Probe missing = probe("key");
        ErrorResponse error = missing.failure((ok, err) -> missing.sdk.findEmail("Ada", "  ", "", null, null, ok, err));
        assertEquals(0, missing.api.records().size());
        assertFalse(error.getErrors().isEmpty());

        Probe domain = probe("key");
        ErrorResponse domainError = domain.failure((ok, err) -> domain.sdk.findDomain(" ", null, ok, err));
        assertEquals(0, domain.api.records().size());
        assertFalse(domainError.getErrors().isEmpty());

        Probe probe = probe("key");
        probe.success((ok, err) -> probe.sdk.findEmail("Ada", "", "Acme", null, "", ok, err));
        FakeApi.RecordedRequest request = probe.only();
        assertEquals("/v2/guessformat", request.path);
        assertEquals("Ada", request.field("first_name"));
        assertEquals("Acme", request.field("company_name"));
        assertNull(request.field("domain"));
        assertNull(request.field("middle_name"));
        assertNull(request.field("last_name"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.findDomain("example.com", null, ok, err));
        assertEquals("example.com", probe.only().field("domain"));
        assertNull(probe.only().field("company_name"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.guessFormat("example.com", null, null, "Lovelace", ok, err));
        FakeApi.RecordedRequest guess = probe.only();
        assertEquals("GET", guess.method);
        assertEquals("/v2/guessformat", guess.path);
        assertNull(guess.field("first_name"));
        assertEquals("Lovelace", guess.field("last_name"));
    }

    @Test
    void usageCreditsActivityAndScoring() {
        Probe probe = probe("usage-key");
        Date start = Date.from(LocalDate.of(2020, 1, 2).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(LocalDate.of(2020, 1, 3).atStartOfDay(ZoneId.systemDefault()).toInstant());
        probe.success((ok, err) -> probe.sdk.getApiUsage(start, end, ok, err));
        FakeApi.RecordedRequest usage = probe.only();
        assertEquals("/v2/getapiusage", usage.path);
        assertEquals("2020-01-02", usage.field("start_date"));
        assertEquals("2020-01-03", usage.field("end_date"));

        probe.api.clear();
        ZBCreditsResponse credits = probe.success((ok, err) -> probe.sdk.getCredits(ok, err));
        assertEquals("usage-key", credits.getCredits());
        assertEquals("/v2/getcredits", probe.only().path);

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.getActivityData("person@example.com", ok, err));
        assertEquals("/v2/activity", probe.only().path);

        probe.api.clear();
        ZBScoreEmailResponse scored = probe.success((ok, err) -> probe.sdk.scoreEmail("person@example.com", ok, err));
        assertEquals("9", scored.getScore());
        assertEquals("GET", probe.only().method);
        assertEquals("/v2/scoring", probe.only().path);

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.scoreEmailPost("person@example.com", ok, err));
        FakeApi.RecordedRequest posted = probe.only();
        assertEquals("POST", posted.method);
        assertEquals("/v2/scoring", posted.path);
        assertEquals("application/x-www-form-urlencoded", posted.contentType);
        assertEquals("person@example.com", posted.field("email"));
    }

    @Test
    void bulkFileRoundTripAndBooleans() throws Exception {
        Probe probe = probe("file-key");
        File csv = tempDir.resolve("emails.csv").toFile();
        Files.writeString(csv.toPath(), "email\na@example.com\n");
        ZeroBounceSDK.SendFileOptions options = new ZeroBounceSDK.SendFileOptions()
                .setHasHeaderRow(false)
                .setRemoveDuplicate(true)
                .setAllowPhase2(false)
                .setFirstNameColumn(0)
                .setReturnUrl("https://example.com/hook");
        ZBSendFileResponse sent = probe.success((ok, err) -> {
            try {
                probe.sdk.sendFile(csv, 1, options, ok, err);
            } catch (ZBException e) {
                fail(e);
            }
        });
        assertEquals("fid", sent.getFileId());
        FakeApi.RecordedRequest upload = probe.only();
        assertEquals("POST", upload.method);
        assertEquals("bulkapi.zerobounce.net", upload.host);
        assertEquals("/v2/sendfile", upload.path);
        assertEquals("multipart/form-data", upload.contentType);
        assertEquals("1", upload.field("email_address_column"));
        assertEquals("false", upload.field("has_header_row"));
        assertEquals("true", upload.field("remove_duplicate"));
        assertEquals("false", upload.field("allow_phase_2"));
        assertNull(upload.field("first_name_column"));
        assertEquals("emails.csv", upload.field("file"));

        probe.api.clear();
        assertThrows(ZBException.class, () -> probe.sdk.sendFile(csv, 0, null, r -> fail(), e -> fail()));
        assertEquals(0, probe.api.records().size());

        probe.api.clear();
        byte[] bytes = "email\nb@example.com\n".getBytes(StandardCharsets.UTF_8);
        probe.success((ok, err) -> {
            try {
                probe.sdk.sendFile(new ByteArrayInputStream(bytes), "stream.csv", 2, null, ok, err);
            } catch (ZBException e) {
                fail(e);
            }
        });
        assertEquals("2", probe.only().field("email_address_column"));
        assertEquals("stream.csv", probe.only().field("file"));
        assertNull(probe.only().field("allow_phase_2"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.fileStatus("fid", ok, err));
        assertEquals("/v2/filestatus", probe.only().path);
        assertEquals("fid", probe.only().field("file_id"));

        probe.api.clear();
        Path out = tempDir.resolve("out.csv");
        ZBGetFileOptions downloadOptions = new ZBGetFileOptions()
                .setDownloadType(ZBDownloadType.PHASE_1)
                .setActivityData(false);
        probe.success((ok, err) -> probe.sdk.getFile("fid", out.toString(), downloadOptions, ok, err));
        FakeApi.RecordedRequest download = probe.only();
        assertEquals("/v2/getfile", download.path);
        assertEquals("phase_1", download.field("download_type"));
        assertEquals("false", download.field("activity_data"));
        assertEquals("email,status\nok@example.com,valid\n", Files.readString(out));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.deleteFile("fid", ok, err));
        assertEquals("/v2/deletefile", probe.only().path);
    }

    @Test
    void scoringBulkOmitsValidationOnlyFields() throws Exception {
        Probe probe = probe("score-file");
        File csv = tempDir.resolve("score.csv").toFile();
        Files.writeString(csv.toPath(), "email\na@example.com\n");
        ZeroBounceSDK.ScoringSendFileOptions options = new ZeroBounceSDK.ScoringSendFileOptions()
                .setHasHeaderRow(false)
                .setRemoveDuplicate(false);
        probe.success((ok, err) -> {
            try {
                probe.sdk.scoringSendFile(csv, 1, options, ok, err);
            } catch (ZBException e) {
                fail(e);
            }
        });
        FakeApi.RecordedRequest upload = probe.only();
        assertEquals("/v2/scoring/sendfile", upload.path);
        assertEquals("false", upload.field("has_header_row"));
        assertEquals("false", upload.field("remove_duplicate"));
        assertNull(upload.field("allow_phase_2"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.scoringFileStatus("fid", ok, err));
        assertEquals("/v2/scoring/filestatus", probe.only().path);

        probe.api.clear();
        Path out = tempDir.resolve("score-out.csv");
        probe.success((ok, err) -> probe.sdk.scoringGetFile(
                "fid",
                out.toString(),
                new ZBGetFileOptions().setDownloadType(ZBDownloadType.COMBINED).setActivityData(true),
                ok,
                err
        ));
        FakeApi.RecordedRequest download = probe.only();
        assertEquals("/v2/scoring/getfile", download.path);
        assertEquals("combined", download.field("download_type"));
        assertNull(download.field("activity_data"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.scoringDeleteFile("fid", ok, err));
        assertEquals("/v2/scoring/deletefile", probe.only().path);
    }

    @Test
    void priority3EndpointsAndFalseHeaderFlag() throws Exception {
        Probe probe = probe("p3");
        File csv = tempDir.resolve("people.csv").toFile();
        Files.writeString(csv.toPath(), "domain\nexample.com\n");
        ZeroBounceSDK.EmailFinderSendFileOptions finderOptions = new ZeroBounceSDK.EmailFinderSendFileOptions()
                .setHasHeaderRow(false)
                .setFirstNameColumn(0)
                .setFullNameColumn(3);
        probe.success((ok, err) -> probe.sdk.sendEmailFinderFile(csv, 1, finderOptions, ok, err));
        FakeApi.RecordedRequest finder = probe.only();
        assertEquals("bulkapi.zerobounce.net", finder.host);
        assertEquals("/email-finder/sendfile", finder.path);
        assertEquals("1", finder.field("domain_column"));
        assertEquals("false", finder.field("has_header_row"));
        assertNull(finder.field("first_name_column"));
        assertEquals("3", finder.field("full_name_column"));
        assertEquals("people.csv", finder.field("file"));

        probe.api.clear();
        InputStream stream = new ByteArrayInputStream("domain\nexample.com\n".getBytes(StandardCharsets.UTF_8));
        probe.success((ok, err) -> probe.sdk.sendEmailFinderFileStream(stream, "names.csv", 2, null, ok, err));
        assertEquals("names.csv", probe.only().field("file"));
        assertNull(probe.only().field("has_header_row"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.emailFinderFileStatus("fid", ok, err));
        assertEquals("/email-finder/filestatus", probe.only().path);
        probe.api.clear();
        Path found = tempDir.resolve("found.csv");
        probe.success((ok, err) -> probe.sdk.emailFinderGetFile("fid", found.toString(), ok, err));
        assertEquals("/email-finder/getfile", probe.only().path);
        assertTrue(Files.size(found) > 0);
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.emailFinderDeleteFile("fid", ok, err));
        assertEquals("/email-finder/deletefile", probe.only().path);

        probe.api.clear();
        ZeroBounceSDK.DomainSearchSendFileOptions domainOptions = new ZeroBounceSDK.DomainSearchSendFileOptions()
                .setHasHeaderRow(false);
        probe.success((ok, err) -> probe.sdk.sendDomainSearchFile(csv, 4, domainOptions, ok, err));
        FakeApi.RecordedRequest domain = probe.only();
        assertEquals("/domain-search/sendfile", domain.path);
        assertEquals("4", domain.field("domain_column"));
        assertEquals("false", domain.field("has_header_row"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.sendDomainSearchFileStream(
                new ByteArrayInputStream("d\n".getBytes(StandardCharsets.UTF_8)),
                "domains.csv",
                1,
                null,
                ok,
                err
        ));
        assertEquals("domains.csv", probe.only().field("file"));
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.domainSearchFileStatus("fid", ok, err));
        assertEquals("/domain-search/filestatus", probe.only().path);
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.domainSearchGetFile("fid", tempDir.resolve("d.csv").toString(), ok, err));
        assertEquals("/domain-search/getfile", probe.only().path);
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.domainSearchDeleteFile("fid", ok, err));
        assertEquals("/domain-search/deletefile", probe.only().path);

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.sendListEvaluatorFile(csv, 1, ok, err));
        assertEquals("/v2/listevaluator/", probe.only().path);
        assertEquals("1", probe.only().field("email_address_column"));
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.sendListEvaluatorFileStream(
                new ByteArrayInputStream("e\n".getBytes(StandardCharsets.UTF_8)),
                "list.csv",
                1,
                ok,
                err
        ));
        assertEquals("list.csv", probe.only().field("file"));
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.getListEvaluatorResult("fid", ok, err));
        assertEquals("/v2/listevaluator/fid/", probe.only().path);
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.sendEvaluatedFile(csv, ok, err));
        assertEquals("/v2/send-evaluated-file/", probe.only().path);

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.addFilter("exclude", "email", "spam@", ok, err));
        FakeApi.RecordedRequest added = probe.only();
        assertEquals("POST", added.method);
        assertEquals("/v2/filters/add", added.path);
        assertEquals("exclude", added.field("rule"));
        assertEquals("spam@", added.field("value"));
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.listFilters(false, ok, err));
        assertEquals("GET", probe.only().method);
        assertEquals("/v2/filters/list", probe.only().path);
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.listFilters(true, ok, err));
        assertEquals("POST", probe.only().method);
        assertEquals("application/x-www-form-urlencoded", probe.only().contentType);
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.deleteFilter("exclude", "email", "spam@", ok, err));
        assertEquals("/v2/filters/delete", probe.only().path);
    }

    @Test
    void s3SendsZeroEmailColumnAndOmitsUnsetColumns() {
        Probe probe = probe("s3-key");
        probe.success((ok, err) -> probe.sdk.listS3Files(null, "  ", ok, err));
        FakeApi.RecordedRequest listed = probe.only();
        assertEquals("bulkapi.zerobounce.net", listed.host);
        assertEquals("/listS3files", listed.path);
        assertNull(listed.field("bucket"));
        assertNull(listed.field("search"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.listS3Files("inbox", "csv", ok, err));
        assertEquals("inbox", probe.only().field("bucket"));
        assertEquals("csv", probe.only().field("search"));

        probe.api.clear();
        ZeroBounceSDK.ValidateS3FileOptions validate = new ZeroBounceSDK.ValidateS3FileOptions()
                .setFileName("emails.csv")
                .setEmailAddressColumn(0)
                .setHasHeaderRow(false)
                .setAllowPhase2(false)
                .setFirstNameColumn(0);
        probe.success((ok, err) -> probe.sdk.validateS3File(validate, ok, err));
        FakeApi.RecordedRequest validation = probe.only();
        assertEquals("POST", validation.method);
        assertEquals("/validateS3file", validation.path);
        assertEquals("0", validation.field("email_address_column"));
        assertEquals("false", validation.field("has_header_row"));
        assertEquals("false", validation.field("allow_phase_2"));
        assertNull(validation.field("first_name_column"));

        probe.api.clear();
        ZeroBounceSDK.ScoreS3FileOptions score = new ZeroBounceSDK.ScoreS3FileOptions()
                .setFileName("emails.csv")
                .setRemoveDuplicate(false);
        probe.success((ok, err) -> probe.sdk.scoreS3File(score, ok, err));
        FakeApi.RecordedRequest scoring = probe.only();
        assertEquals("/scoreS3file", scoring.path);
        assertEquals("false", scoring.field("remove_duplicate"));
        assertNull(scoring.field("allow_phase_2"));
        assertNull(scoring.field("email_address_column"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.exportS3File("fid", "bucket", ok, err));
        FakeApi.RecordedRequest exported = probe.only();
        assertEquals("/exportS3file", exported.path);
        assertEquals("fid", exported.field("file_id"));
        assertEquals("bucket", exported.field("export_bucket"));
    }

    @Test
    void invalidArgumentsDoNotSend() throws Exception {
        Probe blankKey = probe("");
        ErrorResponse blank = blankKey.failure((ok, err) -> blankKey.sdk.getCredits(ok, err));
        assertEquals(0, blankKey.api.records().size());
        assertFalse(blank.getErrors().isEmpty());

        ZeroBounceSDK uninitialized = new ZeroBounceSDK();
        uninitialized.transport = new FakeApi();
        AtomicReference<ErrorResponse> initError = new AtomicReference<>();
        uninitialized.getCredits(response -> fail(String.valueOf(response)), initError::set);
        assertNotNull(initError.get());
        assertEquals(0, ((FakeApi) uninitialized.transport).records().size());

        Probe probe = probe("key");
        ErrorResponse email = probe.failure((ok, err) -> probe.sdk.validate("  ", null, ok, err));
        assertEquals(0, probe.api.records().size());
        assertFalse(email.getErrors().isEmpty());

        ErrorResponse score = probe.failure((ok, err) -> probe.sdk.scoreEmailPost("", ok, err));
        assertEquals(0, probe.api.records().size());
        assertFalse(score.getErrors().isEmpty());

        ErrorResponse status = probe.failure((ok, err) -> probe.sdk.fileStatus("  ", ok, err));
        assertEquals(0, probe.api.records().size());

        File csv = tempDir.resolve("blank.csv").toFile();
        Files.writeString(csv.toPath(), "a\n");
        ErrorResponse finder = probe.failure((ok, err) -> probe.sdk.sendEmailFinderFile(csv, 0, null, ok, err));
        assertEquals(0, probe.api.records().size());
        assertFalse(finder.getErrors().isEmpty());

        ErrorResponse filter = probe.failure((ok, err) -> probe.sdk.addFilter("", "email", "x", ok, err));
        assertEquals(0, probe.api.records().size());

        ErrorResponse s3 = probe.failure((ok, err) -> probe.sdk.validateS3File(
                new ZeroBounceSDK.ValidateS3FileOptions().setFileName("  "),
                ok,
                err
        ));
        assertEquals(0, probe.api.records().size());
        assertFalse(s3.getErrors().isEmpty());

        ErrorResponse exported = probe.failure((ok, err) -> probe.sdk.exportS3File("fid", " ", ok, err));
        assertEquals(0, probe.api.records().size());
    }

    @Test
    void separateClientsDoNotMixKeys() throws Exception {
        Probe first = probe("key-one");
        Probe second = probe("key-two");
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<ZBCreditsResponse> firstResponse = new AtomicReference<>();
        AtomicReference<ZBCreditsResponse> secondResponse = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread one = new Thread(() -> runCredits(first, start, done, firstResponse, failure));
        Thread two = new Thread(() -> runCredits(second, start, done, secondResponse, failure));
        one.start();
        two.start();
        start.countDown();
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
        one.join();
        two.join();
        assertNull(failure.get());
        assertEquals("key-one", firstResponse.get().getCredits());
        assertEquals("key-two", secondResponse.get().getCredits());
        assertEquals("key-one", first.only().apiKey);
        assertEquals("key-two", second.only().apiKey);
    }

    @Test
    void urlConnectionDoesNotFollowRedirects() throws Exception {
        ZeroBounceSDK sdk = spy(new ZeroBounceSDK());
        sdk.initialize("key");
        URL url = mock(URL.class);
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(url.openConnection()).thenReturn(connection);
        when(connection.getResponseCode()).thenReturn(200);
        when(connection.getInputStream()).thenReturn(new ByteArrayInputStream("{\"Credits\":\"1\"}".getBytes(StandardCharsets.UTF_8)));
        doReturn(url).when(sdk).createUrlFrom(anyString());
        sdk.getCredits(response -> assertEquals("1", response.getCredits()), error -> fail(error.getErrors().toString()));
        verify(connection).setInstanceFollowRedirects(false);
    }

    @Test
    void apacheDownloadDoesNotFollowRedirects() throws Exception {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.initialize("key");
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        StatusLine status = mock(StatusLine.class);
        ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
        when(client.execute(captor.capture())).thenReturn(response);
        when(response.getEntity()).thenReturn(entity);
        when(response.getStatusLine()).thenReturn(status);
        when(status.getStatusCode()).thenReturn(200);
        when(response.getFirstHeader("Content-Type")).thenReturn(null);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream("email\n".getBytes(StandardCharsets.UTF_8)));
        try (MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class)) {
            clients.when(HttpClients::createDefault).thenReturn(client);
            Path out = tempDir.resolve("apache.csv");
            sdk.getFile("fid", out.toString(), responseBody -> {
            }, error -> fail(error.getErrors().toString()));
        }
        RequestConfig config = ((HttpRequestBase) captor.getValue()).getConfig();
        assertNotNull(config);
        assertFalse(config.isRedirectsEnabled());
    }

    @Test
    void validateResponseEqualityIncludesActivityFields() {
        ZBValidateResponse left = new ZBValidateResponse();
        left.setAddress("a@example.com");
        left.setActiveInDays("5");
        left.setActiveFirstSeen("2024-01-01");
        ZBValidateResponse right = new ZBValidateResponse();
        right.setAddress("a@example.com");
        right.setActiveInDays("9");
        right.setActiveFirstSeen("2024-01-01");
        assertNotEquals(left, right);
        assertNotEquals(left.hashCode(), right.hashCode());
        right.setActiveInDays("5");
        right.setActiveFirstSeen("2024-02-01");
        assertNotEquals(left, right);
    }

    private static void runCredits(
            Probe probe,
            CountDownLatch start,
            CountDownLatch done,
            AtomicReference<ZBCreditsResponse> response,
            AtomicReference<Throwable> failure
    ) {
        try {
            start.await();
            response.set(probe.success((ok, err) -> probe.sdk.getCredits(ok, err)));
        } catch (Throwable throwable) {
            failure.set(throwable);
        } finally {
            done.countDown();
        }
    }

    private static void assertShort(ErrorResponse error, String secret) {
        assertNotNull(error);
        assertFalse(error.getErrors().isEmpty());
        String text = String.join("\n", error.getErrors());
        assertTrue(text.length() < 400, text.substring(0, Math.min(text.length(), 120)));
        String lower = text.toLowerCase();
        assertFalse(lower.contains("<html"));
        assertFalse(lower.contains("<!doctype"));
        assertFalse(lower.contains("<p>blocked</p>"));
        assertFalse(text.contains(secret));
        assertFalse(text.contains("Error: Error:"));
        assertFalse(text.contains("[Error]:"));
    }

    private Probe probe(String apiKey) {
        return new Probe(apiKey);
    }

    private static final class Probe {
        final ZeroBounceSDK sdk = new ZeroBounceSDK();
        final FakeApi api = new FakeApi();

        Probe(String apiKey) {
            sdk.transport = api;
            sdk.initialize(apiKey);
        }

        FakeApi.RecordedRequest only() {
            assertEquals(1, api.records().size(), api.records().toString());
            return api.records().get(0);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        <T> T success(Call call) {
            AtomicReference value = new AtomicReference();
            AtomicReference<ErrorResponse> error = new AtomicReference<>();
            call.run(value::set, error::set);
            if (error.get() != null) {
                fail(error.get().getErrors().toString());
            }
            assertNotNull(value.get());
            return (T) value.get();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        ErrorResponse failure(Call call) {
            AtomicReference value = new AtomicReference();
            AtomicReference<ErrorResponse> error = new AtomicReference<>();
            call.run(value::set, error::set);
            assertNull(value.get());
            assertNotNull(error.get());
            return error.get();
        }
    }

    private interface Call {
        @SuppressWarnings("rawtypes")
        void run(ZeroBounceSDK.OnSuccessCallback success, ZeroBounceSDK.OnErrorCallback error);
    }
}
