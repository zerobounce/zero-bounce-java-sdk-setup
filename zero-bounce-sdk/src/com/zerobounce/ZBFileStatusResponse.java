package com.zerobounce;

import com.google.gson.annotations.SerializedName;

public class ZBFileStatusResponse {
    Boolean success = false;
    String message = null;
    @SerializedName("file_status")
    String fileStatus = null;

    @Override
    public String toString() {
        return "ZBFileStatusResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", fileStatus='" + fileStatus + '\'' +
                '}';
    }
}
