package com.zerobounce;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * The model used for the GET /scoring and GET /sendFile requests.
 */
public class ZBSendFileResponse {

    @NotNull
    private Boolean success = false;

    @Nullable
    private String message = null;

    @SerializedName("file_name")
    @Nullable
    private String fileName = null;

    @SerializedName("file_id")
    @Nullable
    private String fileId = null;

    public @NotNull Boolean getSuccess() {
        return success;
    }

    public void setSuccess(@NotNull Boolean success) {
        this.success = success;
    }

    public @Nullable String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    public @Nullable String getFileName() {
        return fileName;
    }

    public void setFileName(@Nullable String fileName) {
        this.fileName = fileName;
    }

    public @Nullable String getFileId() {
        return fileId;
    }

    public void setFileId(@Nullable String fileId) {
        this.fileId = fileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBSendFileResponse that = (ZBSendFileResponse) o;
        return Objects.equals(success, that.success)
                && Objects.equals(message, that.message)
                && Objects.equals(fileName, that.fileName)
                && Objects.equals(fileId, that.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, fileName, fileId);
    }

    @Override
    public String toString() {
        return "ZBSendFileResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileId='" + fileId + '\'' +
                '}';
    }

    /**
     * A class that properly deserializes a JSON to a [ZBSendFileResponse].
     */
    public static class Deserializer implements JsonDeserializer<ZBSendFileResponse> {

        @Override
        public ZBSendFileResponse deserialize(
                JsonElement jsonElement,
                Type type,
                JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {

            JsonObject jobject = jsonElement.getAsJsonObject();
            ZBSendFileResponse r = new ZBSendFileResponse();
            if (jobject.has("success")) {
                r.success = jobject.get("success").getAsBoolean();
            }
            if (jobject.has("message")) {
                if (jobject.get("message").isJsonArray()) {
                    JsonArray array = jobject.get("message").getAsJsonArray();
                    if (array.size() > 0) {
                        r.message = array.get(0).getAsString();
                    }
                } else {
                    r.message = jobject.get("message").getAsString();
                }
            }
            if (jobject.has("file_name")) {
                r.fileName = jobject.get("file_name").getAsString();
            }
            if (jobject.has("file_id")) {
                r.fileId = jobject.get("file_id").getAsString();
            }

            return r;
        }
    }
}
