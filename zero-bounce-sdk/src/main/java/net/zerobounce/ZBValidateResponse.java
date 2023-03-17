package net.zerobounce;

import com.google.gson.annotations.SerializedName;
import com.sun.istack.internal.Nullable;

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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public ZBValidateStatus getStatus() {
        return status;
    }

    public void setStatus(ZBValidateStatus status) {
        this.status = status;
    }

    public ZBValidateSubStatus getSubStatus() {
        return subStatus;
    }

    public void setSubStatus(ZBValidateSubStatus subStatus) {
        this.subStatus = subStatus;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDidYouMean() {
        return didYouMean;
    }

    public void setDidYouMean(String didYouMean) {
        this.didYouMean = didYouMean;
    }

    public String getDomainAgeDays() {
        return domainAgeDays;
    }

    public void setDomainAgeDays(String domainAgeDays) {
        this.domainAgeDays = domainAgeDays;
    }

    public Boolean getFreeEmail() {
        return freeEmail;
    }

    public void setFreeEmail(Boolean freeEmail) {
        this.freeEmail = freeEmail;
    }

    public Boolean getMxFound() {
        return mxFound;
    }

    public void setMxFound(Boolean mxFound) {
        this.mxFound = mxFound;
    }

    public String getMxRecord() {
        return mxRecord;
    }

    public void setMxRecord(String mxRecord) {
        this.mxRecord = mxRecord;
    }

    public String getSmtpProvider() {
        return smtpProvider;
    }

    public void setSmtpProvider(String smtpProvider) {
        this.smtpProvider = smtpProvider;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Date getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Date processedAt) {
        this.processedAt = processedAt;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
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
