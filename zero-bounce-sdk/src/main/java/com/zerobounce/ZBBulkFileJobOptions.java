package com.zerobounce;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The documented fields shared by the six S3 and SFTP/FTP file jobs.
 * <p>
 * {@code fileName} is required by every job except
 * {@link ZeroBounceSDK#validateSFTPFile}, where the docs list it as optional:
 * leaving it null there uses the input bucket configured in your ZeroBounce
 * settings.
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
     * @param fileNameRequired false only for validateSFTPfile
     * @throws IllegalArgumentException when a required field is missing or blank
     */
    void fill(@NotNull Map<String, String> fields, boolean fileNameRequired) {
        boolean blank = fileName != null && fileName.trim().isEmpty();
        if ((fileName == null && fileNameRequired) || blank) {
            throw new IllegalArgumentException("Empty parameter: file_name");
        }
        fields.put("has_header_row", String.valueOf(hasHeaderRow));
        if (fileName != null) {
            fields.put("file_name", fileName);
        }
        if (removeDuplicates != null) {
            fields.put("remove_duplicates", String.valueOf(removeDuplicates));
        }
        if (returnUrl != null) {
            fields.put("return_url", returnUrl);
        }
    }
}
