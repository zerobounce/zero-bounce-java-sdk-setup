package com.zerobounce;

import org.jetbrains.annotations.Nullable;

/**
 * Optional query parameters for bulk {@code getfile}.
 * {@code activityData} applies to validation bulk only; it is ignored for {@link ZeroBounceSDK#scoringGetFile}.
 */
public class ZBGetFileOptions {

    @Nullable
    private ZBDownloadType downloadType;

    @Nullable
    private Boolean activityData;

    public @Nullable ZBDownloadType getDownloadType() {
        return downloadType;
    }

    public ZBGetFileOptions setDownloadType(@Nullable ZBDownloadType downloadType) {
        this.downloadType = downloadType;
        return this;
    }

    public @Nullable Boolean getActivityData() {
        return activityData;
    }

    public ZBGetFileOptions setActivityData(@Nullable Boolean activityData) {
        this.activityData = activityData;
        return this;
    }
}
