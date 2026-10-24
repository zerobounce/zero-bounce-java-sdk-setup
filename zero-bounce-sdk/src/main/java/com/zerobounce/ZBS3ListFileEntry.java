package com.zerobounce;

import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One file entry from GET /listS3files.
 *
 * The public docs write the keys capitalised ("Name", "Size", "LastModified"),
 * while the other ZeroBounce SDKs read them lower-cased. Rather than pick one
 * and silently decode nulls when the API answers the other, each field accepts
 * both spellings through Gson's {@code alternate}.
 */
public class ZBS3ListFileEntry {

    @SerializedName(value = "name", alternate = {"Name"})
    @Nullable
    private String name = null;

    @SerializedName(value = "size", alternate = {"Size"})
    private long size = 0;

    @SerializedName(value = "last_modified", alternate = {"LastModified", "lastModified"})
    @Nullable
    private String lastModified = null;

    public @Nullable String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public @Nullable String getLastModified() {
        return lastModified;
    }

    public void setLastModified(@Nullable String lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBS3ListFileEntry that = (ZBS3ListFileEntry) o;
        return size == that.size
                && Objects.equals(name, that.name)
                && Objects.equals(lastModified, that.lastModified);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, size, lastModified);
    }

    @Override
    public String toString() {
        return "ZBS3ListFileEntry{name='" + name + "', size=" + size + ", lastModified='" + lastModified + "'}";
    }
}
