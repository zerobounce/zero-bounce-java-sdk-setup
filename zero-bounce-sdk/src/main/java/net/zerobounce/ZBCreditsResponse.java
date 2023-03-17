package net.zerobounce;

import com.google.gson.annotations.SerializedName;
import com.sun.istack.internal.Nullable;

import java.util.Objects;

/**
 * The model class used for the GET /getcredits request.
 */
public class ZBCreditsResponse {

    @SerializedName("Credits")
    @Nullable
    private String credits = null;

    public String getCredits() {
        return credits;
    }

    public void setCredits(String credits) {
        this.credits = credits;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBCreditsResponse that = (ZBCreditsResponse) o;
        return Objects.equals(credits, that.credits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(credits);
    }

    @Override
    public String toString() {
        return "ZBCreditsResponse{" +
                "credits='" + credits + '\'' +
                '}';
    }
}