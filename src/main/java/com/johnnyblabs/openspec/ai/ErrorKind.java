package com.johnnyblabs.openspec.ai;

/**
 * The class of a failed Direct API request, which decides the suggestion shown with the provider's message.
 * Chosen by {@link DirectApiErrors#classify} from the error body first and the HTTP status second.
 */
public enum ErrorKind {
    AUTH,
    PERMISSION,
    BILLING,
    SPEND_LIMIT,
    RATE_LIMIT,
    OVERLOADED,
    SERVICE_TIER,
    UNKNOWN
}
