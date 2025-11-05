package com.zerobounce;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
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

    final String apiBaseUrl = "https://api.zerobounce.net/v2";
    private final String bulkApiBaseUrl = "https://bulkapi.zerobounce.net/v2";
    private final String bulkApiScoringBaseUrl = "https://bulkapi.zerobounce.net/v2/scoring";
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
     * [timeoutInMillis] is set to 0 (no timeout by default)
     *
     * @param apiKey the API key
     */
    public void initialize(String apiKey) {
        this.apiKey = apiKey;
        timeoutInMillis = 0;
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
                new HashMap<String, String>() {
                    {
                        put("api_key", apiKey);
                        put("email", email);
                        put("ip_address", ipAddress != null ? ipAddress : "");
                    }
                },
                null,
                new TypeToken<ZBValidateResponse>() {
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
                new TypeToken<ZBValidateBatchResponse>() {
                },
                successCallback,
                errorCallback
        );
    }

    /**
     * Tries to guess the format of the given [email].
     *
     * @param domain          the email domain for which to find the email format
     * @param firstName       the first name of the person whose email format is being searched; optional
     * @param middleName      the middle name of the person whose email format is being searched; optional
     * @param lastName        the last name of the person whose email format is being searched; optional
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
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
                new HashMap<String, String>() {
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
                new TypeToken<ZBEmailFinderResponse>() {
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
                new HashMap<String, String>() {
                    {
                        put("api_key", apiKey);
                        put("start_date", dateFormat.format(startDate));
                        put("end_date", dateFormat.format(endDate));
                    }
                },
                null,
                new TypeToken<ZBGetApiUsageResponse>() {
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
                new HashMap<String, String>() {
                    {
                        put("api_key", apiKey);
                    }
                },
                null,
                new TypeToken<ZBCreditsResponse>() {
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
                new HashMap<String, String>() {
                    {
                        put("api_key", apiKey);
                        put("file_id", fileId);
                    }
                },
                null,
                new TypeToken<ZBFileStatusResponse>() {
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
        _getFile(false, fileId, downloadPath, successCallback, errorCallback);
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
        _getFile(true, fileId, downloadPath, successCallback, errorCallback);
    }

    /**
     * The getFile API allows users to get the validation results file for the file that has  been submitted.
     * This method implements the actual request logic.
     *
     * @param scoring         *true* if the AI scoring should be used, or *false* otherwise
     * @param fileId          the returned file ID when calling sendFile API
     * @param downloadPath    the path to which to download the file
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    private void _getFile(
            @NotNull Boolean scoring,
            @NotNull String fileId,
            @NotNull String downloadPath,
            @NotNull OnSuccessCallback<ZBGetFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        try {
            File file = new File(downloadPath);
            if (file.isDirectory()) {
                ErrorResponse errorResponse = ErrorResponse.parseError("Invalid file path");
                errorCallback.onError(errorResponse);
                return;
            }
            file.getParentFile().mkdirs();
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet downloadFile = new HttpGet(
                        (scoring ? bulkApiScoringBaseUrl : bulkApiBaseUrl) + "/getfile?api_key=" + apiKey + "&file_id=" + fileId
                );
                CloseableHttpResponse response = client.execute(downloadFile);
                HttpEntity entity = response.getEntity();
                String contentType = response.getFirstHeader("Content-Type").getValue();

                // Check that the Content-Type is application/json or not. If it's not, then the response is the actual
                // file. Otherwise, the server has returned a JSON error.
                if (!ContentType.APPLICATION_JSON.getMimeType().equals(contentType)) {
                    if (entity != null) {
                        FileOutputStream outStream = new FileOutputStream(file);
                        entity.writeTo(outStream);
                    }
                    ZBGetFileResponse rsp = new ZBGetFileResponse();
                    rsp.setLocalFilePath(downloadPath);
                    successCallback.onSuccess(rsp);
                } else {
                    ErrorResponse errorResponse = ErrorResponse.parseError(EntityUtils.toString(entity));
                    errorCallback.onError(errorResponse);
                }
            }
        } catch (Exception e) {
            logError("ZeroBounceSDK::getFile failed", e);
            ErrorResponse errorResponse = ErrorResponse.parseError(e.getMessage());
            errorCallback.onError(errorResponse);
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
                new HashMap<String, String>() {
                    {
                        put("api_key", apiKey);
                        put("file_id", fileId);
                    }
                },
                null,
                new TypeToken<ZBDeleteFileResponse>() {
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
                new HashMap<String, String>() {
                    {
                        put("api_key", apiKey);
                        put("email", email);
                    }
                },
                null,
                new TypeToken<ZBActivityDataResponse>() {
                },
                successCallback,
                errorCallback
        );
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

            con.setConnectTimeout(timeoutInMillis);

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
