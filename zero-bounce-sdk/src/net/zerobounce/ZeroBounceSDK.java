package net.zerobounce;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ZeroBounceSDK {

    private static ZeroBounceSDK _instance;

    public static ZeroBounceSDK getInstance() {
        if (_instance == null) _instance = new ZeroBounceSDK();
        return _instance;
    }

    private final String apiBaseUrl = "https://api.zerobounce.net/v2";
    private final String bulkApiBaseUrl = "https://bulkapi.zerobounce.net/v2";
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private String apiKey;
    private Gson gson;

    private ZeroBounceSDK() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.registerTypeAdapter(ZBSendFileResponse.class, new ZBSendFileResponse.Deserializer());
        gsonBuilder.setDateFormat("yyyy-MM-dd HH:mm:ss");
        gson = gsonBuilder.create();
    }

    public void initialize(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * @param email     The email address you want to validate
     * @param ipAddress The IP Address the email signed up from (Can be blank)
     */
    public void validate(
            String email, String ipAddress,
            OnSuccessCallback<ZBValidateResponse> successCallback,
            OnFailureCallback failureCallback) {
        if (invalidApiKey(failureCallback)) return;

        sendRequest(
                apiBaseUrl + "/validate?api_key=" + apiKey +
                        "&email=" + email +
                        "&ip_address=" + (ipAddress != null ? ipAddress : ""),
                successCallback,
                failureCallback,
                new TypeToken<ZBValidateResponse>() {
                });
    }

    /**
     * This API will tell you how many credits you have left on your account. It's simple, fast and easy to use.
     */
    public void getCredits(
            OnSuccessCallback<ZBCreditsResponse> successCallback,
            OnFailureCallback failureCallback) {
        if (invalidApiKey(failureCallback)) return;

        sendRequest(
                apiBaseUrl + "/getcredits?api_key=" + apiKey,
                successCallback, failureCallback,
                new TypeToken<ZBCreditsResponse>() {
                });
    }

    /**
     * @param startDate The start date of when you want to view API usage
     * @param endDate   The end date of when you want to view API usage
     */
    public void getApiUsage(
            Date startDate, Date endDate,
            OnSuccessCallback<ZBGetApiUsageResponse> successCallback,
            OnFailureCallback failureCallback) {
        if (invalidApiKey(failureCallback)) return;

        sendRequest(
                apiBaseUrl + "/getapiusage?api_key=" + apiKey
                        + "&start_date=" + dateFormat.format(startDate)
                        + "&end_date=" + dateFormat.format(endDate),
                successCallback,
                failureCallback,
                new TypeToken<ZBGetApiUsageResponse>() {
                });
    }

    /**
     * @param fileId The returned file ID when calling sendFile API.
     */
    public void fileStatus(
            String fileId,
            OnSuccessCallback<ZBFileStatusResponse> successCallback,
            OnFailureCallback failureCallback) {
        _fileStatus(false, fileId, successCallback, failureCallback);
    }

    /**
     * @param fileId The returned file ID when calling sendFile API.
     */
    public void scoringFileStatus(
            String fileId,
            OnSuccessCallback<ZBFileStatusResponse> successCallback,
            OnFailureCallback failureCallback) {
        _fileStatus(true, fileId, successCallback, failureCallback);
    }

    private void _fileStatus(
            Boolean scoring,
            String fileId,
            OnSuccessCallback<ZBFileStatusResponse> successCallback,
            OnFailureCallback failureCallback) {
        if (invalidApiKey(failureCallback)) return;

        sendRequest(
                bulkApiBaseUrl + (scoring ? "/scoring" : "") + "/filestatus?api_key=" + apiKey + "&file_id=" + fileId,
                successCallback, failureCallback,
                new TypeToken<ZBFileStatusResponse>() {
                });
    }

    /**
     * @param fileId The returned file ID when calling sendfile API.
     */
    public void deleteFile(String fileId, OnSuccessCallback<ZBDeleteFileResponse> successCallback, OnFailureCallback failureCallback) {
        _deleteFile(false, fileId, successCallback, failureCallback);
    }

    /**
     * @param fileId The returned file ID when calling sendfile API.
     */
    public void scoringDeleteFile(String fileId, OnSuccessCallback<ZBDeleteFileResponse> successCallback, OnFailureCallback failureCallback) {
        _deleteFile(true, fileId, successCallback, failureCallback);
    }

    private void _deleteFile(Boolean scoring, String fileId, OnSuccessCallback<ZBDeleteFileResponse> successCallback, OnFailureCallback failureCallback) {
        if (invalidApiKey(failureCallback)) return;

        sendRequest(
                bulkApiBaseUrl + (scoring ? "/scoring" : "") + "/deletefile?api_key=" + apiKey + "&file_id=" + fileId,
                successCallback,
                failureCallback, new TypeToken<ZBDeleteFileResponse>() {
                });
    }

    public static class SendFileOptions {
        String returnUrl;
        int firstNameColumn;
        int lastNameColumn;
        int genderColumn;
        int ipAddressColumn;
        boolean hasHeaderRow;

        public SendFileOptions withReturnUrl(String returnUrl) {
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

        public SendFileOptions setHasHeaderRow(boolean hasHeaderRow) {
            this.hasHeaderRow = hasHeaderRow;
            return this;
        }
    }

    public static class ScoringSendFileOptions {
        String returnUrl;
        boolean hasHeaderRow;

        public ScoringSendFileOptions withReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public ScoringSendFileOptions setHasHeaderRow(boolean hasHeaderRow) {
            this.hasHeaderRow = hasHeaderRow;
            return this;
        }
    }

    /**
     * The sendfile API allows user to send a file for bulk email validation
     */
    public void sendFile(
            File file, int emailAddressColumn,
            OnSuccessCallback<ZBSendFileResponse> successCallback, OnFailureCallback failureCallback) {
        this.sendFile(file, emailAddressColumn, successCallback, failureCallback, null);
    }

    /**
     * The sendfile API allows user to send a file for bulk email validation
     */
    public void sendFile(
            File file, int emailAddressColumn,
            OnSuccessCallback<ZBSendFileResponse> successCallback,
            OnFailureCallback failureCallback, SendFileOptions options) {
        _sendFile(false, file, emailAddressColumn, successCallback, failureCallback, options);
    }

    /**
     * The scoringSendfile API allows user to send a file for bulk email validation
     */
    public void scoringSendFile(
            File file, int emailAddressColumn,
            OnSuccessCallback<ZBSendFileResponse> successCallback,
            OnFailureCallback failureCallback) {
        scoringSendFile(file, emailAddressColumn, successCallback, failureCallback, null);
    }

    /**
     * The scoringSendfile API allows user to send a file for bulk email validation
     */
    public void scoringSendFile(
            File file, int emailAddressColumn,
            OnSuccessCallback<ZBSendFileResponse> successCallback,
            OnFailureCallback failureCallback, ScoringSendFileOptions options) {
        _sendFile(true, file, emailAddressColumn, successCallback, failureCallback,
                options != null ? new SendFileOptions().withReturnUrl(options.returnUrl).setHasHeaderRow(options.hasHeaderRow) : null);
    }

    private void _sendFile(Boolean scoring,
                           File file, int emailAddressColumn,
                           OnSuccessCallback<ZBSendFileResponse> successCallback,
                           OnFailureCallback failureCallback, SendFileOptions options) {
        if (invalidApiKey(failureCallback)) return;

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost uploadFile = new HttpPost(bulkApiBaseUrl + (scoring ? "/scoring" : "") + "/sendFile");
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            builder.addPart("api_key", new StringBody(apiKey, ContentType.TEXT_PLAIN));
            builder.addPart("email_address_column", new StringBody(String.valueOf(emailAddressColumn), ContentType.TEXT_PLAIN));

            if (options != null) {
                if (options.returnUrl != null)
                    builder.addPart("return_url", new StringBody(options.returnUrl, ContentType.TEXT_PLAIN));
                if (options.firstNameColumn > 0)
                    builder.addPart("first_name_column", new StringBody(String.valueOf(options.firstNameColumn), ContentType.TEXT_PLAIN));
                if (options.lastNameColumn > 0)
                    builder.addPart("last_name_column", new StringBody(String.valueOf(options.lastNameColumn), ContentType.TEXT_PLAIN));
                if (options.genderColumn > 0)
                    builder.addPart("gender_column", new StringBody(String.valueOf(options.genderColumn), ContentType.TEXT_PLAIN));
                if (options.ipAddressColumn > 0)
                    builder.addPart("ip_address_column", new StringBody(String.valueOf(options.ipAddressColumn), ContentType.TEXT_PLAIN));
                builder.addPart("has_header_row", new StringBody(String.valueOf(options.hasHeaderRow), ContentType.TEXT_PLAIN));
            }

            builder.addPart("file", new FileBody(file));

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            CloseableHttpResponse response = httpClient.execute(uploadFile);
            HttpEntity responseEntity = response.getEntity();

            BufferedReader in = new BufferedReader(new InputStreamReader(responseEntity.getContent()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            String rsp = content.toString();

            System.out.println("ZeroBounceSDK::sendRequest rsp=" + rsp);

            Type type = new TypeToken<ZBSendFileResponse>() {
            }.getType();
            ZBSendFileResponse sendFileResponse = gson.fromJson(rsp, type);

            if (successCallback != null) successCallback.onSuccess(sendFileResponse);
        } catch (Exception e) {
            e.printStackTrace();
            if (failureCallback != null) failureCallback.onError(e.getMessage());
        }
    }

    /**
     * The getfile API allows users to get the validation results file for the file been submitted using sendfile API
     */
    public void getFile(
            String fileId, String downloadPath,
            OnSuccessCallback<ZBGetFileResponse> successCallback,
            OnFailureCallback failureCallback) {
        _getFile(false, fileId, downloadPath, successCallback, failureCallback);
    }

    /**
     * The scoringGetFile API allows users to get the validation results file for the file been submitted using scoringSendfile API
     */
    public void scoringGetFile(
            String fileId, String downloadPath,
            OnSuccessCallback<ZBGetFileResponse> successCallback,
            OnFailureCallback failureCallback) {
        _getFile(true, fileId, downloadPath, successCallback, failureCallback);
    }

    private void _getFile(Boolean scoring,
                          String fileId, String downloadPath,
                          OnSuccessCallback<ZBGetFileResponse> successCallback,
                          OnFailureCallback failureCallback) {
        try {
            File file = new File(downloadPath);
            if (file.isFile()) {
                if (failureCallback != null) failureCallback.onError("Invalid file path");
                return;
            }
            file.getParentFile().mkdirs();
            CloseableHttpClient client = HttpClients.createDefault();
            HttpGet downloadFile = new HttpGet(bulkApiBaseUrl + (scoring ? "/scoring" : "") + "/getFile?api_key=" + apiKey + "&file_id=" + fileId);
            CloseableHttpResponse response = client.execute(downloadFile);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                FileOutputStream outStream = new FileOutputStream(file);
                entity.writeTo(outStream);
            }
            if (successCallback != null) {
                ZBGetFileResponse rsp = new ZBGetFileResponse();
                rsp.localFilePath = downloadPath;
                successCallback.onSuccess(rsp);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (failureCallback != null) failureCallback.onError(e.getMessage());
        }
    }

    private boolean invalidApiKey(OnFailureCallback failureCallback) {
        if (apiKey == null) {
            if (failureCallback != null)
                failureCallback.onError("ZeroBounce SDK is not initialized. Please call ZeroBounceSDK.initialize(context, apiKey) first");
            return true;
        }
        return false;
    }

    public interface OnSuccessCallback<T> {
        void onSuccess(T response);
    }

    public interface OnFailureCallback {
        void onError(String errorMessage);
    }

    private <T> void sendRequest(String urlPath, OnSuccessCallback<T> successCallback, OnFailureCallback failureCallback, TypeToken<T> typeToken) {
        try {
            System.out.println("ZeroBounceSDK::sendRequest urlPath=" + urlPath);

            URL url = new URL(urlPath);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            //con.setRequestProperty("Content-Type", "application/json");

            int status = con.getResponseCode();
            System.out.println("ZeroBounceSDK::sendRequest status: " + status);
            Reader streamReader;
            if (status > 299) {
                streamReader = new InputStreamReader(con.getErrorStream());
            } else {
                streamReader = new InputStreamReader(con.getInputStream());
            }

            BufferedReader in = new BufferedReader(streamReader);
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            con.disconnect();
            String rsp = content.toString();

            System.out.println("ZeroBounceSDK::sendRequest rsp=" + rsp);

            Type type = typeToken.getType();
            T response = gson.fromJson(rsp, type);

            if (successCallback != null) successCallback.onSuccess(response);
        } catch (Exception e) {
            e.printStackTrace();
            if (failureCallback != null) failureCallback.onError(e.getMessage());
        }

    }
}
