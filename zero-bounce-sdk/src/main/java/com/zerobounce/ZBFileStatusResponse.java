package com.zerobounce;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

/**
 * The model used for the GET /scoring and GET /filestatus requests.
 */
public class ZBFileStatusResponse {

    @NotNull
    private Boolean success = false;

    @Nullable
    private String message = null;

    @SerializedName("file_id")
    @Nullable
    private String fileId = null;

    @SerializedName("file_name")
    @Nullable
    private String fileName = null;

    @SerializedName("upload_date")
    @Nullable
    private Date uploadDate = null;

    @SerializedName("file_status")
    @Nullable
    private String fileStatus = null;

    @SerializedName("complete_percentage")
    @Nullable
    private String completePercentage = null;

    @SerializedName("error_reason")
    @Nullable
    private String errorReason = null;

    @SerializedName("return_url")
    @Nullable
    private String returnUrl = null;

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

    public @Nullable String getFileId() {
        return fileId;
    }

    public void setFileId(@Nullable String fileId) {
        this.fileId = fileId;
    }

    public @Nullable String getFileName() {
        return fileName;
    }

    public void setFileName(@Nullable String fileName) {
        this.fileName = fileName;
    }

    public @Nullable Date getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(@Nullable Date uploadDate) {
        this.uploadDate = uploadDate;
    }

    public @Nullable String getFileStatus() {
        return fileStatus;
    }

    public void setFileStatus(@Nullable String fileStatus) {
        this.fileStatus = fileStatus;
    }

    public @Nullable String getCompletePercentage() {
        return completePercentage;
    }

    public void setCompletePercentage(@Nullable String completePercentage) {
        this.completePercentage = completePercentage;
    }

    public @Nullable String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(@Nullable String errorReason) {
        this.errorReason = errorReason;
    }

    public @Nullable String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(@Nullable String returnUrl) {
        this.returnUrl = returnUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBFileStatusResponse that = (ZBFileStatusResponse) o;
        return Objects.equals(success, that.success)
                && Objects.equals(message, that.message)
                && Objects.equals(fileId, that.fileId)
                && Objects.equals(fileName, that.fileName)
                && Objects.equals(uploadDate, that.uploadDate)
                && Objects.equals(fileStatus, that.fileStatus)
                && Objects.equals(completePercentage, that.completePercentage)
                && Objects.equals(errorReason, that.errorReason)
                && Objects.equals(returnUrl, that.returnUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, fileId, fileName, uploadDate, fileStatus, completePercentage,
                errorReason, returnUrl
        );
    }

    @Override
    public String toString() {
        return "ZBFileStatusResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", fileId='" + fileId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", uploadDate=" + uploadDate +
                ", fileStatus='" + fileStatus + '\'' +
                ", completePercentage='" + completePercentage + '\'' +
                ", errorReason='" + errorReason + '\'' +
                ", returnUrl='" + returnUrl + '\'' +
                '}';
    }
}
