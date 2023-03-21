package net.zerobounce;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The model used for the GET /getapiusage request.
 */
public class ZBGetApiUsageResponse {

    // Total number of times the API has been called
    private int total = 0;

    // Total valid email addresses returned by the API
    @SerializedName("status_valid")
    private int statusValid = 0;

    // Total invalid email addresses returned by the API
    @SerializedName("status_invalid")
    private int statusInvalid = 0;

    // Total catch-all email addresses returned by the API
    @SerializedName("status_catch_all")
    private int statusCatchAll = 0;

    // Total do not mail email addresses returned by the API
    @SerializedName("status_do_not_mail")
    private int statusDoNotMail = 0;

    // Total spamtrap email addresses returned by the API
    @SerializedName("status_spamtrap")
    private int statusSpamtrap = 0;

    // Total unknown email addresses returned by the API
    @SerializedName("status_unknown")
    private int statusUnknown = 0;

    // Total number of times the API has a sub status of "toxic"
    @SerializedName("sub_status_toxic")
    private int subStatusToxic = 0;

    // Total number of times the API has a sub status of "disposable"
    @SerializedName("sub_status_disposable")
    private int subStatusDisposable = 0;

    // Total number of times the API has a sub status of "role_based"
    @SerializedName("sub_status_role_based")
    private int subStatusRoleBased = 0;

    // Total number of times the API has a sub status of "possible_trap"
    @SerializedName("sub_status_possible_trap")
    private int subStatusPossibleTrap = 0;

    // Total number of times the API has a sub status of "global_suppression"
    @SerializedName("sub_status_global_suppression")
    private int subStatusGlobalSuppression = 0;

    // Total number of times the API has a sub status of "timeout_exceeded"
    @SerializedName("sub_status_timeout_exceeded")
    private int subStatusTimeoutExceeded = 0;

    // Total number of times the API has a sub status of "mail_server_temporary_error"
    @SerializedName("sub_status_mail_server_temporary_error")
    private int subStatusMailServerTemporaryError = 0;

    // Total number of times the API has a sub status of "mail_server_did_not_respond"
    @SerializedName("sub_status_mail_server_did_not_respond")
    private int subStatusMailServerDidNotResponse = 0;

    // Total number of times the API has a sub status of "greylisted"
    @SerializedName("sub_status_greylisted")
    private int subStatusGreyListed = 0;

    // Total number of times the API has a sub status of "antispam_system"
    @SerializedName("sub_status_antispam_system")
    private int subStatusAntiSpamSystem = 0;

    // Total number of times the API has a sub status of "does_not_accept_mail"
    @SerializedName("sub_status_does_not_accept_mail")
    private int subStatusDoesNotAcceptMail = 0;

    // Total number of times the API has a sub status of "exception_occurred"
    @SerializedName("sub_status_exception_occurred")
    private int subStatusExceptionOccurred = 0;

    // Total number of times the API has a sub status of "failed_syntax_check"
    @SerializedName("sub_status_failed_syntax_check")
    private int subStatusFailedSyntaxCheck = 0;

    // Total number of times the API has a sub status of "mailbox_not_found"
    @SerializedName("sub_status_mailbox_not_found")
    private int subStatusMailboxNotFound = 0;

    // Total number of times the API has a sub status of "unroutable_ip_address"
    @SerializedName("sub_status_unroutable_ip_address")
    private int subStatusUnRoutableIpAddress = 0;

    // Total number of times the API has a sub status of "possible_typo"
    @SerializedName("sub_status_possible_typo")
    private int subStatusPossibleTypo = 0;

    // Total number of times the API has a sub status of "no_dns_entries"
    @SerializedName("sub_status_no_dns_entries")
    private int subStatusNoDnsEntries = 0;

    // Total role based catch alls the API has a sub status of "role_based_catch_all"
    @SerializedName("sub_status_role_based_catch_all")
    private int subStatusRoleBasedCatchAll = 0;

    // Total number of times the API has a sub status of "mailbox_quota_exceeded"
    @SerializedName("sub_status_mailbox_quota_exceeded")
    private int subStatusMailboxQuotaExceeded = 0;

    // Total forcible disconnects the API has a sub status of "forcible_disconnect"
    @SerializedName("sub_status_forcible_disconnect")
    private int subStatusForcibleDisconnect = 0;

    // Total failed SMTP connections the API has a sub status of "failed_smtp_connection"
    @SerializedName("sub_status_failed_smtp_connection")
    private int subStatusFailedSmtpConnection = 0;

    // Start date of query
    @SerializedName("start_date")
    @Nullable
    private String startDate = null;

    // End date of query
    @SerializedName("end_date")
    @Nullable
    private String endDate = null;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getStatusValid() {
        return statusValid;
    }

    public void setStatusValid(int statusValid) {
        this.statusValid = statusValid;
    }

    public int getStatusInvalid() {
        return statusInvalid;
    }

