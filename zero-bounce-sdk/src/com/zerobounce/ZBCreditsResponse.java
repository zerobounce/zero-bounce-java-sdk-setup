package com.zerobounce;

import com.google.gson.annotations.SerializedName;

public class ZBCreditsResponse {
    @SerializedName("Credits")
    String credits = null;

    @Override
    public String toString() {
        return "ZBCreditsResponse{" +
                "credits='" + credits + '\'' +
                '}';
    }
}