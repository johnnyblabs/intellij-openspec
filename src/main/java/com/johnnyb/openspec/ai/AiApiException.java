package com.johnnyb.openspec.ai;

import org.jetbrains.annotations.Nullable;

public class AiApiException extends Exception {

    private final int statusCode;
    private final @Nullable String provider;
    private final @Nullable String suggestion;

    public AiApiException(String message) {
        super(message);
        this.statusCode = 0;
        this.provider = null;
        this.suggestion = null;
    }

    public AiApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.provider = null;
        this.suggestion = null;
    }

    public AiApiException(String message, int statusCode, @Nullable String provider,
                          @Nullable String suggestion) {
        super(message);
        this.statusCode = statusCode;
        this.provider = provider;
        this.suggestion = suggestion;
    }

    public AiApiException(String message, int statusCode, @Nullable String provider,
                          @Nullable String suggestion, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.provider = provider;
        this.suggestion = suggestion;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public @Nullable String getProvider() {
        return provider;
    }

    public @Nullable String getSuggestion() {
        return suggestion;
    }
}
