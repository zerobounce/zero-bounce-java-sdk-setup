package com.zerobounce;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The documented fields shared by the six S3 and SFTP/FTP file jobs.
 * <p>
 * LIVE: {@code fileName} and {@code returnUrl} are required on every one of
 * them, {@code validateSFTPFile} included, although the docs list
 * {@code fileName} as optional for that one. Without either the API answers
 * "This field is required".
 */
public class ZBBulkFileJobOptions {

    @Nullable
    private String fileName = null;

    private boolean hasHeaderRow = true;

    @Nullable
    private Boolean removeDuplicates = null;

    @Nullable
    private String returnUrl = null;

    public ZBBulkFileJobOptions setFileName(@Nullable String fileName) {
        this.fileName = fileName;
        return this;
    }

    public ZBBulkFileJobOptions setHasHeaderRow(boolean hasHeaderRow) {
        this.hasHeaderRow = hasHeaderRow;
        return this;
    }

    public ZBBulkFileJobOptions setRemoveDuplicates(@Nullable Boolean removeDuplicates) {
        this.removeDuplicates = removeDuplicates;
        return this;
    }

    public ZBBulkFileJobOptions setReturnUrl(@Nullable String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }

    public @Nullable String getFileName() {
        return fileName;
    }

    public boolean isHasHeaderRow() {
        return hasHeaderRow;
    }

    public @Nullable Boolean getRemoveDuplicates() {
        return removeDuplicates;
    }

    public @Nullable String getReturnUrl() {
        return returnUrl;
    }

    /**
     * Adds the documented fields to the given form map.
     *
     * @throws IllegalArgumentException when a required field is missing or blank
     */
    void fill(@NotNull Map<String, String> fields) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty parameter: file_name");
        }
        if (returnUrl == null || returnUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty parameter: return_url");
        }
        fields.put("has_header_row", String.valueOf(hasHeaderRow));
        fields.put("file_name", fileName);
        fields.put("return_url", returnUrl);
        if (removeDuplicates != null) {
            fields.put("remove_duplicates", String.valueOf(removeDuplicates));
        }
    }
}
