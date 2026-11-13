package com.zerobounce;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * The model used for the POST /validatebatch request.
 */
public class ZBValidateBatchResponse {

    /** Starts as an empty batch, so an answer without {@code email_batch} reads as one (standard.md section 3). */
    @SerializedName("email_batch")
    @NotNull
    @com.google.gson.annotations.JsonAdapter(value = StrictTypes.NullAsNoRows.class, nullSafe = false)
    List<ZBValidateResponse> emailBatch = new ArrayList<>();

    @Nullable List<LinkedHashMap<String, Object>> errors;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBValidateBatchResponse that = (ZBValidateBatchResponse) o;
        return Objects.equals(emailBatch, that.emailBatch) && Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(emailBatch, errors);
    }

    @Override
    public String toString() {
        return "ZBValidateBatchResponse{" +
                "emailBatch=" + emailBatch +
                ", errors=" + errors +
                '}';
    }

    public List<ZBValidateResponse> getEmailBatch() {
        return this.emailBatch;
    }

    public List<LinkedHashMap<String, Object>> getErrors() {
        return this.errors;
    }
}
