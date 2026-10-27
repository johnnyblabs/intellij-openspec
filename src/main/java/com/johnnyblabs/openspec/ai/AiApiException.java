package com.johnnyblabs.openspec.ai;

import org.jetbrains.annotations.Nullable;

public class AiApiException extends Exception {

    private final int statusCode;
    private final @Nullable String provider;
    private final @Nullable String suggestion;
    private final ErrorKind kind;
    private final @Nullable String requestId;

    public AiApiException(String message) {
        this(message, 0, null, null, ErrorKind.UNKNOWN, null, null);
    }

    public AiApiException(String message, Throwable cause) {
        this(message, 0, null, null, ErrorKind.UNKNOWN, null, cause);
    }

    /** An HTTP failure whose kind is taken from the status alone. */
    public AiApiException(String message, int statusCode, @Nullable String provider,
                          @Nullable String suggestion) {
        this(message, statusCode, provider, suggestion, DirectApiErrors.fromStatus(statusCode), null, null);
    }

    public AiApiException(String message, int statusCode, @Nullable String provider,
                          @Nullable String suggestion, Throwable cause) {
        this(message, statusCode, provider, suggestion, DirectApiErrors.fromStatus(statusCode), null, cause);
    }

    AiApiException(String message, int statusCode, @Nullable String provider, @Nullable String suggestion,
                   ErrorKind kind, @Nullable String requestId, @Nullable Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.provider = provider;
        this.suggestion = suggestion;
        this.kind = kind;
        this.requestId = requestId;
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

    public ErrorKind getKind() {
        return kind;
    }

    /** The provider's request ID for this failure, when the response carried one. It also ends the message. */
    public @Nullable String getRequestId() {
        return requestId;
    }

    /** The message followed by the suggestion, as every surface that reports a Direct API failure shows it. */
    public String describe() {
        String message = getMessage() != null ? getMessage() : "";
        return suggestion == null ? message : message + " — " + suggestion;
    }
}
