package net.zerobounce;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The model used for the GET /scoring and GET /getFile requests.
 */
public class ZBGetFileResponse {

    @Nullable
    private String localFilePath = null;

    public @Nullable String getLocalFilePath() {
        return localFilePath;
    }

    public void setLocalFilePath(@Nullable String localFilePath) {
        this.localFilePath = localFilePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBGetFileResponse that = (ZBGetFileResponse) o;
        return Objects.equals(localFilePath, that.localFilePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localFilePath);
    }

    @Override
    public String toString() {
        return "ZBGetFileResponse{" +
                "localFilePath='" + localFilePath + '\'' +
                '}';
    }
}
