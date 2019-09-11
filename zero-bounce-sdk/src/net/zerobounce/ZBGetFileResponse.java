package net.zerobounce;

import com.google.gson.annotations.SerializedName;

public class ZBGetFileResponse {
    @SerializedName("localFilePath") String localFilePath = null;

    @Override
    public String toString() {
        return "ZBGetFileResponse{" +
                "localFilePath='" + localFilePath + '\'' +
                '}';
    }
}
