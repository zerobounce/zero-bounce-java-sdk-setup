package com.zerobounce;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A model class that stores the data of a domain format found in the [ZBEmailFinderResponse].
 */
public class DomainFormat {

    @NotNull
    private String format = "";

    @NotNull
    private String confidence = "";

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DomainFormat that = (DomainFormat) o;
        return Objects.equals(format, that.format) && Objects.equals(confidence, that.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, confidence);
    }

    @Override
    public String toString() {
        return "DomainFormat{" +
                "format='" + format + '\'' +
                ", confidence='" + confidence + '\'' +
                '}';
    }
}
