package com.zerobounce;

import com.google.gson.annotations.SerializedName;

enum ZBValidateStatus {
    @SerializedName("valid")
    VALID,
    @SerializedName("invalid")
    INVALID,
    @SerializedName("catch-all")
    CATCH_ALL,
    @SerializedName("unknown")
    UNKNOWN,
    @SerializedName("spamtrap")
    SPAMTRAP,
    @SerializedName("abuse")
    ABUSE,
    @SerializedName("do_not_mail")
    DO_NOT_MAIL
}