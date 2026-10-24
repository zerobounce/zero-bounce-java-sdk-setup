package com.zerobounce;

import com.google.gson.annotations.SerializedName;

/**
 * One file entry from GET /listS3files.
 */
public class ZBS3ListFileEntry {

    private String name;
    private int size;

    @SerializedName("last_modified")
    private String lastModified;

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public String getLastModified() {
        return lastModified;
    }
}
