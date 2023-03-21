package net.zerobounce;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

/**
 * The model used for the GET /validate request.
 */
public class ZBValidateResponse {

    // The email address you are validating.
    @Nullable
    private String address = null;

    // [valid, invalid, catch-all, unknown, spamtrap, abuse, do_not_mail]
    @Nullable
    private ZBValidateStatus status = null;

    // [antispam_system, greylisted, mail_server_temporary_error, forcible_disconnect, mail_server_did_not_respond, timeout_exceeded, failed_smtp_connection, mailbox_quota_exceeded, exception_occurred, possible_traps, role_based, global_suppression, mailbox_not_found, no_dns_entries, failed_syntax_check, possible_typo, unroutable_ip_address, leading_period_removed, does_not_accept_mail, alias_address, role_based_catch_all, disposable, toxic]
    @SerializedName("sub_status")
    @Nullable
    private ZBValidateSubStatus subStatus = null;

    // The portion of the email address before the "@" symbol.
    @Nullable
    private String account = null;

    // The portion of the email address after the "@" symbol.
    @Nullable
    private String domain = null;

    // Suggestive Fix for an email typo
    @SerializedName("did_you_mean")
    @Nullable
    private String didYouMean = null;

    // Age of the email domain in days or [null].
    @SerializedName("domain_age_days")
    @Nullable
    private String domainAgeDays = null;

    //@Nullable [true/false] If the email comes from a free provider.
    @SerializedName("free_email")
    @Nullable
    private Boolean freeEmail = false;

    // [true/false] Does the domain have an MX record.
    @SerializedName("mx_found")
    @Nullable
    private Boolean mxFound = false;

    // The preferred MX record of the domain
    @SerializedName("mx_record")
    @Nullable
    private String mxRecord = null;

    // The SMTP Provider of the email or [null] [BETA].
    @SerializedName("smtp_provider")
    @Nullable
    private String smtpProvider = null;

    // The first name of the owner of the email when available or [null].
    @SerializedName("firstname")
    @Nullable
    private String firstName = null;

    // The last name of the owner of the email when available or [null].
    @SerializedName("lastname")
    @Nullable
    private String lastName = null;

    // The gender of the owner of the email when available or [null].
    @Nullable
    private String gender = null;

    // The city of the IP passed in.
    @Nullable
    private String city = null;

    // The region/state of the IP passed in.
    @Nullable
    private String region = null;

    // The zipcode of the IP passed in.
    @SerializedName("zipcode")
    @Nullable
    private String zipCode = null;

    // The country of the IP passed in.
    @Nullable
    private String country = null;

    // The UTC time the email was validated.
    @SerializedName("processed_at")
    @Nullable
    private Date processedAt = null;

    @Nullable
    private String error = null;

    public @Nullable String getAddress() {
        return address;
    }

    public void setAddress(@Nullable String address) {
        this.address = address;
    }

    public @Nullable ZBValidateStatus getStatus() {
        return status;
    }

    public void setStatus(@Nullable ZBValidateStatus status) {
        this.status = status;
    }

    public @Nullable ZBValidateSubStatus getSubStatus() {
        return subStatus;
    }

    public void setSubStatus(@Nullable ZBValidateSubStatus subStatus) {
        this.subStatus = subStatus;
    }

    public @Nullable String getAccount() {
        return account;
    }

    public void setAccount(@Nullable String account) {
        this.account = account;
    }

    public @Nullable String getDomain() {
        return domain;
    }

    public void setDomain(@Nullable String domain) {
        this.domain = domain;
    }

    public @Nullable String getDidYouMean() {
        return didYouMean;
    }

    public void setDidYouMean(@Nullable String didYouMean) {
        this.didYouMean = didYouMean;
    }

    public @Nullable String getDomainAgeDays() {
        return domainAgeDays;
    }

    public void setDomainAgeDays(@Nullable String domainAgeDays) {
        this.domainAgeDays = domainAgeDays;
    }

    public @Nullable Boolean getFreeEmail() {
        return freeEmail;
    }

