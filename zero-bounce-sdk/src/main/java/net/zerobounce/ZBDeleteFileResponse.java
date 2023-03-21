package net.zerobounce;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