    public void setStatusInvalid(int statusInvalid) {
        this.statusInvalid = statusInvalid;
    }

    public int getStatusCatchAll() {
        return statusCatchAll;
    }

    public void setStatusCatchAll(int statusCatchAll) {
        this.statusCatchAll = statusCatchAll;
    }

    public int getStatusDoNotMail() {
        return statusDoNotMail;
    }

    public void setStatusDoNotMail(int statusDoNotMail) {
        this.statusDoNotMail = statusDoNotMail;
    }

    public int getStatusSpamtrap() {
        return statusSpamtrap;
    }

    public void setStatusSpamtrap(int statusSpamtrap) {
        this.statusSpamtrap = statusSpamtrap;
    }

    public int getStatusUnknown() {
        return statusUnknown;
    }

    public void setStatusUnknown(int statusUnknown) {
        this.statusUnknown = statusUnknown;
    }

    public int getSubStatusToxic() {
        return subStatusToxic;
    }

    public void setSubStatusToxic(int subStatusToxic) {
        this.subStatusToxic = subStatusToxic;
    }

    public int getSubStatusDisposable() {
        return subStatusDisposable;
    }

    public void setSubStatusDisposable(int subStatusDisposable) {
        this.subStatusDisposable = subStatusDisposable;
    }

    public int getSubStatusRoleBased() {
        return subStatusRoleBased;
    }

    public void setSubStatusRoleBased(int subStatusRoleBased) {
        this.subStatusRoleBased = subStatusRoleBased;
    }

    public int getSubStatusPossibleTrap() {
        return subStatusPossibleTrap;
    }

    public void setSubStatusPossibleTrap(int subStatusPossibleTrap) {
        this.subStatusPossibleTrap = subStatusPossibleTrap;
    }

    public int getSubStatusGlobalSuppression() {
        return subStatusGlobalSuppression;
    }

    public void setSubStatusGlobalSuppression(int subStatusGlobalSuppression) {
        this.subStatusGlobalSuppression = subStatusGlobalSuppression;
    }

    public int getSubStatusTimeoutExceeded() {
        return subStatusTimeoutExceeded;
    }

    public void setSubStatusTimeoutExceeded(int subStatusTimeoutExceeded) {
        this.subStatusTimeoutExceeded = subStatusTimeoutExceeded;
    }

    public int getSubStatusMailServerTemporaryError() {
        return subStatusMailServerTemporaryError;
    }

    public void setSubStatusMailServerTemporaryError(int subStatusMailServerTemporaryError) {
        this.subStatusMailServerTemporaryError = subStatusMailServerTemporaryError;
    }

    public int getSubStatusMailServerDidNotResponse() {
        return subStatusMailServerDidNotResponse;
    }

    public void setSubStatusMailServerDidNotResponse(int subStatusMailServerDidNotResponse) {
        this.subStatusMailServerDidNotResponse = subStatusMailServerDidNotResponse;
    }

    public int getSubStatusGreyListed() {
        return subStatusGreyListed;
    }

    public void setSubStatusGreyListed(int subStatusGreyListed) {
        this.subStatusGreyListed = subStatusGreyListed;
    }

    public int getSubStatusAntiSpamSystem() {
        return subStatusAntiSpamSystem;
    }

    public void setSubStatusAntiSpamSystem(int subStatusAntiSpamSystem) {
        this.subStatusAntiSpamSystem = subStatusAntiSpamSystem;
    }

    public int getSubStatusDoesNotAcceptMail() {
        return subStatusDoesNotAcceptMail;
    }

    public void setSubStatusDoesNotAcceptMail(int subStatusDoesNotAcceptMail) {
        this.subStatusDoesNotAcceptMail = subStatusDoesNotAcceptMail;
    }

    public int getSubStatusExceptionOccurred() {
        return subStatusExceptionOccurred;
    }

    public void setSubStatusExceptionOccurred(int subStatusExceptionOccurred) {
        this.subStatusExceptionOccurred = subStatusExceptionOccurred;
    }

    public int getSubStatusFailedSyntaxCheck() {
        return subStatusFailedSyntaxCheck;
    }

    public void setSubStatusFailedSyntaxCheck(int subStatusFailedSyntaxCheck) {
        this.subStatusFailedSyntaxCheck = subStatusFailedSyntaxCheck;
    }

    public int getSubStatusMailboxNotFound() {
        return subStatusMailboxNotFound;
    }

    public void setSubStatusMailboxNotFound(int subStatusMailboxNotFound) {
        this.subStatusMailboxNotFound = subStatusMailboxNotFound;
    }

    public int getSubStatusUnRoutableIpAddress() {
        return subStatusUnRoutableIpAddress;
    }

    public void setSubStatusUnRoutableIpAddress(int subStatusUnRoutableIpAddress) {
        this.subStatusUnRoutableIpAddress = subStatusUnRoutableIpAddress;
    }

    public int getSubStatusPossibleTypo() {
        return subStatusPossibleTypo;
    }

