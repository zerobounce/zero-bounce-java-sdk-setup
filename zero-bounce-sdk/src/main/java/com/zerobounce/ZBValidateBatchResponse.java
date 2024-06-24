package com.zerobounce;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * The model used for the POST /validatebatch request.
 */
public class ZBValidateBatchResponse {

    @SerializedName("email_batch")
    @NotNull
    List<ZBValidateResponse> emailBatch;

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

    public List<ZBValidateResponse> getEmailBatch() {
        return this.emailBatch;
    }

    public List<LinkedHashMap<String, Object>> getErrors() {
        return this.errors;
    }
}
