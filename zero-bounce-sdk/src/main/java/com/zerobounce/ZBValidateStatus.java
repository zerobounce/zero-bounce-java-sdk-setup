package com.zerobounce;

import com.google.gson.annotations.SerializedName;

/**
 * The model class that lists all the possible statuses of the email validation result.
 */
public enum ZBValidateStatus {
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