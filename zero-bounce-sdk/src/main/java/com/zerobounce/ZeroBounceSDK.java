package com.zerobounce;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * The ZeroBounce main class. All the requests are implemented here.
 */
public class ZeroBounceSDK {

    private static volatile ZeroBounceSDK instance;
    private static volatile ZBLogger logger = ZBLogger.noop();
    private static volatile boolean logPayloads = false;

    public static ZeroBounceSDK getInstance() {
        // The implementation below uses a double-check locking mechanism to boost performance while keeping the
        // singleton thread-safe.
        if (instance == null) {
            synchronized (ZeroBounceSDK.class) {
                if (instance == null) {
                    instance = new ZeroBounceSDK();
                }
                return instance;
            }
        }
        return instance;
    }

    /**
     * Sets a custom logger that will receive log messages emitted by the SDK.
     *
     * @param logger the logger to use; when {@code null} logging is disabled
     */
    public static void setLogger(@Nullable ZBLogger logger) {
        ZeroBounceSDK.logger = logger != null ? logger : ZBLogger.noop();
    }

    /**
     * Enables or disables logging of HTTP payload content.
     *
     * @param enabled {@code true} to include payloads in debug logs
     */
    public static void setLogPayloads(boolean enabled) {
        logPayloads = enabled;
    }

    private static final int DEFAULT_TIMEOUT_MILLIS = 120_000;

    String apiBaseUrl = "https://api.zerobounce.net/v2";
    private final String bulkApiBaseUrl = "https://bulkapi.zerobounce.net/v2";
    private final String bulkApiScoringBaseUrl = "https://bulkapi.zerobounce.net/v2/scoring";
    /**
     * The S3 and SFTP bulk jobs live on the bulkapi host WITHOUT the /v2 path.
     * See https://www.zerobounce.net/docs/api-integrations.
     */
    private final String bulkApiRootUrl = "https://bulkapi.zerobounce.net";
    /**
     * The list evaluator and the Inbox Placement Tester have their own base URLs,
     * not paths built from the bulk one. Both are overridable through
     * {@link #initialize(String, String, String, String)}.
     */
    String listEvaluatorBaseUrl = "https://bulkapi.zerobounce.net/v2/listevaluator";
    String inboxPlacementBaseUrl = "https://bulkapi.zerobounce.net/api/ipt";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private String apiKey;
    private int timeoutInMillis;
    private final Gson gson;

