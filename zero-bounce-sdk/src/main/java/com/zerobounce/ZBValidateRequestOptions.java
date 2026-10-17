package com.zerobounce;

import org.jetbrains.annotations.Nullable;

/**
 * Optional query parameters for GET /validate.
 */
public class ZBValidateRequestOptions {

    @Nullable
    private String ipAddress;

    @Nullable
    private Integer timeout;

    @Nullable
    private Boolean activityData;

    @Nullable
    private Boolean verifyPlus;

    private boolean usePost;

    @Nullable
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(@Nullable String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @Nullable
    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(@Nullable Integer timeout) {
        this.timeout = timeout;
    }

    @Nullable
    public Boolean getActivityData() {
        return activityData;
    }

    public void setActivityData(@Nullable Boolean activityData) {
        this.activityData = activityData;
    }

    @Nullable
    public Boolean getVerifyPlus() {
        return verifyPlus;
    }

    public void setVerifyPlus(@Nullable Boolean verifyPlus) {
        this.verifyPlus = verifyPlus;
    }

    public boolean isUsePost() {
        return usePost;
    }

    public void setUsePost(boolean usePost) {
        this.usePost = usePost;
    }
}
