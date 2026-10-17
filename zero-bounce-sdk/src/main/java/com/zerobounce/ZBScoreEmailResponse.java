package com.zerobounce;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Response for GET/POST /scoring (real-time AI scoring).
 */
public class ZBScoreEmailResponse {

    @Nullable
    private String email = null;

    @Nullable
    private Integer score = null;

    public @Nullable String getEmail() {
        return email;
    }

    public void setEmail(@Nullable String email) {
        this.email = email;
    }

    public @Nullable Integer getScore() {
        return score;
    }

    public void setScore(@Nullable Integer score) {
        this.score = score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBScoreEmailResponse that = (ZBScoreEmailResponse) o;
        return Objects.equals(email, that.email) && Objects.equals(score, that.score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, score);
    }

    @Override
    public @NotNull String toString() {
        return "ZBScoreEmailResponse{" +
                "email='" + email + '\'' +
                ", score=" + score +
                '}';
    }
}
