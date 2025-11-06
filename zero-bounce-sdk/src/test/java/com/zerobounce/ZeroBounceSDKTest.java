package com.zerobounce;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ZeroBounceSDKTest {

    @Spy
    private ZeroBounceSDK zeroBounceSDK;

    private Gson gson;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private static final String API_KEY = "some-api-key";

    @BeforeEach
    public void setUp() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.registerTypeAdapter(ZBSendFileResponse.class, new ZBSendFileResponse.Deserializer());
        gsonBuilder.registerTypeAdapter(Date.class, new GsonDateDeserializer());
        gson = gsonBuilder.create();

        zeroBounceSDK.initialize(API_KEY);
    }

    @Test
    public void validateEmail_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                        "address":"flowerjill@aol.com",
                        "status":"valid",
                        "sub_status":"",
                        "free_email":true,
                        "did_you_mean":null,
                        "account":"flowerjill",
                        "domain":"aol.com",
                        "domain_age_days": "8426",
                        "smtp_provider":"yahoo",
                        "mx_record":"mx-aol.mail.gm0.yahoodns.net",
                        "mx_found": "true",
                        "firstname":"Jill",
                        "lastname":"Stein",
                        "gender":"female",
                        "country":"United States",
                        "region":"Florida",
                        "city":"West Palm Beach",
                        "zipcode":"33401",
                        "processed_at":"2017-04-01 02:48:02.592"
                        }""";
        ZBValidateResponse expectedResponse = gson.fromJson(responseJson, ZBValidateResponse.class);

        String email = "flowerjill@aol.com";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/validate",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("email", email);
                        put("ip_address", "");
                    }
                }
        );
        mockRequest(
                urlPath,
                200,
                responseJson,
                "",
                () -> zeroBounceSDK.validate(
                        email,
                        null,
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );
    }

    @Test
    public void validateEmail_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String email = "flowerjill@aol.com";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/validate",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("email", email);
                        put("ip_address", "");
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.validate(
                        email,
                        null,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse))
        );
    }

    @Test
    public void validateBatchEmails_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                      "email_batch": [
                          {
                              "address": "valid@example.com",
                              "status": "valid",
                              "sub_status": "",
                              "free_email": false,
                              "did_you_mean": null,
                              "account": null,
                              "domain": null,
                              "domain_age_days": "9692",
                              "smtp_provider": "example",
                              "mx_found": "true",
                              "mx_record": "mx.example.com",
                              "firstname": "zero",
                              "lastname": "bounce",
                              "gender": "male",
                              "country": null,
                              "region": null,
                              "city": null,
                              "zipcode": null,
                              "processed_at": "2020-09-17 17:43:11.829"
                          },
                          {
                              "address": "invalid@example.com",
                              "status": "invalid",
                              "sub_status": "mailbox_not_found",
                              "free_email": false,
                              "did_you_mean": null,
                              "account": null,
                              "domain": null,
                              "domain_age_days": "9692",
                              "smtp_provider": "example",
                              "mx_found": "true",
                              "mx_record": "mx.example.com",
                              "firstname": "zero",
                              "lastname": "bounce",
                              "gender": "male",
                              "country": null,
                              "region": null,
                              "city": null,
                              "zipcode": null,
                              "processed_at": "2020-09-17 17:43:11.830"
                          },
                          {
                              "address": "disposable@example.com",
                              "status": "do_not_mail",
                              "sub_status": "disposable",
                              "free_email": false,
                              "did_you_mean": null,
                              "account": null,
                              "domain": null,
                              "domain_age_days": "9692",
                              "smtp_provider": "example",
                              "mx_found": "true",
                              "mx_record": "mx.example.com",
                              "firstname": "zero",
                              "lastname": "bounce",
                              "gender": "male",
                              "country": null,
                              "region": null,
                              "city": null,
                              "zipcode": null,
                              "processed_at": "2020-09-17 17:43:11.830"
                          }
                      ],
                      "errors": []
                  }""";
        ZBValidateBatchResponse expectedResponse = gson.fromJson(responseJson, ZBValidateBatchResponse.class);

        List<ZBValidateBatchData> emailsData = new ArrayList<>();
        emailsData.add(new ZBValidateBatchData("valid@example.com", "1.1.1.1"));
        emailsData.add(new ZBValidateBatchData("invalid@example.com", "1.1.1.1"));
        emailsData.add(new ZBValidateBatchData("disposable@example.com", null));

        String urlPath = "https://api.zerobounce.net/v2/validatebatch";
        mockRequest(
                urlPath,
                200,
                responseJson,
                "",
                () -> zeroBounceSDK.validateBatch(
                        emailsData,
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );
    }

    @Test
    public void validateBatchEmails_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                      "email_batch": [],
                      "errors": [
                          {
                              "error": "Invalid API Key or your account ran out of credits",
                              "email_address": "all"
                          }
                      ]
                    }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        List<ZBValidateBatchData> emailsData = new ArrayList<>();
        emailsData.add(new ZBValidateBatchData("valid@example.com", "1.1.1.1"));
        emailsData.add(new ZBValidateBatchData("invalid@example.com", "1.1.1.1"));
        emailsData.add(new ZBValidateBatchData("disposable@example.com", null));

        String urlPath = "https://api.zerobounce.net/v2/validatebatch";
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.validateBatch(
                        emailsData,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void guessFormat_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                      "email": "john.doe@example.com",
                      "domain": "example.com",
                      "format": "first.last",
                      "status": "valid",
                      "sub_status": "",
                      "confidence": "HIGH",
                      "did_you_mean": "",
                      "failure_reason": "",
                      "other_domain_formats": [
                        {
                            "format": "first_last",
                            "confidence": "HIGH"
                        },
                        {
                            "format": "first",
                            "confidence": "MEDIUM"
                        }
                      ]
                    }""";
        ZBEmailFinderResponse expectedResponse = gson.fromJson(responseJson, ZBEmailFinderResponse.class);

        String domain = "example.com";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("domain", domain);
                    }
                }
        );
        mockRequest(
                urlPath,
                200,
                responseJson,
                "",
                () -> zeroBounceSDK.guessFormat(
                        domain,
                        null,
                        null,
                        null,
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );
    }

    @Test
    public void guessFormat_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String domain = "example.com";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("domain", domain);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.guessFormat(
                        domain,
                        null,
                        null,
                        null,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void findEmail_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
            {
                  "email": "john.doe@example.com",
                  "domain": "example.com",
                  "company_name": "X company",
                  "email_confidence": "HIGH",
                  "did_you_mean": "",
                  "failure_reason": ""
                }""";

        ZBFindEmailResponse expectedResponse = gson.fromJson(responseJson, ZBFindEmailResponse.class);

        String domain = "example.com";
        String firstName = "John doe";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("domain", domain);
                        put("firstName", firstName);
                    }
                }
        );

        mockRequest(
                urlPath,
                200,
                responseJson,
                "",
                () -> zeroBounceSDK.findEmail(
                        firstName,
                        domain,
                        null,
                        null,
                        null,
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString()))
        );
    }

    @Test
    public void findEmail_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String domain = "example.com";
        String firstName = "John doe";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("domain", domain);
                        put("firstName", firstName);
                    }
                }
        );

        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.findEmail(
                        firstName,
                        domain,
                        null,
                        null,
                        null,
                        response -> fail(response.toString()), errorResponse ->
                                assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void findEmail_NoCompanyNameNoDomain_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":[\"Either companyName or domain must be provided.\"]}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String firstName = "John doe";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("firstName", firstName);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.findEmail(
                        firstName,
                        null,
                        null,
                        null,
                        null,
                        response -> fail(response.toString()), errorResponse ->
                                assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void findDomain_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
            {
                  "email": "john.doe@example.com",
                  "domain": "example.com",
                  "company_name": "X company",
                  "format": "first.last",
                  "confidence": "HIGH",
                  "did_you_mean": "",
                  "failure_reason": "",
                  "other_domain_formats": [
                    {
                        "format": "first_last",
                        "confidence": "HIGH"
                    },
                    {
                        "format": "first",
                        "confidence": "MEDIUM"
                    }
                  ]
                }""";
        ZBFindDomainResponse expectedResponse = gson.fromJson(responseJson, ZBFindDomainResponse.class);

        String domain = "example.com";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("domain", domain);
                    }
                }
        );
        mockRequest(
                urlPath,
                200,
                responseJson,
                "",
                () -> zeroBounceSDK.findDomain(
                        domain,
                        null,
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );
    }

    @Test
    public void findDomain_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String domain = "example.com";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("domain", domain);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "", responseJson,
                () -> zeroBounceSDK.findDomain(
                        domain,
                        null,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void findDomain_NoCompanyNameNoDomain_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":[\"Either companyName or domain must be provided.\"]}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "", responseJson,
                () -> zeroBounceSDK.findDomain(
                        null,
                        null,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void getCredits_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"Credits\":2375323}";
        ZBCreditsResponse expectedResponse = gson.fromJson(responseJson, ZBCreditsResponse.class);

        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/getcredits",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                    }
                }
        );
        mockRequest(
                urlPath,
                200,
                responseJson,
                "",
                () -> zeroBounceSDK.getCredits(
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );
    }

    @Test
    public void getCredits_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"Credits\":-1}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/getcredits",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.getCredits(
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void getApiUsage_ReturnSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                      "total": 3,
                      "status_valid": 1,
                      "status_invalid": 2,
                      "status_catch_all": 0,
                      "status_do_not_mail": 0,
                      "status_spamtrap": 0,
                      "status_unknown": 0,
                      "sub_status_toxic": 0,
                      "sub_status_disposable": 0,
                      "sub_status_role_based": 0,
                      "sub_status_possible_trap": 0,
                      "sub_status_global_suppression": 0,
                      "sub_status_timeout_exceeded": 0,
                      "sub_status_mail_server_temporary_error": 0,
                      "sub_status_mail_server_did_not_respond": 0,
                      "sub_status_greylisted": 0,
                      "sub_status_antispam_system": 0,
                      "sub_status_does_not_accept_mail": 0,
                      "sub_status_exception_occurred": 0,
                      "sub_status_failed_syntax_check": 0,
                      "sub_status_mailbox_not_found": 2,
                      "sub_status_unroutable_ip_address": 0,
                      "sub_status_possible_typo": 0,
                      "sub_status_no_dns_entries": 0,
                      "sub_status_role_based_catch_all": 0,
                      "sub_status_mailbox_quota_exceeded": 0,
                      "sub_status_forcible_disconnect": 0,
                      "sub_status_failed_smtp_connection": 0,
                      "start_date": "1/1/2018",
                      "end_date": "12/12/2019"
                    }""";
        ZBGetApiUsageResponse expectedResponse = gson.fromJson(responseJson, ZBGetApiUsageResponse.class);

        Date startDate = new Date();
        startDate.setTime(startDate.getTime() - 5 * 24 * 60 * 60 * 1000);  // previous 5 days
        Date endDate = new Date();

        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/getapiusage",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("start_date", dateFormat.format(startDate));
                        put("end_date", dateFormat.format(endDate));
                    }
                }
        );
        mockRequest(
                urlPath,
                200,
                responseJson,
                "",
                () -> zeroBounceSDK.getApiUsage(
                        startDate,
                        endDate,
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );
    }

    @Test
    public void getApiUsage_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":\"Invalid API Key\"}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        Date startDate = new Date();
        startDate.setTime(startDate.getTime() - 5 * 24 * 60 * 60 * 1000);  // previous 5 days
        Date endDate = new Date();

        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/getapiusage",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("start_date", dateFormat.format(startDate));
                        put("end_date", dateFormat.format(endDate));
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.getApiUsage(
                        startDate,
                        endDate,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void sendFile_FileStatus_GetFile_DeleteFile_ReturnSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                      "success": true,
                      "message": "File Accepted",
                      "file_name": "email_file.csv",
                      "file_id": "b222a0fd-90d5-416c-8f1a-9cc3851fc823"
                    }""";
        ZBSendFileResponse expectedResponse = gson.fromJson(responseJson, ZBSendFileResponse.class);

        File file = new File("../email_file.csv");
        String urlPath = "https://bulkapi.zerobounce.net/v2/sendfile";
        mockMultipartRequest(
                urlPath,
                200,
                ContentType.APPLICATION_JSON.getMimeType(),
                responseJson,
                () -> zeroBounceSDK.sendFile(
                        file,
                        1,
                        null,
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );


        // -----------------------------------
        // Prepare to check that the file status request works

        String fileStatusJson = """
                {
                    "success": true,
                    "file_id": "b222a0fd-90d5-416c-8f1a-9cc3851fc823",
                    "file_name": "email_file.csv",
                    "upload_date": "10/20/2018 4:35:58 PM",
                    "file_status": "Complete",
                    "complete_percentage": "100%",
                    "return_url": "Your return URL if provided when calling sendfile API"
                  }""";
        ZBFileStatusResponse fileStatusResponse = gson.fromJson(fileStatusJson, ZBFileStatusResponse.class);

        String fileId = expectedResponse.getFileId();
        String fileStatusUrlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/filestatus",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        assertNotNull(fileId);
        mockRequest(
                fileStatusUrlPath,
                200,
                fileStatusJson,
                "",
                () -> zeroBounceSDK.fileStatus(
                        fileId,
                        response -> assertEquals(fileStatusResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );

        // -----------------------------------
        // Prepare to get the file we just uploaded

        ZBGetFileResponse getFileResponse = new ZBGetFileResponse();
        getFileResponse.setLocalFilePath("./downloads/b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv");

        String getFileUrlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/getfile",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockMultipartRequest(
                getFileUrlPath,
                200,
                ContentType.DEFAULT_BINARY.getMimeType(),
                responseJson,
                () -> zeroBounceSDK.getFile(
                        fileId,
                        "./downloads/b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv",
                        response -> assertEquals(getFileResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );

        // -----------------------------------
        // Prepare to delete the file we just uploaded

        String deleteFileJson = """
                {
                  "success": true,
                  "message": "File Deleted",
                  "file_name": "test2",
                  "file_id": "b222a0fd-90d5-416c-8f1a-9cc3851fc823"
                }""";
        ZBDeleteFileResponse deleteFileResponse = gson.fromJson(deleteFileJson, ZBDeleteFileResponse.class);

        String deleteFileUrlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/deletefile",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockRequest(
                deleteFileUrlPath,
                200,
                deleteFileJson,
                "",
                () -> zeroBounceSDK.deleteFile(
                        fileId,
                        response -> assertEquals(deleteFileResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );
    }

    @Test
    public void scoringSendFile_ScoringFileStatus_ScoringGetFile_ScoringDeleteFile_ReturnSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                      "success": true,
                      "message": "File Accepted",
                      "file_name": "email_file.csv",
                      "file_id": "b222a0fd-90d5-416c-8f1a-9cc3851fc823"
                    }""";
        ZBSendFileResponse expectedResponse = gson.fromJson(responseJson, ZBSendFileResponse.class);

        File file = new File("../email_file.csv");
        String urlPath = "https://bulkapi.zerobounce.net/v2/scoring/sendfile";
        mockMultipartRequest(
                urlPath,
                200,
                ContentType.APPLICATION_JSON.getMimeType(),
                responseJson,
                () -> zeroBounceSDK.scoringSendFile(
                        file,
                        1,
                        null,
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );


        // -----------------------------------
        // Prepare to check that the file status request works

        String fileStatusJson = """
                {
                    "success": true,
                    "file_id": "b222a0fd-90d5-416c-8f1a-9cc3851fc823",
                    "file_name": "email_file.csv",
                    "upload_date": "10/20/2018 4:35:58 PM",
                    "file_status": "Complete",
                    "complete_percentage": "100%",
                    "return_url": "Your return URL if provided when calling sendfile API"
                  }""";
        ZBFileStatusResponse fileStatusResponse = gson.fromJson(fileStatusJson, ZBFileStatusResponse.class);

        String fileId = expectedResponse.getFileId();
        String fileStatusUrlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/scoring/filestatus",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        assertNotNull(fileId);

        mockRequest(
                fileStatusUrlPath,
                200,
                fileStatusJson,
                "",
                () -> zeroBounceSDK.scoringFileStatus(
                        fileId,
                        response -> assertEquals(fileStatusResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );

        // -----------------------------------
        // Prepare to get the file we just uploaded

        ZBGetFileResponse getFileResponse = new ZBGetFileResponse();
        getFileResponse.setLocalFilePath("./downloads/b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv");

        String getFileUrlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/scoring/getfile",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockMultipartRequest(
                getFileUrlPath,
                200,
                ContentType.DEFAULT_BINARY.getMimeType(),
                responseJson,
                () -> zeroBounceSDK.scoringGetFile(
                        fileId,
                        "./downloads/b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv",
                        response -> assertEquals(getFileResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );


        // -----------------------------------
        // Prepare to delete the file we just uploaded

        String deleteFileJson = """
                {
                  "success": true,
                  "message": "File Deleted",
                  "file_name": "test2",
                  "file_id": "b222a0fd-90d5-416c-8f1a-9cc3851fc823"
                }""";
        ZBDeleteFileResponse deleteFileResponse = gson.fromJson(deleteFileJson, ZBDeleteFileResponse.class);

        String deleteFileUrlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/scoring/deletefile",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockRequest(
                deleteFileUrlPath,
                200,
                deleteFileJson,
                "",
                () -> zeroBounceSDK.scoringDeleteFile(
                        fileId,
                        response -> assertEquals(deleteFileResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );
    }

    @Test
    public void sendFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                      "success": false,
                      "message": [
                          "Error messages"
                      ]
                  }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        File file = new File("../email_file.csv");
        String urlPath = "https://bulkapi.zerobounce.net/v2/sendfile";
        mockMultipartRequest(
                urlPath,
                400,
                ContentType.APPLICATION_JSON.getMimeType(),
                responseJson,
                () -> zeroBounceSDK.sendFile(
                        file,
                        1,
                        null,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );


    }

    @Test
    public void scoringSendFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                      "success": false,
                      "message": [
                          "Error messages"
                      ]
                  }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        File file = new File("../email_file.csv");
        String urlPath = "https://bulkapi.zerobounce.net/v2/scoring/sendfile";
        mockMultipartRequest(
                urlPath,
                400,
                ContentType.APPLICATION_JSON.getMimeType(),
                responseJson,
                () -> zeroBounceSDK.scoringSendFile(
                        file,
                        1,
                        null,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void fileStatus_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                    "success": false,
                    "message": "Error messages"
                  }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/filestatus",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.fileStatus(
                        fileId,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void scoringFileStatus_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                    "success": false,
                    "message": "Error messages"
                  }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/scoring/filestatus",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.scoringFileStatus(
                        fileId,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void getFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                    "success": false,
                    "message": "Error messages"
                }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/getfile",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockMultipartRequest(
                urlPath,
                400,
                ContentType.APPLICATION_JSON.getMimeType(),
                responseJson,
                () -> zeroBounceSDK.getFile(
                        fileId,
                        "./downloads/file.csv",
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void scoringGetFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                    "success": false,
                    "message": "Error messages"
                }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/scoring/getfile",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockMultipartRequest(
                urlPath,
                400,
                ContentType.APPLICATION_JSON.getMimeType(),
                responseJson,
                () -> zeroBounceSDK.scoringGetFile(
                        fileId,
                        "./downloads/file.csv",
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void deleteFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                  "success": false,
                  "message": "File cannot be found."
                }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/deletefile",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.deleteFile(
                        fileId,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void scoringDeleteFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                  "success": false,
                  "message": "File cannot be found."
                }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = getEncodedUrl(
                "https://bulkapi.zerobounce.net/v2/scoring/deletefile",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("file_id", fileId);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.scoringDeleteFile(
                        fileId,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void getActivityData_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                  "found": true,
                  "active_in_days": 60
                }""";
        ZBActivityDataResponse expectedResponse = gson.fromJson(responseJson, ZBActivityDataResponse.class);

        String email = "flowerjill@aol.com";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/activity",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("email", email);
                    }
                }
        );
        mockRequest(
                urlPath,
                200,
                responseJson,
                "",
                () -> zeroBounceSDK.getActivityData(
                        email,
                        response -> assertEquals(expectedResponse, response),
                        errorResponse -> fail(errorResponse.toString())
                )
        );
    }

    @Test
    public void getActivityData_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = """
                {
                  "found": false,
                  "active_in_days": null
                }""";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String email = "flowerjill@aol.com";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/activity",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                        put("email", email);
                    }
                }
        );
        mockRequest(
                urlPath,
                400,
                "",
                responseJson,
                () -> zeroBounceSDK.getActivityData(
                        email,
                        response -> fail(response.toString()),
                        errorResponse -> assertEquals(expectedResponse, errorResponse)
                )
        );
    }

    @Test
    public void defaultLoggerEmitsNoConsoleOutput() throws Exception {
        String responseJson = "{\"Credits\":2375323}";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/getcredits",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                    }
                }
        );

        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));

        ZeroBounceSDK.setLogger(null);
        ZeroBounceSDK.setLogPayloads(false);

        try {
            mockRequest(
                    urlPath,
                    200,
                    responseJson,
                    "",
                    () -> zeroBounceSDK.getCredits(
                            response -> {
                            },
                            errorResponse -> fail(errorResponse.toString())
                    )
            );

        } finally {
            System.setOut(originalOut);
            ZeroBounceSDK.setLogger(null);
            ZeroBounceSDK.setLogPayloads(false);
        }

        assertEquals("", capturedOut.toString());
    }

    @Test
    public void payloadLoggingUsesConfiguredLoggerOnly() throws Exception {
        String responseJson = "{\"Credits\":2375323}";
        String urlPath = getEncodedUrl(
                "https://api.zerobounce.net/v2/getcredits",
                new HashMap<>() {
                    {
                        put("api_key", API_KEY);
                    }
                }
        );

        List<String> debugMessages = new ArrayList<>();
        ZBLogger testLogger = new ZBLogger() {
            @Override
            public void debug(String msg) {
                debugMessages.add(msg);
            }
        };

        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));

        ZeroBounceSDK.setLogger(testLogger);
        ZeroBounceSDK.setLogPayloads(true);

        try {
            mockRequest(
                    urlPath,
                    200,
                    responseJson,
                    "",
                    () -> zeroBounceSDK.getCredits(
                            response -> {
                            }, errorResponse -> fail(errorResponse.toString())
                    )
            );

        } finally {
            System.setOut(originalOut);
            ZeroBounceSDK.setLogger(null);
            ZeroBounceSDK.setLogPayloads(false);
        }

        assertTrue(debugMessages.stream().anyMatch(message -> message.contains("ZeroBounceSDK::sendRequest rsp=" + responseJson)));
        assertEquals("", capturedOut.toString());
    }

    private String getEncodedUrl(
            String urlPath,
            @Nullable Map<String, String> queryParameters
    ) throws URISyntaxException {
        URIBuilder ub = new URIBuilder(urlPath);
        if (queryParameters != null) {
            for (Map.Entry<String, String> param : queryParameters.entrySet()) {
                ub.addParameter(param.getKey(), param.getValue());
            }
        }
        return ub.toString();
    }

    private void mockRequest(
            @NotNull String urlPath,
            int statusCode,
            @NotNull String response,
            @NotNull String errorResponse,
            Runnable block
    ) throws IOException {
        URL url = mock(URL.class, withSettings().useConstructor(urlPath));
        HttpURLConnection con = mock(HttpURLConnection.class);
        lenient().when(url.openConnection()).thenReturn(con);
        lenient().when(con.getResponseCode()).thenReturn(statusCode);
        lenient().when(con.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        lenient().when(con.getInputStream()).thenReturn(new ByteArrayInputStream(response.getBytes()));
        lenient().when(con.getErrorStream()).thenReturn(new ByteArrayInputStream(errorResponse.getBytes()));
        lenient().doReturn(url).when(zeroBounceSDK).createUrlFrom(anyString());
        block.run();
    }

    private void mockMultipartRequest(
            @NotNull String urlPath,
            int statusCode,
            String contentType,
            @NotNull String response,
            ThrowingRunnable block
    ) throws Exception {
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockedResponse = mock(CloseableHttpResponse.class);
        HttpEntity mockedEntity = mock(HttpEntity.class);
        StatusLine statusLine = mock(StatusLine.class);
        Header header = mock(Header.class);

        try (
                MockedConstruction<HttpPost> ignored = Mockito.mockConstruction(
                        HttpPost.class,
                        withSettings().useConstructor(urlPath)
                );
                MockedStatic<HttpClients> ignored1 = Mockito.mockStatic(HttpClients.class)
        ) {
            lenient().when(HttpClients.createDefault()).thenReturn(httpClient);
            lenient().when(httpClient.execute(any())).thenReturn(mockedResponse);
            lenient().when(mockedResponse.getEntity()).thenReturn(mockedEntity);
            lenient().when(mockedResponse.getStatusLine()).thenReturn(statusLine);
            lenient().when(mockedResponse.getFirstHeader("Content-Type")).thenReturn(header);
            lenient().when(header.getValue()).thenReturn(contentType);
            lenient().when(statusLine.getStatusCode()).thenReturn(statusCode);
            lenient().when(mockedEntity.getContent()).thenReturn(new ByteArrayInputStream(response.getBytes()));

            block.run();
        }
    }
}