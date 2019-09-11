package net.zerobounce;

import com.google.gson.annotations.SerializedName;

public class ZBGetApiUsageResponse {
    // Total number of times the API has been called
    @SerializedName("total") int total = 0;

    // Total valid email addresses returned by the API
    @SerializedName("status_valid") int statusValid = 0;

    // Total invalid email addresses returned by the API
    @SerializedName("status_invalid") int statusInvalid = 0;

    // Total catch-all email addresses returned by the API
    @SerializedName("status_catch_all") int statusCatchAll = 0;

    // Total do not mail email addresses returned by the API
    @SerializedName("status_do_not_mail") int statusDoNotMail = 0;

    // Total spamtrap email addresses returned by the API
    @SerializedName("status_spamtrap") int statusSpamtrap = 0;

    // Total unknown email addresses returned by the API
    @SerializedName("status_unknown") int statusUnknown = 0;

    // Total number of times the API has a sub status of "toxic"
    @SerializedName("sub_status_toxic") int subStatusToxic= 0;

    // Total number of times the API has a sub status of "disposable"
    @SerializedName("sub_status_disposable") int subStatusDisposable= 0;

    // Total number of times the API has a sub status of "role_based"
    @SerializedName("sub_status_role_based") int subStatusRoleBased= 0;

    // Total number of times the API has a sub status of "possible_trap"
    @SerializedName("sub_status_possible_trap") int subStatusPossibleTrap= 0;

    // Total number of times the API has a sub status of "global_suppression"
    @SerializedName("sub_status_global_suppression") int subStatusGlobalSuppression= 0;

    // Total number of times the API has a sub status of "timeout_exceeded"
    @SerializedName("sub_status_timeout_exceeded") int subStatusTimeoutExceeded= 0;

    // Total number of times the API has a sub status of "mail_server_temporary_error"
    @SerializedName("sub_status_mail_server_temporary_error") int subStatusMailServerTemporaryError= 0;

    // Total number of times the API has a sub status of "mail_server_did_not_respond"
    @SerializedName("sub_status_mail_server_did_not_respond") int subStatusMailServerDidNotResponse= 0;

    // Total number of times the API has a sub status of "greylisted"
    @SerializedName("sub_status_greylisted") int subStatusGreyListed= 0;

    // Total number of times the API has a sub status of "antispam_system"
    @SerializedName("sub_status_antispam_system") int subStatusAntiSpamSystem= 0;

    // Total number of times the API has a sub status of "does_not_accept_mail"
    @SerializedName("sub_status_does_not_accept_mail") int subStatusDoesNotAcceptMail= 0;

    // Total number of times the API has a sub status of "exception_occurred"
    @SerializedName("sub_status_exception_occurred") int subStatusExceptionOccurred= 0;

    // Total number of times the API has a sub status of "failed_syntax_check"
    @SerializedName("sub_status_failed_syntax_check") int subStatusFailedSyntaxCheck= 0;

    // Total number of times the API has a sub status of "mailbox_not_found"
    @SerializedName("sub_status_mailbox_not_found") int subStatusMailboxNotFound= 0;

    // Total number of times the API has a sub status of "unroutable_ip_address"
    @SerializedName("sub_status_unroutable_ip_address") int subStatusUnRoutableIpAddress= 0;

    // Total number of times the API has a sub status of "possible_typo"
    @SerializedName("sub_status_possible_typo") int subStatusPossibleTypo= 0;

    // Total number of times the API has a sub status of "no_dns_entries"
    @SerializedName("sub_status_no_dns_entries") int subStatusNoDnsEntries= 0;

    // Total role based catch alls the API has a sub status of "role_based_catch_all"
    @SerializedName("sub_status_role_based_catch_all") int subStatusRoleBasedCatchAll= 0;

    // Total number of times the API has a sub status of "mailbox_quota_exceeded"
    @SerializedName("sub_status_mailbox_quota_exceeded") int subStatusMailboxQuotaExceeded= 0;

    // Total forcible disconnects the API has a sub status of "forcible_disconnect"
    @SerializedName("sub_status_forcible_disconnect") int subStatusForcibleDisconnect= 0;

    // Total failed SMTP connections the API has a sub status of "failed_smtp_connection"
    @SerializedName("sub_status_failed_smtp_connection") int subStatusFailedSmtpConnection= 0;

    // Start date of query
    @SerializedName("start_date") String startDate = null;

    // End date of query
    @SerializedName("end_date") String endDate = null;

    @Override
    public String toString() {
        return "ZBGetApiUsageResponse{" +
                "total=" + total +
                ", statusValid=" + statusValid +
                ", statusInvalid=" + statusInvalid +
                ", statusCatchAll=" + statusCatchAll +
                ", statusDoNotMail=" + statusDoNotMail +
                ", statusSpamtrap=" + statusSpamtrap +
                ", statusUnknown=" + statusUnknown +
                ", subStatusToxic=" + subStatusToxic +
                ", subStatusDisposable=" + subStatusDisposable +
                ", subStatusRoleBased=" + subStatusRoleBased +
                ", subStatusPossibleTrap=" + subStatusPossibleTrap +
                ", subStatusGlobalSuppression=" + subStatusGlobalSuppression +
                ", subStatusTimeoutExceeded=" + subStatusTimeoutExceeded +
                ", subStatusMailServerTemporaryError=" + subStatusMailServerTemporaryError +
                ", subStatusMailServerDidNotResponse=" + subStatusMailServerDidNotResponse +
                ", subStatusGreyListed=" + subStatusGreyListed +
                ", subStatusAntiSpamSystem=" + subStatusAntiSpamSystem +
                ", subStatusDoesNotAcceptMail=" + subStatusDoesNotAcceptMail +
                ", subStatusExceptionOccurred=" + subStatusExceptionOccurred +
                ", subStatusFailedSyntaxCheck=" + subStatusFailedSyntaxCheck +
                ", subStatusMailboxNotFound=" + subStatusMailboxNotFound +
                ", subStatusUnRoutableIpAddress=" + subStatusUnRoutableIpAddress +
                ", subStatusPossibleTypo=" + subStatusPossibleTypo +
                ", subStatusNoDnsEntries=" + subStatusNoDnsEntries +
                ", subStatusRoleBasedCatchAll=" + subStatusRoleBasedCatchAll +
                ", subStatusMailboxQuotaExceeded=" + subStatusMailboxQuotaExceeded +
                ", subStatusForcibleDisconnect=" + subStatusForcibleDisconnect +
                ", subStatusFailedSmtpConnection=" + subStatusFailedSmtpConnection +
                ", startDate='" + startDate + '\'' +
                ", endDate='" + endDate + '\'' +
                '}';
    }
}