    private ZeroBounceSDK() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.registerTypeAdapter(ZBSendFileResponse.class, new ZBSendFileResponse.Deserializer());
        gsonBuilder.registerTypeAdapter(Date.class, new GsonDateDeserializer());
        gson = gsonBuilder.create();
    }

    /**
     * Initializes the SDK.
     * <p>
     * [timeoutInMillis] is set to 120000 (120 seconds) by default
     *
     * @param apiKey the API key
     */
    public void initialize(String apiKey) {
        this.apiKey = apiKey;
        timeoutInMillis = DEFAULT_TIMEOUT_MILLIS;
    }

    /**
     * Initializes the SDK.
     *
     * @param apiKey          the API key
     * @param timeoutInMillis the timeout in milliseconds to use for all requests (0 for no timeout)
     */
    public void initialize(String apiKey, int timeoutInMillis) {
        this.apiKey = apiKey;
        this.timeoutInMillis = timeoutInMillis;
    }

    /**
     * Initializes the SDK.
     *
     * @param apiKey          the API key
     * @param apiBaseUrl      the API base URL
     */
    public void initialize(String apiKey, @Nullable String apiBaseUrl) {
        this.apiKey = apiKey;
        timeoutInMillis = DEFAULT_TIMEOUT_MILLIS;
        if (apiBaseUrl != null) {
            requireHttpsUrl(apiBaseUrl);
            this.apiBaseUrl = apiBaseUrl;
        }
    }

    /**
     * Initializes the SDK, overriding the list evaluator and Inbox Placement
     * Tester base URLs as well.
     * <p>
     * Those two are separate hosts rather than paths built from the bulk base,
     * so an override of {@code apiBaseUrl} alone leaves them pointing at the
     * live API.
     *
     * @param apiKey                the API key
     * @param apiBaseUrl            the main API base URL, or null to keep the default
     * @param listEvaluatorBaseUrl  the list evaluator base URL, or null to keep the default
     * @param inboxPlacementBaseUrl the Inbox Placement Tester base URL, or null to keep the default
     */
    public void initialize(
            String apiKey,
            @Nullable String apiBaseUrl,
            @Nullable String listEvaluatorBaseUrl,
            @Nullable String inboxPlacementBaseUrl
    ) {
        initialize(apiKey, apiBaseUrl);
        if (listEvaluatorBaseUrl != null) {
            requireHttpsUrl(listEvaluatorBaseUrl);
            this.listEvaluatorBaseUrl = listEvaluatorBaseUrl;
        }
        if (inboxPlacementBaseUrl != null) {
            requireHttpsUrl(inboxPlacementBaseUrl);
            this.inboxPlacementBaseUrl = inboxPlacementBaseUrl;
        }
    }

    static void requireHttpsUrl(String url) {
        if (url == null || !url.regionMatches(true, 0, "https://", 0, 8)) {
            throw new IllegalArgumentException("apiBaseUrl must be an https:// URL");
        }
    }

    /**
     * Validates the given [email].
     *
     * @param email           the email address you want to validate
     * @param ipAddress       the IP Address the email signed up from (Can be blank)
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void validate(
            @NotNull String email,
            @Nullable String ipAddress,
            @NotNull OnSuccessCallback<ZBValidateResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        sendRequest(
                apiBaseUrl + "/validate",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        put("email", email);
                        put("ip_address", ipAddress != null ? ipAddress : "");
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * Validates a batch of emails.
     *
     * @param emails          the email addresses you want to validate
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void validateBatch(
            @NotNull List<ZBValidateBatchData> emails,
            @NotNull OnSuccessCallback<ZBValidateBatchResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        Map<String, Object> body = new HashMap<>();
        body.put("api_key", apiKey);
        body.put("email_batch", emails);

        sendRequest(
                apiBaseUrl + "/validatebatch",
                null,
                body,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * @deprecated The 'guessFormat' method has been split into two specific functions.
     * If you are finding a person's email, use {@link #findEmail(String, String, String, String, String, com.zerobounce.ZeroBounceSDK.OnSuccessCallback, com.zerobounce.ZeroBounceSDK.OnErrorCallback)}.
     * If you are only determining the domain's email pattern, use {@link #findDomain(String, String, com.zerobounce.ZeroBounceSDK.OnSuccessCallback, com.zerobounce.ZeroBounceSDK.OnErrorCallback)}.
     */
    @Deprecated
    public void guessFormat(
            @NotNull String domain,
            @Nullable String firstName,
            @Nullable String middleName,
            @Nullable String lastName,
            @NotNull OnSuccessCallback<ZBEmailFinderResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        sendRequest(
                apiBaseUrl + "/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        put("domain", domain);
                        if (firstName != null) {
                            put("first_name", firstName);
                        }
                        if (middleName != null) {
                            put("middle_name", middleName);
                        }
                        if (lastName != null) {
                            put("last_name", lastName);
                        }
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * Finds the email based on a given [firstName], [domain] or [companyName].
     * <p>
     * **Note**: At least one of [domain] or [companyName] must be provided.
     *
     * @param firstName       the first name of the person whose email format is being searched
     * @param domain          the email domain for which to find the email format
     * @param companyName     the company name for which to find the email format
     * @param middleName      the middle name of the person whose email format is being searched;
     *                        optional
     * @param lastName        the last name of the person whose email format is being searched; optional
     * @param successCallback the response callback
     * @param errorCallback   the error callback
     */
    public void findEmail(
            @NotNull String firstName,
            @Nullable String domain,
            @Nullable String companyName,
            @Nullable String middleName,
            @Nullable String lastName,
            @NotNull OnSuccessCallback<ZBFindEmailResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        // Validate that at least one of companyName or domain is provided.
        if ((companyName == null || companyName.isEmpty()) && (domain == null || domain.isEmpty())) {
            errorCallback.onError(ErrorResponse.parseError("Either companyName or domain must be provided."));
            return;
        }

        sendRequest(
                apiBaseUrl + "/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        put("first_name", firstName);
                        if (domain != null) {
                            put("domain", domain);
                        }
                        if (companyName != null) {
                            put("company_name", companyName);
                        }
                        if (middleName != null) {
                            put("middle_name", middleName);
                        }
                        if (lastName != null) {
                            put("last_name", lastName);
                        }
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * Find other domain formats based on a given [domain] or [companyName].
     * <p>
     * **Note**: At least one of [domain] or [companyName] must be provided.
     *
     * @param domain           the email domain for which to find the email format
     * @param companyName      the company name for which to find the email format
     * @param successCallback the response callback
     * @param errorCallback    the error callback
     */
    public void findDomain(
            @Nullable String domain,
            @Nullable String companyName,
            @NotNull OnSuccessCallback<ZBFindDomainResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        // Validate that at least one of companyName or domain is provided.
        if ((companyName == null || companyName.isEmpty()) && (domain == null || domain.isEmpty())) {
            errorCallback.onError(ErrorResponse.parseError("Either companyName or domain must be provided."));
            return;
        }

        sendRequest(
                apiBaseUrl + "/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        if (domain != null) {
                            put("domain", domain);
                        }
                        if (companyName != null) {
                            put("company_name", companyName);
                        }
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * Returns the API usage between the given dates.
     *
     * @param startDate       the start date of when you want to view API usage
     * @param endDate         the end date of when you want to view API usage
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void getApiUsage(
            @NotNull Date startDate,
            @NotNull Date endDate,
            @NotNull OnSuccessCallback<ZBGetApiUsageResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        sendRequest(
                apiBaseUrl + "/getapiusage",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        put("start_date", dateFormat.format(startDate));
                        put("end_date", dateFormat.format(endDate));
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * This API will tell you how many credits you have left on your account. It's simple, fast and easy to use.
     *
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void getCredits(
            @NotNull OnSuccessCallback<ZBCreditsResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        sendRequest(
                apiBaseUrl + "/getcredits",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * The sendFile API allows user to send a file for bulk email validation.
     *
     * @param file                    the file to send
     * @param emailAddressColumnIndex the column index of the email address in the file. Index starts from 1.
     * @param options                 the send file options
     * @param successCallback         the success callback
     * @param errorCallback           the error callback
     */
    public void sendFile(
            @NotNull File file,
            int emailAddressColumnIndex,
            @Nullable SendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) throws ZBException {
        if (invalidApiKey(errorCallback)) return;

        _sendFile(false, file, emailAddressColumnIndex, options, successCallback, errorCallback);
    }

    /**
     * The sendFile API allows user to send a file for bulk email validation from a stream
     * (e.g. in-memory CSV or an uploaded file stream). The stream is not closed by the SDK.
     *
     * @param inputStream             the stream containing the file data
     * @param fileName                the file name to use for the upload (e.g. "emails.csv")
     * @param emailAddressColumnIndex the column index of the email address in the file. Index starts from 1.
     * @param options                 the send file options
     * @param successCallback         the success callback
     * @param errorCallback           the error callback
     */
    public void sendFile(
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int emailAddressColumnIndex,
            @Nullable SendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) throws ZBException {
        if (invalidApiKey(errorCallback)) return;

        _sendFileStream(false, inputStream, fileName, emailAddressColumnIndex, options, successCallback, errorCallback);
    }

    /**
     * The scoringSendFile API allows user to send a file for bulk email validation.
     *
     * @param file                    the file to send
     * @param emailAddressColumnIndex the column index of the email address in the file. Index starts from 1.
     * @param options                 the send file options
     * @param successCallback         the success callback
     * @param errorCallback           the error callback
     */
    public void scoringSendFile(
            @NotNull File file,
            int emailAddressColumnIndex,
            @Nullable ScoringSendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) throws ZBException {
        if (invalidApiKey(errorCallback)) return;

        SendFileOptions newOptions = null;

        if (options != null) {
            newOptions = new SendFileOptions()
                    .setReturnUrl(options.returnUrl)
                    .setHasHeaderRow(options.hasHeaderRow)
                    .setRemoveDuplicate(options.removeDuplicate);
        }

        _sendFile(true,
                file,
                emailAddressColumnIndex,
                newOptions,
                successCallback,
                errorCallback
        );
    }

    /**
     * The scoringSendFile API allows user to send a file for bulk email validation from a stream.
     * The stream is not closed by the SDK.
     *
     * @param inputStream             the stream containing the file data
     * @param fileName                the file name to use for the upload (e.g. "emails.csv")
     * @param emailAddressColumnIndex the column index of the email address in the file. Index starts from 1.
     * @param options                 the scoring send file options
     * @param successCallback         the success callback
     * @param errorCallback           the error callback
     */
    public void scoringSendFile(
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int emailAddressColumnIndex,
            @Nullable ScoringSendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) throws ZBException {
        if (invalidApiKey(errorCallback)) return;

        SendFileOptions newOptions = null;
        if (options != null) {
            newOptions = new SendFileOptions()
                    .setReturnUrl(options.returnUrl)
                    .setHasHeaderRow(options.hasHeaderRow)
                    .setRemoveDuplicate(options.removeDuplicate);
        }

        _sendFileStream(true, inputStream, fileName, emailAddressColumnIndex, newOptions, successCallback, errorCallback);
    }

    /**
     * The sendFile API allows user to send a file for bulk email validation. This method implements the actual
     * request logic.
     *
     * @param scoring                 *true* if the AI scoring should be used, or *false* otherwise
     * @param file                    the file to send
     * @param emailAddressColumnIndex the column index of the email address in the file. Index starts from 1
     * @param options                 the send file options
     * @param successCallback         the success callback
     * @param errorCallback           the error callback
     */
    private void _sendFile(
            @NotNull Boolean scoring,
            @NotNull File file,
            int emailAddressColumnIndex,
            @Nullable SendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback) throws ZBException {

        String urlPath = (scoring ? bulkApiScoringBaseUrl : bulkApiBaseUrl) + "/sendfile";
        logDebug("ZeroBounceSDK::sendFile urlPath=" + urlPath);

        if (emailAddressColumnIndex < 1) {
            throw new ZBException("Index for emailAddressColumnIndex must start from 1.");
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(urlPath);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            builder.addPart("api_key", new StringBody(apiKey, ContentType.TEXT_PLAIN));
            builder.addPart(
                    "email_address_column",
                    new StringBody(String.valueOf(emailAddressColumnIndex), ContentType.TEXT_PLAIN)
            );

            if (options != null) {
                if (options.returnUrl != null) {
                    builder.addPart("return_url",
                            new StringBody(options.returnUrl, ContentType.TEXT_PLAIN)
                    );
                }
                if (options.firstNameColumn > 0) {
                    builder.addPart("first_name_column",
                            new StringBody(String.valueOf(options.firstNameColumn), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.lastNameColumn > 0) {
                    builder.addPart("last_name_column",
                            new StringBody(String.valueOf(options.lastNameColumn), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.genderColumn > 0) {
                    builder.addPart("gender_column",
                            new StringBody(String.valueOf(options.genderColumn), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.ipAddressColumn > 0) {
                    builder.addPart(
                            "ip_address_column",
                            new StringBody(String.valueOf(options.ipAddressColumn), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.hasHeaderRow != null) {
                    builder.addPart(
                            "has_header_row",
                            new StringBody(String.valueOf(options.hasHeaderRow), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.removeDuplicate != null) {
                    builder.addPart(
                            "remove_duplicate",
                            new StringBody(String.valueOf(options.removeDuplicate), ContentType.TEXT_PLAIN)
                    );
                }
                if (!scoring && options.allowPhase2 != null) {
                    builder.addPart(
                            "allow_phase_2",
                            new StringBody(Boolean.toString(options.allowPhase2), ContentType.TEXT_PLAIN)
                    );
                }
            }

            builder.addPart("file", new FileBody(file));

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            CloseableHttpResponse response = httpClient.execute(uploadFile);
            HttpEntity responseEntity = response.getEntity();
            int status = response.getStatusLine().getStatusCode();

            logDebug("ZeroBounceSDK::sendFile status: " + status);

            StringBuilder content = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(responseEntity.getContent()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
            }

            String rsp = content.toString();

            logDebug("ZeroBounceSDK::sendFile rsp=" + rsp);

            if (status > 299) {
                ErrorResponse errorResponse = ErrorResponse.parseError(rsp);
                errorCallback.onError(errorResponse);
            } else {
                Type type = new TypeToken<ZBSendFileResponse>() {
                }.getType();
                ZBSendFileResponse sendFileResponse = gson.fromJson(rsp, type);

                successCallback.onSuccess(sendFileResponse);
            }

        } catch (Exception e) {
            logError("ZeroBounceSDK::sendFile failed", e);
            ErrorResponse errorResponse = ErrorResponse.parseError(e.getMessage());
            errorCallback.onError(errorResponse);
        }
    }

    private void _sendFileStream(
            @NotNull Boolean scoring,
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int emailAddressColumnIndex,
            @Nullable SendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback) throws ZBException {

        String urlPath = (scoring ? bulkApiScoringBaseUrl : bulkApiBaseUrl) + "/sendfile";
        logDebug("ZeroBounceSDK::sendFile urlPath=" + urlPath);

        if (emailAddressColumnIndex < 1) {
            throw new ZBException("Index for emailAddressColumnIndex must start from 1.");
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(urlPath);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            builder.addPart("api_key", new StringBody(apiKey, ContentType.TEXT_PLAIN));
            builder.addPart(
                    "email_address_column",
                    new StringBody(String.valueOf(emailAddressColumnIndex), ContentType.TEXT_PLAIN)
            );

            if (options != null) {
                if (options.returnUrl != null) {
                    builder.addPart("return_url",
                            new StringBody(options.returnUrl, ContentType.TEXT_PLAIN)
                    );
                }
                if (options.firstNameColumn > 0) {
                    builder.addPart("first_name_column",
                            new StringBody(String.valueOf(options.firstNameColumn), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.lastNameColumn > 0) {
                    builder.addPart("last_name_column",
                            new StringBody(String.valueOf(options.lastNameColumn), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.genderColumn > 0) {
                    builder.addPart("gender_column",
                            new StringBody(String.valueOf(options.genderColumn), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.ipAddressColumn > 0) {
                    builder.addPart(
                            "ip_address_column",
                            new StringBody(String.valueOf(options.ipAddressColumn), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.hasHeaderRow != null) {
                    builder.addPart(
                            "has_header_row",
                            new StringBody(String.valueOf(options.hasHeaderRow), ContentType.TEXT_PLAIN)
                    );
                }
                if (options.removeDuplicate != null) {
                    builder.addPart(
                            "remove_duplicate",
                            new StringBody(String.valueOf(options.removeDuplicate), ContentType.TEXT_PLAIN)
                    );
                }
                if (!scoring && options.allowPhase2 != null) {
                    builder.addPart(
                            "allow_phase_2",
                            new StringBody(Boolean.toString(options.allowPhase2), ContentType.TEXT_PLAIN)
                    );
                }
            }

            builder.addPart("file", new InputStreamBody(inputStream, ContentType.create("text/csv"), fileName));

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            CloseableHttpResponse response = httpClient.execute(uploadFile);
            HttpEntity responseEntity = response.getEntity();
            int status = response.getStatusLine().getStatusCode();

            logDebug("ZeroBounceSDK::sendFile status: " + status);

            StringBuilder content = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(responseEntity.getContent()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
            }

            String rsp = content.toString();

            logDebug("ZeroBounceSDK::sendFile rsp=" + rsp);

            if (status > 299) {
                ErrorResponse errorResponse = ErrorResponse.parseError(rsp);
                errorCallback.onError(errorResponse);
            } else {
                Type type = new TypeToken<ZBSendFileResponse>() {
                }.getType();
                ZBSendFileResponse sendFileResponse = gson.fromJson(rsp, type);

                successCallback.onSuccess(sendFileResponse);
            }

        } catch (Exception e) {
            logError("ZeroBounceSDK::sendFile failed", e);
            ErrorResponse errorResponse = ErrorResponse.parseError(e.getMessage());
            errorCallback.onError(errorResponse);
        }
    }

    /**
     * Returns the status of a file submitted for email validation.
     *
     * @param fileId          the returned file ID when calling sendFile API
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void fileStatus(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBFileStatusResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        _fileStatus(false, fileId, successCallback, errorCallback);
    }

    /**
     * Returns the status of a file submitted for email validation using the AI Scoring request.
     *
     * @param fileId          the returned file ID when calling scoringSendFile API
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void scoringFileStatus(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBFileStatusResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        _fileStatus(true, fileId, successCallback, errorCallback);
    }

    /**
     * Returns the status of a file submitted for email validation. This method implements the actual request logic.
     *
     * @param scoring         *true* if the AI scoring should be used, or *false* otherwise
     * @param fileId          the returned file ID when calling either the sendFile or scoringSendFile APIs
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    private void _fileStatus(
            @NotNull Boolean scoring,
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBFileStatusResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        sendRequest(
                (scoring ? bulkApiScoringBaseUrl : bulkApiBaseUrl) + "/filestatus",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        put("file_id", fileId);
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * The getFile API allows users to get the validation results file for the file been submitted using sendFile API.
     *
     * @param fileId          the returned file ID when calling sendFile API
     * @param downloadPath    the path to which to download the file
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void getFile(
            @NotNull String fileId,
            @NotNull String downloadPath,
            @NotNull OnSuccessCallback<ZBGetFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        _getFile(false, fileId, downloadPath, null, successCallback, errorCallback);
    }

    /**
     * Validation bulk {@code getfile} with optional {@code download_type} and {@code activity_data}.
     */
    public void getFile(
            @NotNull String fileId,
            @NotNull String downloadPath,
            @Nullable ZBGetFileOptions options,
            @NotNull OnSuccessCallback<ZBGetFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        _getFile(false, fileId, downloadPath, options, successCallback, errorCallback);
    }

    /**
     * The scoringGetFile API allows users to get the validation results file for the file been submitted using
     * scoringSendFile API.
     *
     * @param fileId          the returned file ID when calling sendFile API
     * @param downloadPath    the path to which to download the file
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void scoringGetFile(
            @NotNull String fileId,
            @NotNull String downloadPath,
            @NotNull OnSuccessCallback<ZBGetFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        _getFile(true, fileId, downloadPath, null, successCallback, errorCallback);
    }

    /**
     * Scoring bulk {@code getfile} with optional {@code download_type} ({@code activityData} is ignored).
     */
    public void scoringGetFile(
            @NotNull String fileId,
            @NotNull String downloadPath,
            @Nullable ZBGetFileOptions options,
            @NotNull OnSuccessCallback<ZBGetFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        _getFile(true, fileId, downloadPath, options, successCallback, errorCallback);
    }

    private URI buildGetFileUri(boolean scoring, @NotNull String fileId, @Nullable ZBGetFileOptions options)
            throws URISyntaxException {
        String base = (scoring ? bulkApiScoringBaseUrl : bulkApiBaseUrl) + "/getfile";
        URIBuilder ub = new URIBuilder(base);
        ub.addParameter("api_key", apiKey);
        ub.addParameter("file_id", fileId);
        if (options != null) {
            if (options.getDownloadType() != null) {
                ub.addParameter("download_type", options.getDownloadType().getQueryValue());
            }
            if (!scoring && options.getActivityData() != null) {
                ub.addParameter("activity_data", options.getActivityData() ? "true" : "false");
            }
        }
        return ub.build();
    }

    private static boolean getFileJsonIndicatesFailure(@NotNull JsonObject o) {
        if (o.has("success") && !o.get("success").isJsonNull()) {
            try {
                if (!o.get("success").getAsBoolean()) {
                    return true;
                }
            } catch (Exception ignored) {
                // non-boolean success — treat as non-failure here; other fields may still signal error
            }
        }
        return nonEmptyStringField(o, "message")
                || nonEmptyStringField(o, "error")
                || nonEmptyStringField(o, "error_message");
    }

    private static boolean nonEmptyStringField(@NotNull JsonObject o, @NotNull String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return false;
        }
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return !e.getAsString().trim().isEmpty();
        }
        if (e.isJsonArray()) {
            JsonArray arr = e.getAsJsonArray();
            for (JsonElement item : arr) {
                if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    if (!item.getAsString().trim().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The getFile API allows users to get the validation results file for the file that has  been submitted.
     * This method implements the actual request logic.
     */
    private void _getFile(
            @NotNull Boolean scoring,
            @NotNull String fileId,
            @NotNull String downloadPath,
            @Nullable ZBGetFileOptions options,
            @NotNull OnSuccessCallback<ZBGetFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (fileId == null || fileId.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_id"));
            return;
        }
        try {
            File file = new File(downloadPath);
            if (file.isDirectory()) {
                errorCallback.onError(ErrorResponse.parseError("Invalid file path"));
                return;
            }
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            URI uri = buildGetFileUri(scoring, fileId, options);
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet downloadFile = new HttpGet(uri);
                try (CloseableHttpResponse response = client.execute(downloadFile)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    HttpEntity entity = response.getEntity();
                    Header ctHeader = response.getFirstHeader("Content-Type");
                    String contentTypeRaw = ctHeader != null ? ctHeader.getValue() : "";
                    String mime = contentTypeRaw.split(";")[0].trim().toLowerCase(Locale.ROOT);

                    byte[] bodyBytes = entity != null ? EntityUtils.toByteArray(entity) : new byte[0];

                    if (ContentType.APPLICATION_JSON.getMimeType().equalsIgnoreCase(mime)) {
                        String json = new String(bodyBytes, StandardCharsets.UTF_8);
                        try {
                            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                            if (getFileJsonIndicatesFailure(obj)) {
                                errorCallback.onError(ErrorResponse.parseError(json));
                                return;
                            }
                            if (obj.has("success")) {
                                errorCallback.onError(ErrorResponse.parseError(json));
                                return;
                            }
                        } catch (Exception parseEx) {
                            errorCallback.onError(ErrorResponse.parseError(json));
                            return;
                        }
                    }

                    if (statusCode > 299) {
                        errorCallback.onError(ErrorResponse.parseError(new String(bodyBytes, StandardCharsets.UTF_8)));
                        return;
                    }

                    try (FileOutputStream outStream = new FileOutputStream(file)) {
                        outStream.write(bodyBytes);
                    }
                    ZBGetFileResponse rsp = new ZBGetFileResponse();
                    rsp.setLocalFilePath(downloadPath);
                    successCallback.onSuccess(rsp);
                }
            }
        } catch (Exception e) {
            logError("ZeroBounceSDK::getFile failed", e);
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
        }
    }

    /**
     * Delete a file.
     *
     * @param fileId          the returned file ID when calling sendFile API
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void deleteFile(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBDeleteFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        _deleteFile(false, fileId, successCallback, errorCallback);
    }

    /**
     * Delete a file submitted using the scoring API.
     *
     * @param fileId          the returned file ID when calling sendFile API
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void scoringDeleteFile(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBDeleteFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        _deleteFile(true, fileId, successCallback, errorCallback);
    }

    /**
     * Delete a file. This method implements the actual request logic.
     *
     * @param scoring         *true* if the AI scoring should be used, or *false* otherwise
     * @param fileId          the returned file ID when calling sendFile API
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    private void _deleteFile(
            @NotNull Boolean scoring,
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBDeleteFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        sendRequest(
                (scoring ? bulkApiScoringBaseUrl : bulkApiBaseUrl) + "/deletefile",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        put("file_id", fileId);
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * The request returns data regarding opens, clicks, forwards and unsubscribes that have taken place in the past
     * 30, 90, 180 or 365 days.
     *
     * @param email           the email address
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void getActivityData(
            @NotNull String email,
            @NotNull OnSuccessCallback<ZBActivityDataResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        sendRequest(
                apiBaseUrl + "/activity",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        put("email", email);
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    // ---------------------------------------------------------------------
    // Validate options, real-time AI scoring, filters
    // ---------------------------------------------------------------------

    /**
     * Validates the given email with the documented optional parameters.
     * <p>
     * An option left null is not sent. With {@link ZBValidateOptions#setUsePost(boolean)}
     * the same fields go out as a form-encoded POST instead of a query string.
     *
     * @param email           the email address you want to validate
     * @param ipAddress       the IP Address the email signed up from (can be blank)
     * @param options         the optional parameters, or null for none
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void validate(
            @NotNull String email,
            @Nullable String ipAddress,
            @Nullable ZBValidateOptions options,
            @NotNull OnSuccessCallback<ZBValidateResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("api_key", apiKey);
        parameters.put("email", email);
        parameters.put("ip_address", ipAddress != null ? ipAddress : "");
        try {
            if (options != null) {
                options.fill(parameters);
            }
        } catch (IllegalArgumentException e) {
            // Rejected before any request is sent.
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
            return;
        }

        boolean usePost = options != null && options.isUsePost();
        sendRequestWithMethod(
                usePost ? "POST" : "GET",
                apiBaseUrl + "/validate",
                usePost ? null : parameters,
                usePost ? parameters : null,
                null,
                new TypeToken<ZBValidateResponse>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * Real-time AI scoring for one address (GET /v2/scoring).
     */
    public void scoreEmail(
            @NotNull String email,
            @NotNull OnSuccessCallback<ZBScoreEmailResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        scoreEmail(email, false, successCallback, errorCallback);
    }

    /**
     * Real-time AI scoring for one address.
     *
     * @param usePost true sends the form-encoded POST /v2/scoring instead of the GET
     */
    public void scoreEmail(
            @NotNull String email,
            boolean usePost,
            @NotNull OnSuccessCallback<ZBScoreEmailResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (email.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: email"));
            return;
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("api_key", apiKey);
        parameters.put("email", email);

        sendRequestWithMethod(
                usePost ? "POST" : "GET",
                apiBaseUrl + "/scoring",
                usePost ? null : parameters,
                usePost ? parameters : null,
                null,
                new TypeToken<ZBScoreEmailResponse>() {
                },
                successCallback,
                errorCallback
        );
    }

    /** Adds an account filter (POST /v2/filters/add). */
    public void addFilter(
            @NotNull String rule,
            @NotNull String target,
            @NotNull String value,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        filterRequest("/filters/add", rule, target, value, true, successCallback, errorCallback);
    }

    /** Deletes an account filter (POST /v2/filters/delete). */
    public void deleteFilter(
            @NotNull String rule,
            @NotNull String target,
            @NotNull String value,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        filterRequest("/filters/delete", rule, target, value, true, successCallback, errorCallback);
    }

    /**
     * Lists the account filters (/v2/filters/list).
     *
     * @param usePost true sends a POST instead of a GET; both are documented
     */
    public void listFilters(
            boolean usePost,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        filterRequest("/filters/list", null, null, null, usePost, successCallback, errorCallback);
    }

    private void filterRequest(
            @NotNull String path,
            @Nullable String rule,
            @Nullable String target,
            @Nullable String value,
            boolean usePost,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("api_key", apiKey);
        if (rule != null) parameters.put("rule", rule);
        if (target != null) parameters.put("target", target);
        if (value != null) parameters.put("value", value);

        sendRequestWithMethod(
                usePost ? "POST" : "GET",
                apiBaseUrl + path,
                usePost ? null : parameters,
                usePost ? parameters : null,
                null,
                new TypeToken<JsonObject>() {
                },
                successCallback,
                errorCallback
        );
    }

    // ---------------------------------------------------------------------
    // S3 and SFTP/FTP bulk
    // https://www.zerobounce.net/docs/api-integrations/amazon-s3-bulk-api
    // https://www.zerobounce.net/docs/api-integrations/sftp_bulk_api
    // ---------------------------------------------------------------------

    /**
     * Lists the files in your Amazon S3 bucket (GET /listS3files).
     *
     * @param bucket an optional bucket filter, or null
     * @param search an optional name filter, or null
     */
    public void listS3Files(
            @Nullable String bucket,
            @Nullable String search,
            @NotNull OnSuccessCallback<List<ZBS3ListFileEntry>> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("api_key", apiKey);
        if (bucket != null && !bucket.trim().isEmpty()) parameters.put("bucket", bucket);
        if (search != null && !search.trim().isEmpty()) parameters.put("search", search);

        sendRequestWithMethod(
                "GET",
                bulkApiRootUrl + "/listS3files",
                parameters,
                null,
                null,
                new TypeToken<JsonElement>() {
                },
                payload -> successCallback.onSuccess(parseS3FileList(payload)),
                errorCallback
        );
    }

    /**
     * listS3files is documented as {"success": true, "files": [...]} but also
     * answers with a bare array, so both shapes are accepted.
     */
    private List<ZBS3ListFileEntry> parseS3FileList(@Nullable JsonElement payload) {
        Type listType = new TypeToken<List<ZBS3ListFileEntry>>() {
        }.getType();
        if (payload == null || payload.isJsonNull()) {
            return new ArrayList<>();
        }
        if (payload.isJsonArray()) {
            return gson.fromJson(payload, listType);
        }
        if (payload.isJsonObject() && payload.getAsJsonObject().has("files")) {
            return gson.fromJson(payload.getAsJsonObject().get("files"), listType);
        }
        return new ArrayList<>();
    }

    /** Validates a file in your Amazon S3 bucket (POST /validateS3file). */
    public void validateS3File(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/validateS3file", options, true, successCallback, errorCallback);
    }

    /** Scores a file in your Amazon S3 bucket (POST /scoreS3file). */
    public void scoreS3File(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/scoreS3file", options, true, successCallback, errorCallback);
    }

    /** Runs the email finder on a file in your Amazon S3 bucket (POST /emailFinderS3file). */
    public void emailFinderS3File(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/emailFinderS3file", options, true, successCallback, errorCallback);
    }

    /** Runs domain search on a file in your Amazon S3 bucket (POST /domainSearchS3file). */
    public void domainSearchS3File(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/domainSearchS3file", options, true, successCallback, errorCallback);
    }

    /**
     * Exports a validated or scored file back to Amazon S3 (POST /exportS3file).
     *
     * @param exportBucket the destination bucket, or null to use the one from
     *                     your ZeroBounce settings
     */
    public void exportS3File(
            @NotNull String fileId,
            @Nullable String exportBucket,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        exportBulkFile("/exportS3file", "export_bucket", fileId, exportBucket, successCallback, errorCallback);
    }

    // SFTP / FTP bulk. The server, path and protocol are never sent by the SDK;
    // they come from your ZeroBounce settings.

    /**
     * Validates a file from your SFTP/FTP server (POST /validateSFTPfile).
     * <p>
     * This is the one job whose file_name the docs list as optional: leave
     * {@link ZBBulkFileJobOptions#setFileName(String)} unset to use the input
     * bucket from your settings.
     */
    public void validateSFTPFile(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/validateSFTPfile", options, false, successCallback, errorCallback);
    }

    /** Scores a file from your SFTP/FTP server (POST /scoreSFTPfile). */
    public void scoreSFTPFile(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/scoreSFTPfile", options, true, successCallback, errorCallback);
    }

    /** Runs the email finder on a file from your SFTP/FTP server (POST /emailFinderSFTPfile). */
    public void emailFinderSFTPFile(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/emailFinderSFTPfile", options, true, successCallback, errorCallback);
    }

    /** Runs domain search on a file from your SFTP/FTP server (POST /domainSearchSFTPfile). */
    public void domainSearchSFTPFile(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/domainSearchSFTPfile", options, true, successCallback, errorCallback);
    }

    /**
     * Exports a validated or scored file back to your SFTP/FTP server
     * (POST /exportSFTPfile).
     * <p>
     * Note the field name: this endpoint takes export_path, not the
     * export_bucket that {@link #exportS3File} sends.
     *
     * @param exportPath the destination path, or null to use the one from your
     *                   ZeroBounce settings
     */
    public void exportSFTPFile(
            @NotNull String fileId,
            @Nullable String exportPath,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        exportBulkFile("/exportSFTPfile", "export_path", fileId, exportPath, successCallback, errorCallback);
    }

    /** POSTs one of the six documented S3 / SFTP file jobs on the bulkapi root. */
    private void bulkRootFileJob(
            @NotNull String path,
            @NotNull ZBBulkFileJobOptions options,
            boolean fileNameRequired,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        Map<String, String> fields = new LinkedHashMap<>();
        try {
            options.fill(fields, fileNameRequired);
        } catch (IllegalArgumentException e) {
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
            return;
        }
        fields.put("api_key", apiKey);

        sendRequestWithMethod(
                "POST",
                bulkApiRootUrl + path,
                null,
                fields,
                null,
                new TypeToken<ZBSendFileResponse>() {
                },
                successCallback,
                errorCallback
        );
    }

    private void exportBulkFile(
            @NotNull String path,
            @NotNull String destinationField,
            @NotNull String fileId,
            @Nullable String destination,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (fileId.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_id"));
            return;
        }
        if (destination != null && destination.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: " + destinationField));
            return;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("file_id", fileId);
        if (destination != null) {
            fields.put(destinationField, destination);
        }
        fields.put("api_key", apiKey);

        sendRequestWithMethod(
                "POST",
                bulkApiRootUrl + path,
                null,
                fields,
                null,
                new TypeToken<ZBSendFileResponse>() {
                },
                successCallback,
                errorCallback
        );
    }

    // ---------------------------------------------------------------------
    // List evaluator. Its own base URL, not a path built from the bulk one.
    // ---------------------------------------------------------------------

    /**
     * The evaluation of an uploaded file (GET /v2/listevaluator/{file_id}/).
     * <p>
     * The file id is URL-encoded as a single path segment.
     */
    public void getListEvaluatorResult(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (fileId.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_id"));
            return;
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("api_key", apiKey);

        sendRequestWithMethod(
                "GET",
                listEvaluatorBaseUrl + "/" + encodePathSegment(fileId.trim()) + "/",
                parameters,
                null,
                null,
                new TypeToken<JsonObject>() {
                },
                successCallback,
                errorCallback
        );
    }

    /** URL-encodes a value as a single path segment, so a slash in it stays encoded. */
    private static String encodePathSegment(String value) {
        return urlEncode(value).replace("+", "%20");
    }

    // ---------------------------------------------------------------------
    // Inbox Placement Tester (bulkapi.zerobounce.net/api/ipt).
    // The API key goes on the query string, bodies are camelCase JSON, and
    // errors come back under a "detail" key (Django REST framework).
    // Spends the account's inbox-test credits.
    // ---------------------------------------------------------------------

    /** Seed providers available for new tests (GET /api/ipt/providers). */
    public void getInboxPlacementProviders(
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        inboxPlacementRequest("GET", "/providers", null, null, successCallback, errorCallback);
    }

    /** Supported test types and the default one (GET /api/ipt/test-types). */
    public void getInboxPlacementTestTypes(
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        inboxPlacementRequest("GET", "/test-types", null, null, successCallback, errorCallback);
    }

    /**
     * Starts a test and returns its seed list (POST /api/ipt/test).
     *
     * @param name    the test name, required
     * @param options the optional fields, or null. Only the fields that are set are sent.
     */
    public void createInboxPlacementTest(
            @NotNull String name,
            @Nullable CreateInboxPlacementTestOptions options,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (name.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: name"));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        if (options != null) {
            options.fill(body);
        }
        inboxPlacementRequest("POST", "/test", null, body, successCallback, errorCallback);
    }

    /** The results of one test (GET /api/ipt/test/{id}). */
    public void getInboxPlacementTest(
            @NotNull String testId,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        String segment = inboxPlacementTestPath(testId, errorCallback);
        if (segment == null) return;
        inboxPlacementRequest("GET", "/test/" + segment, null, null, successCallback, errorCallback);
    }

    /** The account's tests (GET /api/ipt/test). */
    public void listInboxPlacementTests(
            @Nullable ListInboxPlacementTestsOptions options,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        Map<String, String> query = new LinkedHashMap<>();
        if (options != null) {
            options.fill(query);
        }
        inboxPlacementRequest("GET", "/test", query, null, successCallback, errorCallback);
    }

    /** Deletes one test (DELETE /api/ipt/test/{id}). */
    public void deleteInboxPlacementTest(
            @NotNull String testId,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        String segment = inboxPlacementTestPath(testId, errorCallback);
        if (segment == null) return;
        inboxPlacementRequest("DELETE", "/test/" + segment, null, null, successCallback, errorCallback);
    }

    /** The configured webhook (GET /api/ipt/webhook). */
    public void getInboxPlacementWebhook(
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        inboxPlacementRequest("GET", "/webhook", null, null, successCallback, errorCallback);
    }

    /** Sets the webhook that test results post to (PUT /api/ipt/webhook). */
    public void setInboxPlacementWebhook(
            @NotNull String webhookUrl,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (webhookUrl.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: webhook_url"));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("webhookUrl", webhookUrl);
        inboxPlacementRequest("PUT", "/webhook", null, body, successCallback, errorCallback);
    }

    private @Nullable String inboxPlacementTestPath(
            @NotNull String testId,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (testId.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: test_id"));
            return null;
        }
        return encodePathSegment(testId.trim());
    }

    private void inboxPlacementRequest(
            @NotNull String method,
            @NotNull String path,
            @Nullable Map<String, String> query,
            @Nullable Map<String, Object> body,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        Map<String, String> parameters = query != null ? new LinkedHashMap<>(query) : new LinkedHashMap<>();
        parameters.put("api_key", apiKey);

        sendRequestWithMethod(
                method,
                inboxPlacementBaseUrl + path,
                parameters,
                null,
                body,
                new TypeToken<JsonObject>() {
                },
                successCallback,
                errorCallback
        );
    }

    /** The optional fields of a new Inbox Placement test. Only what is set is sent. */
    public static class CreateInboxPlacementTestOptions {

        @Nullable
        private Integer testType = null;
        @Nullable
        private String senderEmail = null;
        @Nullable
        private List<Integer> providerIds = null;
        @Nullable
        private List<Integer> seedIds = null;
        @Nullable
        private List<Map<String, Object>> providers = null;
        @Nullable
        private String webhookUrl = null;

        public CreateInboxPlacementTestOptions setTestType(@Nullable Integer testType) {
            this.testType = testType;
            return this;
        }

        public CreateInboxPlacementTestOptions setSenderEmail(@Nullable String senderEmail) {
            this.senderEmail = senderEmail;
            return this;
        }

        public CreateInboxPlacementTestOptions setProviderIds(@Nullable List<Integer> providerIds) {
            this.providerIds = providerIds;
            return this;
        }

        public CreateInboxPlacementTestOptions setSeedIds(@Nullable List<Integer> seedIds) {
            this.seedIds = seedIds;
            return this;
        }

        public CreateInboxPlacementTestOptions setProviders(@Nullable List<Map<String, Object>> providers) {
            this.providers = providers;
            return this;
        }

        public CreateInboxPlacementTestOptions setWebhookUrl(@Nullable String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        void fill(@NotNull Map<String, Object> body) {
            if (testType != null) body.put("testType", testType);
            if (senderEmail != null) body.put("senderEmail", senderEmail);
            if (providerIds != null) body.put("providerIds", providerIds);
            if (seedIds != null) body.put("seedIds", seedIds);
            if (providers != null) body.put("providers", providers);
            if (webhookUrl != null) body.put("webhookUrl", webhookUrl);
        }
    }

    /** The documented filters of the Inbox Placement test list. */
    public static class ListInboxPlacementTestsOptions {

        @Nullable
        private String status = null;
        @Nullable
        private Integer page = null;
        @Nullable
        private Integer pageSize = null;

        public ListInboxPlacementTestsOptions setStatus(@Nullable String status) {
            this.status = status;
            return this;
        }

        public ListInboxPlacementTestsOptions setPage(@Nullable Integer page) {
            this.page = page;
            return this;
        }

        public ListInboxPlacementTestsOptions setPageSize(@Nullable Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        void fill(@NotNull Map<String, String> query) {
            if (status != null) query.put("status", status);
            if (page != null) query.put("page", String.valueOf(page));
            if (pageSize != null) query.put("pageSize", String.valueOf(pageSize));
        }
    }

    /**
     * Checks if the [apiKey] is invalid or not and if it is, then it throws an error through the provided
     * [errorCallback].
     *
     * @param errorCallback the error callback
     * @return **true** if the [apiKey] is null or **false** otherwise
     */
    private boolean invalidApiKey(@NotNull OnErrorCallback errorCallback) {
        if (apiKey == null) {
            ErrorResponse errorResponse = ErrorResponse.parseError(
                    "ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(apiKey) first"
            );
            errorCallback.onError(errorResponse);
            return true;
        }
        return false;
    }

    /**
     * An interface that provides a single method for a success callback.
     */
    public interface OnSuccessCallback<T> {
        void onSuccess(T response);
    }

    /**
     * An interface that provides a single method for an error callback.
     */
    public interface OnErrorCallback {
        void onError(ErrorResponse errorResponse);
    }

    /**
     * The helper method that handles any type of request.
     *
     * @param urlPath         the url
     * @param queryParameters the query parameters if the request is a GET request
     * @param body            the body parameters if the request is a POST request
     * @param typeToken       a [TypeToken] class that is used to convert the JSON response
     *                        to a specific response object
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    private <T> void sendRequest(
            @NotNull String urlPath,
            @Nullable Map<String, String> queryParameters,
            @Nullable Map<String, Object> body,
            @NotNull TypeToken<T> typeToken,
            @NotNull OnSuccessCallback<T> successCallback,
            @NotNull OnErrorCallback errorCallback) {
        try {
            logDebug("ZeroBounceSDK::sendRequest preparing request: " + urlPath);
            URIBuilder ub = new URIBuilder(urlPath);

            if (queryParameters != null) {
                for (Map.Entry<String, String> param : queryParameters.entrySet()) {
                    ub.addParameter(param.getKey(), param.getValue());
                }
            }

            URL url = createUrlFrom(ub.toString());
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestProperty("Accept", "application/json");
//            con.setRequestProperty("Content-Type", "application/json");
            // Timeouts must be set before connect(); the POST branch connects explicitly below.
            con.setConnectTimeout(timeoutInMillis);
            con.setReadTimeout(timeoutInMillis);

            String httpMethod;
            if (body == null) {
                httpMethod = "GET";
                con.setRequestMethod("GET");
            } else {
                httpMethod = "POST";
                con.setRequestMethod("POST");
                con.setDoOutput(true);

                // Attach the body to the request
                byte[] out = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
                int length = out.length;

                con.setFixedLengthStreamingMode(length);
                con.connect();
                try (OutputStream os = con.getOutputStream()) {
                    os.write(out);
                }
            }

            logDebug("ZeroBounceSDK::sendRequest executing " + httpMethod + " " + urlPath);

            int status = con.getResponseCode();
            logDebug("ZeroBounceSDK::sendRequest status: " + status);
            Reader streamReader;
            if (status > 299) {
                streamReader = new InputStreamReader(con.getErrorStream());
            } else {
                streamReader = new InputStreamReader(con.getInputStream());
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader in = new BufferedReader(streamReader)) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
            }

            con.disconnect();
            String rsp = content.toString();

            logPayload("ZeroBounceSDK::sendRequest rsp=" + rsp);

            if (status > 299) {
                ErrorResponse errorResponse = ErrorResponse.parseError(rsp);
                errorCallback.onError(errorResponse);
            } else {
                T response = gson.fromJson(rsp, typeToken.getType());
                successCallback.onSuccess(response);
            }
        } catch (Exception e) {
            logError("ZeroBounceSDK::sendRequest failed", e);
            ErrorResponse errorResponse = ErrorResponse.parseError(e.getMessage());
            errorCallback.onError(errorResponse);
        }
    }

    /**
     * The helper that handles a request with an explicit method and an optional
     * body, for the endpoints {@link #sendRequest} cannot express: form-encoded
     * POSTs, and the Inbox Placement Tester's PUT and DELETE.
     *
     * @param method          GET, POST, PUT or DELETE
     * @param urlPath         the url
     * @param queryParameters the query parameters, appended to the url
     * @param formFields      form-encoded body fields, or null
     * @param jsonBody        a JSON body, or null. Mutually exclusive with formFields.
     */
    private <T> void sendRequestWithMethod(
            @NotNull String method,
            @NotNull String urlPath,
            @Nullable Map<String, String> queryParameters,
            @Nullable Map<String, String> formFields,
            @Nullable Object jsonBody,
            @Nullable TypeToken<T> typeToken,
            @Nullable OnSuccessCallback<T> successCallback,
            @NotNull OnErrorCallback errorCallback) {
        try {
            URIBuilder ub = new URIBuilder(urlPath);
            if (queryParameters != null) {
                for (Map.Entry<String, String> param : queryParameters.entrySet()) {
                    ub.addParameter(param.getKey(), param.getValue());
                }
            }

            URL url = createUrlFrom(ub.toString());
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestProperty("Accept", "application/json");
            con.setConnectTimeout(timeoutInMillis);
            con.setReadTimeout(timeoutInMillis);
            con.setRequestMethod(method);

            byte[] out = null;
            if (formFields != null) {
                StringBuilder form = new StringBuilder();
                for (Map.Entry<String, String> field : formFields.entrySet()) {
                    if (form.length() > 0) form.append('&');
                    form.append(urlEncode(field.getKey())).append('=').append(urlEncode(field.getValue()));
                }
                out = form.toString().getBytes(StandardCharsets.UTF_8);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            } else if (jsonBody != null) {
                out = gson.toJson(jsonBody).getBytes(StandardCharsets.UTF_8);
                con.setRequestProperty("Content-Type", "application/json");
            }

            if (out != null) {
                con.setDoOutput(true);
                con.setFixedLengthStreamingMode(out.length);
                con.connect();
                try (OutputStream os = con.getOutputStream()) {
                    os.write(out);
                }
            }

            logDebug("ZeroBounceSDK::sendRequestWithMethod executing " + method + " " + urlPath);

            int status = con.getResponseCode();
            Reader streamReader = status > 299
                    ? new InputStreamReader(con.getErrorStream())
                    : new InputStreamReader(con.getInputStream());

            StringBuilder content = new StringBuilder();
            try (BufferedReader in = new BufferedReader(streamReader)) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
            }
            con.disconnect();
            String rsp = content.toString();
            logPayload("ZeroBounceSDK::sendRequestWithMethod rsp=" + rsp);

            if (status > 299) {
                errorCallback.onError(ErrorResponse.parseError(rsp));
                return;
            }
            if (successCallback == null) {
                return;
            }
            if (typeToken == null || rsp.trim().isEmpty()) {
                successCallback.onSuccess(null);
                return;
            }
            successCallback.onSuccess(gson.fromJson(rsp, typeToken.getType()));
        } catch (Exception e) {
            logError("ZeroBounceSDK::sendRequestWithMethod failed", e);
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Creates and returns a URL object based on the given [url] parameter.
     *
     * @param url a url
     * @return a URL object
     */
    URL createUrlFrom(String url) throws MalformedURLException {
        return URI.create(url).toURL();
    }

    private static void logDebug(String message) {
        logger.debug(message);
    }

    private static void logPayload(String message) {
        if (logPayloads) {
            logger.debug(message);
        }
    }

    private static void logError(String message, Exception exception) {
        logger.error(message, exception);
    }

    /**
     * A class that can be used to configure the [sendFile] request.
     */
    public static class SendFileOptions {

        @Nullable
        String returnUrl;

        int firstNameColumn;

        int lastNameColumn;

        int genderColumn;

        int ipAddressColumn;

        @Nullable
        Boolean hasHeaderRow;

        @Nullable
        Boolean removeDuplicate;

        /**
         * When non-null, sent as {@code allow_phase_2} for validation bulk sendfile only (not scoring).
         */
        @Nullable
        Boolean allowPhase2;

        public SendFileOptions setReturnUrl(@Nullable String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public SendFileOptions setFirstNameColumn(int firstNameColumn) {
            this.firstNameColumn = firstNameColumn;
            return this;
        }

        public SendFileOptions setLastNameColumn(int lastNameColumn) {
            this.lastNameColumn = lastNameColumn;
            return this;
        }

        public SendFileOptions setGenderColumn(int genderColumn) {
            this.genderColumn = genderColumn;
            return this;
        }

        public SendFileOptions setIpAddressColumn(int ipAddressColumn) {
            this.ipAddressColumn = ipAddressColumn;
            return this;
        }

        public SendFileOptions setHasHeaderRow(@Nullable Boolean hasHeaderRow) {
            this.hasHeaderRow = hasHeaderRow;
            return this;
        }

        public SendFileOptions setRemoveDuplicate(@Nullable Boolean removeDuplicate) {
            this.removeDuplicate = removeDuplicate;
            return this;
        }

        public SendFileOptions setAllowPhase2(@Nullable Boolean allowPhase2) {
            this.allowPhase2 = allowPhase2;
            return this;
        }
    }

    /**
     * A class that can be used to configure the [scoringSendFile] request.
     */
    public static class ScoringSendFileOptions {

        @NotNull
        String returnUrl = "";

        @NotNull
        Boolean hasHeaderRow = false;

        @Nullable
        Boolean removeDuplicate = null;

        public ScoringSendFileOptions setReturnUrl(@NotNull String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public ScoringSendFileOptions setHasHeaderRow(@NotNull Boolean hasHeaderRow) {
            this.hasHeaderRow = hasHeaderRow;
            return this;
        }

        public ScoringSendFileOptions setRemoveDuplicate(@Nullable Boolean removeDuplicate) {
            this.removeDuplicate = removeDuplicate;
            return this;
        }
    }
}
