package com.zerobounce;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The documented optional parameters of single validation
 * (<a href="https://www.zerobounce.net/docs/email-validation-api-quickstart/">docs</a>).
 * <p>
 * A field left null is not sent. Booleans go on the wire as the strings
 * "true"/"false", which is what the API expects on both the GET and the form POST.
 */
public class ZBValidateOptions {

    /** Timeout in seconds. The API accepts 3 to 60. */
    @Nullable
    private Integer timeout = null;

    /** Asks for the subscriber activity data block. */
    @Nullable
    private Boolean activityData = null;

    /** Asks for the extended verification. */
    @Nullable
    private Boolean verifyPlus = null;

    /** Asks for domain_website_exists and domain_registrant_company_name. */
    @Nullable
    private Boolean domainInfo = null;

    /** Retries a greylisted address in the background. */
    @Nullable
    private Boolean greylistProcessing = null;

    /**
     * Where the greylist retry posts its result. It needs
     * {@link #greylistProcessing} set to true and is rejected without it.
     */
    @Nullable
    private String callbackUrl = null;

    /** Sends a form-encoded POST /v2/validate instead of a GET. */
    private boolean usePost = false;

    public ZBValidateOptions setTimeout(@Nullable Integer timeout) {
        this.timeout = timeout;
        return this;
    }

    public ZBValidateOptions setActivityData(@Nullable Boolean activityData) {
        this.activityData = activityData;
        return this;
    }

    public ZBValidateOptions setVerifyPlus(@Nullable Boolean verifyPlus) {
        this.verifyPlus = verifyPlus;
        return this;
    }

    public ZBValidateOptions setDomainInfo(@Nullable Boolean domainInfo) {
        this.domainInfo = domainInfo;
        return this;
    }

    public ZBValidateOptions setGreylistProcessing(@Nullable Boolean greylistProcessing) {
        this.greylistProcessing = greylistProcessing;
        return this;
    }

    public ZBValidateOptions setCallbackUrl(@Nullable String callbackUrl) {
        this.callbackUrl = callbackUrl;
        return this;
    }

    public ZBValidateOptions setUsePost(boolean usePost) {
        this.usePost = usePost;
        return this;
    }

    public @Nullable Integer getTimeout() {
        return timeout;
    }

    public @Nullable Boolean getActivityData() {
        return activityData;
    }

    public @Nullable Boolean getVerifyPlus() {
        return verifyPlus;
    }

    public @Nullable Boolean getDomainInfo() {
        return domainInfo;
    }

    public @Nullable Boolean getGreylistProcessing() {
        return greylistProcessing;
    }

    public @Nullable String getCallbackUrl() {
        return callbackUrl;
    }

    public boolean isUsePost() {
        return usePost;
    }

    /**
     * Adds the options that are set to the given parameter map.
     *
     * @throws IllegalArgumentException if the options cannot produce a valid request
     */
    void fill(@NotNull Map<String, String> parameters) {
        if (timeout != null) {
            if (timeout < 3 || timeout > 60) {
                throw new IllegalArgumentException("timeout must be between 3 and 60 seconds");
            }
            parameters.put("timeout", String.valueOf(timeout));
        }
        if (activityData != null) {
            parameters.put("activity_data", String.valueOf(activityData));
        }
        if (verifyPlus != null) {
            parameters.put("verify_plus", String.valueOf(verifyPlus));
        }
        if (domainInfo != null) {
            parameters.put("domain_info", String.valueOf(domainInfo));
        }
        if (greylistProcessing != null) {
            parameters.put("greylist_processing", String.valueOf(greylistProcessing));
        }
        if (callbackUrl != null) {
            if (callbackUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("Empty parameter: callback_url");
            }
            // The callback is where the greylist retry posts its result, so it is
            // meaningless — and rejected by the API — without the retry itself.
            if (!Boolean.TRUE.equals(greylistProcessing)) {
                throw new IllegalArgumentException("callback_url requires greylist_processing to be true");
            }
            parameters.put("callback_url", callbackUrl);
        }
    }
}
