package net.zerobounce;

import java.util.Date;

public class ZBValidateResponse {
    //The email address you are validating.
    String address = null;

    //[valid, invalid, catch-all, unknown, spamtrap, abuse, do_not_mail]
    ZBValidateStatus status = null;

    //[antispam_system, greylisted, mail_server_temporary_error, forcible_disconnect, mail_server_did_not_respond, timeout_exceeded, failed_smtp_connection, mailbox_quota_exceeded, exception_occurred, possible_traps, role_based, global_suppression, mailbox_not_found, no_dns_entries, failed_syntax_check, possible_typo, unroutable_ip_address, leading_period_removed, does_not_accept_mail, alias_address, role_based_catch_all, disposable, toxic]
    ZBValidateSubStatus subStatus = null;

    //The portion of the email address before the "@" symbol.
    String account = null;

    //The portion of the email address after the "@" symbol.
    String domain = null;

    //Suggestive Fix for an email typo
    String didYouMean = null;

    //Age of the email domain in days or [null].
    String domainAgeDays = null;

    //[true/false] If the email comes from a free provider.
    Boolean freeEmail = false;

    //[true/false] Does the domain have an MX record.
    Boolean mxFound = false;

    //The preferred MX record of the domain
    String mxRecord = null;

    //The SMTP Provider of the email or [null] [BETA].
    String smtpProvider = null;

    //The first name of the owner of the email when available or [null].
    String firstName = null;

    //The last name of the owner of the email when available or [null].
    String lastName = null;

    //The gender of the owner of the email when available or [null].
    String gender = null;

    //The city of the IP passed in.
    String city = null;

    //The region/state of the IP passed in.
    String region = null;

    //The zipcode of the IP passed in.
    String zipCode = null;

    //The country of the IP passed in.
    String country = null;

    //The UTC time the email was validated.
    Date processedAt = null;

    String error = null;

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
                ", processedAt='" + processedAt.toString() + '\'' +
                ", error='" + error + '\'' +
                '}';
    }
}
