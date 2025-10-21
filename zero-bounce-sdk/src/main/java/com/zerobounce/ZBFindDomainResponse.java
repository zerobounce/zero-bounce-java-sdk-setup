package src.main.java.com.zerobounce;

import com.google.gson.annotations.SerializedName;
import com.zerobounce.DomainFormat;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ZBFindDomainResponse {

    @NotNull
    private String email = "";

    @NotNull
    private String domain = "";

    @NotNull
    @SerializedName("company_name")
    private String companyName = "";

    @NotNull
    private String format = "";

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

    public @NotNull String getEmail() {
        return email;
    }

    public void setEmail(@NotNull String email) {
        this.email = email;
    }

    public @NotNull String getDomain() {
        return domain;
    }

    public void setDomain(@NotNull String domain) {
        this.domain = domain;
    }

    public @NotNull String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(@NotNull String companyName) {
        this.companyName = companyName;
    }

    public @NotNull String getFormat() {
        return format;
    }

    public void setFormat(@NotNull String format) {
        this.format = format;
    }

    public @NotNull String getConfidence() {
        return confidence;
    }

    public void setConfidence(@NotNull String confidence) {
        this.confidence = confidence;
    }

    public @NotNull String getDidYouMean() {
        return didYouMean;
    }

    public void setDidYouMean(@NotNull String didYouMean) {
        this.didYouMean = didYouMean;
    }

    public @NotNull String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(@NotNull String failureReason) {
        this.failureReason = failureReason;
    }

    public @NotNull List<DomainFormat> getOtherDomainFormats() {
        return otherDomainFormats;
    }

    public void setOtherDomainFormats(@NotNull List<DomainFormat> otherDomainFormats) {
        this.otherDomainFormats = otherDomainFormats;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ZBFindDomainResponse that = (ZBFindDomainResponse) o;
        return Objects.equals(email, that.email) && Objects.equals(domain, that.domain) && Objects.equals(companyName, that.companyName) && Objects.equals(format, that.format) && Objects.equals(confidence, that.confidence) && Objects.equals(didYouMean, that.didYouMean) && Objects.equals(failureReason, that.failureReason) && Objects.equals(otherDomainFormats, that.otherDomainFormats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, domain, companyName, format, confidence, didYouMean, failureReason, otherDomainFormats);
    }

    @Override
    public String toString() {
        return "ZBFindDomainResponse{" +
                "email='" + email + '\'' +
                ", domain='" + domain + '\'' +
                ", companyName='" + companyName + '\'' +
                ", format='" + format + '\'' +
                ", confidence='" + confidence + '\'' +
                ", didYouMean='" + didYouMean + '\'' +
                ", failureReason='" + failureReason + '\'' +
                ", otherDomainFormats=" + otherDomainFormats +
                '}';
    }
}
