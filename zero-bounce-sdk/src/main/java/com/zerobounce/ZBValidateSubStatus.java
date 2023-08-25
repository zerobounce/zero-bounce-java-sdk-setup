package com.zerobounce;

import com.google.gson.annotations.SerializedName;

/**
 * The model class that lists all the possible sub-statuses of the email validation result.
 */
public enum ZBValidateSubStatus {
    @SerializedName("")
    NONE,

    @SerializedName("antispam_system")
    ANTISPAM_SYSTEM,

    @SerializedName("greylisted")
    GREYLISTED,

    @SerializedName("mail_server_temporary_error")
    MAIL_SERVER_TEMPORARY_ERROR,

    @SerializedName("forcible_disconnect")
    FORCIBLE_DISCONNECT,

    @SerializedName("mail_server_did_not_respond")
    MAIL_SERVER_DID_NOT_RESPOND,

    @SerializedName("timeout_exceeded")
    TIMEOUT_EXCEEDED,

    @SerializedName("failed_smtp_connection")
    FAILED_SMTP_CONNECTION,

    @SerializedName("mailbox_quota_exceeded")
    MAILBOX_QUOTA_EXCEEDED,

    @SerializedName("exception_occurred")
    EXCEPTION_OCCURRED,

    @SerializedName("possible_traps")
    POSSIBLE_TRAPS,

    @SerializedName("role_based")
    ROLE_BASED,

    @SerializedName("global_suppression")
    GLOBAL_SUPPRESSION,

    @SerializedName("mailbox_not_found")
    MAILBOX_NOT_FOUND,

    @SerializedName("no_dns_entries")
    NO_DNS_ENTRIES,

    @SerializedName("failed_syntax_check")
    FAILED_SYNTAX_CHECK,

    @SerializedName("possible_typo")
    POSSIBLE_TYPO,

    @SerializedName("unroutable_ip_address")
    UNROUTABLE_IP_ADDRESS,

    @SerializedName("leading_period_removed")
    LEADING_PERIOD_REMOVED,

    @SerializedName("does_not_accept_mail")
    DOES_NOT_ACCPET_MAIL,

    @SerializedName("alias_address")
    ALIAS_ADDRESS,

    @SerializedName("role_based_catch_all")
    ROLE_BASED_CATCH_ALL,

    @SerializedName("disposable")
    DISPOSABLE,

    @SerializedName("toxic")
    TOXIC
}