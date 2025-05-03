package com.example.acortadorurl;

import com.google.gson.annotations.SerializedName;

public class ShortUrlResponse {
    @SerializedName("short_url")
    private String shortUrl;

    public String getShortUrl() {
        return shortUrl;
    }
}