    public void setFreeEmail(@Nullable Boolean freeEmail) {
        this.freeEmail = freeEmail;
    }

    public @Nullable Boolean getMxFound() {
        return mxFound;
    }

    public void setMxFound(@Nullable Boolean mxFound) {
        this.mxFound = mxFound;
    }

    public @Nullable String getMxRecord() {
        return mxRecord;
    }

    public void setMxRecord(@Nullable String mxRecord) {
        this.mxRecord = mxRecord;
    }

    public @Nullable String getSmtpProvider() {
        return smtpProvider;
    }

    public void setSmtpProvider(@Nullable String smtpProvider) {
        this.smtpProvider = smtpProvider;
    }

    public @Nullable String getFirstName() {
        return firstName;
    }

    public void setFirstName(@Nullable String firstName) {
        this.firstName = firstName;
    }

    public @Nullable String getLastName() {
        return lastName;
    }

    public void setLastName(@Nullable String lastName) {
        this.lastName = lastName;
    }

    public @Nullable String getGender() {
        return gender;
    }

    public void setGender(@Nullable String gender) {
        this.gender = gender;
    }

    public @Nullable String getCity() {
        return city;
    }

    public void setCity(@Nullable String city) {
        this.city = city;
    }

    public @Nullable String getRegion() {
        return region;
    }

    public void setRegion(@Nullable String region) {
        this.region = region;
    }

    public @Nullable String getZipCode() {
        return zipCode;
    }

    public void setZipCode(@Nullable String zipCode) {
        this.zipCode = zipCode;
    }

    public @Nullable String getCountry() {
        return country;
    }

    public void setCountry(@Nullable String country) {
        this.country = country;
    }

    public @Nullable Date getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(@Nullable Date processedAt) {
        this.processedAt = processedAt;
    }

    public @Nullable String getError() {
        return error;
    }

    public void setError(@Nullable String error) {
        this.error = error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBValidateResponse that = (ZBValidateResponse) o;
        return Objects.equals(address, that.address)
                && status == that.status
                && subStatus == that.subStatus
                && Objects.equals(account, that.account)
                && Objects.equals(domain, that.domain)
                && Objects.equals(didYouMean, that.didYouMean)
                && Objects.equals(domainAgeDays, that.domainAgeDays)
                && Objects.equals(freeEmail, that.freeEmail)
                && Objects.equals(mxFound, that.mxFound)
                && Objects.equals(mxRecord, that.mxRecord)
                && Objects.equals(smtpProvider, that.smtpProvider)
                && Objects.equals(firstName, that.firstName)
                && Objects.equals(lastName, that.lastName)
                && Objects.equals(gender, that.gender)
                && Objects.equals(city, that.city)
                && Objects.equals(region, that.region)
                && Objects.equals(zipCode, that.zipCode)
                && Objects.equals(country, that.country)
                && Objects.equals(processedAt, that.processedAt)
                && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, status, subStatus, account, domain, didYouMean, domainAgeDays, freeEmail,
                mxFound, mxRecord, smtpProvider, firstName, lastName, gender, city, region, zipCode, country,
                processedAt, error
        );
    }

    @Override
    public String toString() {
        return "ZBValidateResponse{" +
                "address='" + address + '\'' +
                ", account='" + account + '\'' +
                ", status=" + status +
                ", subStatus=" + subStatus +
                ", domain='" + domain + '\'' +
                ", didYouMean='" + didYouMean + '\'' +
                ", domainAgeDays='" + domainAgeDays + '\'' +
                ", freeEmail=" + freeEmail +
                ", mxFound=" + mxFound +
                ", mxRecord='" + mxRecord + '\'' +
                ", smtpProvider='" + smtpProvider + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", gender='" + gender + '\'' +
                ", city='" + city + '\'' +
                ", region='" + region + '\'' +
                ", zipCode='" + zipCode + '\'' +
                ", country='" + country + '\'' +
                ", processedAt='" + processedAt + '\'' +
                ", error='" + error + '\'' +
                '}';
    }
}
