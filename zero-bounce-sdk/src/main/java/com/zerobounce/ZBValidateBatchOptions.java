package com.zerobounce;

import org.jetbrains.annotations.Nullable;

/**
 * Optional body fields for POST /validatebatch.
 */
public class ZBValidateBatchOptions {

    @Nullable
    private Integer timeout;

    @Nullable
    private Boolean activityData;

    @Nullable
    private Boolean verifyPlus;

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
}
