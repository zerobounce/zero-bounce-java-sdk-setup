package com.zerobounce;

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
import java.util.LinkedHashMap;
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
        if (missingNameColumn(options, errorCallback)) {
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
        if (missingNameColumn(options, errorCallback)) {
            return;
        }
        String url = sdk.priority3EmailFinderBaseUrl() + "/sendfile";
        sendBulkRootMultipartStream(url, inputStream, fileName, buildEmailFinderFields(domainColumn, options), successCallback, errorCallback);
    }

    /**
     * The bulk email finder needs a name to work from: one of
     * {@code first_name_column} and {@code full_name_column} is required.
     */
    private boolean missingNameColumn(
            @Nullable ZeroBounceSDK.EmailFinderSendFileOptions options,
            @NotNull ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (options != null && (options.firstNameColumn > 0 || options.fullNameColumn > 0)) {
            return false;
        }
        errorCallback.onError(ErrorResponse.parseError(
                "Empty parameter: first_name_column or full_name_column"));
        return true;
    }

    private Map<String, String> buildEmailFinderFields(int domainColumn, @Nullable ZeroBounceSDK.EmailFinderSendFileOptions options) {
        Map<String, String> fields = new HashMap<>();
        fields.put("domain_column", String.valueOf(domainColumn));
        if (options != null) {
            if (options.hasHeaderRow != null) {
                fields.put("has_header_row", options.hasHeaderRow ? "true" : "false");
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
        if (options != null && options.hasHeaderRow != null) {
            fields.put("has_header_row", options.hasHeaderRow ? "true" : "false");
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
            if (sdk.transport != null) {
                SdkHttp.Result result = sdk.transport.exchange(new SdkHttp("GET", url, null, null, null));
                finishBulkDownload(downloadPath, result.status, result.body, successCallback, errorCallback);
                return;
            }
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) sdk.createUrlFrom(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(sdk.priority3TimeoutMillis());
            con.setReadTimeout(sdk.priority3TimeoutMillis());
            int status = con.getResponseCode();
            InputStream stream = status > 299 ? con.getErrorStream() : con.getInputStream();
            byte[] body = stream.readAllBytes();
            con.disconnect();
            finishBulkDownload(downloadPath, status, body, successCallback, errorCallback);
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(sdk.redactedFailure(e)));
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
        // The upload and the result read the same list evaluator base URL, so
        // overriding that host does not split the pair across two hosts.
        sendBulkRootMultipart(sdk.priority3ListEvaluatorBaseUrl() + "/", file, fields, successCallback, errorCallback);
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
                sdk.priority3ListEvaluatorBaseUrl() + "/",
                inputStream,
                fileName,
                fields,
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
        if (deliverMultipartTransport(url, fields, file.getName(), successCallback, errorCallback)) {
            return;
        }
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(url);
            uploadFile.setConfig(sdk.apacheRequestConfig());
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                builder.addPart(entry.getKey(), new StringBody(entry.getValue(), ContentType.TEXT_PLAIN));
            }
            builder.addPart("api_key", new StringBody(sdk.priority3ApiKey(), ContentType.TEXT_PLAIN));
            builder.addPart("file", new FileBody(file));
            uploadFile.setEntity(builder.build());
            handleMultipartResponse(httpClient.execute(uploadFile), successCallback, errorCallback);
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(sdk.redactedFailure(e)));
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
        if (deliverMultipartTransport(url, fields, fileName, successCallback, errorCallback)) {
            return;
        }
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(url);
            uploadFile.setConfig(sdk.apacheRequestConfig());
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                builder.addPart(entry.getKey(), new StringBody(entry.getValue(), ContentType.TEXT_PLAIN));
            }
            builder.addPart("api_key", new StringBody(sdk.priority3ApiKey(), ContentType.TEXT_PLAIN));
            builder.addPart("file", new InputStreamBody(inputStream, ContentType.DEFAULT_BINARY, fileName));
            uploadFile.setEntity(builder.build());
            handleMultipartResponse(httpClient.execute(uploadFile), successCallback, errorCallback);
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(sdk.redactedFailure(e)));
        }
    }

    private void finishBulkDownload(
            String downloadPath,
            int status,
            byte[] body,
            ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse> successCallback,
            ZeroBounceSDK.OnErrorCallback errorCallback
    ) throws IOException {
        String downloadError = HttpMessages.downloadFailure(status, body, null);
        if (downloadError != null) {
            String text = new String(body, StandardCharsets.UTF_8);
            if (HttpMessages.preferShortMessage(text)) {
                errorCallback.onError(ErrorResponse.parseError(downloadError));
            } else {
                errorCallback.onError(ErrorResponse.parseError(text));
            }
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
    }

    private boolean deliverMultipartTransport(
            String url,
            Map<String, String> fields,
            String fileName,
            ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        if (sdk.transport == null) {
            return false;
        }
        Map<String, String> recorded = new LinkedHashMap<>(fields);
        String key = sdk.priority3ApiKey();
        recorded.put("api_key", key == null ? "" : key);
        recorded.put("file", fileName == null || fileName.isBlank() ? "<file>" : fileName);
        try {
            SdkHttp.Result result = sdk.transport.exchange(
                    new SdkHttp("POST", url, "multipart/form-data", null, recorded));
            String rsp = new String(result.body, StandardCharsets.UTF_8);
            if (!sdk.reportHttpFailure(result.status, rsp, errorCallback)) {
                successCallback.onSuccess(sdk.priority3Gson().fromJson(rsp, ZBSendFileResponse.class));
            }
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(sdk.redactedFailure(e)));
        }
        return true;
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
        if (!sdk.reportHttpFailure(status, rsp, errorCallback)) {
            Type type = new TypeToken<ZBSendFileResponse>() {
            }.getType();
            ZBSendFileResponse sendFileResponse = sdk.priority3Gson().fromJson(rsp, type);
            successCallback.onSuccess(sendFileResponse);
        }
    }
}
