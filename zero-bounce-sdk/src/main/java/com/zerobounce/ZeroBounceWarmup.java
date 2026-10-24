package com.zerobounce;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the ZeroBounce Warmup API
 * (<a href="https://www.zerobounce.net/docs/warmup-api">docs</a>).
 * <p>
 * The Warmup API is separate from the email validation API: its own host, its
 * own team-scoped key, and an {@code API-Key} header instead of an
 * {@code api_key} parameter. Use {@code ZeroBounceWarmup} with a Warmup API
 * key; {@link ZeroBounceSDK} stays for the validation API key.
 * <p>
 * Every method answers through a callback, like {@link ZeroBounceSDK}.
 * Failures carry the HTTP status on {@link ErrorResponse#getStatusCode()}.
 */
public class ZeroBounceWarmup {

    public static final String DEFAULT_BASE_URL = "https://warmup-api.zerobounce.net";
    private static final String BASE_PATH = "/api/v1/warmup";
    private static final int DEFAULT_TIMEOUT_MILLIS = 120_000;

    /** Required by the schema but always replaced with the key's own team. */
    private static final int ANY_TEAM_ID = 1;

    private static final List<String> ONBOARDING_MODELS = Arrays.asList("self_managed", "zb_managed");
    private static final List<String> EMAIL_TYPES =
            Arrays.asList("transactional", "newsletter", "marketing");

    private static final ZeroBounceWarmup instance = new ZeroBounceWarmup();

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private String apiKey;
    private String root = DEFAULT_BASE_URL + BASE_PATH;
    private int timeoutInMillis = DEFAULT_TIMEOUT_MILLIS;
    /** Why the client is unusable, or null when it is ready. */
    private String setupProblem = "ZeroBounceWarmup is not initialized. Call initialize(\"API_KEY\") first";

    ZeroBounceWarmup() {
    }

    public static ZeroBounceWarmup getInstance() {
        return instance;
    }

    /** A client on the default Warmup host. */
    public void initialize(@NotNull String apiKey) {
        initialize(apiKey, DEFAULT_BASE_URL);
    }

    /**
     * A client on a custom https base URL.
     * <p>
     * A key with surrounding whitespace or control characters, or a base URL
     * that is not https, is rejected here: every later call then fails with
     * that message rather than sending the key anywhere.
     */
    public void initialize(@NotNull String apiKey, @NotNull String baseUrl) {
        this.apiKey = apiKey;
        if (apiKey.trim().isEmpty()) {
            setupProblem = "api_key is required";
            return;
        }
        // A key with surrounding whitespace or control characters would be
        // quoted, unredacted, in the transport's own error.
        if (!apiKey.equals(apiKey.trim()) || hasControlCharacters(apiKey)) {
            setupProblem = "api_key has surrounding whitespace or control characters";
            return;
        }
        if (!baseUrl.regionMatches(true, 0, "https://", 0, 8)) {
            setupProblem = "base_url must be an https:// URL";
            return;
        }
        String trimmed = baseUrl;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        root = trimmed + BASE_PATH;
        setupProblem = null;
    }

    /** The request timeout in milliseconds, 0 for none. */
    public void setTimeout(int timeoutInMillis) {
        this.timeoutInMillis = timeoutInMillis;
    }

    private static boolean hasControlCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    // -- transport -----------------------------------------------------------

    /** Replace every form of a secret in a message with ***. */
    private String redact(@Nullable String text, String... secrets) {
        if (text == null) {
            return "";
        }
        String value = text;
        if (apiKey != null && !apiKey.isEmpty()) {
            value = value.replace(apiKey, "***");
        }
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) {
                value = value.replace(secret, "***");
            }
        }
        return value;
    }

    /** Separated like ZeroBounceSDK.createUrlFrom so tests never touch the network. */
    URL createUrlFrom(String url) throws MalformedURLException {
        return URI.create(url).toURL();
    }

    private boolean notReady(@NotNull ZeroBounceSDK.OnErrorCallback errorCallback) {
        if (setupProblem == null) {
            return false;
        }
        errorCallback.onError(ErrorResponse.parseError(setupProblem));
        return true;
    }

    /** What one HTTP exchange produced. */
    private static class Result {
        int status;
        String contentType = "";
        String body = "";
    }

    private @Nullable Result send(
            @NotNull String method,
            @NotNull String url,
            @Nullable String jsonBody,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback,
            String... secrets
    ) {
        try {
            HttpURLConnection con = (HttpURLConnection) createUrlFrom(url).openConnection();
            con.setRequestMethod(method);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("API-Key", apiKey);
            con.setConnectTimeout(timeoutInMillis);
            con.setReadTimeout(timeoutInMillis);
            con.setInstanceFollowRedirects(false);

            if (jsonBody != null) {
                byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                con.setFixedLengthStreamingMode(out.length);
                con.connect();
                try (OutputStream os = con.getOutputStream()) {
                    os.write(out);
                }
            }

            Result result = new Result();
            result.status = con.getResponseCode();
            String contentType = con.getHeaderField("Content-Type");
            result.contentType = contentType != null ? contentType : "";

            Reader streamReader = result.status > 299
                    ? new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8)
                    : new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8);
            StringBuilder content = new StringBuilder();
            try (BufferedReader in = new BufferedReader(streamReader)) {
                String line;
                while ((line = in.readLine()) != null) {
                    content.append(line);
                }
            }
            con.disconnect();
            result.body = content.toString();

            if (result.status >= 300 && result.status < 400) {
                errorCallback.onError(ErrorResponse.parseError(
                        "ZeroBounce returned a redirect, which this client does not follow.",
                        result.status));
                return null;
            }
            if (result.status > 299) {
                errorCallback.onError(ErrorResponse.parseError(
                        redact(messageFromBody(result.body, result.status), secrets), result.status));
                return null;
            }
            return result;
        } catch (Exception e) {
            // Rebuilt rather than wrapped: the original holds the URL and headers.
            errorCallback.onError(ErrorResponse.parseError(redact(e.getMessage(), secrets)));
            return null;
        }
    }

    /**
     * The JSON payload of a success. A documented empty success (204, or an
     * empty 200) is handed to the caller as {@code null}.
     */
    private void json(
            @NotNull String method,
            @NotNull String url,
            @Nullable String jsonBody,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback,
            String... secrets
    ) {
        Result result = send(method, url, jsonBody, errorCallback, secrets);
        if (result == null) {
            return;
        }
        if (result.status == 204 || result.body.trim().isEmpty()) {
            successCallback.onSuccess(null);
            return;
        }
        if (!looksLikeJson(result.body)) {
            // Gson parses leniently, so junk would come back as a string
            // instead of raising: reject it before it reaches the parser.
            errorCallback.onError(ErrorResponse.parseError(
                    redact(messageFromBody(result.body, result.status), secrets), result.status));
            return;
        }
        try {
            // LIVE: not every endpoint answers with an object. /seeds is a JSON
            // array and /seed-count is a bare number, so anything that is not
            // an object is wrapped under "data" and one return type covers all.
            com.google.gson.JsonElement parsed = JsonParser.parseString(result.body);
            if (parsed.isJsonObject()) {
                successCallback.onSuccess(parsed.getAsJsonObject());
                return;
            }
            JsonObject wrapper = new JsonObject();
            wrapper.add("data", parsed);
            successCallback.onSuccess(wrapper);
        } catch (RuntimeException e) {
            errorCallback.onError(ErrorResponse.parseError(
                    redact(messageFromBody(result.body, result.status), secrets), result.status));
        }
    }

    /** A CSV download, returned as text. */
    private void csv(
            @NotNull String url,
            @NotNull ZeroBounceSDK.OnSuccessCallback<String> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        Result result = send("GET", url, null, errorCallback);
        if (result == null) {
            return;
        }
        if (result.body.trim().isEmpty()
                || result.contentType.toLowerCase(java.util.Locale.ROOT).contains("application/json")) {
            errorCallback.onError(ErrorResponse.parseError(
                    redact(messageFromBody(result.body, result.status)), result.status));
            return;
        }
        successCallback.onSuccess(result.body);
    }

    /**
     * Whether a body could be JSON at all. Gson's parser is lenient and would
     * read bare junk as a string, so the shape is checked first.
     */
    private static boolean looksLikeJson(@Nullable String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char first = trimmed.charAt(0);
        if (first == '{' || first == '[' || first == '"' || first == '-' || Character.isDigit(first)) {
            return true;
        }
        return trimmed.equals("true") || trimmed.equals("false") || trimmed.equals("null");
    }

    /** Turn an error body into a short message. Never raw HTML or a parser dump. */
    private String messageFromBody(@Nullable String body, int status) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            return "Empty response from ZeroBounce (HTTP " + status + ")";
        }
        String head = trimmed.substring(0, Math.min(2000, trimmed.length()))
                .toLowerCase(java.util.Locale.ROOT);
        if (head.contains("<!doctype html") || head.contains("<html")) {
            return "ZeroBounce returned an HTML error page instead of an API response.";
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                com.google.gson.JsonElement parsed = JsonParser.parseString(trimmed);
                // A top-level list of strings, e.g. ["In-progress tests cannot be deleted."].
                if (parsed.isJsonArray()) {
                    for (com.google.gson.JsonElement item : parsed.getAsJsonArray()) {
                        if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
                                && !item.getAsString().trim().isEmpty()) {
                            return item.getAsString();
                        }
                    }
                    return "ZeroBounce returned an error (HTTP " + status + ").";
                }
                JsonObject object = parsed.getAsJsonObject();
                for (String key : new String[]{"message", "error", "error_message", "detail"}) {
                    if (!object.has(key) || object.get(key).isJsonNull()) {
                        continue;
                    }
                    com.google.gson.JsonElement value = object.get(key);
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        return value.getAsString();
                    }
                    if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()) {
                        com.google.gson.JsonElement first = value.getAsJsonArray().get(0);
                        if (first.isJsonPrimitive() && first.getAsJsonPrimitive().isString()) {
                            return first.getAsString();
                        }
                        // A validation error list: [{loc: [...], msg: "..."}].
                        if (first.isJsonObject() && first.getAsJsonObject().has("msg")) {
                            JsonObject detail = first.getAsJsonObject();
                            String field = "";
                            if (detail.has("loc") && detail.get("loc").isJsonArray()
                                    && detail.getAsJsonArray("loc").size() > 1) {
                                StringBuilder path = new StringBuilder();
                                com.google.gson.JsonArray loc = detail.getAsJsonArray("loc");
                                for (int i = 1; i < loc.size(); i++) {
                                    if (path.length() > 0) path.append('.');
                                    path.append(loc.get(i).getAsString());
                                }
                                field = path.toString();
                            }
                            String msg = detail.get("msg").getAsString();
                            return field.isEmpty() ? msg : field + ": " + msg;
                        }
                    }
                }
                return "ZeroBounce returned an error (HTTP " + status + ").";
            } catch (RuntimeException e) {
                return "ZeroBounce returned a response that is not valid JSON.";
            }
        }
        if (trimmed.length() > 1000) {
            return "ZeroBounce returned a response that is not valid JSON.";
        }
        return trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** URL-encodes a value as a single path segment, so a slash in it stays encoded. */
    private static String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }

    private String url(String path, Map<String, String> query) {
        StringBuilder url = new StringBuilder(root).append(path);
        String separator = "?";
        for (Map.Entry<String, String> parameter : query.entrySet()) {
            url.append(separator).append(encode(parameter.getKey())).append('=')
               .append(encode(parameter.getValue()));
            separator = "&";
        }
        return url.toString();
    }

    // -- argument checks -----------------------------------------------------

    private boolean missing(
            @Nullable String value, String name, @NotNull ZeroBounceSDK.OnErrorCallback errorCallback) {
        if (value == null || value.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError(name + " is required"));
            return true;
        }
        return false;
    }

    private boolean outOfRange(
            int value, int low, int high, String name,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback) {
        if (value < low || value > high) {
            errorCallback.onError(
                    ErrorResponse.parseError(name + " must be " + low + " to " + high));
            return true;
        }
        return false;
    }

    private static boolean isIsoDate(@Nullable String value) {
        return value != null && value.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    // -- sending accounts ----------------------------------------------------

    /** Every sending account of the key's team. GET /sending-accounts */
    public void listSendingAccounts(
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        json("GET", url("/sending-accounts", Map.of()), null, successCallback, errorCallback);
    }

    /** One sending account. GET /sending-account */
    public void getSendingAccount(
            @NotNull String fromAddress,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return;
        json("GET", url("/sending-account", Map.of("from_address", fromAddress)),
                null, successCallback, errorCallback);
    }

    /**
     * Add a from-address to warmup. POST /sending-accounts
     * <p>
     * {@code self_managed} runs your own mailbox and needs
     * {@link AddSendingAccountOptions#setEngagementRuleId(Integer)} 0-4.
     * {@code zb_managed} has ZeroBounce provision it and takes
     * {@code emailContent} and {@code managedSending}; a 200 means accepted,
     * and the mailbox is provisioned afterwards.
     */
    public void addSendingAccount(
            @NotNull String fromAddress,
            @NotNull String onboardingModel,
            @Nullable AddSendingAccountOptions options,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return;
        if (!ONBOARDING_MODELS.contains(onboardingModel)) {
            errorCallback.onError(
                    ErrorResponse.parseError("onboarding_model must be self_managed or zb_managed"));
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from_address", fromAddress);
        body.put("onboarding_model", onboardingModel);
        AddSendingAccountOptions set = options != null ? options : new AddSendingAccountOptions();

        if ("self_managed".equals(onboardingModel)) {
            if (set.engagementRuleId == null) {
                errorCallback.onError(
                        ErrorResponse.parseError("engagement_rule_id is required for self_managed"));
                return;
            }
            if (outOfRange(set.engagementRuleId, 0, 4, "engagement_rule_id", errorCallback)) return;
            body.put("engagement_rule_id", set.engagementRuleId);
            if (set.emailContent != null || set.managedSending != null) {
                errorCallback.onError(
                        ErrorResponse.parseError("email_content and managed_sending are for zb_managed"));
                return;
            }
        }
        if (set.ignoreConflict != null) {
            body.put("ignore_conflict", set.ignoreConflict);
        }
        if (set.emailContent != null) {
            Map<String, Object> content = new LinkedHashMap<>();
            if (set.emailContent.typesOfEmails != null && !set.emailContent.typesOfEmails.isEmpty()) {
                for (String kind : set.emailContent.typesOfEmails) {
                    if (!EMAIL_TYPES.contains(kind)) {
                        errorCallback.onError(ErrorResponse.parseError(
                                "types_of_emails must be transactional, newsletter and/or marketing"));
                        return;
                    }
                }
                content.put("types_of_emails", set.emailContent.typesOfEmails);
            }
            if (set.emailContent.industry != null) content.put("industry", set.emailContent.industry);
            if (set.emailContent.companyName != null) content.put("company_name", set.emailContent.companyName);
            if (set.emailContent.website != null) content.put("website", set.emailContent.website);
            body.put("email_content", content);
        }
        if (set.managedSending != null) {
            Map<String, Object> sending = new LinkedHashMap<>();
            if (set.managedSending.timezoneOffset != null) {
                if (outOfRange(set.managedSending.timezoneOffset, -11, 14, "timezone_offset", errorCallback)) {
                    return;
                }
                sending.put("timezone_offset", set.managedSending.timezoneOffset);
            }
            if (set.managedSending.warmupStartDate != null) {
                if (!isIsoDate(set.managedSending.warmupStartDate)) {
                    errorCallback.onError(ErrorResponse.parseError("warmup_start_date must be YYYY-MM-DD"));
                    return;
                }
                sending.put("warmup_start_date", set.managedSending.warmupStartDate);
            }
            if (set.managedSending.dailyLimit != null) {
                sending.put("daily_limit", set.managedSending.dailyLimit);
            }
            body.put("managed_sending", sending);
        }

        json("POST", url("/sending-accounts", Map.of()), gson.toJson(body),
                successCallback, errorCallback);
    }

    /**
     * Change an account's engagement rule, status and auto-enable flag.
     * PUT /sending-accounts. {@code statusId} is 0 to 8 (4 Paused, 5 Disabled).
     */
    public void updateSendingAccount(
            @NotNull String fromAddress,
            int engagementRuleId,
            int statusId,
            boolean autoEnable,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return;
        if (outOfRange(engagementRuleId, 0, 4, "engagement_rule_id", errorCallback)) return;
        if (outOfRange(statusId, 0, 8, "status_id", errorCallback)) return;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("team_id", ANY_TEAM_ID);
        body.put("from_address", fromAddress);
        body.put("engagement_rule_id", engagementRuleId);
        body.put("status_id", statusId);
        body.put("auto_enable", autoEnable);
        json("PUT", url("/sending-accounts", Map.of()), gson.toJson(body),
                successCallback, errorCallback);
    }

    /** Remove a sending account. DELETE /sending-accounts. Destructive and final. */
    public void deleteSendingAccount(
            @NotNull String fromAddress,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return;
        json("DELETE", url("/sending-accounts", Map.of("from_address", fromAddress)),
                null, successCallback, errorCallback);
    }

    /**
     * Move an account and its history to a new from-address.
     * PUT /smtp-credentials - despite the path, no SMTP credentials are stored.
     */
    public void changeFromAddress(
            @NotNull String oldFromAddress,
            @NotNull String newFromAddress,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)
                || missing(oldFromAddress, "old_from_address", errorCallback)
                || missing(newFromAddress, "new_from_address", errorCallback)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("team_id", ANY_TEAM_ID);
        body.put("old_from_address", oldFromAddress);
        body.put("new_from_address", newFromAddress);
        json("PUT", url("/smtp-credentials", Map.of()), gson.toJson(body),
                successCallback, errorCallback);
    }

    /**
     * Registered from-addresses. GET /from-addresses
     * <p>
     * {@code searchFrom} takes letters, digits and {@code . - _ @ +} only.
     * {@code offset} and {@code limit} go on the wire as strings.
     */
    public void listFromAddresses(
            @Nullable String searchFrom,
            @Nullable Integer offset,
            @Nullable Integer limit,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        Map<String, String> query = new LinkedHashMap<>();
        if (searchFrom != null) {
            if (!searchFrom.matches("[A-Za-z0-9.\\-_@+]*")) {
                errorCallback.onError(ErrorResponse.parseError(
                        "search_from takes letters, digits and . - _ @ + only"));
                return;
            }
            query.put("search_from", searchFrom);
        }
        if (offset != null) {
            if (offset < 0) {
                errorCallback.onError(ErrorResponse.parseError("offset must be a non-negative integer"));
                return;
            }
            query.put("offset", String.valueOf(offset));
        }
        if (limit != null) {
            if (limit < 0) {
                errorCallback.onError(ErrorResponse.parseError("limit must be a non-negative integer"));
                return;
            }
            query.put("limit", String.valueOf(limit));
        }
        json("GET", url("/from-addresses", query), null, successCallback, errorCallback);
    }

    // -- engagement rules ----------------------------------------------------

    /** -1 Custom, 0 Default, 1 Slow Pace, 2 Repair, 3 Boost, 4 Protect. */
    public void getEngagementRules(
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        json("GET", url("/engagement-rules", Map.of()), null, successCallback, errorCallback);
    }

    /** Set a standard rule (0 to 4) on an address. PUT /engagement-rule */
    public void setEngagementRule(
            @NotNull String cFrom,
            int ruleId,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return;
        if (outOfRange(ruleId, 0, 4, "rule_id", errorCallback)) return;
        json("PUT", url("/engagement-rule", Map.of("c_from", cFrom)),
                gson.toJson(Map.of("rule_id", ruleId)), successCallback, errorCallback);
    }

    /** An address's custom rule, with can_update and remaining_seconds. */
    public void getCustomEngagement(
            @NotNull String cFrom,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return;
        String segment = cFrom.trim();
        if (segment.equals(".") || segment.equals("..")) {
            errorCallback.onError(ErrorResponse.parseError("c_from is invalid"));
            return;
        }
        json("GET", url("/custom-engagement/" + encodePathSegment(segment), Map.of()),
                null, successCallback, errorCallback);
    }

    /** Create a custom rule. Starts a 7 day cooldown on updates. */
    public void createCustomEngagement(
            @NotNull String cFrom, int replyPct, int importantPct, int clickPct,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        String body = engagementBody(cFrom, replyPct, importantPct, clickPct, errorCallback);
        if (body == null) return;
        json("POST", url("/custom-engagement", Map.of()), body, successCallback, errorCallback);
    }

    /** Update a custom rule. During the 7 day cooldown the API answers 429. */
    public void updateCustomEngagement(
            @NotNull String cFrom, int replyPct, int importantPct, int clickPct,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        String body = engagementBody(cFrom, replyPct, importantPct, clickPct, errorCallback);
        if (body == null) return;
        json("PUT", url("/custom-engagement", Map.of()), body, successCallback, errorCallback);
    }

    private @Nullable String engagementBody(
            String cFrom, int replyPct, int importantPct, int clickPct,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (missing(cFrom, "c_from", errorCallback)) return null;
        if (outOfRange(replyPct, 5, 45, "reply_pct", errorCallback)) return null;
        if (outOfRange(importantPct, 5, 40, "important_pct", errorCallback)) return null;
        if (outOfRange(clickPct, 0, 70, "click_pct", errorCallback)) return null;
        if (clickPct > (int) (importantPct * 1.75)) {
            errorCallback.onError(
                    ErrorResponse.parseError("click_pct must be at most important_pct x 1.75"));
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("c_from", cFrom);
        body.put("reply_pct", replyPct);
        body.put("important_pct", importantPct);
        body.put("click_pct", clickPct);
        return gson.toJson(body);
    }

    /** Delete a custom rule and fall back to standard rule 1-4. */
    public void deleteCustomEngagement(
            @NotNull String cFrom,
            int engagementRuleId,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return;
        if (outOfRange(engagementRuleId, 1, 4, "engagement_rule_id", errorCallback)) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("c_from", cFrom);
        body.put("engagement_rule_id", engagementRuleId);
        json("DELETE", url("/custom-engagement", Map.of()), gson.toJson(body),
                successCallback, errorCallback);
    }

    // -- statistics, seeds and utilities -------------------------------------

    /** The seed list. GET /seeds */
    public void getSeeds(
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        json("GET", url("/seeds", Map.of()), null, successCallback, errorCallback);
    }

    /** The seed list as CSV (columns seed,provider). GET /seeds/download */
    public void downloadSeeds(
            @NotNull ZeroBounceSDK.OnSuccessCallback<String> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        csv(url("/seeds/download", Map.of()), successCallback, errorCallback);
    }

    /** Number of seeds. GET /seed-count */
    public void getSeedCount(
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        json("GET", url("/seed-count", Map.of()), null, successCallback, errorCallback);
    }

    /** Today's warmup progress. GET /progress-today */
    public void getProgressToday(
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        json("GET", url("/progress-today", Map.of()), null, successCallback, errorCallback);
    }

    /** Messages sent today. GET /sent-today */
    public void getSentToday(
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        json("GET", url("/sent-today", Map.of()), null, successCallback, errorCallback);
    }

    /** Current daily volume. GET /emails-per-day */
    public void getEmailsPerDay(
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)) return;
        json("GET", url("/emails-per-day", Map.of()), null, successCallback, errorCallback);
    }

    /** Statistics for an address. An address with no history returns zeros. */
    public void getStatistics(
            @NotNull String fromAddress,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return;
        json("GET", url("/statistics", Map.of("from_address", fromAddress)),
                null, successCallback, errorCallback);
    }

    /** Daily statistics between two dates, inclusive. GET /statistics-per-day */
    public void getStatisticsPerDay(
            @NotNull String fromAddress,
            @NotNull String startDate,
            @NotNull String endDate,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return;
        if (!isIsoDate(startDate)) {
            errorCallback.onError(ErrorResponse.parseError("start_date must be YYYY-MM-DD"));
            return;
        }
        if (!isIsoDate(endDate)) {
            errorCallback.onError(ErrorResponse.parseError("end_date must be YYYY-MM-DD"));
            return;
        }
        if (startDate.compareTo(endDate) > 0) {
            errorCallback.onError(ErrorResponse.parseError("start_date is later than end_date"));
            return;
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("from_address", fromAddress);
        query.put("start_date", startDate);
        query.put("end_date", endDate);
        json("GET", url("/statistics-per-day", query), null, successCallback, errorCallback);
    }

    /** Detailed statistics for an address. GET /statistics-detailed */
    public void getStatisticsDetailed(
            @NotNull String cFrom,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return;
        json("GET", url("/statistics-detailed", Map.of("c_from", cFrom)),
                null, successCallback, errorCallback);
    }

    /** An address's statistics as CSV. GET /statistics/download */
    public void downloadStatistics(
            @NotNull String cFrom,
            @NotNull ZeroBounceSDK.OnSuccessCallback<String> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(cFrom, "c_from", errorCallback)) return;
        csv(url("/statistics/download", Map.of("c_from", cFrom)), successCallback, errorCallback);
    }

    /** Statistics for one from-address. GET /from-address-statistics */
    public void getFromAddressStatistics(
            @NotNull String fromAddress,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return;
        json("GET", url("/from-address-statistics", Map.of("from_address", fromAddress)),
                null, successCallback, errorCallback);
    }

    /**
     * SPF, DKIM and DMARC status of a from-address's domain.
     * GET /check-domain-dmarc
     * <p>
     * LIVE: pass an <em>email address</em>. The live API answers "Please
     * provide a valid email address" for a bare domain, although the docs
     * describe a domain.
     */
    public void checkDomainDmarc(
            @NotNull String fromAddress,
            @Nullable String dkimSelector,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback) || missing(fromAddress, "from_address", errorCallback)) return;
        if (!fromAddress.contains("@")) {
            errorCallback.onError(ErrorResponse.parseError(
                    "from_address must be an email address, e.g. sender@example.com"));
            return;
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("from_address", fromAddress);
        if (dkimSelector != null) {
            if (missing(dkimSelector, "dkim_selector", errorCallback)) return;
            query.put("dkim_selector", dkimSelector);
        }
        json("GET", url("/check-domain-dmarc", query), null, successCallback, errorCallback);
    }

    /**
     * Check that SMTP credentials connect and hand off a message.
     * POST /test-smtp-configuration
     * <p>
     * Nothing is stored, and the test message goes to a ZeroBounce test
     * recipient, not {@code fromAddress}.
     * <p>
     * LIVE: the API answers any failed SMTP login with HTTP 500. That is an API
     * bug. The username and password are masked in every error from here.
     */
    public void testSmtpConfiguration(
            @NotNull String fromAddress,
            @NotNull String username,
            @NotNull String password,
            @NotNull String server,
            int port,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonObject> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (notReady(errorCallback)
                || missing(fromAddress, "from_address", errorCallback)
                || missing(username, "username", errorCallback)
                || missing(password, "password", errorCallback)
                || missing(server, "server", errorCallback)) {
            return;
        }
        if (outOfRange(port, 1, 65535, "port", errorCallback)) return;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from_address", fromAddress);
        body.put("username", username);
        body.put("password", password);
        body.put("server", server);
        body.put("port", port);
        json("POST", url("/test-smtp-configuration", Map.of()), gson.toJson(body),
                successCallback, errorCallback, password, username);
    }

    // -- option holders ------------------------------------------------------

    /** What ZeroBounce should send on your behalf, for zb_managed onboarding. */
    public static class EmailContent {
        @Nullable List<String> typesOfEmails;
        @Nullable String industry;
        @Nullable String companyName;
        @Nullable String website;

        /** Any of transactional, newsletter, marketing. */
        public EmailContent setTypesOfEmails(@Nullable List<String> typesOfEmails) {
            this.typesOfEmails = typesOfEmails;
            return this;
        }

        public EmailContent setIndustry(@Nullable String industry) {
            this.industry = industry;
            return this;
        }

        public EmailContent setCompanyName(@Nullable String companyName) {
            this.companyName = companyName;
            return this;
        }

        public EmailContent setWebsite(@Nullable String website) {
            this.website = website;
            return this;
        }
    }

    /** When ZeroBounce should send, for zb_managed onboarding. */
    public static class ManagedSending {
        @Nullable Integer timezoneOffset;
        @Nullable String warmupStartDate;
        @Nullable Integer dailyLimit;

        /** -11 to 14. */
        public ManagedSending setTimezoneOffset(@Nullable Integer timezoneOffset) {
            this.timezoneOffset = timezoneOffset;
            return this;
        }

        /** YYYY-MM-DD. */
        public ManagedSending setWarmupStartDate(@Nullable String warmupStartDate) {
            this.warmupStartDate = warmupStartDate;
            return this;
        }

        public ManagedSending setDailyLimit(@Nullable Integer dailyLimit) {
            this.dailyLimit = dailyLimit;
            return this;
        }
    }

    /** The optional arguments of {@link #addSendingAccount}. */
    public static class AddSendingAccountOptions {
        @Nullable Integer engagementRuleId;
        @Nullable Boolean ignoreConflict;
        @Nullable EmailContent emailContent;
        @Nullable ManagedSending managedSending;

        /** Required for self_managed: 0 Default, 1 Slow Pace, 2 Repair, 3 Boost, 4 Protect. */
        public AddSendingAccountOptions setEngagementRuleId(@Nullable Integer engagementRuleId) {
            this.engagementRuleId = engagementRuleId;
            return this;
        }

        public AddSendingAccountOptions setIgnoreConflict(@Nullable Boolean ignoreConflict) {
            this.ignoreConflict = ignoreConflict;
            return this;
        }

        /** zb_managed only. */
        public AddSendingAccountOptions setEmailContent(@Nullable EmailContent emailContent) {
            this.emailContent = emailContent;
            return this;
        }

        /** zb_managed only. */
        public AddSendingAccountOptions setManagedSending(@Nullable ManagedSending managedSending) {
            this.managedSending = managedSending;
            return this;
        }
    }
}
