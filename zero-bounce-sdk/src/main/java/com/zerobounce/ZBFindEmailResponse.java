package src.main.java.com.zerobounce;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ZBFindEmailResponse {

    @NotNull
    private String email = "";

    @NotNull
    @SerializedName("email_confidence")
    private String emailConfidence = "";

    @NotNull
    private String domain = "";

    @NotNull
    @SerializedName("company_name")
    private String companyName = "";

    @NotNull
    @SerializedName("did_you_mean")
    private String didYouMean = "";

    @NotNull
    @SerializedName("failure_reason")
    private String failureReason = "";

    public @NotNull String getEmail() {
        return email;
    }

    public void setEmail(@NotNull String email) {
        this.email = email;
    }

    public @NotNull String getEmailConfidence() {
        return emailConfidence;
    }

    public void setEmailConfidence(@NotNull String emailConfidence) {
        this.emailConfidence = emailConfidence;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ZBFindEmailResponse that = (ZBFindEmailResponse) o;
        return Objects.equals(email, that.email) && Objects.equals(emailConfidence, that.emailConfidence) && Objects.equals(domain, that.domain) && Objects.equals(companyName, that.companyName) && Objects.equals(didYouMean, that.didYouMean) && Objects.equals(failureReason, that.failureReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, emailConfidence, domain, companyName, didYouMean, failureReason);
    }

    @Override
    public String toString() {
        return "ZBFindEmailResponse{" +
                "email='" + email + '\'' +
                ", emailConfidence='" + emailConfidence + '\'' +
                ", domain='" + domain + '\'' +
                ", companyName='" + companyName + '\'' +
                ", didYouMean='" + didYouMean + '\'' +
                ", failureReason='" + failureReason + '\'' +
                '}';
    }
}
