package com.zerobounce;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Offline tests for the endpoints ported from the Python SDK: the validate
 * options, real-time AI scoring, the filters, the list evaluator, the S3 and
 * SFTP/FTP bulk jobs and the Inbox Placement Tester.
 * <p>
 * Every request is answered by a mocked {@link HttpURLConnection}; nothing here
 * reaches the live API. The assertions are on what actually went on the wire.
 */
@ExtendWith(MockitoExtension.class)
public class OfflineApiTest {

    @Spy
    private ZeroBounceSDK zeroBounceSDK;

    private static final String API_KEY = "offline-key";

    /** What one mocked call recorded. */
    private static class Recorded {
        String url;
        String method;
        String body;
        /** Request headers, for the Inbox Placement Tester's Bearer key. */
        final Map<String, String> headers = new LinkedHashMap<>();

        /** The form body parsed into field/value pairs. */
        Map<String, String> form() {
            Map<String, String> fields = new LinkedHashMap<>();
            if (body == null || body.isEmpty()) return fields;
            for (String pair : body.split("&")) {
                int split = pair.indexOf('=');
                if (split < 0) continue;
                fields.put(
                        URLDecoder.decode(pair.substring(0, split), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8)
                );
            }
            return fields;
        }

        /** The query string parsed into parameter/value pairs. */
        Map<String, String> query() {
            Map<String, String> params = new LinkedHashMap<>();
            int mark = url.indexOf('?');
            if (mark < 0) return params;
            for (String pair : url.substring(mark + 1).split("&")) {
                int split = pair.indexOf('=');
                if (split < 0) continue;
                params.put(
                        URLDecoder.decode(pair.substring(0, split), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8)
                );
            }
            return params;
        }

        /** The url without its query string, still percent-encoded. */
        String path() {
            int mark = url.indexOf('?');
            return mark < 0 ? url : url.substring(0, mark);
        }
    }

