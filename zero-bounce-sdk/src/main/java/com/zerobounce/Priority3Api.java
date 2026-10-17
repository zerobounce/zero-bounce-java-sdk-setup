package com.zerobounce;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class Priority3Api {

    private final ZeroBounceSDK sdk;

    Priority3Api(ZeroBounceSDK sdk) {
        this.sdk = sdk;
    }

    void sendEmailFinderFile(
            @NotNull File file,
            int domainColumn,
            @Nullable ZeroBounceSDK.EmailFinderSendFileOptions options,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (domainColumn < 1) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: domain_column"));
            return;
        }
        String url = sdk.priority3EmailFinderBaseUrl() + "/sendfile";
        sendBulkRootMultipart(url, file, buildEmailFinderFields(domainColumn, options), successCallback, errorCallback);
    }

    void sendEmailFinderFileStream(
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int domainColumn,
            @Nullable ZeroBounceSDK.EmailFinderSendFileOptions options,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (domainColumn < 1) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: domain_column"));
            return;
        }
        String url = sdk.priority3EmailFinderBaseUrl() + "/sendfile";
        sendBulkRootMultipartStream(url, inputStream, fileName, buildEmailFinderFields(domainColumn, options), successCallback, errorCallback);
    }

    private Map<String, String> buildEmailFinderFields(int domainColumn, @Nullable ZeroBounceSDK.EmailFinderSendFileOptions options) {
        Map<String, String> fields = new HashMap<>();
        fields.put("domain_column", String.valueOf(domainColumn));
        if (options != null) {
            if (options.hasHeaderRow != null && options.hasHeaderRow) {
                fields.put("has_header_row", "true");
            }
            if (options.firstNameColumn > 0) {
                fields.put("first_name_column", String.valueOf(options.firstNameColumn));
            }
            if (options.lastNameColumn > 0) {
                fields.put("last_name_column", String.valueOf(options.lastNameColumn));
            }
            if (options.middleNameColumn > 0) {
                fields.put("middle_name_column", String.valueOf(options.middleNameColumn));
            }
            if (options.fullNameColumn > 0) {
                fields.put("full_name_column", String.valueOf(options.fullNameColumn));
            }
        }
        return fields;
    }

    void sendDomainSearchFile(
            @NotNull File file,
            int domainColumn,
            @Nullable ZeroBounceSDK.DomainSearchSendFileOptions options,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (domainColumn < 1) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: domain_column"));
            return;
        }
        sendBulkRootMultipart(
                sdk.priority3DomainSearchBaseUrl() + "/sendfile",
                file,
                buildDomainSearchFields(domainColumn, options),
                successCallback,
                errorCallback
        );
    }

    void sendDomainSearchFileStream(
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int domainColumn,
            @Nullable ZeroBounceSDK.DomainSearchSendFileOptions options,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (domainColumn < 1) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: domain_column"));
            return;
        }
        sendBulkRootMultipartStream(
                sdk.priority3DomainSearchBaseUrl() + "/sendfile",
                inputStream,
                fileName,
                buildDomainSearchFields(domainColumn, options),
                successCallback,
                errorCallback
        );
    }

    private Map<String, String> buildDomainSearchFields(int domainColumn, @Nullable ZeroBounceSDK.DomainSearchSendFileOptions options) {
        Map<String, String> fields = new HashMap<>();
        fields.put("domain_column", String.valueOf(domainColumn));
        if (options != null && options.hasHeaderRow != null && options.hasHeaderRow) {
            fields.put("has_header_row", "true");
        }
        return fields;
    }

    void bulkRootFileStatus(
            @NotNull String baseUrl,
            @NotNull String fileId,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBFileStatusResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        sdk.priority3SendRequest(
                baseUrl + "/filestatus",
                new HashMap<>() {
                    {
                        put("api_key", sdk.priority3ApiKey());
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

    void bulkRootGetFile(
            @NotNull String baseUrl,
            @NotNull String fileId,
            @NotNull String downloadPath,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        try {
            String url = baseUrl + "/getfile?api_key="
                    + java.net.URLEncoder.encode(sdk.priority3ApiKey(), StandardCharsets.UTF_8)
                    + "&file_id=" + java.net.URLEncoder.encode(fileId, StandardCharsets.UTF_8);
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) sdk.createUrlFrom(url).openConnection();
            con.setConnectTimeout(sdk.priority3TimeoutMillis());
            con.setReadTimeout(sdk.priority3TimeoutMillis());
            int status = con.getResponseCode();
            InputStream stream = status > 299 ? con.getErrorStream() : con.getInputStream();
            byte[] body = stream.readAllBytes();
            con.disconnect();
            if (status > 299) {
                errorCallback.onError(ErrorResponse.parseError(new String(body, StandardCharsets.UTF_8)));
                return;
            }
            String bodyStr = new String(body, StandardCharsets.UTF_8).trim();
            if (bodyStr.startsWith("{")) {
                try {
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    if (obj.has("success") && obj.get("success").isJsonPrimitive() && !obj.get("success").getAsBoolean()) {
                        errorCallback.onError(ErrorResponse.parseError(bodyStr));
                        return;
                    }
                    if (obj.has("message") || obj.has("error") || obj.has("error_message")) {
                        errorCallback.onError(ErrorResponse.parseError(bodyStr));
                        return;
                    }
                } catch (Exception ignored) {
                    // not a JSON error payload — treat as file bytes
                }
            }
            File out = new File(downloadPath);
            File parent = out.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(body);
            }
            ZBGetFileResponse response = new ZBGetFileResponse();
            response.setLocalFilePath(downloadPath);
            successCallback.onSuccess(response);
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
        }
    }

    void bulkRootDeleteFile(
            @NotNull String baseUrl,
            @NotNull String fileId,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBDeleteFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        sdk.priority3SendRequest(
                baseUrl + "/deletefile",
                new HashMap<>() {
                    {
                        put("api_key", sdk.priority3ApiKey());
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

    void sendListEvaluatorFile(
            @NotNull File file,
            int emailAddressColumn,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (emailAddressColumn < 1) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: email_address_column"));
            return;
        }
        Map<String, String> fields = new HashMap<>();
        fields.put("email_address_column", String.valueOf(emailAddressColumn));
        sendBulkRootMultipart(sdk.priority3BulkApiBaseUrl() + "/listevaluator/", file, fields, successCallback, errorCallback);
    }

    void sendListEvaluatorFileStream(
            @NotNull InputStream inputStream,
            @NotNull String fileName,
            int emailAddressColumn,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (emailAddressColumn < 1) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: email_address_column"));
            return;
        }
        Map<String, String> fields = new HashMap<>();
        fields.put("email_address_column", String.valueOf(emailAddressColumn));
        sendBulkRootMultipartStream(
                sdk.priority3BulkApiBaseUrl() + "/listevaluator/",
                inputStream,
                fileName,
                fields,
                successCallback,
                errorCallback
        );
    }

    void getListEvaluatorResult(
            @NotNull String fileId,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonElement> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        sdk.priority3SendRequest(
                sdk.priority3BulkApiBaseUrl() + "/listevaluator/" + fileId + "/",
                new HashMap<>() {
                    {
                        put("api_key", sdk.priority3ApiKey());
                    }
                },
                null,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    void sendEvaluatedFile(
            @NotNull File file,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        sendBulkRootMultipart(
                sdk.priority3BulkApiBaseUrl() + "/send-evaluated-file/",
                file,
                new HashMap<>(),
                successCallback,
                errorCallback
        );
    }

    void addFilter(
            @NotNull String rule,
            @NotNull String target,
            @NotNull String value,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonElement> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        sdk.priority3SendFormPost(
                sdk.priority3ApiBaseUrl() + "/filters/add",
                new HashMap<>() {
                    {
                        put("api_key", sdk.priority3ApiKey());
                        put("rule", rule);
                        put("target", target);
                        put("value", value);
                    }
                },
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    void listFilters(
            boolean usePost,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonElement> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (usePost) {
            sdk.priority3SendFormPost(
                    sdk.priority3ApiBaseUrl() + "/filters/list",
                    new HashMap<>() {
                        {
                            put("api_key", sdk.priority3ApiKey());
                        }
                    },
                    new TypeToken<>() {
                    },
                    successCallback,
                    errorCallback
            );
        } else {
            sdk.priority3SendRequest(
                    sdk.priority3ApiBaseUrl() + "/filters/list",
                    new HashMap<>() {
                        {
                            put("api_key", sdk.priority3ApiKey());
                        }
                    },
                    null,
                    new TypeToken<>() {
                    },
                    successCallback,
                    errorCallback
            );
        }
    }

    void deleteFilter(
            @NotNull String rule,
            @NotNull String target,
            @NotNull String value,
            @NotNull ZeroBounceSDK.OnSuccessCallback<JsonElement> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        sdk.priority3SendFormPost(
                sdk.priority3ApiBaseUrl() + "/filters/delete",
                new HashMap<>() {
                    {
                        put("api_key", sdk.priority3ApiKey());
                        put("rule", rule);
                        put("target", target);
                        put("value", value);
                    }
                },
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    void validateS3File(
            @NotNull ZeroBounceSDK.ValidateS3FileOptions options,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (options.fileName == null || options.fileName.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_name"));
            return;
        }
        postBulkRootForm("/validateS3file", buildValidateS3Fields(options), successCallback, errorCallback);
    }

    void scoreS3File(
            @NotNull ZeroBounceSDK.ScoreS3FileOptions options,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (options.fileName == null || options.fileName.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_name"));
            return;
        }
        Map<String, String> fields = new HashMap<>();
        fields.put("file_name", options.fileName);
        putOptionalBool(fields, "has_header_row", options.hasHeaderRow);
        putOptionalBool(fields, "remove_duplicates", options.removeDuplicates);
        putOptionalBool(fields, "remove_duplicate", options.removeDuplicate);
        if (options.emailAddressColumn != null) {
            fields.put("email_address_column", String.valueOf(options.emailAddressColumn));
        }
        postBulkRootForm("/scoreS3file", fields, successCallback, errorCallback);
    }

    void exportS3File(
            @NotNull String fileId,
            @NotNull String exportBucket,
            @NotNull ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (fileId.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: file_id"));
            return;
        }
        if (exportBucket.trim().isEmpty()) {
            errorCallback.onError(ErrorResponse.parseError("Empty parameter: export_bucket"));
            return;
        }
        Map<String, String> fields = new HashMap<>();
        fields.put("file_id", fileId);
        fields.put("export_bucket", exportBucket);
        postBulkRootForm("/exportS3file", fields, successCallback, errorCallback);
    }

    private Map<String, String> buildValidateS3Fields(ZeroBounceSDK.ValidateS3FileOptions options) {
        Map<String, String> fields = new HashMap<>();
        fields.put("file_name", options.fileName);
        putOptionalBool(fields, "has_header_row", options.hasHeaderRow);
        putOptionalBool(fields, "remove_duplicates", options.removeDuplicates);
        putOptionalBool(fields, "remove_duplicate", options.removeDuplicate);
        if (options.emailAddressColumn != null) {
            fields.put("email_address_column", String.valueOf(options.emailAddressColumn));
        }
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
        if (options.allowPhase2 != null) {
            fields.put("allow_phase_2", Boolean.toString(options.allowPhase2));
        }
        return fields;
    }

    private void putOptionalBool(Map<String, String> fields, String key, @Nullable Boolean value) {
        if (value != null) {
            fields.put(key, String.valueOf(value));
        }
    }

    private void postBulkRootForm(
            String path,
            Map<String, String> fields,
            ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        Map<String, String> form = new HashMap<>(fields);
        form.put("api_key", sdk.priority3ApiKey());
        sdk.priority3SendFormPost(
                sdk.priority3BulkApiRootUrl() + path,
                form,
                new TypeToken<>() {
                },
                successCallback,
                errorCallback
        );
    }

    private void sendBulkRootMultipart(
            String url,
            File file,
            Map<String, String> fields,
            ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(url);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                builder.addPart(entry.getKey(), new StringBody(entry.getValue(), ContentType.TEXT_PLAIN));
            }
            builder.addPart("api_key", new StringBody(sdk.priority3ApiKey(), ContentType.TEXT_PLAIN));
            builder.addPart("file", new FileBody(file));
            uploadFile.setEntity(builder.build());
            handleMultipartResponse(httpClient.execute(uploadFile), successCallback, errorCallback);
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
        }
    }

    private void sendBulkRootMultipartStream(
            String url,
            InputStream inputStream,
            String fileName,
            Map<String, String> fields,
            ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(url);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                builder.addPart(entry.getKey(), new StringBody(entry.getValue(), ContentType.TEXT_PLAIN));
            }
            builder.addPart("api_key", new StringBody(sdk.priority3ApiKey(), ContentType.TEXT_PLAIN));
            builder.addPart("file", new InputStreamBody(inputStream, ContentType.DEFAULT_BINARY, fileName));
            uploadFile.setEntity(builder.build());
            handleMultipartResponse(httpClient.execute(uploadFile), successCallback, errorCallback);
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(e.getMessage()));
        }
    }

    private void handleMultipartResponse(
            CloseableHttpResponse response,
            ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            ZeroBounceSDK.OnErrorCallback errorCallback
    ) throws IOException {
        HttpEntity responseEntity = response.getEntity();
        int status = response.getStatusLine().getStatusCode();
        StringBuilder content = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(responseEntity.getContent()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
        }
        String rsp = content.toString();
        if (status > 299) {
            errorCallback.onError(ErrorResponse.parseError(rsp));
        } else {
            Type type = new TypeToken<ZBSendFileResponse>() {
            }.getType();
            ZBSendFileResponse sendFileResponse = sdk.priority3Gson().fromJson(rsp, type);
            successCallback.onSuccess(sendFileResponse);
        }
    }
}
