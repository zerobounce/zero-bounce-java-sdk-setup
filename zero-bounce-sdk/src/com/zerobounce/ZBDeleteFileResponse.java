package com.zerobounce;

import com.google.gson.annotations.SerializedName;

public class ZBDeleteFileResponse {
    @SerializedName("success") Boolean success = false;
    @SerializedName("message") String message = null;
    @SerializedName("file_name") String fileName = null;
    @SerializedName("file_id") String fileId = null;

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
