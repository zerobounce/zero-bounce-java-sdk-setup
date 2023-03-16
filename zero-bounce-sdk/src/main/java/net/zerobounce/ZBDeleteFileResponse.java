package net.zerobounce;

import com.google.gson.annotations.SerializedName;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.util.Objects;

/**
 * The model class for the GET /deletefile request.
 */
public class ZBDeleteFileResponse {

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

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBDeleteFileResponse that = (ZBDeleteFileResponse) o;
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
        return "ZBDeleteFileResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileId='" + fileId + '\'' +
                '}';
    }
}