    public void setSubStatusPossibleTypo(int subStatusPossibleTypo) {
        this.subStatusPossibleTypo = subStatusPossibleTypo;
    }

    public int getSubStatusNoDnsEntries() {
        return subStatusNoDnsEntries;
    }

    public void setSubStatusNoDnsEntries(int subStatusNoDnsEntries) {
        this.subStatusNoDnsEntries = subStatusNoDnsEntries;
    }

    public int getSubStatusRoleBasedCatchAll() {
        return subStatusRoleBasedCatchAll;
    }

    public void setSubStatusRoleBasedCatchAll(int subStatusRoleBasedCatchAll) {
        this.subStatusRoleBasedCatchAll = subStatusRoleBasedCatchAll;
    }

    public int getSubStatusMailboxQuotaExceeded() {
        return subStatusMailboxQuotaExceeded;
    }

    public void setSubStatusMailboxQuotaExceeded(int subStatusMailboxQuotaExceeded) {
        this.subStatusMailboxQuotaExceeded = subStatusMailboxQuotaExceeded;
    }

    public int getSubStatusForcibleDisconnect() {
        return subStatusForcibleDisconnect;
    }

    public void setSubStatusForcibleDisconnect(int subStatusForcibleDisconnect) {
        this.subStatusForcibleDisconnect = subStatusForcibleDisconnect;
    }

    public int getSubStatusFailedSmtpConnection() {
        return subStatusFailedSmtpConnection;
    }

    public void setSubStatusFailedSmtpConnection(int subStatusFailedSmtpConnection) {
        this.subStatusFailedSmtpConnection = subStatusFailedSmtpConnection;
    }

    public @Nullable String getStartDate() {
        return startDate;
    }

    public void setStartDate(@Nullable String startDate) {
        this.startDate = startDate;
    }

    public @Nullable String getEndDate() {
        return endDate;
    }

    public void setEndDate(@Nullable String endDate) {
        this.endDate = endDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBGetApiUsageResponse that = (ZBGetApiUsageResponse) o;
        return total == that.total
                && statusValid == that.statusValid
                && statusInvalid == that.statusInvalid
                && statusCatchAll == that.statusCatchAll
                && statusDoNotMail == that.statusDoNotMail
                && statusSpamtrap == that.statusSpamtrap
                && statusUnknown == that.statusUnknown
                && subStatusToxic == that.subStatusToxic
                && subStatusDisposable == that.subStatusDisposable
                && subStatusRoleBased == that.subStatusRoleBased
                && subStatusPossibleTrap == that.subStatusPossibleTrap
                && subStatusGlobalSuppression == that.subStatusGlobalSuppression
                && subStatusTimeoutExceeded == that.subStatusTimeoutExceeded
                && subStatusMailServerTemporaryError == that.subStatusMailServerTemporaryError
                && subStatusMailServerDidNotResponse == that.subStatusMailServerDidNotResponse
                && subStatusGreyListed == that.subStatusGreyListed
                && subStatusAntiSpamSystem == that.subStatusAntiSpamSystem
                && subStatusDoesNotAcceptMail == that.subStatusDoesNotAcceptMail
                && subStatusExceptionOccurred == that.subStatusExceptionOccurred
                && subStatusFailedSyntaxCheck == that.subStatusFailedSyntaxCheck
                && subStatusMailboxNotFound == that.subStatusMailboxNotFound
                && subStatusUnRoutableIpAddress == that.subStatusUnRoutableIpAddress
                && subStatusPossibleTypo == that.subStatusPossibleTypo
                && subStatusNoDnsEntries == that.subStatusNoDnsEntries
                && subStatusRoleBasedCatchAll == that.subStatusRoleBasedCatchAll
                && subStatusMailboxQuotaExceeded == that.subStatusMailboxQuotaExceeded
                && subStatusForcibleDisconnect == that.subStatusForcibleDisconnect
                && subStatusFailedSmtpConnection == that.subStatusFailedSmtpConnection
                && Objects.equals(startDate, that.startDate)
                && Objects.equals(endDate, that.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(total, statusValid, statusInvalid, statusCatchAll, statusDoNotMail, statusSpamtrap,
                statusUnknown, subStatusToxic, subStatusDisposable, subStatusRoleBased, subStatusPossibleTrap,
                subStatusGlobalSuppression, subStatusTimeoutExceeded, subStatusMailServerTemporaryError,
                subStatusMailServerDidNotResponse, subStatusGreyListed, subStatusAntiSpamSystem,
                subStatusDoesNotAcceptMail, subStatusExceptionOccurred, subStatusFailedSyntaxCheck,
                subStatusMailboxNotFound, subStatusUnRoutableIpAddress, subStatusPossibleTypo, subStatusNoDnsEntries,
                subStatusRoleBasedCatchAll, subStatusMailboxQuotaExceeded, subStatusForcibleDisconnect,
                subStatusFailedSmtpConnection, startDate, endDate
        );
    }

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
