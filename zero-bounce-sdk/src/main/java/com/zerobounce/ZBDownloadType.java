package com.zerobounce;

import org.jetbrains.annotations.NotNull;

/**
 * Values for bulk {@code getfile} query parameter {@code download_type} (validation and scoring).
 */
public enum ZBDownloadType {
    PHASE_1,
    PHASE_2,
    COMBINED;

    /**
     * @return the API query value (e.g. {@code phase_1})
     */
    public @NotNull String getQueryValue() {
        return switch (this) {
            case PHASE_1 -> "phase_1";
            case PHASE_2 -> "phase_2";
            case COMBINED -> "combined";
        };
    }
}
