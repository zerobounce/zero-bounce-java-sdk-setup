package net.zerobounce;

import com.google.gson.annotations.SerializedName;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.util.Objects;

/**
 * The model used for the GET /activity request.
 */
public class ZBActivityDataResponse {

    @NotNull
    private Boolean found = false;

    @SerializedName("active_in_days")
    @Nullable
    private Integer activeInDays = null;

    public Boolean getFound() {
        return found;
    }

    public void setFound(Boolean found) {
        this.found = found;
    }

    public Integer getActiveInDays() {
        return activeInDays;
    }

    public void setActiveInDays(Integer activeInDays) {
        this.activeInDays = activeInDays;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBActivityDataResponse that = (ZBActivityDataResponse) o;
        return Objects.equals(found, that.found) && Objects.equals(activeInDays, that.activeInDays);
    }

    @Override
    public int hashCode() {
        return Objects.hash(found, activeInDays);
    }

    @Override
    public String toString() {
        return "ZBActivityData{" +
                "found=" + found +
                ", activeInDays='" + activeInDays + '\'' +
                '}';
    }
}
