package com.zerobounce;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** The response of the real-time AI scoring endpoint (/v2/scoring). */
public class ZBScoreEmailResponse {

    @Nullable
    private String email = null;

    /**
     * The API returns this as a number or a string depending on the account; it is
     * an integer, kept as text. A fraction, an exponent, a non-integer string, a
     * value outside the int range, a boolean, an array or an object is an error (E7).
     */
    @Nullable
    @com.google.gson.annotations.JsonAdapter(StrictTypes.IntegerText.class)
    private String score = null;

    @Nullable
    private String status = null;

    @Nullable
    private String error = null;

    public @Nullable String getEmail() {
        return email;
    }

    public void setEmail(@Nullable String email) {
        this.email = email;
    }

    public @Nullable String getScore() {
        return score;
    }

    public void setScore(@Nullable String score) {
        this.score = score;
    }

    public @Nullable String getStatus() {
        return status;
    }

    public void setStatus(@Nullable String status) {
        this.status = status;
    }

    public @Nullable String getError() {
        return error;
    }

    public void setError(@Nullable String error) {
        this.error = error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZBScoreEmailResponse that = (ZBScoreEmailResponse) o;
        return Objects.equals(email, that.email)
                && Objects.equals(score, that.score)
                && Objects.equals(status, that.status)
                && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, score, status, error);
    }

    @Override
    public String toString() {
        return "ZBScoreEmailResponse{email='" + email + "', score='" + score
                + "', status='" + status + "', error='" + error + "'}";
    }
}
