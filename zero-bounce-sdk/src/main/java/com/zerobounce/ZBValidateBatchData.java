package com.zerobounce;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The model used for constructing the body of the POST /validatebatch request.
 */
public class ZBValidateBatchData {

    @SerializedName("email_address")
    @NotNull
    String email;

    @SerializedName("ip_address")
    @Nullable
    String ip = null;

    public ZBValidateBatchData() {}

    public ZBValidateBatchData(@NotNull String email, @Nullable String ip) {
        this.email = email;
        this.ip = ip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBValidateBatchData that = (ZBValidateBatchData) o;
        return Objects.equals(email, that.email) && Objects.equals(ip, that.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, ip);
    }
}
