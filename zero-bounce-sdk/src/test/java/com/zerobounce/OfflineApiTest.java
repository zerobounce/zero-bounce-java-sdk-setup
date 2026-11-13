package com.zerobounce;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonElement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    void validateWithOptionsSendsFalseFlagsAndPostForm() {
        Probe probe = probe("key a/b");
        ZBValidateRequestOptions options = new ZBValidateRequestOptions();
        options.setIpAddress("203.0.113.5");
        options.setTimeout(3);
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
        assertEquals("203.0.113.5", request.field("ip_address"));
        assertEquals("3", request.field("timeout"));
        assertEquals("false", request.field("activity_data"));
        assertEquals("true", request.field("verify_plus"));
        assertTrue(request.rawBody.contains("email="));
        assertFalse(request.rawBody.contains(" "));
        assertFalse(request.url.contains(" "));
    }

    @Test
    void validateWithOptionsIsGatedLikeValidate() {
        // validateWithOptions is a wrapper over validate, so the documented
        // 3-60 second timeout is rejected before the request on both.
        Probe probe = probe("key-gate");
        ZBValidateRequestOptions tooSmall = new ZBValidateRequestOptions();
        tooSmall.setTimeout(0);
        ErrorResponse error = probe.failure(
                (ok, err) -> probe.sdk.validateWithOptions("person@example.com", tooSmall, ok, err)
        );
        assertEquals(0, probe.api.records().size());
        assertTrue(error.getErrors().toString().contains("timeout"), error.getErrors().toString());

        ErrorResponse blank = probe.failure(
                (ok, err) -> probe.sdk.validateWithOptions("  ", null, ok, err)
        );
        assertEquals(0, probe.api.records().size());
        assertTrue(blank.getErrors().toString().contains("email"), blank.getErrors().toString());

        // A null options object still sends the two required parameters.
        probe.success((ok, err) -> probe.sdk.validateWithOptions("person@example.com", null, ok, err));
        FakeApi.RecordedRequest request = probe.only();
        assertEquals("GET", request.method);
        assertEquals("person@example.com", request.field("email"));
        assertEquals("", request.field("ip_address"));
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
    void aDeadKeyIsAnErrorOnEveryCall() {
        Probe probe = probe("dead-key-user");
        String drained = "Invalid API key or your account ran out of credits";
        Map<String, Object[]> calls = new LinkedHashMap<>();
        calls.put("validate", new Object[]{(Call) (ok, err) -> probe.sdk.validate("a@example.com", "", ok, err), drained});
        calls.put("getActivityData", new Object[]{(Call) (ok, err) -> probe.sdk.getActivityData("a@example.com", ok, err), drained});
        calls.put("getApiUsage", new Object[]{(Call) (ok, err) -> probe.sdk.getApiUsage(new Date(0), new Date(86_400_000L), ok, err), "Invalid API key"});
        calls.put("findEmail", new Object[]{(Call) (ok, err) -> probe.sdk.findEmail("john", "example.com", null, null, null, ok, err), drained});
        calls.put("findDomain", new Object[]{(Call) (ok, err) -> probe.sdk.findDomain("example.com", null, ok, err), drained});
        calls.put("guessFormat", new Object[]{(Call) (ok, err) -> probe.sdk.guessFormat("example.com", "john", null, null, ok, err), drained});
        calls.put("addFilter", new Object[]{(Call) (ok, err) -> probe.sdk.addFilter("allow", "email", "x@example.com", ok, err), drained});
        calls.put("deleteFilter", new Object[]{(Call) (ok, err) -> probe.sdk.deleteFilter("allow", "email", "x@example.com", ok, err), drained});
        calls.put("listFilters GET", new Object[]{(Call) (ok, err) -> probe.sdk.listFilters(false, ok, err), drained});
        calls.put("listFilters POST", new Object[]{(Call) (ok, err) -> probe.sdk.listFilters(true, ok, err), drained});
        for (Map.Entry<String, Object[]> entry : calls.entrySet()) {
            AtomicReference<Object> value = new AtomicReference<>();
            AtomicReference<ErrorResponse> error = new AtomicReference<>();
            ((Call) entry.getValue()[0]).run(value::set, error::set);
            assertNull(value.get(), entry.getKey() + " returned a result");
            assertNotNull(error.get(), entry.getKey() + " reported nothing");
            assertEquals(List.of(entry.getValue()[1]), error.get().getErrors(), entry.getKey());
        }
        // -1 is the documented credit balance of an invalid key, not an error.
        ZBCreditsResponse credits = probe.success((ok, err) -> probe.sdk.getCredits(ok, err));
        assertEquals("-1", credits.getCredits());
    }

    @Test
    void aBareMessageIsStillAResultWhereTheApiMeansOne() {
        Probe pending = probe("greylist-pending-user");
        ZBValidateResponse validated = pending.success((ok, err) -> pending.sdk.validate("a@example.com", "", ok, err));
        assertEquals("Greylist validation still in progress. Please try again later.", validated.getMessage());

        Probe filter = probe("filter-message-user");
        JsonElement added = filter.success((ok, err) -> filter.sdk.addFilter("allow", "email", "x@example.com", ok, err));
        assertEquals("Filter has been added.", added.getAsJsonObject().get("Message").getAsString());
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
        ZeroBounceSDK.EmailFinderSendFileOptions streamOptions =
                new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2);
        probe.success((ok, err) -> probe.sdk.sendEmailFinderFileStream(stream, "names.csv", 2, streamOptions, ok, err));
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
        // LIVE: send-evaluated-file takes the evaluator's file_id as a form
        // field. There is no file-upload overload.
        probe.success((ok, err) -> probe.sdk.sendEvaluatedFile("eval-fid", ok, err));
        FakeApi.RecordedRequest evaluated = probe.only();
        assertEquals("POST", evaluated.method);
        assertEquals("/v2/send-evaluated-file/", evaluated.path);
        assertEquals("eval-fid", evaluated.field("file_id"));

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
                .setReturnUrl("https://example.com/done")
                .setEmailAddressColumn(0)
                .setHasHeaderRow(false)
                .setAllowPhase2(false)
                .setFirstNameColumn(0);
        probe.success((ok, err) -> probe.sdk.validateS3File(validate, ok, err));
        FakeApi.RecordedRequest validation = probe.only();
        assertEquals("POST", validation.method);
        assertEquals("bulkapi.zerobounce.net", validation.host);
        assertEquals("/validateS3file", validation.path);
        assertEquals("s3-key", validation.apiKey);
        assertEquals("emails.csv", validation.field("file_name"));
        // LIVE: required on every S3 / SFTP job, on this overload too.
        assertEquals("https://example.com/done", validation.field("return_url"));
        assertEquals("0", validation.field("email_address_column"));
        assertEquals("false", validation.field("has_header_row"));
        assertEquals("false", validation.field("allow_phase_2"));
        assertNull(validation.field("first_name_column"));

        probe.api.clear();
        ZeroBounceSDK.ScoreS3FileOptions score = new ZeroBounceSDK.ScoreS3FileOptions()
                .setFileName("emails.csv")
                .setReturnUrl("https://example.com/done")
                .setRemoveDuplicate(false);
        probe.success((ok, err) -> probe.sdk.scoreS3File(score, ok, err));
        FakeApi.RecordedRequest scoring = probe.only();
        assertEquals("/scoreS3file", scoring.path);
        assertEquals("emails.csv", scoring.field("file_name"));
        assertEquals("https://example.com/done", scoring.field("return_url"));
        // has_header_row defaults to true, like the ZBBulkFileJobOptions overload.
        assertEquals("true", scoring.field("has_header_row"));
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
    void s3OptionOverloadsRequireTheSameFields() {
        // The ValidateS3FileOptions and ScoreS3FileOptions overloads share the
        // ZBBulkFileJobOptions implementation, so the required-field gate is
        // the same on all four entry points.
        Probe probe = probe("s3-gate");

        ErrorResponse validateName = probe.failure((ok, err) -> probe.sdk.validateS3File(
                new ZeroBounceSDK.ValidateS3FileOptions().setReturnUrl("https://example.com/done"), ok, err));
        assertTrue(validateName.getErrors().toString().contains("file_name"),
                validateName.getErrors().toString());

        ErrorResponse validateReturn = probe.failure((ok, err) -> probe.sdk.validateS3File(
                new ZeroBounceSDK.ValidateS3FileOptions().setFileName("emails.csv"), ok, err));
        assertTrue(validateReturn.getErrors().toString().contains("return_url"),
                validateReturn.getErrors().toString());

        ErrorResponse scoreName = probe.failure((ok, err) -> probe.sdk.scoreS3File(
                new ZeroBounceSDK.ScoreS3FileOptions().setReturnUrl("https://example.com/done"), ok, err));
        assertTrue(scoreName.getErrors().toString().contains("file_name"),
                scoreName.getErrors().toString());

        // ScoreS3FileOptions had no returnUrl at all, so return_url was never
        // sent and the live call failed with "This field is required".
        ErrorResponse scoreReturn = probe.failure((ok, err) -> probe.sdk.scoreS3File(
                new ZeroBounceSDK.ScoreS3FileOptions().setFileName("emails.csv"), ok, err));
        assertTrue(scoreReturn.getErrors().toString().contains("return_url"),
                scoreReturn.getErrors().toString());

        assertEquals(0, probe.api.records().size());
    }

    @Test
    void emailFinderUploadNeedsAFirstNameOrFullNameColumn() throws Exception {
        Probe probe = probe("finder-key");
        File csv = tempDir.resolve("names.csv").toFile();
        Files.writeString(csv.toPath(), "domain\nexample.com\n");

        ErrorResponse none = probe.failure(
                (ok, err) -> probe.sdk.sendEmailFinderFile(csv, 1, null, ok, err));
        assertTrue(none.getErrors().toString().contains("first_name_column"), none.getErrors().toString());

        ErrorResponse neither = probe.failure((ok, err) -> probe.sdk.sendEmailFinderFile(
                csv, 1, new ZeroBounceSDK.EmailFinderSendFileOptions().setLastNameColumn(2), ok, err));
        assertTrue(neither.getErrors().toString().contains("full_name_column"), neither.getErrors().toString());

        ErrorResponse stream = probe.failure((ok, err) -> probe.sdk.sendEmailFinderFileStream(
                new ByteArrayInputStream("d\n".getBytes(StandardCharsets.UTF_8)), "n.csv", 1, null, ok, err));
        assertFalse(stream.getErrors().isEmpty());
        assertEquals(0, probe.api.records().size());

        // Either one on its own is enough.
        probe.success((ok, err) -> probe.sdk.sendEmailFinderFile(
                csv, 1, new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2), ok, err));
        assertEquals("2", probe.only().field("first_name_column"));
        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.sendEmailFinderFile(
                csv, 1, new ZeroBounceSDK.EmailFinderSendFileOptions().setFullNameColumn(3), ok, err));
        assertEquals("3", probe.only().field("full_name_column"));
    }

    @Test
    void findEmailLeavesOutABlankFirstName() {
        // first_name is optional on guessformat.
        Probe probe = probe("finder");
        probe.success((ok, err) -> probe.sdk.findEmail(null, "example.com", null, null, "Doe", ok, err));
        FakeApi.RecordedRequest request = probe.only();
        assertEquals("GET", request.method);
        assertEquals("api.zerobounce.net", request.host);
        assertEquals("/v2/guessformat", request.path);
        assertNull(request.field("first_name"));
        assertEquals("example.com", request.field("domain"));
        assertEquals("Doe", request.field("last_name"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.findEmail("   ", null, "Example Inc", null, null, ok, err));
        assertNull(probe.only().field("first_name"));
        assertEquals("Example Inc", probe.only().field("company_name"));

        probe.api.clear();
        probe.success((ok, err) -> probe.sdk.findEmail("John", "example.com", null, null, null, ok, err));
        assertEquals("John", probe.only().field("first_name"));
    }

    @Test
    void listEvaluatorUploadFollowsTheListEvaluatorBaseUrl() throws Exception {
        // The upload and the result are a pair: overriding the list evaluator
        // host must not leave the upload on the bulk host.
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        FakeApi api = new FakeApi();
        sdk.transport = api;
        sdk.initialize("eval-key", null, "https://bulk.example.test/v2/listevaluator", null);

        File csv = tempDir.resolve("list.csv").toFile();
        Files.writeString(csv.toPath(), "email\na@example.com\n");
        sdk.sendListEvaluatorFile(csv, 1, response -> {
        }, error -> fail(error.getErrors().toString()));
        sdk.getListEvaluatorResult("fid", response -> {
        }, error -> fail(error.getErrors().toString()));

        List<FakeApi.RecordedRequest> records = api.records();
        assertEquals(2, records.size(), records.toString());
        assertEquals("bulk.example.test", records.get(0).host);
        assertEquals("/v2/listevaluator/", records.get(0).path);
        assertEquals("1", records.get(0).field("email_address_column"));
        assertEquals("bulk.example.test", records.get(1).host);
        assertEquals("/v2/listevaluator/fid/", records.get(1).path);
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
    void jsonCallsDoNotFollowRedirects() throws Exception {
        try (LoopbackServer server = new LoopbackServer(LoopbackServer.redirect(302, "https://elsewhere.invalid/"));
             MockedStatic<HttpClients> ignored = server.apacheClients()) {
            ZeroBounceSDK sdk = server.sdk();
            sdk.initialize("key");
            sdk.getCredits(response -> fail("redirect accepted"), error -> assertEquals(302, error.getStatusCode()));
            assertEquals(1, server.requests().size());
            assertFalse(server.apacheConfigs.get(0).isRedirectsEnabled());
        }
    }

    @Test
    void apacheDownloadDoesNotFollowRedirects() throws Exception {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        sdk.initialize("key");
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        ClassicHttpResponse response = mock(ClassicHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        ArgumentCaptor<HttpUriRequestBase> captor = ArgumentCaptor.forClass(HttpUriRequestBase.class);
        when(client.execute(captor.capture(), org.mockito.ArgumentMatchers.<HttpClientResponseHandler<SdkHttp.Result>>any()))
                .thenAnswer(invocation -> invocation.<HttpClientResponseHandler<SdkHttp.Result>>getArgument(1)
                        .handleResponse(response));
        when(response.getEntity()).thenReturn(entity);
        when(response.getCode()).thenReturn(200);
        when(response.getFirstHeader("Content-Type")).thenReturn(null);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream("email\n".getBytes(StandardCharsets.UTF_8)));
        try (MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class)) {
            clients.when(HttpClients::custom).thenAnswer(invocation -> HttpClientTestSupport.builder(client));
            Path out = tempDir.resolve("apache.csv");
            sdk.getFile("fid", out.toString(), responseBody -> {
            }, error -> fail(error.getErrors().toString()));
        }
        RequestConfig config = captor.getValue().getConfig();
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

    // -----------------------------------------------------------------
    // sendRequestWithMethod: validate (GET and POST), scoreEmail, the
    // filters, the S3/SFTP jobs and exports, listS3Files, the list
    // evaluator result, sendEvaluatedFile and the Inbox Placement Tester.
    // Its failures get the same handling as sendRequest's.
    // -----------------------------------------------------------------

    private static final String METHOD_PATH_KEY = "method-path-secret-key";

    /** One call per entry point built on sendRequestWithMethod. */
    private static Map<String, Call> methodPathCalls(ZeroBounceSDK sdk) {
        ZBBulkFileJobOptions job = new ZBBulkFileJobOptions()
                .setFileName("emails.csv")
                .setReturnUrl("https://example.com/done");
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("validate GET", (ok, err) -> sdk.validate("a@example.com", "", ok, err));
        calls.put("validate POST", (ok, err) -> sdk.validate("a@example.com", "",
                new ZBValidateOptions().setUsePost(true), ok, err));
        calls.put("scoreEmail", (ok, err) -> sdk.scoreEmail("a@example.com", ok, err));
        calls.put("scoreEmail POST", (ok, err) -> sdk.scoreEmail("a@example.com", true, ok, err));
        calls.put("listFilters", (ok, err) -> sdk.listFilters(false, ok, err));
        calls.put("addFilter", (ok, err) -> sdk.addFilter("block", "email", "x@example.com", ok, err));
        calls.put("listS3Files", (ok, err) -> sdk.listS3Files(null, null, ok, err));
        calls.put("validateS3File", (ok, err) -> sdk.validateS3File(job, ok, err));
        calls.put("validateSFTPFile", (ok, err) -> sdk.validateSFTPFile(job, ok, err));
        calls.put("exportS3File", (ok, err) -> sdk.exportS3File("fid", null, ok, err));
        calls.put("exportSFTPFile", (ok, err) -> sdk.exportSFTPFile("fid", null, ok, err));
        calls.put("getListEvaluatorResult", (ok, err) -> sdk.getListEvaluatorResult("fid", ok, err));
        calls.put("sendEvaluatedFile", (ok, err) -> sdk.sendEvaluatedFile("fid", ok, err));
        calls.put("getInboxPlacementProviders", (ok, err) -> sdk.getInboxPlacementProviders(ok, err));
        calls.put("setInboxPlacementWebhook", (ok, err) -> sdk.setInboxPlacementWebhook("https://example.com/hook", ok, err));
        calls.put("deleteInboxPlacementTest", (ok, err) -> sdk.deleteInboxPlacementTest("42", ok, err));
        return calls;
    }

    /** Runs every sendRequestWithMethod call against one canned transport answer. */
    private static Map<String, ErrorResponse> methodPathFailures(SdkHttp.Transport transport) {
        ZeroBounceSDK sdk = new ZeroBounceSDK();
        List<SdkHttp> sent = new java.util.ArrayList<>();
        sdk.transport = call -> {
            sent.add(call);
            return transport.exchange(call);
        };
        sdk.initialize(METHOD_PATH_KEY);
        Map<String, ErrorResponse> failures = new LinkedHashMap<>();
        for (Map.Entry<String, Call> entry : methodPathCalls(sdk).entrySet()) {
            int before = sent.size();
            AtomicReference<Object> value = new AtomicReference<>();
            AtomicReference<ErrorResponse> error = new AtomicReference<>();
            entry.getValue().run(value::set, error::set);
            assertEquals(before + 1, sent.size(), entry.getKey() + " did not send exactly one request");
            assertNull(value.get(), entry.getKey() + " returned a result");
            assertNotNull(error.get(), entry.getKey() + " reported nothing");
            failures.put(entry.getKey(), error.get());
        }
        return failures;
    }

    @Test
    void methodPathHtml403IsShortAndKeepsItsStatus() {
        byte[] page = ("<!DOCTYPE html><html><head><title>Access denied | zerobounce.net</title></head><body><!--"
                + METHOD_PATH_KEY + "-->" + "<p>blocked</p>".repeat(100_000) + "</body></html>")
                .getBytes(StandardCharsets.UTF_8);
        methodPathFailures(call -> new SdkHttp.Result(403, "text/html; charset=UTF-8", page))
                .forEach((name, error) -> {
                    assertShort(error, METHOD_PATH_KEY);
                    assertTrue(error.getErrors().get(0).contains("Access denied"), name + ": " + error.getErrors());
                    assertEquals(403, error.getStatusCode(), name);
                });
    }

    @Test
    void methodPathExceptionNeverCarriesTheKey() {
        methodPathFailures(call -> {
            throw new java.io.IOException("Error: could not reach " + call.url + " with api_key=" + METHOD_PATH_KEY);
        }).forEach((name, error) -> {
            assertShort(error, METHOD_PATH_KEY);
            assertTrue(String.join("\n", error.getErrors()).contains("***"), name + ": " + error.getErrors());
            assertFalse(error.getErrors().get(0).startsWith("Error:"), name + ": " + error.getErrors());
            assertEquals(0, error.getStatusCode(), name);
        });
    }

    @Test
    void methodPathEmpty500IsAMessageWithItsStatus() {
        methodPathFailures(call -> new SdkHttp.Result(500, "", new byte[0]))
                .forEach((name, error) -> {
                    assertEquals(List.of("Empty response from ZeroBounce (HTTP 500)"), error.getErrors(), name);
                    assertEquals(500, error.getStatusCode(), name);
                });
    }

    @Test
    void methodPathEmpty500WithNoErrorStreamKeepsItsStatus() throws Exception {
        // HttpURLConnection has no error stream at all for a failed response
        // with no body. That used to be a NullPointerException with no message:
        // an empty error list and status 0.
        ZeroBounceSDK sdk = spy(new ZeroBounceSDK());
        sdk.initialize(METHOD_PATH_KEY);
        doReturn(new SdkHttp.Result(500, "", new byte[0])).when(sdk)
                .exchangeHttp(anyString(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class),
                        org.mockito.ArgumentMatchers.nullable(byte[].class), org.mockito.ArgumentMatchers.nullable(Map.class));
        for (Map.Entry<String, Call> entry : Map.of(
                "validate", (Call) (ok, err) -> sdk.validate("a@example.com", "", ok, err),
                "getCredits", (Call) (ok, err) -> sdk.getCredits(ok, err)
        ).entrySet()) {
            AtomicReference<Object> value = new AtomicReference<>();
            AtomicReference<ErrorResponse> error = new AtomicReference<>();
            entry.getValue().run(value::set, error::set);
            assertNull(value.get(), entry.getKey());
            assertNotNull(error.get(), entry.getKey());
            assertEquals(List.of("Empty response from ZeroBounce (HTTP 500)"), error.get().getErrors(), entry.getKey());
            assertEquals(500, error.get().getStatusCode(), entry.getKey());
        }
    }

    @Test
    void methodPathStripsADoubledErrorPrefix() {
        methodPathFailures(call -> new SdkHttp.Result(400, "text/plain", "Error: Error: boom".getBytes(StandardCharsets.UTF_8)))
                .forEach((name, error) -> {
                    assertEquals(List.of("boom"), error.getErrors(), name);
                    assertEquals(400, error.getStatusCode(), name);
                });
        methodPathFailures(call -> new SdkHttp.Result(400, "application/json",
                "{\"error\":\"Error: Error: boom\"}".getBytes(StandardCharsets.UTF_8)))
                .forEach((name, error) -> {
                    assertEquals(List.of("boom"), error.getErrors(), name);
                    assertEquals(400, error.getStatusCode(), name);
                });
    }

    @Test
    void methodPathCloudflare1020IsItsTitle() {
        String body = "{\"type\":\"https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/error-1020/\","
                + "\"title\":\"Error 1020: Access denied\",\"status\":403,\"detail\":\"The request was blocked by a Cloudflare firewall rule configured by the site owner.\","
                + "\"error_code\":1020,\"cloudflare_error\":true,\"what_you_should_do\":\"Do not retry. This block is intentional.\"}";
        methodPathFailures(call -> new SdkHttp.Result(403, "application/json", body.getBytes(StandardCharsets.UTF_8)))
                .forEach((name, error) -> {
                    assertEquals(List.of("Error 1020: Access denied"), error.getErrors(), name);
                    assertEquals(403, error.getStatusCode(), name);
                });
    }

    @Test
    void methodPathSuccessFalseIsAnErrorWithItsMessage() {
        methodPathFailures(call -> new SdkHttp.Result(200, "application/json",
                "{\"success\":false,\"message\":\"File cannot be found.\"}".getBytes(StandardCharsets.UTF_8)))
                .forEach((name, error) -> {
                    assertTrue(error.getErrors().contains("File cannot be found."), name + ": " + error.getErrors());
                    assertEquals(Boolean.FALSE, error.getSuccess(), name);
                    assertEquals(200, error.getStatusCode(), name);
                });
    }

    @Test
    void uploadFailuresGetTheSameShortMessages() throws Exception {
        File csv = tempDir.resolve("upload.csv").toFile();
        Files.writeString(csv.toPath(), "email\na@example.com\n");

        Probe cloudflare = probe("cf-json-upload");
        ErrorResponse blocked = cloudflare.failure((ok, err) -> {
            try {
                cloudflare.sdk.sendFile(csv, 1, null, ok, err);
            } catch (ZBException e) {
                fail(e);
            }
        });
        assertEquals(List.of("Error 1020: Access denied"), blocked.getErrors());
        assertEquals(403, blocked.getStatusCode());

        Probe prefixed = probe("errprefix-upload");
        ErrorResponse boom = prefixed.failure((ok, err) -> prefixed.sdk.sendEmailFinderFile(
                csv, 1, new ZeroBounceSDK.EmailFinderSendFileOptions().setFirstNameColumn(2), ok, err));
        assertEquals(List.of("boom"), boom.getErrors());
        assertEquals(400, boom.getStatusCode());
    }

    @Test
    void blankEmailAndFileIdsAreRejectedBeforeARequest() {
        Probe probe = probe("blank-key");
        String path = tempDir.resolve("never.csv").toString();
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("getActivityData", (ok, err) -> probe.sdk.getActivityData(" ", ok, err));
        calls.put("deleteFile", (ok, err) -> probe.sdk.deleteFile(" ", ok, err));
        calls.put("scoringDeleteFile", (ok, err) -> probe.sdk.scoringDeleteFile(" ", ok, err));
        calls.put("emailFinderFileStatus", (ok, err) -> probe.sdk.emailFinderFileStatus(" ", ok, err));
        calls.put("emailFinderGetFile", (ok, err) -> probe.sdk.emailFinderGetFile(" ", path, ok, err));
        calls.put("emailFinderDeleteFile", (ok, err) -> probe.sdk.emailFinderDeleteFile(" ", ok, err));
        calls.put("domainSearchFileStatus", (ok, err) -> probe.sdk.domainSearchFileStatus(" ", ok, err));
        calls.put("domainSearchGetFile", (ok, err) -> probe.sdk.domainSearchGetFile(" ", path, ok, err));
        calls.put("domainSearchDeleteFile", (ok, err) -> probe.sdk.domainSearchDeleteFile(" ", ok, err));
        for (Map.Entry<String, Call> entry : calls.entrySet()) {
            ErrorResponse error = probe.failure(entry.getValue());
            assertEquals(0, probe.api.records().size(), entry.getKey() + " sent a request");
            String expected = entry.getKey().equals("getActivityData") ? "Empty parameter: email" : "Empty parameter: file_id";
            assertEquals(List.of(expected), error.getErrors(), entry.getKey());
        }
        assertFalse(Files.exists(tempDir.resolve("never.csv")));
    }

    @Test
    void bulkRootDownloadsKeepTheHttpStatus() {
        Probe finder = probe("html403-finder-status");
        ErrorResponse finderError = finder.failure((ok, err) -> finder.sdk.emailFinderGetFile(
                "fid", tempDir.resolve("f.csv").toString(), ok, err));
        assertShort(finderError, "html403-finder-status");
        assertEquals(403, finderError.getStatusCode());

        Probe domain = probe("html403-domain-status");
        ErrorResponse domainError = domain.failure((ok, err) -> domain.sdk.domainSearchGetFile(
                "fid", tempDir.resolve("d.csv").toString(), ok, err));
        assertEquals(403, domainError.getStatusCode());

        Probe html200 = probe("html200-domain-status");
        ErrorResponse okPage = html200.failure((ok, err) -> html200.sdk.domainSearchGetFile(
                "fid", tempDir.resolve("h.csv").toString(), ok, err));
        assertEquals(200, okPage.getStatusCode());

        Probe dead = probe("badkey-finder-status");
        ErrorResponse deadKey = dead.failure((ok, err) -> dead.sdk.emailFinderGetFile(
                "fid", tempDir.resolve("k.csv").toString(), ok, err));
        assertEquals(200, deadKey.getStatusCode());
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
