package com.zerobounce;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;

@RunWith(PowerMockRunner.class)
@PrepareForTest({URL.class, HttpClients.class, ZeroBounceSDK.class})
@PowerMockIgnore({"javax.net.ssl.*"})
public class ZeroBounceSDKTest {

    private Gson gson;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private static final String API_KEY = "some-api-key";

    @Before
    public void setUp() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.registerTypeAdapter(ZBSendFileResponse.class, new ZBSendFileResponse.Deserializer());
        gsonBuilder.registerTypeAdapter(Date.class, new GsonDateDeserializer());
        gson = gsonBuilder.create();

        ZeroBounceSDK.getInstance().initialize(API_KEY);
    }

    @Test
    public void validateEmail_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "        \"address\":\"flowerjill@aol.com\",\n" +
                "        \"status\":\"valid\",\n" +
                "        \"sub_status\":\"\",\n" +
                "        \"free_email\":true,\n" +
                "        \"did_you_mean\":null,\n" +
                "        \"account\":\"flowerjill\",\n" +
                "        \"domain\":\"aol.com\",\n" +
                "        \"domain_age_days\": \"8426\",\n" +
                "        \"smtp_provider\":\"yahoo\",\n" +
                "        \"mx_record\":\"mx-aol.mail.gm0.yahoodns.net\",\n" +
                "        \"mx_found\": \"true\",\n" +
                "        \"firstname\":\"Jill\",\n" +
                "        \"lastname\":\"Stein\",\n" +
                "        \"gender\":\"female\",\n" +
                "        \"country\":\"United States\",\n" +
                "        \"region\":\"Florida\",\n" +
                "        \"city\":\"West Palm Beach\",\n" +
                "        \"zipcode\":\"33401\",\n" +
                "        \"processed_at\":\"2017-04-01 02:48:02.592\"\n" +
                "        }";
        ZBValidateResponse expectedResponse = gson.fromJson(responseJson, ZBValidateResponse.class);

        String email = "flowerjill@aol.com";
        String urlPath = "https://api.zerobounce.in/v2/validate?api_key=" + API_KEY + "&email=" + email + "&ip_address=";
        mockRequest(urlPath, 200, responseJson, "");

        ZeroBounceSDK.getInstance().validate(
                email,
                null,
                response -> {
                    assertEquals(expectedResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });
    }

    @Test
    public void validateEmail_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String email = "flowerjill@aol.com";
        String urlPath = "https://api.zerobounce.in/v2/validate?api_key=" + API_KEY + "&email=" + email + "&ip_address=";
        mockRequest(urlPath, 400, "", responseJson);

        ZeroBounceSDK.getInstance().validate(
                email,
                null,
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void validateBatchEmails_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "      \"email_batch\": [\n" +
                "          {\n" +
                "              \"address\": \"valid@example.com\",\n" +
                "              \"status\": \"valid\",\n" +
                "              \"sub_status\": \"\",\n" +
                "              \"free_email\": false,\n" +
                "              \"did_you_mean\": null,\n" +
                "              \"account\": null,\n" +
                "              \"domain\": null,\n" +
                "              \"domain_age_days\": \"9692\",\n" +
                "              \"smtp_provider\": \"example\",\n" +
                "              \"mx_found\": \"true\",\n" +
                "              \"mx_record\": \"mx.example.com\",\n" +
                "              \"firstname\": \"zero\",\n" +
                "              \"lastname\": \"bounce\",\n" +
                "              \"gender\": \"male\",\n" +
                "              \"country\": null,\n" +
                "              \"region\": null,\n" +
                "              \"city\": null,\n" +
                "              \"zipcode\": null,\n" +
                "              \"processed_at\": \"2020-09-17 17:43:11.829\"\n" +
                "          },\n" +
                "          {\n" +
                "              \"address\": \"invalid@example.com\",\n" +
                "              \"status\": \"invalid\",\n" +
                "              \"sub_status\": \"mailbox_not_found\",\n" +
                "              \"free_email\": false,\n" +
                "              \"did_you_mean\": null,\n" +
                "              \"account\": null,\n" +
                "              \"domain\": null,\n" +
                "              \"domain_age_days\": \"9692\",\n" +
                "              \"smtp_provider\": \"example\",\n" +
                "              \"mx_found\": \"true\",\n" +
                "              \"mx_record\": \"mx.example.com\",\n" +
                "              \"firstname\": \"zero\",\n" +
                "              \"lastname\": \"bounce\",\n" +
                "              \"gender\": \"male\",\n" +
                "              \"country\": null,\n" +
                "              \"region\": null,\n" +
                "              \"city\": null,\n" +
                "              \"zipcode\": null,\n" +
                "              \"processed_at\": \"2020-09-17 17:43:11.830\"\n" +
                "          },\n" +
                "          {\n" +
                "              \"address\": \"disposable@example.com\",\n" +
                "              \"status\": \"do_not_mail\",\n" +
                "              \"sub_status\": \"disposable\",\n" +
                "              \"free_email\": false,\n" +
                "              \"did_you_mean\": null,\n" +
                "              \"account\": null,\n" +
                "              \"domain\": null,\n" +
                "              \"domain_age_days\": \"9692\",\n" +
                "              \"smtp_provider\": \"example\",\n" +
                "              \"mx_found\": \"true\",\n" +
                "              \"mx_record\": \"mx.example.com\",\n" +
                "              \"firstname\": \"zero\",\n" +
                "              \"lastname\": \"bounce\",\n" +
                "              \"gender\": \"male\",\n" +
                "              \"country\": null,\n" +
                "              \"region\": null,\n" +
                "              \"city\": null,\n" +
                "              \"zipcode\": null,\n" +
                "              \"processed_at\": \"2020-09-17 17:43:11.830\"\n" +
                "          }\n" +
                "      ],\n" +
                "      \"errors\": []\n" +
                "  }";
        ZBValidateBatchResponse expectedResponse = gson.fromJson(responseJson, ZBValidateBatchResponse.class);

        String urlPath = "https://api.zerobounce.in/v2/validatebatch";
        mockRequest(urlPath, 200, responseJson, "");

        List<ZBValidateBatchData> emailsData = new ArrayList<ZBValidateBatchData>();
        emailsData.add(new ZBValidateBatchData("valid@example.com", "1.1.1.1"));
        emailsData.add(new ZBValidateBatchData("invalid@example.com", "1.1.1.1"));
        emailsData.add(new ZBValidateBatchData("disposable@example.com", null));

        ZeroBounceSDK.getInstance().validateBatch(
                emailsData,
                response -> {
                    assertEquals(expectedResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });
    }

    @Test
    public void validateBatchEmails_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "      \"email_batch\": [],\n" +
                "      \"errors\": [\n" +
                "          {\n" +
                "              \"error\": \"Invalid API Key or your account ran out of credits\",\n" +
                "              \"email_address\": \"all\"\n" +
                "          }\n" +
                "      ]\n" +
                "    }";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String urlPath = "https://api.zerobounce.in/v2/validatebatch";
        mockRequest(urlPath, 400, "", responseJson);

        List<ZBValidateBatchData> emailsData = new ArrayList<ZBValidateBatchData>();
        emailsData.add(new ZBValidateBatchData("valid@example.com", "1.1.1.1"));
        emailsData.add(new ZBValidateBatchData("invalid@example.com", "1.1.1.1"));
        emailsData.add(new ZBValidateBatchData("disposable@example.com", null));

        ZeroBounceSDK.getInstance().validateBatch(
                emailsData,
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void guessFormat_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "      \"email\": \"john.doe@example.com\",\n" +
                "      \"domain\": \"example.com\",\n" +
                "      \"format\": \"first.last\",\n" +
                "      \"status\": \"valid\",\n" +
                "      \"sub_status\": \"\",\n" +
                "      \"confidence\": \"HIGH\",\n" +
                "      \"did_you_mean\": \"\",\n" +
                "      \"failure_reason\": \"\",\n" +
                "      \"other_domain_formats\": [\n" +
                "        {\n" +
                "            \"format\": \"first_last\",\n" +
                "            \"confidence\": \"HIGH\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"format\": \"first\",\n" +
                "            \"confidence\": \"MEDIUM\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }";
        ZBEmailFinderResponse expectedResponse = gson.fromJson(responseJson, ZBEmailFinderResponse.class);

        String domain = "example.com";
        String urlPath = "https://api.zerobounce.net/v2/guessformat?api_key=" + API_KEY + "&domain=" + domain;
        mockRequest(urlPath, 200, responseJson, "");

        ZeroBounceSDK.getInstance().guessFormat(
                domain,
                null,
                null,
                null,
                response -> {
                    assertEquals(expectedResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });
    }

    @Test
    public void guessFormat_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":[\"Invalid API Key or your account ran out of credits\"]}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String domain = "example.com";
        String urlPath = "https://api.zerobounce.net/v2/guessformat?api_key=" + API_KEY + "&domain=" + domain;
        mockRequest(urlPath, 400, "", responseJson);

        ZeroBounceSDK.getInstance().guessFormat(
                domain,
                null,
                null,
                null,
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void getCredits_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"Credits\":2375323}";
        ZBCreditsResponse expectedResponse = gson.fromJson(responseJson, ZBCreditsResponse.class);

        String urlPath = "https://api.zerobounce.in/v2/getcredits?api_key=" + API_KEY;
        mockRequest(urlPath, 200, responseJson, "");

        ZeroBounceSDK.getInstance().getCredits(
                response -> {
                    assertEquals(expectedResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });
    }

    @Test
    public void getCredits_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"Credits\":-1}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String urlPath = "https://api.zerobounce.in/v2/getcredits?api_key=" + API_KEY;
        mockRequest(urlPath, 400, "", responseJson);

        ZeroBounceSDK.getInstance().getCredits(
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void getApiUsage_ReturnSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "      \"total\": 3,\n" +
                "      \"status_valid\": 1,\n" +
                "      \"status_invalid\": 2,\n" +
                "      \"status_catch_all\": 0,\n" +
                "      \"status_do_not_mail\": 0,\n" +
                "      \"status_spamtrap\": 0,\n" +
                "      \"status_unknown\": 0,\n" +
                "      \"sub_status_toxic\": 0,\n" +
                "      \"sub_status_disposable\": 0,\n" +
                "      \"sub_status_role_based\": 0,\n" +
                "      \"sub_status_possible_trap\": 0,\n" +
                "      \"sub_status_global_suppression\": 0,\n" +
                "      \"sub_status_timeout_exceeded\": 0,\n" +
                "      \"sub_status_mail_server_temporary_error\": 0,\n" +
                "      \"sub_status_mail_server_did_not_respond\": 0,\n" +
                "      \"sub_status_greylisted\": 0,\n" +
                "      \"sub_status_antispam_system\": 0,\n" +
                "      \"sub_status_does_not_accept_mail\": 0,\n" +
                "      \"sub_status_exception_occurred\": 0,\n" +
                "      \"sub_status_failed_syntax_check\": 0,\n" +
                "      \"sub_status_mailbox_not_found\": 2,\n" +
                "      \"sub_status_unroutable_ip_address\": 0,\n" +
                "      \"sub_status_possible_typo\": 0,\n" +
                "      \"sub_status_no_dns_entries\": 0,\n" +
                "      \"sub_status_role_based_catch_all\": 0,\n" +
                "      \"sub_status_mailbox_quota_exceeded\": 0,\n" +
                "      \"sub_status_forcible_disconnect\": 0,\n" +
                "      \"sub_status_failed_smtp_connection\": 0,\n" +
                "      \"start_date\": \"1/1/2018\",\n" +
                "      \"end_date\": \"12/12/2019\"\n" +
                "    }";
        ZBGetApiUsageResponse expectedResponse = gson.fromJson(responseJson, ZBGetApiUsageResponse.class);

        Date startDate = new Date();
        startDate.setTime(startDate.getTime() - 5 * 24 * 60 * 60 * 1000);  // previous 5 days
        Date endDate = new Date();

        String urlPath = "https://api.zerobounce.in/v2/getapiusage?api_key=" + API_KEY
                + "&start_date=" + dateFormat.format(startDate)
                + "&end_date=" + dateFormat.format(endDate);
        mockRequest(urlPath, 200, responseJson, "");

        ZeroBounceSDK.getInstance().getApiUsage(
                startDate,
                endDate,
                response -> {
                    assertEquals(expectedResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });
    }

    @Test
    public void getApiUsage_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\"error\":\"Invalid API Key\"}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        Date startDate = new Date();
        startDate.setTime(startDate.getTime() - 5 * 24 * 60 * 60 * 1000);  // previous 5 days
        Date endDate = new Date();

        String urlPath = "https://api.zerobounce.in/v2/getapiusage?api_key=" + API_KEY
                + "&start_date=" + dateFormat.format(startDate)
                + "&end_date=" + dateFormat.format(endDate);
        mockRequest(urlPath, 400, "", responseJson);

        ZeroBounceSDK.getInstance().getApiUsage(
                startDate,
                endDate,
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void sendFile_FileStatus_GetFile_DeleteFile_ReturnSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "      \"success\": true,\n" +
                "      \"message\": \"File Accepted\",\n" +
                "      \"file_name\": \"email_file.csv\",\n" +
                "      \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\"\n" +
                "    }";
        ZBSendFileResponse expectedResponse = gson.fromJson(responseJson, ZBSendFileResponse.class);

        File file = new File("../email_file.csv");
        String urlPath = "https://bulkapi.zerobounce.in/v2/sendfile";
        mockMultipartRequest(urlPath, 200, ContentType.APPLICATION_JSON.getMimeType(), responseJson);

        ZeroBounceSDK.getInstance().sendFile(
                file,
                1,
                null,
                response -> {
                    assertEquals(expectedResponse, response);
                },
                errorResponse -> {
                    fail(errorResponse.toString());
                }
        );

        // -----------------------------------
        // Prepare to check that the file status request works

        String fileStatusJson = "{\n" +
                "    \"success\": true,\n" +
                "    \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\",\n" +
                "    \"file_name\": \"email_file.csv\",\n" +
                "    \"upload_date\": \"10/20/2018 4:35:58 PM\",\n" +
                "    \"file_status\": \"Complete\",\n" +
                "    \"complete_percentage\": \"100%\",\n" +
                "    \"return_url\": \"Your return URL if provided when calling sendfile API\"\n" +
                "  }";
        ZBFileStatusResponse fileStatusResponse = gson.fromJson(fileStatusJson, ZBFileStatusResponse.class);

        String fileId = expectedResponse.getFileId();
        String fileStatusUrlPath = "https://bulkapi.zerobounce.in/v2/filestatus?api_key=" + API_KEY + "&file_id=" + fileId;
        mockRequest(fileStatusUrlPath, 200, fileStatusJson, "");

        assertNotNull(fileId);

        ZeroBounceSDK.getInstance().fileStatus(
                fileId,
                response -> {
                    assertEquals(fileStatusResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });

        // -----------------------------------
        // Prepare to get the file we just uploaded

        ZBGetFileResponse getFileResponse = new ZBGetFileResponse();
        getFileResponse.setLocalFilePath("./downloads/b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv");

        String getFileUrlPath = "https://bulkapi.zerobounce.in/v2/getfile?api_key=" + API_KEY + "&file_id=" + fileId;
        mockMultipartRequest(getFileUrlPath, 200, ContentType.DEFAULT_BINARY.getMimeType(), responseJson);

        ZeroBounceSDK.getInstance().getFile(
                fileId,
                "./downloads/b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv",
                response -> {
                    assertEquals(getFileResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });

        // -----------------------------------
        // Prepare to delete the file we just uploaded

        String deleteFileJson = "{\n" +
                "  \"success\": true,\n" +
                "  \"message\": \"File Deleted\",\n" +
                "  \"file_name\": \"test2\",\n" +
                "  \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\"\n" +
                "}";
        ZBDeleteFileResponse deleteFileResponse = gson.fromJson(deleteFileJson, ZBDeleteFileResponse.class);

        String deleteFileUrlPath = "https://bulkapi.zerobounce.in/v2/deletefile?api_key=" + API_KEY + "&file_id=" + fileId;
        mockRequest(deleteFileUrlPath, 200, deleteFileJson, "");

        ZeroBounceSDK.getInstance().deleteFile(
                fileId,
                response -> {
                    assertEquals(deleteFileResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });
    }

    @Test
    public void scoringSendFile_ScoringFileStatus_ScoringGetFile_ScoringDeleteFile_ReturnSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "      \"success\": true,\n" +
                "      \"message\": \"File Accepted\",\n" +
                "      \"file_name\": \"email_file.csv\",\n" +
                "      \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\"\n" +
                "    }";
        ZBSendFileResponse expectedResponse = gson.fromJson(responseJson, ZBSendFileResponse.class);

        File file = new File("../email_file.csv");
        String urlPath = "https://bulkapi.zerobounce.in/v2/scoring/sendfile";
        mockMultipartRequest(urlPath, 200, ContentType.APPLICATION_JSON.getMimeType(), responseJson);

        ZeroBounceSDK.getInstance().scoringSendFile(
                file,
                1,
                null,
                response -> {
                    assertEquals(expectedResponse, response);
                },
                errorResponse -> {
                    fail(errorResponse.toString());
                }
        );

        // -----------------------------------
        // Prepare to check that the file status request works

        String fileStatusJson = "{\n" +
                "    \"success\": true,\n" +
                "    \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\",\n" +
                "    \"file_name\": \"email_file.csv\",\n" +
                "    \"upload_date\": \"10/20/2018 4:35:58 PM\",\n" +
                "    \"file_status\": \"Complete\",\n" +
                "    \"complete_percentage\": \"100%\",\n" +
                "    \"return_url\": \"Your return URL if provided when calling sendfile API\"\n" +
                "  }";
        ZBFileStatusResponse fileStatusResponse = gson.fromJson(fileStatusJson, ZBFileStatusResponse.class);

        String fileId = expectedResponse.getFileId();
        String fileStatusUrlPath = "https://bulkapi.zerobounce.in/v2/scoring/filestatus?api_key=" + API_KEY + "&file_id=" + fileId;
        mockRequest(fileStatusUrlPath, 200, fileStatusJson, "");

        assertNotNull(fileId);

        ZeroBounceSDK.getInstance().scoringFileStatus(
                fileId,
                response -> {
                    assertEquals(fileStatusResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });

        // -----------------------------------
        // Prepare to get the file we just uploaded

        ZBGetFileResponse getFileResponse = new ZBGetFileResponse();
        getFileResponse.setLocalFilePath("./downloads/b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv");

        String getFileUrlPath = "https://bulkapi.zerobounce.in/v2/scoring/getfile?api_key=" + API_KEY + "&file_id=" + fileId;
        mockMultipartRequest(getFileUrlPath, 200, ContentType.DEFAULT_BINARY.getMimeType(), responseJson);

        ZeroBounceSDK.getInstance().scoringGetFile(
                fileId,
                "./downloads/b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv",
                response -> {
                    assertEquals(getFileResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });

        // -----------------------------------
        // Prepare to delete the file we just uploaded

        String deleteFileJson = "{\n" +
                "  \"success\": true,\n" +
                "  \"message\": \"File Deleted\",\n" +
                "  \"file_name\": \"test2\",\n" +
                "  \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\"\n" +
                "}";
        ZBDeleteFileResponse deleteFileResponse = gson.fromJson(deleteFileJson, ZBDeleteFileResponse.class);

        String deleteFileUrlPath = "https://bulkapi.zerobounce.in/v2/scoring/deletefile?api_key=" + API_KEY + "&file_id=" + fileId;
        mockRequest(deleteFileUrlPath, 200, deleteFileJson, "");

        ZeroBounceSDK.getInstance().scoringDeleteFile(
                fileId,
                response -> {
                    assertEquals(deleteFileResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });
    }

    @Test
    public void sendFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "      \"success\": false,\n" +
                "      \"message\": [\n" +
                "          \"Error messages\"\n" +
                "      ]\n" +
                "  }";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        File file = new File("../email_file.csv");
        String urlPath = "https://bulkapi.zerobounce.in/v2/sendfile";
        mockMultipartRequest(urlPath, 400, ContentType.APPLICATION_JSON.getMimeType(), responseJson);

        ZeroBounceSDK.getInstance().sendFile(
                file,
                1,
                null,
                response -> {
                    fail(response.toString());
                },
                errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                }
        );
    }

    @Test
    public void scoringSendFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "      \"success\": false,\n" +
                "      \"message\": [\n" +
                "          \"Error messages\"\n" +
                "      ]\n" +
                "  }";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        File file = new File("../email_file.csv");
        String urlPath = "https://bulkapi.zerobounce.in/v2/scoring/sendfile";
        mockMultipartRequest(urlPath, 400, ContentType.APPLICATION_JSON.getMimeType(), responseJson);

        ZeroBounceSDK.getInstance().scoringSendFile(
                file,
                1,
                null,
                response -> {
                    fail(response.toString());
                },
                errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                }
        );
    }

    @Test
    public void fileStatus_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"message\": \"Error messages\"\n" +
                "  }";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = "https://bulkapi.zerobounce.in/v2/filestatus?api_key=" + API_KEY + "&file_id=" + fileId;
        mockRequest(urlPath, 400, "", responseJson);

        ZeroBounceSDK.getInstance().fileStatus(
                fileId,
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void scoringFileStatus_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"message\": \"Error messages\"\n" +
                "  }";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = "https://bulkapi.zerobounce.in/v2/scoring/filestatus?api_key=" + API_KEY + "&file_id=" + fileId;
        mockRequest(urlPath, 400, "", responseJson);

        ZeroBounceSDK.getInstance().scoringFileStatus(
                fileId,
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void getFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"message\": \"Error messages\"\n" +
                "}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = "https://bulkapi.zerobounce.in/v2/getfile?api_key=" + API_KEY + "&file_id=" + fileId;
        mockMultipartRequest(urlPath, 400, ContentType.APPLICATION_JSON.getMimeType(), responseJson);

        ZeroBounceSDK.getInstance().getFile(
                fileId,
                "./downloads/file.csv",
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void scoringGetFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"message\": \"Error messages\"\n" +
                "}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = "https://bulkapi.zerobounce.in/v2/scoring/getfile?api_key=" + API_KEY + "&file_id=" + fileId;
        mockMultipartRequest(urlPath, 400, ContentType.APPLICATION_JSON.getMimeType(), responseJson);

        ZeroBounceSDK.getInstance().scoringGetFile(
                fileId,
                "./downloads/file.csv",
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void deleteFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "  \"success\": false,\n" +
                "  \"message\": \"File cannot be found.\"\n" +
                "}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = "https://bulkapi.zerobounce.in/v2/deletefile?api_key=" + API_KEY + "&file_id=" + fileId;
        mockRequest(urlPath, 400, "", responseJson);

        ZeroBounceSDK.getInstance().deleteFile(
                fileId,
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void scoringDeleteFile_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "  \"success\": false,\n" +
                "  \"message\": \"File cannot be found.\"\n" +
                "}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String fileId = "some-id";
        String urlPath = "https://bulkapi.zerobounce.in/v2/scoring/deletefile?api_key=" + API_KEY + "&file_id=" + fileId;
        mockRequest(urlPath, 400, "", responseJson);

        ZeroBounceSDK.getInstance().scoringDeleteFile(
                fileId,
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    @Test
    public void getActivityData_ReturnsSuccess() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "  \"found\": true,\n" +
                "  \"active_in_days\": 60\n" +
                "}";
        ZBActivityDataResponse expectedResponse = gson.fromJson(responseJson, ZBActivityDataResponse.class);

        String email = "flowerjill@aol.com";
        String urlPath = "https://api.zerobounce.in/v2/activity?api_key=" + API_KEY + "&email=" + email;
        mockRequest(urlPath, 200, responseJson, "");

        ZeroBounceSDK.getInstance().getActivityData(
                email,
                response -> {
                    assertEquals(expectedResponse, response);
                }, errorResponse -> {
                    fail(errorResponse.toString());
                });
    }

    @Test
    public void getActivityData_ReturnsError() throws Exception {
        // Prepare mock response and add it to the server
        String responseJson = "{\n" +
                "  \"found\": false,\n" +
                "  \"active_in_days\": null\n" +
                "}";
        ErrorResponse expectedResponse = ErrorResponse.parseError(responseJson);

        String email = "flowerjill@aol.com";
        String urlPath = "https://api.zerobounce.in/v2/activity?api_key=" + API_KEY + "&email=" + email;
        mockRequest(urlPath, 400, "", responseJson);

        ZeroBounceSDK.getInstance().getActivityData(
                email,
                response -> {
                    fail(response.toString());
                }, errorResponse -> {
                    assertEquals(expectedResponse, errorResponse);
                });
    }

    private void mockRequest(
            @NotNull String urlPath,
            int statusCode,
            @NotNull String response,
            @NotNull String errorResponse
    ) throws Exception {
        URL url = PowerMockito.mock(URL.class);
        PowerMockito.whenNew(URL.class).withArguments(urlPath).thenReturn(url);
        HttpURLConnection con = PowerMockito.mock(HttpURLConnection.class);
        PowerMockito.when(url.openConnection()).thenReturn(con);
        PowerMockito.when(con.getResponseCode()).thenReturn(statusCode);
        PowerMockito.when(con.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        PowerMockito.when(con.getInputStream()).thenReturn(new ByteArrayInputStream(response.getBytes()));
        PowerMockito.when(con.getErrorStream()).thenReturn(new ByteArrayInputStream(errorResponse.getBytes()));
    }

    private void mockMultipartRequest(
            @NotNull String urlPath,
            int statusCode,
            String contentType,
            @NotNull String response
    ) throws Exception {
        HttpPost httpPost = PowerMockito.mock(HttpPost.class);
        PowerMockito.whenNew(HttpPost.class).withArguments(urlPath).thenReturn(httpPost);

        CloseableHttpClient httpClient = PowerMockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse mockedResponse = PowerMockito.mock(CloseableHttpResponse.class);
        HttpEntity mockedEntity = PowerMockito.mock(HttpEntity.class);
        StatusLine statusLine = PowerMockito.mock(StatusLine.class);
        Header header = PowerMockito.mock(Header.class);

        PowerMockito.mockStatic(HttpClients.class);
        PowerMockito.when(HttpClients.createDefault()).thenReturn(httpClient);
        PowerMockito.when(httpClient.execute(any())).thenReturn(mockedResponse);
        PowerMockito.when(mockedResponse.getEntity()).thenReturn(mockedEntity);
        PowerMockito.when(mockedResponse.getStatusLine()).thenReturn(statusLine);
        PowerMockito.when(mockedResponse.getFirstHeader("Content-Type")).thenReturn(header);
        PowerMockito.when(header.getValue()).thenReturn(contentType);
        PowerMockito.when(statusLine.getStatusCode()).thenReturn(statusCode);
        PowerMockito.when(mockedEntity.getContent()).thenReturn(new ByteArrayInputStream(response.getBytes()));
    }
}