package com.zerobounce;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
    private final String emailFinderBulkBaseUrl = bulkApiRootUrl + "/email-finder";
    private final String domainSearchBulkBaseUrl = bulkApiRootUrl + "/domain-search";
    /**
     * The list evaluator and the Inbox Placement Tester have their own base URLs,
     * not paths built from the bulk one. Both are overridable through
     * {@link #initialize(String, String, String, String)}.
     */
    String listEvaluatorBaseUrl = "https://bulkapi.zerobounce.net/v2/listevaluator";
    String inboxPlacementBaseUrl = "https://bulkapi.zerobounce.net/api/ipt";

    private final Priority3Api priority3Api = new Priority3Api(this);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private String apiKey;
    /** Why the key given to initialize was refused, or null when it was accepted. */
    private String apiKeyProblem;
    private int timeoutInMillis;
    private final Gson gson;

    /**
     * When non-null, every HTTP call is answered by this transport instead of the network.
     * Tests set it. Production leaves it null.
     */
    SdkHttp.Transport transport;

    /**
     * A client that holds one API key. Prefer this over {@link #getInstance()} when more than one
     * key is in use: the singleton shares a single mutable key across the process.
     */
    public ZeroBounceSDK() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.registerTypeAdapter(ZBSendFileResponse.class, new ZBSendFileResponse.Deserializer());
        gsonBuilder.registerTypeAdapter(Date.class, new GsonDateDeserializer());
        StrictBooleanAdapter booleans = new StrictBooleanAdapter();
        gsonBuilder.registerTypeAdapter(Boolean.class, booleans);
        gsonBuilder.registerTypeAdapter(boolean.class, booleans);
        gson = gsonBuilder.create();
    }

    /**
     * Initializes the SDK.
     * <p>
     * [timeoutInMillis] is set to 120000 (120 seconds) by default
     *
     * @param apiKey the API key. A key with surrounding whitespace or control
     *               characters is refused: every later call then fails with that
     *               message instead of sending it.
     */
    public void initialize(String apiKey) {
        setApiKey(apiKey);
        timeoutInMillis = DEFAULT_TIMEOUT_MILLIS;
    }

    /**
     * Initializes the SDK.
     *
     * @param apiKey          the API key
     * @param timeoutInMillis the timeout in milliseconds to use for all requests (0 for no timeout)
     */
    public void initialize(String apiKey, int timeoutInMillis) {
        setApiKey(apiKey);
        this.timeoutInMillis = timeoutInMillis;
    }

    /**
     * Initializes the SDK.
     *
     * @param apiKey          the API key
     * @param apiBaseUrl      the API base URL, such as {@link ZBConstants#API_EU_URL}; a
     *                        trailing slash is removed
     */
    public void initialize(String apiKey, @Nullable String apiBaseUrl) {
        initialize(apiKey, apiBaseUrl, null, null);
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
        // All or nothing: every URL is checked before anything is stored, so a
        // call that throws leaves the key, the timeout and every base URL as
        // they were, and a caller that catches the error cannot go on with a
        // new key next to an old (or half-applied) set of URLs.
        if (apiBaseUrl != null) {
            requireHttpsUrl(apiBaseUrl);
        }
        if (listEvaluatorBaseUrl != null) {
            requireHttpsUrl(listEvaluatorBaseUrl);
        }
        if (inboxPlacementBaseUrl != null) {
            requireHttpsUrl(inboxPlacementBaseUrl);
        }
        setApiKey(apiKey);
        timeoutInMillis = DEFAULT_TIMEOUT_MILLIS;
        if (apiBaseUrl != null) {
            this.apiBaseUrl = withoutTrailingSlashes(apiBaseUrl);
        }
        if (listEvaluatorBaseUrl != null) {
            this.listEvaluatorBaseUrl = withoutTrailingSlashes(listEvaluatorBaseUrl);
        }
        if (inboxPlacementBaseUrl != null) {
            this.inboxPlacementBaseUrl = withoutTrailingSlashes(inboxPlacementBaseUrl);
        }
    }

    /**
     * Keeps the key, unless it has surrounding whitespace or control characters
     * (a key read from a file with its newline, say). Such a key is not stored,
     * so it is never sent or quoted, unredacted, in a transport error, and every
     * call reports why through its error callback, as {@link ZeroBounceWarmup}
     * does. An empty key is still reported as "not initialized".
     */
    private void setApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isBlank()
                && (!apiKey.equals(apiKey.trim()) || hasControlCharacters(apiKey))) {
            this.apiKey = null;
            apiKeyProblem = "api_key has surrounding whitespace or control characters";
            return;
        }
        this.apiKey = apiKey;
        apiKeyProblem = null;
    }

    private static boolean hasControlCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every endpoint path starts with a slash, so a base URL is kept without a
     * trailing one. The {@link ZBConstants} values end in a slash.
     */
    static String withoutTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    static void requireHttpsUrl(String url) {
        if (url == null || !url.regionMatches(true, 0, "https://", 0, 8)) {
            throw new IllegalArgumentException("apiBaseUrl must be an https:// URL");
        }
    }

    private String redactSecrets(@Nullable String message) {
        return redact(message, apiKey);
    }

    /**
     * Replaces each secret in {@code text} with ***, in every form a request
     * carries it and a server can quote it back: as is (a JSON or multipart
     * body), URL-encoded (a query string or a form) and JSON-escaped.
     */
    static @Nullable String redact(@Nullable String text, String... secrets) {
        if (text == null) {
            return null;
        }
        String value = text;
        for (String secret : secrets) {
            if (secret == null || secret.isEmpty()) {
                continue;
            }
            for (String form : secretForms(secret)) {
                value = value.replace(form, "***");
            }
        }
        return value;
    }

    /**
     * The spellings a server can quote a secret back in: as is and JSON-escaped
     * (with or without Gson's HTML-safe escapes such as {@code \u003c}, and with
     * {@code /} written as a backslash and a slash, as PHP's json_encode does),
     * and each of those URL-encoded with a space as {@code +} or {@code %20}
     * (a query string, a form, or a server that echoes a URL-encoded JSON
     * body). The longest come first, so a shorter form never leaves part of a
     * longer one behind.
     */
    private static List<String> secretForms(String secret) {
        List<String> plain = new java.util.ArrayList<>();
        plain.add(secret);
        for (Gson json : new Gson[]{new Gson(), new com.google.gson.GsonBuilder().disableHtmlEscaping().create()}) {
            String quoted = json.toJson(secret);
            String inner = quoted.substring(1, quoted.length() - 1);
            plain.add(inner);
            plain.add(inner.replace("/", "\\/"));
        }
        java.util.Set<String> forms = new java.util.LinkedHashSet<>();
        for (String form : plain) {
            String encoded = java.net.URLEncoder.encode(form, StandardCharsets.UTF_8);
            forms.add(form);
            forms.add(encoded);
            forms.add(encoded.replace("+", "%20"));
        }
        List<String> sorted = new java.util.ArrayList<>(forms);
        sorted.sort((a, b) -> b.length() - a.length());
        return sorted;
    }

    /**
     * The error callback with the key redacted from every message it is given.
     * An error body can quote the request back, and with it the key: a 400
     * "Bad request: https://...?api_key=..." or {"error": "Invalid API key ..."}.
     * Each request helper wraps its callback in this, so every error a request
     * reports goes through it, whichever path built the message.
     */
    @NotNull OnErrorCallback redacting(@NotNull OnErrorCallback errorCallback) {
        return error -> {
            if (error != null) {
                // The list is declared as strings, but a JSON array in an error
                // body can put numbers or objects in it. Those are kept as they
                // are unless their text holds the key.
                @SuppressWarnings({"unchecked", "rawtypes"})
                List<Object> messages = (List) error.getErrors();
                messages.replaceAll(message -> {
                    if (message == null) {
                        return null;
                    }
                    String text = message.toString();
                    String redacted = redactSecrets(text);
                    return redacted.equals(text) ? message : redacted;
                });
            }
            errorCallback.onError(error);
        };
    }

    org.apache.http.client.config.RequestConfig apacheRequestConfig() {
        return org.apache.http.client.config.RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectTimeout(timeoutInMillis)
                .setConnectionRequestTimeout(timeoutInMillis)
                .setSocketTimeout(timeoutInMillis)
                .build();
    }

    String redactedFailure(@NotNull Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Request failed.";
        }
        String redacted = redactSecrets(message);
        if (redacted == null || redacted.isBlank()) {
            return "Request failed.";
        }
        if (HttpMessages.looksLikeHtml(redacted, null)) {
            return HttpMessages.jsonCallFailure(0, redacted, null);
        }
        if (redacted.length() > 1000) {
            return "Request failed.";
        }
        String stripped = HttpMessages.stripPrefix(redacted);
        return stripped.isEmpty() ? "Request failed." : stripped;
    }

    /**
     * A 2xx whose content type is text/html is a proxy's or Cloudflare's page,
     * not an API response, whatever its body looks like: it is reported as an
     * HTML error page with its status. An error status goes through the
     * usual failure handling instead.
     *
     * @return true when the error callback was called
     */
    static boolean reportHtmlContentType(
            int status, @Nullable String contentType, @Nullable String body, @NotNull OnErrorCallback errorCallback) {
        if (status > 299 || contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            return false;
        }
        errorCallback.onError(ErrorResponse.parseError(HttpMessages.htmlMessage(body == null ? "" : body), status));
        return true;
    }

    boolean reportHttpFailure(int status, @Nullable String body, @NotNull OnErrorCallback errorCallback) {
        String text = body == null ? "" : body;
        // LIVE: {"success": false} on an HTTP 200 is a failure, not a
        // result. The API answers an unknown bulk file id that way.
        if (status > 299 || HttpMessages.looksLikeHtml(text, null) || text.trim().isEmpty()
                || apiSuccessIsFalse(text)) {
            reportFailure(status, text, errorCallback);
            return true;
        }
        return false;
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
        validate(email, ipAddress, null, successCallback, errorCallback);
    }

    /**
     * Validates the given email with optional v2 query parameters.
     * <p>
     * A thin wrapper over
     * {@link #validate(String, String, ZBValidateOptions, OnSuccessCallback, OnErrorCallback)},
     * which is the single implementation: it sends every documented option and
     * rejects an out-of-range timeout before the request.
     * {@link ZBValidateRequestOptions} carries a subset of
     * {@link ZBValidateOptions}, so prefer the latter for {@code domain_info},
     * {@code greylist_processing} and {@code callback_url}.
     */
    public void validateWithOptions(
            @NotNull String email,
            @Nullable ZBValidateRequestOptions options,
            @NotNull OnSuccessCallback<ZBValidateResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (options == null) {
            validate(email, null, null, successCallback, errorCallback);
            return;
        }
        ZBValidateOptions mapped = new ZBValidateOptions()
                .setTimeout(options.getTimeout())
                .setActivityData(options.getActivityData())
                .setVerifyPlus(options.getVerifyPlus())
                .setUsePost(options.isUsePost());
        validate(email, options.getIpAddress(), mapped, successCallback, errorCallback);
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
        validateBatch(emails, null, successCallback, errorCallback);
    }

    /**
     * Validates a batch of emails, with the documented optional parameters.
     * <p>
     * An option left null is not sent. The batch {@code timeout} is 10 to 120
     * seconds, and goes out as a JSON string: see
     * {@link ZBValidateOptions#fillBatch(Map)}.
     *
     * @param emails          the email addresses you want to validate
     * @param options         the optional parameters, or null for none
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void validateBatch(
            @NotNull List<ZBValidateBatchData> emails,
            @Nullable ZBValidateOptions options,
            @NotNull OnSuccessCallback<ZBValidateBatchResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", apiKey);
        body.put("email_batch", emails);
        try {
            if (options != null) {
                options.fillBatch(body);
            }
        } catch (IllegalArgumentException e) {
            // Rejected before any request is sent.
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
            return;
        }

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
     * Validates a batch of emails with the batch-only option object.
     * <p>
     * Kept so callers written against {@link ZBValidateBatchOptions} keep
     * compiling; it forwards to
     * {@link #validateBatch(List, ZBValidateOptions, OnSuccessCallback, OnErrorCallback)},
     * so both entry points send the same request and share the same
     * pre-send validation.
     *
     * @param emails          the email addresses you want to validate
     * @param options         the optional parameters, or null for none
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void validateBatchWithOptions(
            @NotNull List<ZBValidateBatchData> emails,
            @Nullable ZBValidateBatchOptions options,
            @NotNull OnSuccessCallback<ZBValidateBatchResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        ZBValidateOptions mapped = null;
        if (options != null) {
            mapped = new ZBValidateOptions()
                    .setTimeout(options.getTimeout())
                    .setActivityData(options.getActivityData())
                    .setVerifyPlus(options.getVerifyPlus());
        }
        validateBatch(emails, mapped, successCallback, errorCallback);
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
                errorCallback,
                true
        );
    }

    /**
     * Finds the email based on a given [firstName], [domain] or [companyName].
     * <p>
     * **Note**: At least one of [domain] or [companyName] must be provided.
     *
     * @param firstName       the first name of the person whose email format is being searched;
     *                        optional, and left out of the request when it is blank
     * @param domain          the email domain for which to find the email format
     * @param companyName     the company name for which to find the email format
     * @param middleName      the middle name of the person whose email format is being searched;
     *                        optional
     * @param lastName        the last name of the person whose email format is being searched; optional
     * @param successCallback the response callback
     * @param errorCallback   the error callback
     */
    public void findEmail(
            @Nullable String firstName,
            @Nullable String domain,
            @Nullable String companyName,
            @Nullable String middleName,
            @Nullable String lastName,
            @NotNull OnSuccessCallback<ZBFindEmailResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        // Validate that at least one of companyName or domain is provided.
        if (isBlank(companyName) && isBlank(domain)) {
            errorCallback.onError(ErrorResponse.parseError("Either companyName or domain must be provided."));
            return;
        }

        sendRequest(
                apiBaseUrl + "/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        // first_name is optional on guessformat: an empty one
                        // is noise on the wire, so it is left out when blank.
                        if (!isBlank(firstName)) {
                            put("first_name", firstName);
                        }
                        if (!isBlank(domain)) {
                            put("domain", domain);
                        }
                        if (!isBlank(companyName)) {
                            put("company_name", companyName);
                        }
                        if (!isBlank(middleName)) {
                            put("middle_name", middleName);
                        }
                        if (!isBlank(lastName)) {
                            put("last_name", lastName);
                        }
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback,
                true
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
        if (isBlank(companyName) && isBlank(domain)) {
            errorCallback.onError(ErrorResponse.parseError("Either companyName or domain must be provided."));
            return;
        }

        sendRequest(
                apiBaseUrl + "/guessformat",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        if (!isBlank(domain)) {
                            put("domain", domain);
                        }
                        if (!isBlank(companyName)) {
                            put("company_name", companyName);
                        }
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback,
                true
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
        errorCallback = redacting(errorCallback);

        String urlPath = (scoring ? bulkApiScoringBaseUrl : bulkApiBaseUrl) + "/sendfile";
        logDebug("ZeroBounceSDK::sendFile urlPath=" + urlPath);

        if (emailAddressColumnIndex < 1) {
            throw new ZBException("Index for emailAddressColumnIndex must start from 1.");
        }

        if (transport != null) {
            Map<String, String> fields = sendFileMultipartFields(scoring, emailAddressColumnIndex, options, file.getName());
            deliverSendFileTransport(urlPath, fields, successCallback, errorCallback);
            return;
        }

        int status;
        String rsp;
        String responseType = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(urlPath);
            uploadFile.setConfig(apacheRequestConfig());
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // The same fields the offline transport sees, from one builder.
            addTextParts(builder, sendFileMultipartFields(scoring, emailAddressColumnIndex, options, file.getName()));
            builder.addPart("file", new FileBody(file));

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            CloseableHttpResponse response = httpClient.execute(uploadFile);
            HttpEntity responseEntity = response.getEntity();
            status = response.getStatusLine().getStatusCode();
            Header typeHeader = response.getFirstHeader("Content-Type");
            responseType = typeHeader != null ? typeHeader.getValue() : "";

            logDebug("ZeroBounceSDK::sendFile status: " + status);

            rsp = SdkHttp.readText(responseEntity.getContent());

            logPayload("ZeroBounceSDK::sendFile rsp=" + rsp);
        } catch (Exception e) {
            logError("ZeroBounceSDK::sendFile failed", e);
            ErrorResponse errorResponse = ErrorResponse.parseError(redactedFailure(e));
            errorCallback.onError(errorResponse);
            return;
        }
        // Covers status > 299, an HTML or empty body, and a {"success": false}
        // answered on an HTTP 200; the callbacks are called outside the try.
        deliverSendFileResponse(status, responseType, rsp, successCallback, errorCallback);
    }

    private void _sendFileStream(
            @NotNull Boolean scoring,
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int emailAddressColumnIndex,
            @Nullable SendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback) throws ZBException {
        errorCallback = redacting(errorCallback);

        String urlPath = (scoring ? bulkApiScoringBaseUrl : bulkApiBaseUrl) + "/sendfile";
        logDebug("ZeroBounceSDK::sendFile urlPath=" + urlPath);

        if (emailAddressColumnIndex < 1) {
            throw new ZBException("Index for emailAddressColumnIndex must start from 1.");
        }

        if (transport != null) {
            Map<String, String> fields = sendFileMultipartFields(scoring, emailAddressColumnIndex, options, fileName);
            deliverSendFileTransport(urlPath, fields, successCallback, errorCallback);
            return;
        }

        int status;
        String rsp;
        String responseType = null;
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(urlPath);
            uploadFile.setConfig(apacheRequestConfig());
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // The same fields the offline transport sees, from one builder.
            addTextParts(builder, sendFileMultipartFields(scoring, emailAddressColumnIndex, options, fileName));
            builder.addPart("file", new InputStreamBody(inputStream, ContentType.create("text/csv"), fileName));

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            CloseableHttpResponse response = httpClient.execute(uploadFile);
            HttpEntity responseEntity = response.getEntity();
            status = response.getStatusLine().getStatusCode();
            Header typeHeader = response.getFirstHeader("Content-Type");
            responseType = typeHeader != null ? typeHeader.getValue() : "";

            logDebug("ZeroBounceSDK::sendFile status: " + status);

            rsp = SdkHttp.readText(responseEntity.getContent());

            logPayload("ZeroBounceSDK::sendFile rsp=" + rsp);
        } catch (Exception e) {
            logError("ZeroBounceSDK::sendFile failed", e);
            ErrorResponse errorResponse = ErrorResponse.parseError(redactedFailure(e));
            errorCallback.onError(errorResponse);
            return;
        }
        // Covers status > 299, an HTML or empty body, and a {"success": false}
        // answered on an HTTP 200; the callbacks are called outside the try.
        deliverSendFileResponse(status, responseType, rsp, successCallback, errorCallback);
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
        if (isBlank(fileId)) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_id"));
            return;
        }

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
        errorCallback = redacting(errorCallback);
        if (invalidApiKey(errorCallback)) return;
        if (fileId == null || fileId.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_id"));
            return;
        }
        File file = new File(downloadPath);
        if (file.isDirectory()) {
            errorCallback.onError(ErrorResponse.parseError("Invalid file path"));
            return;
        }
        int statusCode;
        String contentTypeRaw;
        byte[] bodyBytes;
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            URI uri = buildGetFileUri(scoring, fileId, options);
            if (transport != null) {
                SdkHttp.Result result = transport.exchange(new SdkHttp("GET", uri.toString(), null, null, null));
                statusCode = result.status;
                contentTypeRaw = result.contentType;
                bodyBytes = result.body;
            } else {
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                    HttpGet downloadFile = new HttpGet(uri);
                    downloadFile.setConfig(apacheRequestConfig());
                    try (CloseableHttpResponse response = client.execute(downloadFile)) {
                        statusCode = response.getStatusLine().getStatusCode();
                        HttpEntity entity = response.getEntity();
                        Header ctHeader = response.getFirstHeader("Content-Type");
                        contentTypeRaw = ctHeader != null ? ctHeader.getValue() : "";
                        bodyBytes = entity != null ? EntityUtils.toByteArray(entity) : new byte[0];
                    }
                }
            }
        } catch (Exception e) {
            logError("ZeroBounceSDK::getFile failed", e);
            errorCallback.onError(ErrorResponse.parseError(redactedFailure(e)));
            return;
        }
        // Outside the try, so an exception from either callback reaches the caller.
        deliverGetFile(file, downloadPath, statusCode, contentTypeRaw, bodyBytes, successCallback, errorCallback);
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
        if (blankFileId(fileId, errorCallback)) return;

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
        if (isBlank(email)) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: email"));
            return;
        }

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
        if (isBlank(email)) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: email"));
            return;
        }

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
            @NotNull OnSuccessCallback<JsonElement> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        // Read as a JsonElement for the same reason as listFilters: the filter
        // endpoints do not always answer with a JSON object.
        filterRequest("/filters/add", rule, target, value, true,
                new TypeToken<JsonElement>() {
                }, successCallback, errorCallback);
    }

    /** Deletes an account filter (POST /v2/filters/delete). */
    public void deleteFilter(
            @NotNull String rule,
            @NotNull String target,
            @NotNull String value,
            @NotNull OnSuccessCallback<JsonElement> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        // Read as a JsonElement for the same reason as listFilters: the filter
        // endpoints do not always answer with a JSON object.
        filterRequest("/filters/delete", rule, target, value, true,
                new TypeToken<JsonElement>() {
                }, successCallback, errorCallback);
    }

    /**
     * Lists the account filters (/v2/filters/list).
     *
     * @param usePost true sends a POST instead of a GET; both are documented
     */
    public void listFilters(
            boolean usePost,
            @NotNull OnSuccessCallback<JsonElement> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        // LIVE: /filters/list answers with a JSON *array*, not an object, so
        // this takes a JsonElement. Read as an object it threw.
        filterRequest("/filters/list", null, null, null, usePost,
                new TypeToken<JsonElement>() {
                }, successCallback, errorCallback);
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
        filterRequest(path, rule, target, value, usePost,
                new TypeToken<JsonObject>() {
                }, successCallback, errorCallback);
    }

    /** A supplied-but-blank filter argument is rejected before anything is sent. */
    private boolean blankArgument(@NotNull OnErrorCallback errorCallback, String name, @Nullable String value) {
        if (value != null && value.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: " + name));
            return true;
        }
        return false;
    }

    private <T> void filterRequest(
            @NotNull String path,
            @Nullable String rule,
            @Nullable String target,
            @Nullable String value,
            boolean usePost,
            @NotNull TypeToken<T> typeToken,
            @NotNull OnSuccessCallback<T> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (blankArgument(errorCallback, "rule", rule)
                || blankArgument(errorCallback, "target", target)
                || blankArgument(errorCallback, "value", value)) {
            return;
        }

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
                typeToken,
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
                payload -> {
                    // A payload the SDK cannot read is its own error; the
                    // caller's callback runs outside the try.
                    List<ZBS3ListFileEntry> files;
                    try {
                        files = parseS3FileList(payload);
                    } catch (RuntimeException e) {
                        errorCallback.onError(ErrorResponse.parseError(redactedFailure(e)));
                        return;
                    }
                    successCallback.onSuccess(files);
                },
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
        bulkRootFileJob("/validateS3file", options, successCallback, errorCallback);
    }

    /** Scores a file in your Amazon S3 bucket (POST /scoreS3file). */
    public void scoreS3File(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/scoreS3file", options, successCallback, errorCallback);
    }

    /** Runs the email finder on a file in your Amazon S3 bucket (POST /emailFinderS3file). */
    public void emailFinderS3File(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/emailFinderS3file", options, successCallback, errorCallback);
    }

    /** Runs domain search on a file in your Amazon S3 bucket (POST /domainSearchS3file). */
    public void domainSearchS3File(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/domainSearchS3file", options, successCallback, errorCallback);
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
     * LIVE: the docs list file_name as optional on this job, but the API
     * requires it, so {@link ZBBulkFileJobOptions#setFileName(String)} and
     * {@link ZBBulkFileJobOptions#setReturnUrl(String)} are required here as on
     * every other S3 and SFTP job; a missing one reaches the error callback
     * before the request.
     */
    public void validateSFTPFile(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/validateSFTPfile", options, successCallback, errorCallback);
    }

    /** Scores a file from your SFTP/FTP server (POST /scoreSFTPfile). */
    public void scoreSFTPFile(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/scoreSFTPfile", options, successCallback, errorCallback);
    }

    /** Runs the email finder on a file from your SFTP/FTP server (POST /emailFinderSFTPfile). */
    public void emailFinderSFTPFile(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/emailFinderSFTPfile", options, successCallback, errorCallback);
    }

    /** Runs domain search on a file from your SFTP/FTP server (POST /domainSearchSFTPfile). */
    public void domainSearchSFTPFile(
            @NotNull ZBBulkFileJobOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob("/domainSearchSFTPfile", options, successCallback, errorCallback);
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
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob(path, options, Map.of(), successCallback, errorCallback);
    }

    /**
     * The same job with the extra fields only some of them accept, such as the
     * column indexes and {@code allow_phase_2} of {@code validateS3File}. The
     * shared fields still go through {@link ZBBulkFileJobOptions#fill}, so the
     * required-field gate is the same on every path.
     */
    private void bulkRootFileJob(
            @NotNull String path,
            @NotNull ZBBulkFileJobOptions options,
            @NotNull Map<String, String> extraFields,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;

        Map<String, String> fields = new LinkedHashMap<>();
        try {
            options.fill(fields);
        } catch (IllegalArgumentException e) {
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
            return;
        }
        fields.putAll(extraFields);
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

    /**
     * Submits a finished evaluation for validation (POST /v2/send-evaluated-file/).
     * <p>
     * LIVE: this takes the list evaluator's {@code file_id} as a form field,
     * not the file upload the docs describe - an upload is rejected with
     * "file_id: This field is required". It returns a new validation job's
     * {@code file_id}, which answers "File cannot be found" for a short while
     * after it is accepted.
     *
     * @param fileId          the file_id returned by the list evaluator
     * @param successCallback the success callback
     * @param errorCallback   the error callback
     */
    public void sendEvaluatedFile(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (fileId.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_id"));
            return;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("api_key", apiKey);
        fields.put("file_id", fileId.trim());

        sendRequestWithMethod(
                "POST",
                bulkApiBaseUrl + "/send-evaluated-file/",
                null,
                fields,
                null,
                new TypeToken<ZBSendFileResponse>() {
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
    // The API key goes in an "Authorization: Bearer" header, never on the
    // query string; bodies are camelCase JSON, and errors come back under a
    // "detail" key (Django REST framework).
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
     * @param name    the test name, required, at most 64 characters
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
        if (name.length() > 64) {
            errorCallback.onError(ErrorResponse.parseError("name must be at most 64 characters"));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        try {
            if (options != null) {
                options.fill(body);
            }
        } catch (IllegalArgumentException e) {
            // Rejected before any request is sent.
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
            return;
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

    /**
     * The account's tests (GET /api/ipt/tests).
     * <p>
     * LIVE: the listing is {@code /tests} (plural). {@code /test} is the
     * single-test path and answers {@code Method "GET" not allowed}.
     */
    public void listInboxPlacementTests(
            @Nullable ListInboxPlacementTestsOptions options,
            @NotNull OnSuccessCallback<JsonObject> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        Map<String, String> query = new LinkedHashMap<>();
        if (options != null) {
            options.fill(query);
        }
        inboxPlacementRequest("GET", "/tests", query, null, successCallback, errorCallback);
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

        // LIVE: the Inbox Placement Tester authenticates with a Bearer header.
        // Sent as an api_key parameter the request is rejected as unauthorized,
        // and the key would end up in a query string that gets logged.
        Map<String, String> parameters = query != null ? new LinkedHashMap<>(query) : new LinkedHashMap<>();

        sendRequestWithMethod(
                method,
                inboxPlacementBaseUrl + path,
                parameters,
                null,
                body,
                new TypeToken<JsonObject>() {
                },
                Map.of("Authorization", "Bearer " + apiKey),
                // LIVE: DELETE /test/{id} answers 204 with no body. An empty
                // 2xx from any other Inbox Placement call is an error.
                "DELETE".equals(method),
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

        /**
         * Adds the fields that are set to the request body.
         *
         * @throws IllegalArgumentException if the options cannot produce a valid request
         */
        void fill(@NotNull Map<String, Object> body) {
            if (testType != null) {
                if (testType < 1 || testType > 3) {
                    throw new IllegalArgumentException("testType must be 1, 2 or 3");
                }
                // A type 1 test mails from your own address, so the API needs it.
                if (testType == 1 && (senderEmail == null || senderEmail.trim().isEmpty())) {
                    throw new IllegalArgumentException("senderEmail is required when testType is 1");
                }
                body.put("testType", testType);
            }
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
        @Nullable
        private String createdAfter = null;
        @Nullable
        private String createdBefore = null;

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

        /** Keeps only the tests created at or after this date. */
        public ListInboxPlacementTestsOptions setCreatedAfter(@Nullable String createdAfter) {
            this.createdAfter = createdAfter;
            return this;
        }

        /** Keeps only the tests created at or before this date. */
        public ListInboxPlacementTestsOptions setCreatedBefore(@Nullable String createdBefore) {
            this.createdBefore = createdBefore;
            return this;
        }

        void fill(@NotNull Map<String, String> query) {
            if (status != null) query.put("status", status);
            if (page != null) query.put("page", String.valueOf(page));
            // LIVE: the page size is page_size. Sent as pageSize it is ignored
            // and the whole list comes back, so this listing mixes conventions
            // with the camelCase created* filters beside it.
            if (pageSize != null) query.put("page_size", String.valueOf(pageSize));
            if (createdAfter != null) query.put("createdAfter", createdAfter);
            if (createdBefore != null) query.put("createdBefore", createdBefore);
        }
    }

    /**
     * Real-time AI scoring for a single email (POST /scoring, form-encoded).
     */
    public void scoreEmailPost(
            @NotNull String email,
            @NotNull OnSuccessCallback<ZBScoreEmailResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (isBlank(email)) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: email"));
            return;
        }

        sendFormPostRequest(
                apiBaseUrl + "/scoring",
                new HashMap<>() {
                    {
                        put("api_key", apiKey);
                        put("email", email);
                    }
                },
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    // Priority 3 — bulk email finder, domain search, list evaluator, filters, S3

    public void sendEmailFinderFile(
            @NotNull File file,
            int domainColumn,
            @Nullable EmailFinderSendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        priority3Api.sendEmailFinderFile(file, domainColumn, options, successCallback, errorCallback);
    }

    public void sendEmailFinderFileStream(
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int domainColumn,
            @Nullable EmailFinderSendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        priority3Api.sendEmailFinderFileStream(inputStream, fileName, domainColumn, options, successCallback, errorCallback);
    }

    public void emailFinderFileStatus(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBFileStatusResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (blankFileId(fileId, errorCallback)) return;
        priority3Api.bulkRootFileStatus(emailFinderBulkBaseUrl, fileId, successCallback, errorCallback);
    }

    public void emailFinderGetFile(
            @NotNull String fileId,
            @NotNull String downloadPath,
            @NotNull OnSuccessCallback<ZBGetFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (blankFileId(fileId, errorCallback)) return;
        priority3Api.bulkRootGetFile(emailFinderBulkBaseUrl, fileId, downloadPath, successCallback, errorCallback);
    }

    public void emailFinderDeleteFile(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBDeleteFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (blankFileId(fileId, errorCallback)) return;
        priority3Api.bulkRootDeleteFile(emailFinderBulkBaseUrl, fileId, successCallback, errorCallback);
    }

    public void sendDomainSearchFile(
            @NotNull File file,
            int domainColumn,
            @Nullable DomainSearchSendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        priority3Api.sendDomainSearchFile(file, domainColumn, options, successCallback, errorCallback);
    }

    public void sendDomainSearchFileStream(
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int domainColumn,
            @Nullable DomainSearchSendFileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        priority3Api.sendDomainSearchFileStream(inputStream, fileName, domainColumn, options, successCallback, errorCallback);
    }

    public void domainSearchFileStatus(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBFileStatusResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (blankFileId(fileId, errorCallback)) return;
        priority3Api.bulkRootFileStatus(domainSearchBulkBaseUrl, fileId, successCallback, errorCallback);
    }

    public void domainSearchGetFile(
            @NotNull String fileId,
            @NotNull String downloadPath,
            @NotNull OnSuccessCallback<ZBGetFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (blankFileId(fileId, errorCallback)) return;
        priority3Api.bulkRootGetFile(domainSearchBulkBaseUrl, fileId, downloadPath, successCallback, errorCallback);
    }

    public void domainSearchDeleteFile(
            @NotNull String fileId,
            @NotNull OnSuccessCallback<ZBDeleteFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        if (blankFileId(fileId, errorCallback)) return;
        priority3Api.bulkRootDeleteFile(domainSearchBulkBaseUrl, fileId, successCallback, errorCallback);
    }

    public void sendListEvaluatorFile(
            @NotNull File file,
            int emailAddressColumn,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        priority3Api.sendListEvaluatorFile(file, emailAddressColumn, successCallback, errorCallback);
    }

    public void sendListEvaluatorFileStream(
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int emailAddressColumn,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        if (invalidApiKey(errorCallback)) return;
        priority3Api.sendListEvaluatorFileStream(inputStream, fileName, emailAddressColumn, successCallback, errorCallback);
    }

    /**
     * Validates a file in your Amazon S3 bucket (POST /validateS3file) with the
     * column indexes and {@code allow_phase_2} that only this job takes.
     * <p>
     * Shares its implementation with
     * {@link #validateS3File(ZBBulkFileJobOptions, OnSuccessCallback, OnErrorCallback)},
     * so {@code file_name} and {@code return_url} are required here too.
     */
    public void validateS3File(
            @NotNull ValidateS3FileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob(
                "/validateS3file",
                options.toJobOptions(),
                options.extraFields(),
                successCallback,
                errorCallback
        );
    }

    /**
     * Scores a file in your Amazon S3 bucket (POST /scoreS3file) with the
     * column index this job takes.
     * <p>
     * Shares its implementation with
     * {@link #scoreS3File(ZBBulkFileJobOptions, OnSuccessCallback, OnErrorCallback)},
     * so {@code file_name} and {@code return_url} are required here too.
     */
    public void scoreS3File(
            @NotNull ScoreS3FileOptions options,
            @NotNull OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        bulkRootFileJob(
                "/scoreS3file",
                options.toJobOptions(),
                options.extraFields(),
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
        if (apiKeyProblem != null) {
            errorCallback.onError(ErrorResponse.parseError(apiKeyProblem));
            return true;
        }
        if (apiKey == null || apiKey.isBlank()) {
            ErrorResponse errorResponse = ErrorResponse.parseError(
                    "ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(apiKey) first"
            );
            errorCallback.onError(errorResponse);
            return true;
        }
        return false;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /** A missing or blank file id is rejected before anything is sent. */
    private static boolean blankFileId(@Nullable String fileId, @NotNull OnErrorCallback errorCallback) {
        if (isBlank(fileId)) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_id"));
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
        sendRequest(urlPath, queryParameters, body, typeToken, successCallback, errorCallback, false);
    }

    /**
     * @param messageOnlyIsFailure for the email finder and domain search, whose
     *                             result is never a bare message: see
     *                             {@link HttpMessages#messageOnlyFailure(String)}
     */
    private <T> void sendRequest(
            @NotNull String urlPath,
            @Nullable Map<String, String> queryParameters,
            @Nullable Map<String, Object> body,
            @NotNull TypeToken<T> typeToken,
            @NotNull OnSuccessCallback<T> successCallback,
            @NotNull OnErrorCallback errorCallback,
            boolean messageOnlyIsFailure) {
        errorCallback = redacting(errorCallback);
        // Only the exchange itself is inside the try: an exception thrown by
        // the caller's own callback must reach the caller, not come back
        // through the error callback as if the API had failed.
        int status;
        String rsp;
        String responseType = null;
        try {
            logDebug("ZeroBounceSDK::sendRequest preparing request: " + urlPath);
            URIBuilder ub = new URIBuilder(urlPath);

            if (queryParameters != null) {
                for (Map.Entry<String, String> param : queryParameters.entrySet()) {
                    ub.addParameter(param.getKey(), param.getValue());
                }
            }

            String httpMethod = body == null ? "GET" : "POST";
            byte[] payload = body == null ? null : gson.toJson(body).getBytes(StandardCharsets.UTF_8);
            String urlString = ub.toString();
            if (transport != null) {
                SdkHttp.Result result = transport.exchange(new SdkHttp(
                        httpMethod,
                        urlString,
                        payload == null ? null : "application/json; charset=UTF-8",
                        payload,
                        null
                ));
                status = result.status;
                responseType = result.contentType;
                rsp = new String(result.body, StandardCharsets.UTF_8);
            } else {
                URL url = createUrlFrom(urlString);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setInstanceFollowRedirects(false);
                con.setRequestProperty("Accept", "application/json");
                // Timeouts must be set before connect(); the POST branch connects explicitly below.
                con.setConnectTimeout(timeoutInMillis);
                con.setReadTimeout(timeoutInMillis);

                if (body == null) {
                    con.setRequestMethod("GET");
                } else {
                    con.setRequestMethod("POST");
                    con.setDoOutput(true);
                    con.setFixedLengthStreamingMode(payload.length);
                    con.connect();
                    try (OutputStream os = con.getOutputStream()) {
                        os.write(payload);
                    }
                }

                logDebug("ZeroBounceSDK::sendRequest executing " + httpMethod + " " + urlPath);
                status = con.getResponseCode();
                logDebug("ZeroBounceSDK::sendRequest status: " + status);
                responseType = con.getContentType();
                rsp = readResponse(con, status);
            }
        } catch (Exception e) {
            logError("ZeroBounceSDK::sendRequest failed", e);
            ErrorResponse errorResponse = ErrorResponse.parseError(redactedFailure(e));
            errorCallback.onError(errorResponse);
            return;
        }

        logPayload("ZeroBounceSDK::sendRequest rsp=" + rsp);
        if (messageOnlyIsFailure && status <= 299) {
            String messageOnly = HttpMessages.messageOnlyFailure(rsp);
            if (messageOnly != null) {
                errorCallback.onError(ErrorResponse.parseError(messageOnly, status));
                return;
            }
        }
        deliverJson(status, responseType, rsp, typeToken, successCallback, errorCallback);
    }

    private <T> void sendFormPostRequest(
            @NotNull String urlPath,
            @NotNull Map<String, String> formFields,
            @NotNull TypeToken<T> typeToken,
            @NotNull OnSuccessCallback<T> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        errorCallback = redacting(errorCallback);
        int status;
        String rsp;
        String responseType = null;
        try {
            logDebug("ZeroBounceSDK::sendFormPostRequest preparing request: " + urlPath);
            StringBuilder formBody = new StringBuilder();
            for (Map.Entry<String, String> entry : formFields.entrySet()) {
                if (formBody.length() > 0) {
                    formBody.append('&');
                }
                formBody.append(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                formBody.append('=');
                formBody.append(java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            byte[] out = formBody.toString().getBytes(StandardCharsets.UTF_8);
            if (transport != null) {
                SdkHttp.Result result = transport.exchange(new SdkHttp(
                        "POST",
                        urlPath,
                        "application/x-www-form-urlencoded",
                        out,
                        null
                ));
                status = result.status;
                responseType = result.contentType;
                rsp = new String(result.body, StandardCharsets.UTF_8);
            } else {
                URL url = createUrlFrom(urlPath);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setInstanceFollowRedirects(false);
                con.setRequestProperty("Accept", "application/json");
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                con.setConnectTimeout(timeoutInMillis);
                con.setReadTimeout(timeoutInMillis);
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setFixedLengthStreamingMode(out.length);
                con.connect();
                try (OutputStream os = con.getOutputStream()) {
                    os.write(out);
                }

                status = con.getResponseCode();
                responseType = con.getContentType();
                rsp = readResponse(con, status);
            }
        } catch (Exception e) {
            logError("ZeroBounceSDK::sendFormPostRequest failed", e);
            errorCallback.onError(ErrorResponse.parseError(redactedFailure(e)));
            return;
        }
        logPayload("ZeroBounceSDK::sendFormPostRequest rsp=" + rsp);
        deliverJson(status, responseType, rsp, typeToken, successCallback, errorCallback);
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
        sendRequestWithMethod(method, urlPath, queryParameters, formFields, jsonBody, typeToken,
                null, null, successCallback, errorCallback);
    }

    /**
     * @param emptyIsSuccess whether a 2xx with no body is a success (with a
     *                       null result): null keeps the general rule (only a
     *                       204), true or false decides it for the call
     */
    private <T> void sendRequestWithMethod(
            @NotNull String method,
            @NotNull String urlPath,
            @Nullable Map<String, String> queryParameters,
            @Nullable Map<String, String> formFields,
            @Nullable Object jsonBody,
            @Nullable TypeToken<T> typeToken,
            @Nullable Map<String, String> extraHeaders,
            @Nullable Boolean emptyIsSuccess,
            @Nullable OnSuccessCallback<T> successCallback,
            @NotNull OnErrorCallback errorCallback) {
        errorCallback = redacting(errorCallback);
        int status;
        String rsp;
        String responseType = null;
        try {
            URIBuilder ub = new URIBuilder(urlPath);
            if (queryParameters != null) {
                for (Map.Entry<String, String> param : queryParameters.entrySet()) {
                    ub.addParameter(param.getKey(), param.getValue());
                }
            }

            String urlString = ub.toString();
            String contentType = null;
            byte[] out = null;
            if (formFields != null) {
                StringBuilder form = new StringBuilder();
                for (Map.Entry<String, String> field : formFields.entrySet()) {
                    if (form.length() > 0) form.append('&');
                    form.append(urlEncode(field.getKey())).append('=').append(urlEncode(field.getValue()));
                }
                out = form.toString().getBytes(StandardCharsets.UTF_8);
                contentType = "application/x-www-form-urlencoded";
            } else if (jsonBody != null) {
                out = gson.toJson(jsonBody).getBytes(StandardCharsets.UTF_8);
                contentType = "application/json";
            }

            if (transport != null) {
                // The offline suites replace the network here. Without this branch
                // every call built on sendRequestWithMethod -- the Inbox Placement
                // Tester, the filters, the SFTP jobs, scoring -- would go out to the
                // live API from a test run.
                SdkHttp.Result result = transport.exchange(new SdkHttp(
                        method,
                        urlString,
                        contentType,
                        out,
                        formFields
                ));
                status = result.status;
                responseType = result.contentType;
                rsp = new String(result.body, StandardCharsets.UTF_8);
            } else {
                URL url = createUrlFrom(urlString);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setInstanceFollowRedirects(false);
                con.setRequestProperty("Accept", "application/json");
                if (extraHeaders != null) {
                    for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
                        con.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
                con.setConnectTimeout(timeoutInMillis);
                con.setReadTimeout(timeoutInMillis);
                con.setRequestMethod(method);
                if (contentType != null) {
                    con.setRequestProperty("Content-Type", contentType);
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

                status = con.getResponseCode();
                responseType = con.getContentType();
                rsp = readResponse(con, status);
            }
        } catch (Exception e) {
            logError("ZeroBounceSDK::sendRequestWithMethod failed", e);
            errorCallback.onError(ErrorResponse.parseError(redactedFailure(e)));
            return;
        }
        logPayload("ZeroBounceSDK::sendRequestWithMethod rsp=" + rsp);

        // The same failure handling as sendRequest: a short message for an
        // HTML page, an empty or non-JSON body and a Cloudflare block, the
        // Error: prefix stripped, and the HTTP status kept. LIVE: {"success":
        // false} on an HTTP 200 is a failure, not a result. The API answers
        // an unknown bulk file id that way.
        if (reportHtmlContentType(status, responseType, rsp, errorCallback)) {
            return;
        }
        if (status > 299 || apiSuccessIsFalse(rsp)) {
            reportFailure(status, rsp, errorCallback);
            return;
        }
        if (successCallback == null) {
            return;
        }
        boolean emptyAllowed = emptyIsSuccess != null ? emptyIsSuccess : status == 204;
        if (typeToken != null && rsp.trim().isEmpty() && !emptyAllowed) {
            // A typed endpoint that answers 2xx with no body did not succeed.
            // The Inbox Placement Tester's DELETE legitimately carries nothing
            // (a 204, or an empty 200), and that reaches the success callback.
            errorCallback.onError(ErrorResponse.parseError(
                    HttpMessages.jsonCallFailure(status, rsp, null), status));
            return;
        }
        if (typeToken == null || rsp.trim().isEmpty()) {
            successCallback.onSuccess(null);
            return;
        }
        // The same body checks as sendRequest: an HTTP 200 that carries
        // {"error": ...}, an HTML page or a non-JSON body is a failure, not
        // a result to decode.
        deliverJson(status, responseType, rsp, typeToken, successCallback, errorCallback);
    }

    /**
     * The body of a response. A failed response with no body has no error
     * stream at all, so that reads as an empty body rather than a
     * NullPointerException that would lose the status.
     */
    private static String readResponse(@NotNull HttpURLConnection con, int status) throws IOException {
        InputStream stream = SdkHttp.checkedBody(con, status > 299 ? con.getErrorStream() : con.getInputStream());
        if (stream == null) {
            con.disconnect();
            return "";
        }
        String content = SdkHttp.readText(stream);
        con.disconnect();
        return content;
    }

    /**
     * Whether a JSON body says the call failed although the HTTP status was a
     * success. The API sends {@code success} as a JSON false, "false" or
     * "False" depending on the endpoint.
     */
    private static boolean apiSuccessIsFalse(@Nullable String body) {
        if (body == null) {
            return false;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            return false;
        }
        try {
            JsonObject parsed = JsonParser.parseString(trimmed).getAsJsonObject();
            if (parsed == null || !parsed.has("success")) {
                return false;
            }
            JsonElement value = parsed.get("success");
            if (!value.isJsonPrimitive()) {
                return false;
            }
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return !primitive.getAsBoolean();
            }
            return primitive.isString() && "false".equalsIgnoreCase(primitive.getAsString().trim());
        } catch (RuntimeException e) {
            return false;
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

    private <T> void deliverJson(
            int status,
            @Nullable String contentType,
            String rsp,
            TypeToken<T> typeToken,
            OnSuccessCallback<T> successCallback,
            OnErrorCallback errorCallback
    ) {
        if (reportHtmlContentType(status, contentType, rsp, errorCallback)
                || reportJsonFailure(status, rsp, errorCallback)) {
            return;
        }
        T response;
        try {
            response = gson.fromJson(rsp, typeToken.getType());
        } catch (RuntimeException e) {
            // A body the SDK cannot decode, such as a boolean field that holds
            // "yes". The caller's success callback is called outside this try,
            // so an exception it throws is not reported here.
            logError("ZeroBounceSDK::deliverJson failed", e);
            errorCallback.onError(ErrorResponse.parseError(redactedFailure(e)));
            return;
        }
        successCallback.onSuccess(response);
    }

    /**
     * {@link #deliverJson} for a bulk upload: a failed status, an HTML or empty
     * body and {@code {"success": false}} reach the error callback, and the
     * decoded body the success callback, outside any try.
     */
    void deliverSendFileResponse(
            int status,
            @Nullable String contentType,
            String rsp,
            OnSuccessCallback<ZBSendFileResponse> successCallback,
            OnErrorCallback errorCallback
    ) {
        if (reportHtmlContentType(status, contentType, rsp, errorCallback)
                || reportHttpFailure(status, rsp, errorCallback)) {
            return;
        }
        // A 2xx that is not JSON ("hello", a proxy's text) is an API error with
        // its status, as on every other JSON call, not a decoder failure.
        String notJson = HttpMessages.jsonCallFailure(status, rsp, null);
        if (notJson != null) {
            errorCallback.onError(ErrorResponse.parseError(notJson, status));
            return;
        }
        ZBSendFileResponse response;
        try {
            response = gson.fromJson(rsp, ZBSendFileResponse.class);
        } catch (RuntimeException e) {
            logError("ZeroBounceSDK::sendFile failed", e);
            errorCallback.onError(ErrorResponse.parseError(redactedFailure(e)));
            return;
        }
        successCallback.onSuccess(response);
    }

    /**
     * Reports a JSON call's failure: an account-level batch error, an HTTP
     * status above 299, an HTML, empty or non-JSON body, or an API error in a
     * 2xx body. The message is short, the {@code Error:} prefix is stripped,
     * a Cloudflare block is its title, and the HTTP status is kept.
     *
     * @return true when the error callback was called
     */
    private boolean reportJsonFailure(int status, @Nullable String rsp, @NotNull OnErrorCallback errorCallback) {
        String accountError = HttpMessages.batchAccountError(rsp);
        if (accountError != null) {
            errorCallback.onError(ErrorResponse.parseError(accountError, status));
            return true;
        }
        String failure = HttpMessages.jsonCallFailure(status, rsp, null);
        if (failure == null) {
            failure = HttpMessages.successPayloadError(rsp);
        }
        if (failure == null) {
            return false;
        }
        String body = rsp == null ? "" : rsp;
        if (HttpMessages.preferShortMessage(body)) {
            errorCallback.onError(ErrorResponse.parseError(failure, status));
        } else {
            // Every value of the JSON error, as before, but without a repeated
            // "Error:" prefix such as {"error": "Error: Error: ..."}.
            ErrorResponse parsed = ErrorResponse.parseError(body, status);
            parsed.getErrors().replaceAll(HttpMessages::stripPrefix);
            errorCallback.onError(parsed);
        }
        return true;
    }

    /**
     * {@link #reportJsonFailure} for a response already known to be a failure,
     * such as a {@code {"success": false}} on an HTTP 200.
     */
    private void reportFailure(int status, @Nullable String rsp, @NotNull OnErrorCallback errorCallback) {
        if (!reportJsonFailure(status, rsp, errorCallback)) {
            errorCallback.onError(ErrorResponse.parseError(rsp, status));
        }
    }

    private void deliverGetFile(
            File file,
            String downloadPath,
            int statusCode,
            String contentTypeRaw,
            byte[] bodyBytes,
            OnSuccessCallback<ZBGetFileResponse> successCallback,
            OnErrorCallback errorCallback
    ) {
        ErrorResponse error = downloadError(statusCode, bodyBytes, contentTypeRaw);
        if (error != null) {
            errorCallback.onError(error);
            return;
        }
        if (!saveDownload(file, bodyBytes, errorCallback)) {
            return;
        }
        ZBGetFileResponse rsp = new ZBGetFileResponse();
        rsp.setLocalFilePath(downloadPath);
        successCallback.onSuccess(rsp);
    }

    /**
     * Why a bulk download is not a results file, or null when it should be
     * saved: an error status, an empty body or an HTML page, a JSON content
     * type, or a JSON error body whatever its content type (the API has sent
     * {@code {"success": false, ...}} as text/plain). The file is not written
     * in any of those cases.
     */
    static @Nullable ErrorResponse downloadError(int status, byte[] body, @Nullable String contentType) {
        String text = new String(body, StandardCharsets.UTF_8);
        String failure = HttpMessages.downloadFailure(status, body, contentType);
        if (failure != null) {
            ErrorResponse error = HttpMessages.preferShortMessage(text)
                    ? ErrorResponse.parseError(failure, status)
                    : ErrorResponse.parseError(text, status);
            if (error.getErrors().isEmpty()) {
                // {"success": false} with no message.
                error.getErrors().add(failure);
            }
            return error;
        }
        String mime = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!ContentType.APPLICATION_JSON.getMimeType().equals(mime) && !HttpMessages.downloadJsonError(text)) {
            return null;
        }
        if (HttpMessages.preferShortMessage(text)) {
            return ErrorResponse.parseError(
                    "ZeroBounce returned a JSON response instead of the file (HTTP " + status + ").", status);
        }
        ErrorResponse error = ErrorResponse.parseError(text, status);
        if (error.getErrors().isEmpty()) {
            // {"success": false} with no message.
            error.getErrors().add("ZeroBounce returned an error instead of the file (HTTP " + status + ").");
        }
        return error;
    }

    /**
     * Writes a download to disk. The bytes go to a temporary file in the
     * target's directory, which is renamed over the target only once every byte
     * is written, so a failed write leaves a file already at the target as it
     * was and no partial file. A failure to write is the SDK's own error and
     * goes to the error callback.
     *
     * @return true when the file was written
     */
    static boolean saveDownload(File file, byte[] body, OnErrorCallback errorCallback) {
        return saveDownload(file, body, errorCallback, fileOpener);
    }

    /** Opens the file a download is written to. Tests pass one that fails part-way. */
    interface FileOpener {
        OutputStream open(File file) throws IOException;
    }

    /** How {@link #saveDownload(File, byte[], OnErrorCallback)} opens the temporary file; tests replace it. */
    static volatile FileOpener fileOpener = FileOutputStream::new;

    static boolean saveDownload(File file, byte[] body, OnErrorCallback errorCallback, FileOpener opener) {
        java.nio.file.Path target = file.getAbsoluteFile().toPath();
        java.nio.file.Path temp = null;
        try {
            // Named here rather than by Files.createTempFile, so the file gets
            // the same permissions a direct write would have given it.
            temp = target.resolveSibling("." + target.getFileName() + "." + java.util.UUID.randomUUID() + ".part");
            try (OutputStream outStream = opener.open(temp.toFile())) {
                outStream.write(body);
            }
            try {
                java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // A local write error names the file, not the request, so it holds no key.
            logger.error("ZeroBounceSDK::getFile could not write the file", e);
            if (temp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Nothing more can be done; the error below is what matters.
                }
            }
            String message = e.getMessage();
            errorCallback.onError(ErrorResponse.parseError(
                    message == null || message.isBlank() ? "Could not write the file." : message));
            return false;
        }
        return true;
    }

    /** Every field but the file itself, as text/plain parts in the map's order. */
    private static void addTextParts(MultipartEntityBuilder builder, Map<String, String> fields) {
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!"file".equals(field.getKey())) {
                builder.addPart(field.getKey(), new StringBody(field.getValue(), ContentType.TEXT_PLAIN));
            }
        }
    }

    /**
     * The multipart fields of sendFile and scoringSendFile, used by the real
     * request and the offline transport alike. "file" holds the file name; the
     * real request sends the file's content in that part.
     */
    private Map<String, String> sendFileMultipartFields(
            boolean scoring,
            int emailAddressColumnIndex,
            @Nullable SendFileOptions options,
            @NotNull String fileName
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("api_key", apiKey == null ? "" : apiKey);
        fields.put("email_address_column", String.valueOf(emailAddressColumnIndex));
        fields.put("file", fileName);
        if (options != null) {
            if (options.returnUrl != null) {
                fields.put("return_url", options.returnUrl);
            }
            if (options.firstNameColumn > 0) {
                fields.put("first_name_column", String.valueOf(options.firstNameColumn));
            }
            if (options.lastNameColumn > 0) {
                fields.put("last_name_column", String.valueOf(options.lastNameColumn));
            }
            if (options.genderColumn > 0) {
                fields.put("gender_column", String.valueOf(options.genderColumn));
            }
            if (options.ipAddressColumn > 0) {
                fields.put("ip_address_column", String.valueOf(options.ipAddressColumn));
            }
            if (options.hasHeaderRow != null) {
                fields.put("has_header_row", String.valueOf(options.hasHeaderRow));
            }
            if (options.removeDuplicate != null) {
                fields.put("remove_duplicate", String.valueOf(options.removeDuplicate));
            }
            if (!scoring && options.allowPhase2 != null) {
                fields.put("allow_phase_2", Boolean.toString(options.allowPhase2));
            }
        }
        return fields;
    }

    private void deliverSendFileTransport(
            String urlPath,
            Map<String, String> fields,
            OnSuccessCallback<ZBSendFileResponse> successCallback,
            OnErrorCallback errorCallback
    ) {
        SdkHttp.Result result;
        try {
            result = transport.exchange(new SdkHttp("POST", urlPath, "multipart/form-data", null, fields));
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(redactedFailure(e)));
            return;
        }
        deliverSendFileResponse(result.status, result.contentType, new String(result.body, StandardCharsets.UTF_8),
                successCallback, errorCallback);
    }

    // Everything logged goes through redactSecrets: a URL, a response body or
    // an exception message can carry the key (S8).
    private void logDebug(String message) {
        logger.debug(redactSecrets(message));
    }

    /** A warning through the logger set with {@link #setLogger(ZBLogger)}. */
    static void logWarning(String message) {
        logger.warn(message);
    }

    private void logPayload(String message) {
        if (logPayloads) {
            logger.debug(redactSecrets(message));
        }
    }

    private void logError(String message, Exception exception) {
        logger.error(redactSecrets(message), redactedForLog(exception, 0));
    }

    /**
     * A copy of {@code exception} for the logger: the same type name, message
     * and stack trace, with the key redacted from the message of it and of
     * each cause.
     */
    private Throwable redactedForLog(Throwable exception, int depth) {
        String message = exception.getMessage();
        String text = exception.getClass().getName() + (message == null ? "" : ": " + redactSecrets(message));
        Throwable cause = exception.getCause();
        Throwable copy = new RuntimeException(text,
                cause == null || cause == exception || depth >= 8 ? null : redactedForLog(cause, depth + 1)) {
            @Override
            public String toString() {
                return getMessage();
            }
        };
        copy.setStackTrace(exception.getStackTrace());
        return copy;
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

    public static class EmailFinderSendFileOptions {
        int firstNameColumn;
        int lastNameColumn;
        int middleNameColumn;
        int fullNameColumn;
        @Nullable Boolean hasHeaderRow;

        public EmailFinderSendFileOptions setFirstNameColumn(int firstNameColumn) {
            this.firstNameColumn = firstNameColumn;
            return this;
        }

        public EmailFinderSendFileOptions setLastNameColumn(int lastNameColumn) {
            this.lastNameColumn = lastNameColumn;
            return this;
        }

        public EmailFinderSendFileOptions setMiddleNameColumn(int middleNameColumn) {
            this.middleNameColumn = middleNameColumn;
            return this;
        }

        public EmailFinderSendFileOptions setFullNameColumn(int fullNameColumn) {
            this.fullNameColumn = fullNameColumn;
            return this;
        }

        public EmailFinderSendFileOptions setHasHeaderRow(@Nullable Boolean hasHeaderRow) {
            this.hasHeaderRow = hasHeaderRow;
            return this;
        }
    }

    public static class DomainSearchSendFileOptions {
        @Nullable Boolean hasHeaderRow;

        public DomainSearchSendFileOptions setHasHeaderRow(@Nullable Boolean hasHeaderRow) {
            this.hasHeaderRow = hasHeaderRow;
            return this;
        }
    }

    public static class ValidateS3FileOptions {
        @NotNull String fileName = "";
        @Nullable Boolean hasHeaderRow;
        @Nullable Boolean removeDuplicates;
        @Nullable Boolean removeDuplicate;
        @Nullable Integer emailAddressColumn;
        @Nullable String returnUrl;
        int firstNameColumn;
        int lastNameColumn;
        int genderColumn;
        int ipAddressColumn;
        @Nullable Boolean allowPhase2;

        public ValidateS3FileOptions setFileName(@NotNull String fileName) {
            this.fileName = fileName;
            return this;
        }

        public ValidateS3FileOptions setHasHeaderRow(@Nullable Boolean hasHeaderRow) {
            this.hasHeaderRow = hasHeaderRow;
            return this;
        }

        public ValidateS3FileOptions setRemoveDuplicates(@Nullable Boolean removeDuplicates) {
            this.removeDuplicates = removeDuplicates;
            return this;
        }

        public ValidateS3FileOptions setRemoveDuplicate(@Nullable Boolean removeDuplicate) {
            this.removeDuplicate = removeDuplicate;
            return this;
        }

        public ValidateS3FileOptions setEmailAddressColumn(@Nullable Integer emailAddressColumn) {
            this.emailAddressColumn = emailAddressColumn;
            return this;
        }

        public ValidateS3FileOptions setReturnUrl(@Nullable String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public ValidateS3FileOptions setFirstNameColumn(int firstNameColumn) {
            this.firstNameColumn = firstNameColumn;
            return this;
        }

        public ValidateS3FileOptions setLastNameColumn(int lastNameColumn) {
            this.lastNameColumn = lastNameColumn;
            return this;
        }

        public ValidateS3FileOptions setGenderColumn(int genderColumn) {
            this.genderColumn = genderColumn;
            return this;
        }

        public ValidateS3FileOptions setIpAddressColumn(int ipAddressColumn) {
            this.ipAddressColumn = ipAddressColumn;
            return this;
        }

        public ValidateS3FileOptions setAllowPhase2(@Nullable Boolean allowPhase2) {
            this.allowPhase2 = allowPhase2;
            return this;
        }

        /** The fields shared with every S3 / SFTP job, including the required ones. */
        ZBBulkFileJobOptions toJobOptions() {
            ZBBulkFileJobOptions job = new ZBBulkFileJobOptions()
                    .setFileName(fileName)
                    .setReturnUrl(returnUrl)
                    .setRemoveDuplicates(removeDuplicates);
            if (hasHeaderRow != null) {
                job.setHasHeaderRow(hasHeaderRow);
            }
            return job;
        }

        /** The fields only validateS3file takes. */
        Map<String, String> extraFields() {
            Map<String, String> fields = new LinkedHashMap<>();
            if (removeDuplicate != null) {
                fields.put("remove_duplicate", String.valueOf(removeDuplicate));
            }
            // An email column of 0 is meaningful here, so it is sent when set.
            if (emailAddressColumn != null) {
                fields.put("email_address_column", String.valueOf(emailAddressColumn));
            }
            if (firstNameColumn > 0) {
                fields.put("first_name_column", String.valueOf(firstNameColumn));
            }
            if (lastNameColumn > 0) {
                fields.put("last_name_column", String.valueOf(lastNameColumn));
            }
            if (genderColumn > 0) {
                fields.put("gender_column", String.valueOf(genderColumn));
            }
            if (ipAddressColumn > 0) {
                fields.put("ip_address_column", String.valueOf(ipAddressColumn));
            }
            if (allowPhase2 != null) {
                fields.put("allow_phase_2", String.valueOf(allowPhase2));
            }
            return fields;
        }
    }

    public static class ScoreS3FileOptions {
        @NotNull String fileName = "";
        @Nullable Boolean hasHeaderRow;
        @Nullable Boolean removeDuplicates;
        @Nullable Boolean removeDuplicate;
        @Nullable Integer emailAddressColumn;
        @Nullable String returnUrl;

        public ScoreS3FileOptions setFileName(@NotNull String fileName) {
            this.fileName = fileName;
            return this;
        }

        public ScoreS3FileOptions setHasHeaderRow(@Nullable Boolean hasHeaderRow) {
            this.hasHeaderRow = hasHeaderRow;
            return this;
        }

        public ScoreS3FileOptions setRemoveDuplicates(@Nullable Boolean removeDuplicates) {
            this.removeDuplicates = removeDuplicates;
            return this;
        }

        public ScoreS3FileOptions setRemoveDuplicate(@Nullable Boolean removeDuplicate) {
            this.removeDuplicate = removeDuplicate;
            return this;
        }

        public ScoreS3FileOptions setEmailAddressColumn(@Nullable Integer emailAddressColumn) {
            this.emailAddressColumn = emailAddressColumn;
            return this;
        }

        /** LIVE: required, like on every other S3 / SFTP job. */
        public ScoreS3FileOptions setReturnUrl(@Nullable String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        /** The fields shared with every S3 / SFTP job, including the required ones. */
        ZBBulkFileJobOptions toJobOptions() {
            ZBBulkFileJobOptions job = new ZBBulkFileJobOptions()
                    .setFileName(fileName)
                    .setReturnUrl(returnUrl)
                    .setRemoveDuplicates(removeDuplicates);
            if (hasHeaderRow != null) {
                job.setHasHeaderRow(hasHeaderRow);
            }
            return job;
        }

        /** The fields only scoreS3file takes. {@code allow_phase_2} is not one of them. */
        Map<String, String> extraFields() {
            Map<String, String> fields = new LinkedHashMap<>();
            if (removeDuplicate != null) {
                fields.put("remove_duplicate", String.valueOf(removeDuplicate));
            }
            if (emailAddressColumn != null) {
                fields.put("email_address_column", String.valueOf(emailAddressColumn));
            }
            return fields;
        }
    }

    String priority3ApiKey() {
        return apiKey;
    }

    String priority3ListEvaluatorBaseUrl() {
        return listEvaluatorBaseUrl;
    }

    String priority3EmailFinderBaseUrl() {
        return emailFinderBulkBaseUrl;
    }

    String priority3DomainSearchBaseUrl() {
        return domainSearchBulkBaseUrl;
    }

    int priority3TimeoutMillis() {
        return timeoutInMillis;
    }

    Gson priority3Gson() {
        return gson;
    }

    <T> void priority3SendRequest(
            @NotNull String urlPath,
            @Nullable Map<String, String> queryParameters,
            @Nullable Map<String, Object> body,
            @NotNull TypeToken<T> typeToken,
            @NotNull OnSuccessCallback<T> successCallback,
            @NotNull OnErrorCallback errorCallback
    ) {
        sendRequest(urlPath, queryParameters, body, typeToken, successCallback, errorCallback);
    }
}
