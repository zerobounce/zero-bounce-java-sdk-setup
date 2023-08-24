package com.zerobounce;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The model used for the GET /guessformat request.
 */
public class ZBEmailFinderResponse {

    @NotNull
    private String email = "";

    @NotNull
    private String domain = "";

    @NotNull
    private String format = "";

    @NotNull
    private String status = "";

    @NotNull
    @SerializedName("sub_status")
    private String subStatus = "";

    @NotNull
    private String confidence = "";

    @NotNull
    @SerializedName("did_you_mean")
    private String didYouMean = "";

    @NotNull
    @SerializedName("failure_reason")
    private String failureReason = "";

    @NotNull
    @SerializedName("other_domain_formats")
    private List<DomainFormat> otherDomainFormats = Collections.emptyList();

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubStatus() {
        return subStatus;
    }

    public void setSubStatus(String subStatus) {
        this.subStatus = subStatus;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getDidYouMean() {
        return didYouMean;
    }

    public void setDidYouMean(String didYouMean) {
        this.didYouMean = didYouMean;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public List<DomainFormat> getOtherDomainFormats() {
        return otherDomainFormats;
    }

    public void setOtherDomainFormats(List<DomainFormat> otherDomainFormats) {
        this.otherDomainFormats = otherDomainFormats;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBEmailFinderResponse that = (ZBEmailFinderResponse) o;
        return Objects.equals(email, that.email) && Objects.equals(domain, that.domain) && Objects.equals(format, that.format) && Objects.equals(status, that.status) && Objects.equals(subStatus, that.subStatus) && Objects.equals(confidence, that.confidence) && Objects.equals(didYouMean, that.didYouMean) && Objects.equals(failureReason, that.failureReason) && Objects.equals(otherDomainFormats, that.otherDomainFormats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, domain, format, status, subStatus, confidence, didYouMean, failureReason, otherDomainFormats);
    }

    @Override
    public String toString() {
        return "ZBEmailFinderResponse{" +
                "email='" + email + '\'' +
                ", domain='" + domain + '\'' +
                ", format='" + format + '\'' +
                ", status='" + status + '\'' +
                ", subStatus='" + subStatus + '\'' +
                ", confidence='" + confidence + '\'' +
                ", didYouMean='" + didYouMean + '\'' +
                ", failureReason='" + failureReason + '\'' +
                ", otherDomainFormats=" + otherDomainFormats +
                '}';
    }
}