    private final List<Recorded> recorded = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        zeroBounceSDK.initialize(API_KEY);
        recorded.clear();
    }

    /**
     * Answers every request with the given JSON and records what was sent.
     * Returns the recorded calls, in order.
     */
    private List<Recorded> mockAll(int status, @NotNull String responseJson) throws IOException {
        ByteArrayOutputStream sentBody = new ByteArrayOutputStream();

        URL url = mock(URL.class, withSettings().useConstructor("https://api.zerobounce.net/v2/validate"));
        HttpURLConnection con = mock(HttpURLConnection.class);
        lenient().when(url.openConnection()).thenReturn(con);
        lenient().when(con.getResponseCode()).thenReturn(status);
        lenient().when(con.getOutputStream()).thenReturn(sentBody);
        lenient().when(con.getInputStream())
                .thenAnswer(i -> new ByteArrayInputStream(responseJson.getBytes(StandardCharsets.UTF_8)));
        lenient().when(con.getErrorStream())
                .thenAnswer(i -> new ByteArrayInputStream(responseJson.getBytes(StandardCharsets.UTF_8)));

        lenient().doAnswer(invocation -> {
            Recorded record = new Recorded();
            record.url = invocation.getArgument(0);
            recorded.add(record);
            sentBody.reset();
            return url;
        }).when(zeroBounceSDK).createUrlFrom(anyString());

        lenient().doAnswer(invocation -> {
            if (!recorded.isEmpty()) {
                recorded.get(recorded.size() - 1).method = invocation.getArgument(0);
            }
            return null;
        }).when(con).setRequestMethod(anyString());

        lenient().doAnswer(invocation -> {
            if (!recorded.isEmpty()) {
                recorded.get(recorded.size() - 1).headers
                        .put(invocation.getArgument(0), invocation.getArgument(1));
            }
            return null;
        }).when(con).setRequestProperty(anyString(), anyString());

        // The body is written after setRequestMethod, so read it back lazily.
        lenient().doAnswer(invocation -> {
            if (!recorded.isEmpty()) {
                recorded.get(recorded.size() - 1).body = sentBody.toString(StandardCharsets.UTF_8);
            }
            return null;
        }).when(con).disconnect();

        return recorded;
    }

    private Recorded only() {
        assertEquals(1, recorded.size(), "expected exactly one request");
        Recorded record = recorded.get(0);
        recorded.clear();
        return record;
    }

    private void failOnError(ErrorResponse error) {
        fail(error.getErrors().toString());
    }

    // ------------------------------------------------------------------
    // Validate options
    // ------------------------------------------------------------------

    @Test
    public void validateSendsTheNewOptionsOnlyWhenSet() throws IOException {
        mockAll(200, """
                {"address":"a@example.com","status":"valid","active_in_days":"3",
                 "domain_website_exists":"true","domain_registrant_company_name":"Example Inc"}""");

        zeroBounceSDK.validate("a@example.com", null, null, r -> {
        }, this::failOnError);
        Recorded record = only();
        assertEquals("GET", record.method);
        for (String absent : new String[]{"timeout", "activity_data", "verify_plus", "domain_info",
                "greylist_processing", "callback_url"}) {
            assertFalse(record.query().containsKey(absent), absent + " should not be sent");
        }

        ZBValidateOptions options = new ZBValidateOptions()
                .setTimeout(30)
                .setActivityData(true)
                .setVerifyPlus(false)
                .setDomainInfo(true)
                .setGreylistProcessing(true)
                .setCallbackUrl("https://example.com/greylist");
        zeroBounceSDK.validate("a@example.com", null, options, response -> {
            // The activity_data and domain_info blocks land on the response.
            assertEquals("3", response.getActiveInDays());
            assertEquals("true", response.getDomainWebsiteExists());
            assertEquals("Example Inc", response.getDomainRegistrantCompanyName());
        }, this::failOnError);
        record = only();
        Map<String, String> query = record.query();
        assertEquals("30", query.get("timeout"));
        // Booleans go on the wire as the strings "true"/"false".
        assertEquals("true", query.get("activity_data"));
        assertEquals("false", query.get("verify_plus"));
        assertEquals("true", query.get("domain_info"));
        assertEquals("true", query.get("greylist_processing"));
        assertEquals("https://example.com/greylist", query.get("callback_url"));

        // usePost sends the same fields as a form body instead of a query string.
        zeroBounceSDK.validate("a@example.com", null,
                new ZBValidateOptions().setUsePost(true).setDomainInfo(true), r -> {
                }, this::failOnError);
        record = only();
        assertEquals("POST", record.method);
        assertEquals("a@example.com", record.form().get("email"));
        assertEquals("true", record.form().get("domain_info"));
    }

    @Test
    public void greylistMessageLandsOnTheResponse() throws IOException {
        // While a greylist retry is still running the API answers with "Message"
        // instead of a result.
        mockAll(200, """
                {"address":"a@example.com","Message":"Greylisted email, we will retry"}""");

        zeroBounceSDK.validate("a@example.com", null, null,
                response -> assertEquals("Greylisted email, we will retry", response.getMessage()),
                this::failOnError);
        only();
    }

    @Test
    public void invalidValidateOptionsSendNoRequest() throws IOException {
        mockAll(200, "{}");

        List<String> errors = new ArrayList<>();
        ZeroBounceSDK.OnErrorCallback collect = error -> errors.addAll(error.getErrors());

        zeroBounceSDK.validate("a@example.com", null,
                new ZBValidateOptions().setCallbackUrl("https://example.com/greylist"),
                r -> fail("success"), collect);
        zeroBounceSDK.validate("a@example.com", null,
                new ZBValidateOptions().setGreylistProcessing(false).setCallbackUrl("https://example.com/greylist"),
                r -> fail("success"), collect);
        assertEquals(2, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.contains("greylist_processing")), errors.toString());

        errors.clear();
        zeroBounceSDK.validate("a@example.com", null,
                new ZBValidateOptions().setGreylistProcessing(true).setCallbackUrl("   "),
                r -> fail("success"), collect);
        assertTrue(errors.toString().contains("callback_url"), errors.toString());

        errors.clear();
        zeroBounceSDK.validate("a@example.com", null, new ZBValidateOptions().setTimeout(2),
                r -> fail("success"), collect);
        assertTrue(errors.toString().contains("timeout"), errors.toString());

        assertTrue(recorded.isEmpty(), "no request should have been sent");
    }

    // ------------------------------------------------------------------
    // Real-time AI scoring and filters
    // ------------------------------------------------------------------

    @Test
    public void scoringAndFiltersUseTheMainHost() throws IOException {
        mockAll(200, """
                {"email":"a@example.com","score":"10","status":"valid"}""");

        zeroBounceSDK.scoreEmail("a@example.com",
                response -> assertEquals("10", response.getScore()), this::failOnError);
        Recorded record = only();
        assertEquals("GET", record.method);
        assertTrue(record.path().endsWith("/v2/scoring"), record.path());
        assertEquals("a@example.com", record.query().get("email"));

        zeroBounceSDK.scoreEmail("a@example.com", true, r -> {
        }, this::failOnError);
        record = only();
        assertEquals("POST", record.method);
        assertEquals("a@example.com", record.form().get("email"));

        zeroBounceSDK.addFilter("exclude", "domain", "example.com", r -> {
        }, this::failOnError);
        record = only();
        assertEquals("POST", record.method);
        assertTrue(record.path().endsWith("/v2/filters/add"), record.path());
        assertEquals("exclude", record.form().get("rule"));

        zeroBounceSDK.listFilters(false, r -> {
        }, this::failOnError);
        assertEquals("GET", only().method);
        zeroBounceSDK.listFilters(true, r -> {
        }, this::failOnError);
        assertEquals("POST", only().method);

        zeroBounceSDK.deleteFilter("exclude", "domain", "example.com", r -> {
        }, this::failOnError);
        assertTrue(only().path().endsWith("/v2/filters/delete"));
    }

    @Test
    public void scoreEmailRejectsABlankAddressBeforeARequest() throws IOException {
        mockAll(200, "{}");
        List<String> errors = new ArrayList<>();
        zeroBounceSDK.scoreEmail("   ", r -> fail("success"), e -> errors.addAll(e.getErrors()));
        assertTrue(errors.toString().contains("email"), errors.toString());
        assertTrue(recorded.isEmpty());
    }

    // ------------------------------------------------------------------
    // S3 and SFTP/FTP bulk
    // ------------------------------------------------------------------

    @Test
    public void s3AndSftpFileJobsSendTheDocumentedFieldsOnly() throws IOException {
        mockAll(200, """
                {"success":true,"message":"ok","file_name":"f.csv","file_id":"fid"}""");

        zeroBounceSDK.emailFinderS3File(
                new ZBBulkFileJobOptions()
                        .setFileName("lists/leads.csv")
                        .setHasHeaderRow(true)
                        .setReturnUrl("https://example.com/done"),
                r -> {
                }, this::failOnError);
        Recorded record = only();
        assertEquals("POST", record.method);
        // The S3 and SFTP jobs live on the bulkapi root, WITHOUT /v2.
        assertEquals("https://bulkapi.zerobounce.net/emailFinderS3file", record.path());
        Map<String, String> form = record.form();
        assertEquals("lists/leads.csv", form.get("file_name"));
        assertEquals("true", form.get("has_header_row"));
        assertEquals("https://example.com/done", form.get("return_url"));
        // Unset options stay off the request entirely.
        assertFalse(form.containsKey("remove_duplicates"));

        zeroBounceSDK.domainSearchS3File(
                new ZBBulkFileJobOptions()
                        .setFileName("lists/leads.csv")
                        .setHasHeaderRow(false)
                        .setRemoveDuplicates(true)
                        .setReturnUrl("https://example.com/done"),
                r -> {
                }, this::failOnError);
        record = only();
        assertEquals("https://bulkapi.zerobounce.net/domainSearchS3file", record.path());
        form = record.form();
        assertEquals("false", form.get("has_header_row"));
        assertEquals("true", form.get("remove_duplicates"));
        assertEquals("https://example.com/done", form.get("return_url"));

        // LIVE: file_name is required here too, although the docs list it as
        // optional for this one endpoint.
        ZBBulkFileJobOptions named = new ZBBulkFileJobOptions()
                .setFileName("my_file.txt")
                .setReturnUrl("https://example.com/done");
        zeroBounceSDK.validateSFTPFile(named, r -> {
        }, this::failOnError);
        record = only();
        assertEquals("https://bulkapi.zerobounce.net/validateSFTPfile", record.path());
        assertEquals("my_file.txt", record.form().get("file_name"));
        assertEquals("true", record.form().get("has_header_row"));
        zeroBounceSDK.scoreSFTPFile(named, r -> {
        }, this::failOnError);
        assertEquals("https://bulkapi.zerobounce.net/scoreSFTPfile", only().path());
        zeroBounceSDK.emailFinderSFTPFile(named, r -> {
        }, this::failOnError);
        assertEquals("https://bulkapi.zerobounce.net/emailFinderSFTPfile", only().path());
        zeroBounceSDK.domainSearchSFTPFile(named, r -> {
        }, this::failOnError);
        assertEquals("https://bulkapi.zerobounce.net/domainSearchSFTPfile", only().path());
        zeroBounceSDK.validateS3File(named, r -> {
        }, this::failOnError);
        assertEquals("https://bulkapi.zerobounce.net/validateS3file", only().path());
        zeroBounceSDK.scoreS3File(named, r -> {
        }, this::failOnError);
        assertEquals("https://bulkapi.zerobounce.net/scoreS3file", only().path());

        // exportSFTPfile takes export_path, not the export_bucket exportS3file uses.
        zeroBounceSDK.exportSFTPFile("fid", "/exports", r -> {
        }, this::failOnError);
        record = only();
        assertEquals("https://bulkapi.zerobounce.net/exportSFTPfile", record.path());
        assertEquals("fid", record.form().get("file_id"));
        assertEquals("/exports", record.form().get("export_path"));
        assertFalse(record.form().containsKey("export_bucket"));

        zeroBounceSDK.exportSFTPFile("fid", null, r -> {
        }, this::failOnError);
        assertFalse(only().form().containsKey("export_path"));

        zeroBounceSDK.exportS3File("fid", "bucket-out", r -> {
        }, this::failOnError);
        record = only();
        assertEquals("https://bulkapi.zerobounce.net/exportS3file", record.path());
        assertEquals("bucket-out", record.form().get("export_bucket"));
    }

    @Test
    public void listS3FilesAcceptsBothDocumentedShapes() throws IOException {
        mockAll(200, """
                {"success":true,"files":[{"Name":"lists/leads.csv","Size":12,"LastModified":"2026-01-01"}]}""");

        zeroBounceSDK.listS3Files(null, null, files -> {
            assertEquals(1, files.size());
            assertEquals("lists/leads.csv", files.get(0).getName());
            assertEquals(12, files.get(0).getSize());
        }, this::failOnError);
        Recorded record = only();
        assertEquals("GET", record.method);
        assertEquals("https://bulkapi.zerobounce.net/listS3files", record.path());
        assertFalse(record.query().containsKey("bucket"));
        assertFalse(record.query().containsKey("search"));

        zeroBounceSDK.listS3Files("inbox", "csv", files -> {
        }, this::failOnError);
        record = only();
        assertEquals("inbox", record.query().get("bucket"));
        assertEquals("csv", record.query().get("search"));

        // A bare array is also returned by the API.
        mockAll(200, """
                [{"Name":"bare.csv","Size":1,"LastModified":""}]""");
        recorded.clear();
        zeroBounceSDK.listS3Files(null, null, files -> {
            assertEquals(1, files.size());
            assertEquals("bare.csv", files.get(0).getName());
        }, this::failOnError);
        only();
    }

    @Test
    public void bulkJobsRejectInvalidArgumentsBeforeARequest() throws IOException {
        mockAll(200, "{}");
        List<String> errors = new ArrayList<>();
        ZeroBounceSDK.OnErrorCallback collect = error -> errors.addAll(error.getErrors());

        ZBBulkFileJobOptions blank = new ZBBulkFileJobOptions().setFileName("   ");
        zeroBounceSDK.validateS3File(blank, r -> fail("success"), collect);
        zeroBounceSDK.scoreS3File(blank, r -> fail("success"), collect);
        zeroBounceSDK.emailFinderS3File(blank, r -> fail("success"), collect);
        zeroBounceSDK.domainSearchS3File(blank, r -> fail("success"), collect);
        zeroBounceSDK.scoreSFTPFile(blank, r -> fail("success"), collect);
        zeroBounceSDK.emailFinderSFTPFile(blank, r -> fail("success"), collect);
        zeroBounceSDK.domainSearchSFTPFile(blank, r -> fail("success"), collect);
        // An unset name is allowed for validateSFTPfile, but a blank one is not.
        zeroBounceSDK.validateSFTPFile(blank, r -> fail("success"), collect);
        assertEquals(8, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.contains("file_name")), errors.toString());

        errors.clear();
        zeroBounceSDK.exportSFTPFile("  ", null, r -> fail("success"), collect);
        assertTrue(errors.toString().contains("file_id"), errors.toString());
        errors.clear();
        zeroBounceSDK.exportSFTPFile("fid", " ", r -> fail("success"), collect);
        assertTrue(errors.toString().contains("export_path"), errors.toString());
        errors.clear();
        zeroBounceSDK.exportS3File("fid", " ", r -> fail("success"), collect);
        assertTrue(errors.toString().contains("export_bucket"), errors.toString());

        assertTrue(recorded.isEmpty(), "no request should have been sent");
    }

    // ------------------------------------------------------------------
    // List evaluator and Inbox Placement Tester
    // ------------------------------------------------------------------

    @Test
    public void listEvaluatorUsesItsOwnBaseUrlAndEncodesTheFileId() throws IOException {
        mockAll(200, "{\"success\":true}");

        // The file id is one path segment, so a slash in it must stay encoded.
        zeroBounceSDK.getListEvaluatorResult("a b/c", r -> {
        }, this::failOnError);
        Recorded record = only();
        assertEquals("GET", record.method);
        assertEquals("https://bulkapi.zerobounce.net/v2/listevaluator/a%20b%2Fc/", record.path());

        List<String> errors = new ArrayList<>();
        zeroBounceSDK.getListEvaluatorResult("  ", r -> fail("success"), e -> errors.addAll(e.getErrors()));
        assertTrue(errors.toString().contains("file_id"), errors.toString());
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void successFalseOnAnHttp200IsAnError() throws IOException {
        // LIVE: the API answers an unknown file id with an HTTP 200 whose body
        // says success: false.
        mockAll(200, """
                {"success":false,"message":"File cannot be found."}""");

        List<String> errors = new ArrayList<>();
        int[] status = {-1};
        zeroBounceSDK.fileStatus("0d1b7a4e-1111-2222-3333-444455556666",
                r -> fail("success"),
                error -> {
                    errors.addAll(error.getErrors());
                    status[0] = error.getStatusCode();
                });
        assertTrue(errors.toString().contains("File cannot be found."), errors.toString());
        assertEquals(200, status[0]);
    }

    @Test
    public void validateBatchSendsTimeoutAsAString() throws IOException {
        // LIVE: sent as a JSON number the API rejects the whole batch with the
        // account-level "Invalid API Key or your account ran out of credits".
        mockAll(200, """
                {"email_batch":[],"errors":[]}""");

        zeroBounceSDK.validateBatch(
                List.of(new ZBValidateBatchData("a@example.com", null)),
                new ZBValidateOptions().setTimeout(30).setVerifyPlus(true),
                r -> {
                }, this::failOnError);
        Recorded record = only();
        assertTrue(record.body.contains("\"timeout\":\"30\""), record.body);
        assertTrue(record.body.contains("\"verify_plus\":true"), record.body);

        List<String> errors = new ArrayList<>();
        zeroBounceSDK.validateBatch(
                List.of(new ZBValidateBatchData("a@example.com", null)),
                new ZBValidateOptions().setTimeout(5),
                r -> fail("success"), error -> errors.addAll(error.getErrors()));
        assertTrue(errors.toString().contains("timeout"), errors.toString());
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void sendEvaluatedFilePostsTheFileIdAsAFormField() throws IOException {
        // LIVE: send-evaluated-file takes the evaluator's file_id as a form
        // field, not the file upload the docs describe.
        mockAll(200, """
                {"success":true,"message":"ok","file_id":"new-fid"}""");

        zeroBounceSDK.sendEvaluatedFile("eval-file-1", r -> {
        }, this::failOnError);
        Recorded record = only();
        assertEquals("POST", record.method);
        assertEquals("https://bulkapi.zerobounce.net/v2/send-evaluated-file/", record.path());
        assertEquals("eval-file-1", record.form().get("file_id"));

        List<String> errors = new ArrayList<>();
        zeroBounceSDK.sendEvaluatedFile("  ", r -> fail("success"),
                error -> errors.addAll(error.getErrors()));
        assertTrue(errors.toString().contains("file_id"), errors.toString());
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void s3AndSftpFileJobsRequireFileNameAndReturnUrl() throws IOException {
        mockAll(200, "{}");
        List<String> errors = new ArrayList<>();
        ZeroBounceSDK.OnErrorCallback collect = error -> errors.addAll(error.getErrors());

        // LIVE: both fields are required on every one of these jobs.
        zeroBounceSDK.emailFinderS3File(
                new ZBBulkFileJobOptions().setReturnUrl("https://example.com/done"),
                r -> fail("success"), collect);
        zeroBounceSDK.validateSFTPFile(
                new ZBBulkFileJobOptions().setReturnUrl("https://example.com/done"),
                r -> fail("success"), collect);
        assertTrue(errors.toString().contains("file_name"), errors.toString());

        errors.clear();
        zeroBounceSDK.domainSearchS3File(
                new ZBBulkFileJobOptions().setFileName("f.csv"), r -> fail("success"), collect);
        zeroBounceSDK.scoreSFTPFile(
                new ZBBulkFileJobOptions().setFileName("f.csv"), r -> fail("success"), collect);
        assertTrue(errors.toString().contains("return_url"), errors.toString());
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void inboxPlacementUsesItsOwnHostAndCamelCaseJson() throws IOException {
        mockAll(200, "{\"ok\":true}");

        zeroBounceSDK.getInboxPlacementProviders(r -> {
        }, this::failOnError);
        Recorded record = only();
        assertEquals("https://bulkapi.zerobounce.net/api/ipt/providers", record.path());
        // LIVE: the Inbox Placement Tester authenticates with a Bearer header,
        // and the key must not reach the query string, where it would be logged.
        assertEquals("Bearer " + API_KEY, record.headers.get("Authorization"));
        assertNull(record.query().get("api_key"));

        zeroBounceSDK.getInboxPlacementTestTypes(r -> {
        }, this::failOnError);
        assertEquals("https://bulkapi.zerobounce.net/api/ipt/test-types", only().path());

        zeroBounceSDK.createInboxPlacementTest("Launch",
                new ZeroBounceSDK.CreateInboxPlacementTestOptions()
                        .setTestType(1)
                        .setSenderEmail("news@example.com")
                        .setWebhookUrl("https://example.com/ipt"),
                r -> {
                }, this::failOnError);
        record = only();
        assertEquals("POST", record.method);
        assertEquals("https://bulkapi.zerobounce.net/api/ipt/test", record.path());
        // Bodies are camelCase JSON, and unset fields are left out.
        assertTrue(record.body.contains("\"testType\":1"), record.body);
        assertTrue(record.body.contains("\"senderEmail\":\"news@example.com\""), record.body);
        assertTrue(record.body.contains("\"webhookUrl\":\"https://example.com/ipt\""), record.body);
        assertFalse(record.body.contains("providerIds"), record.body);
        assertFalse(record.body.contains("seedIds"), record.body);

        zeroBounceSDK.listInboxPlacementTests(
                new ZeroBounceSDK.ListInboxPlacementTestsOptions()
                        .setStatus("finished").setPage(1).setPageSize(25),
                r -> {
                }, this::failOnError);
        record = only();
        assertEquals("finished", record.query().get("status"));
        assertEquals("1", record.query().get("page"));
        assertEquals("25", record.query().get("pageSize"));

        zeroBounceSDK.listInboxPlacementTests(null, r -> {
        }, this::failOnError);
        record = only();
        assertFalse(record.query().containsKey("status"));
        assertFalse(record.query().containsKey("page"));

        zeroBounceSDK.deleteInboxPlacementTest("a b/c", r -> {
        }, this::failOnError);
        record = only();
        assertEquals("DELETE", record.method);
        assertEquals("https://bulkapi.zerobounce.net/api/ipt/test/a%20b%2Fc", record.path());

        zeroBounceSDK.setInboxPlacementWebhook("https://example.com/ipt", r -> {
        }, this::failOnError);
        record = only();
        assertEquals("PUT", record.method);
        assertEquals("https://bulkapi.zerobounce.net/api/ipt/webhook", record.path());
        assertTrue(record.body.contains("\"webhookUrl\":\"https://example.com/ipt\""), record.body);

        zeroBounceSDK.getInboxPlacementWebhook(r -> {
        }, this::failOnError);
        assertEquals("https://bulkapi.zerobounce.net/api/ipt/webhook", only().path());

        List<String> errors = new ArrayList<>();
        ZeroBounceSDK.OnErrorCallback collect = error -> errors.addAll(error.getErrors());
        zeroBounceSDK.getInboxPlacementTest("   ", r -> fail("success"), collect);
        assertTrue(errors.toString().contains("test_id"), errors.toString());
        errors.clear();
        zeroBounceSDK.createInboxPlacementTest(" ", null, r -> fail("success"), collect);
        assertTrue(errors.toString().contains("name"), errors.toString());
        errors.clear();
        zeroBounceSDK.setInboxPlacementWebhook("", r -> fail("success"), collect);
        assertTrue(errors.toString().contains("webhook_url"), errors.toString());
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void detailErrorBodyBecomesTheMessage() throws IOException {
        // The Inbox Placement Tester is a Django REST framework app: it reports
        // errors under "detail" rather than the "error"/"message" keys the rest
        // of the API uses.
        mockAll(404, "{\"detail\":\"Inbox placement test not found.\"}");

        List<String> errors = new ArrayList<>();
        zeroBounceSDK.getInboxPlacementTest("123", r -> fail("success"), e -> errors.addAll(e.getErrors()));
        assertTrue(errors.toString().contains("Inbox placement test not found."), errors.toString());
    }

    @Test
    public void listEvaluatorAndInboxPlacementBaseUrlsAreOverridable() {
        // These are separate hosts, not paths built from the bulk base, so an
        // override of the main base URL alone would leave them on the live API.
        zeroBounceSDK.initialize(API_KEY, "https://api.example.test/v2",
                "https://bulk.example.test/v2/listevaluator", "https://bulk.example.test/api/ipt");
        assertEquals("https://bulk.example.test/v2/listevaluator", zeroBounceSDK.listEvaluatorBaseUrl);
        assertEquals("https://bulk.example.test/api/ipt", zeroBounceSDK.inboxPlacementBaseUrl);
        assertThrows(IllegalArgumentException.class, () ->
                zeroBounceSDK.initialize(API_KEY, null, "http://evil.example", null));
    }
}
