package com.zerobounce;

import com.google.gson.reflect.TypeToken;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.InputStreamBody;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
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
        errorCallback = sdk.redacting(errorCallback);
        // The same pre-check as getFile: a directory is refused before any request.
        if (new File(downloadPath).isDirectory()) {
            errorCallback.onError(ErrorResponse.parseError("Invalid file path"));
            return;
        }
        int status;
        String contentType;
        byte[] body;
        try {
            String url = baseUrl + "/getfile?api_key="
                    + java.net.URLEncoder.encode(sdk.priority3ApiKey(), StandardCharsets.UTF_8)
                    + "&file_id=" + java.net.URLEncoder.encode(fileId, StandardCharsets.UTF_8);
            if (sdk.transport != null) {
                SdkHttp.Result result = sdk.transport.exchange(new SdkHttp("GET", url, null, null, null));
                status = result.status;
                contentType = result.contentType;
                body = result.body;
            } else {
                SdkHttp.Result result = sdk.exchangeHttp("GET", url, null, null, null);
                status = result.status;
                contentType = result.contentType;
                body = result.body;
            }
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(sdk.redactedFailure(e)));
            return;
        }
        // Outside the try, so an exception from either callback reaches the caller.
        finishBulkDownload(downloadPath, status, contentType, body, successCallback, errorCallback);
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
        errorCallback = sdk.redacting(errorCallback);
        if (deliverMultipartTransport(url, fields, file.getName(), successCallback, errorCallback)) {
            return;
        }
        SdkHttp.Result response;
        try {
            HttpPost uploadFile = new HttpPost(url);
            // UTF-8 form and file names: LEGACY mode writes a file name's bytes as they are,
            // where the other modes encode them as Latin-1 or percent-escapes.
            MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                    .setContentType(ContentType.MULTIPART_FORM_DATA.withCharset(StandardCharsets.UTF_8))
                    .setMode(HttpMultipartMode.LEGACY)
                    .setCharset(StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                builder.addPart(entry.getKey(), new StringBody(entry.getValue(), ContentType.TEXT_PLAIN));
            }
            builder.addPart("api_key", new StringBody(sdk.priority3ApiKey(), ContentType.TEXT_PLAIN));
            builder.addPart("file", new FileBody(file));
            uploadFile.setEntity(builder.build());
            response = SdkHttp.execute(uploadFile, sdk.priority3TimeoutMillis());
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(sdk.redactedFailure(e)));
            return;
        }
        sdk.deliverSendFileResponse(response.status, response.contentType, new String(response.body, StandardCharsets.UTF_8),
                successCallback, errorCallback);
    }

    private void sendBulkRootMultipartStream(
            String url,
            InputStream inputStream,
            String fileName,
            Map<String, String> fields,
            ZeroBounceSDK.OnSuccessCallback<ZBSendFileResponse> successCallback,
            ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        errorCallback = sdk.redacting(errorCallback);
        if (deliverMultipartTransport(url, fields, fileName, successCallback, errorCallback)) {
            return;
        }
        SdkHttp.Result response;
        try {
            HttpPost uploadFile = new HttpPost(url);
            // UTF-8 form and file names: LEGACY mode writes a file name's bytes as they are,
            // where the other modes encode them as Latin-1 or percent-escapes.
            MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                    .setContentType(ContentType.MULTIPART_FORM_DATA.withCharset(StandardCharsets.UTF_8))
                    .setMode(HttpMultipartMode.LEGACY)
                    .setCharset(StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                builder.addPart(entry.getKey(), new StringBody(entry.getValue(), ContentType.TEXT_PLAIN));
            }
            builder.addPart("api_key", new StringBody(sdk.priority3ApiKey(), ContentType.TEXT_PLAIN));
            builder.addPart("file", new InputStreamBody(inputStream, ContentType.DEFAULT_BINARY, fileName));
            uploadFile.setEntity(builder.build());
            response = SdkHttp.execute(uploadFile, sdk.priority3TimeoutMillis());
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(sdk.redactedFailure(e)));
            return;
        }
        sdk.deliverSendFileResponse(response.status, response.contentType, new String(response.body, StandardCharsets.UTF_8),
                successCallback, errorCallback);
    }

    private void finishBulkDownload(
            String downloadPath,
            int status,
            String contentType,
            byte[] body,
            ZeroBounceSDK.OnSuccessCallback<ZBGetFileResponse> successCallback,
            ZeroBounceSDK.OnErrorCallback errorCallback
    ) {
        // The same checks as getFile: a JSON error body is an error whatever
        // its content type, and nothing is written for it.
        ErrorResponse error = ZeroBounceSDK.downloadError(status, body, contentType);
        if (error != null) {
            errorCallback.onError(error);
            return;
        }
        File out = new File(downloadPath);
        if (!ZeroBounceSDK.saveDownload(out, body, errorCallback)) {
            return;
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
        SdkHttp.Result result;
        try {
            result = sdk.transport.exchange(new SdkHttp("POST", url, "multipart/form-data", null, recorded));
        } catch (Exception e) {
            errorCallback.onError(ErrorResponse.parseError(sdk.redactedFailure(e)));
            return true;
        }
        sdk.deliverSendFileResponse(result.status, result.contentType, new String(result.body, StandardCharsets.UTF_8),
                successCallback, errorCallback);
        return true;
    }

}
