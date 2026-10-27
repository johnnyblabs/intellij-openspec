package com.johnnyblabs.openspec.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpHeaders;

/**
 * Classifies a failed Direct API response and finds its request ID. Each provider's documented error field decides
 * the {@link ErrorKind}; the HTTP status decides only when the body has none. The rules are pinned by the captured,
 * documented and derived bodies under {@code fixtures/ai/<provider>/errors/} (see that directory's README).
 */
final class DirectApiErrors {

    static final String CLAUDE = AiProvider.CLAUDE.getDisplayName();
    static final String OPENAI = AiProvider.OPENAI.getDisplayName();
    static final String GEMINI = AiProvider.GEMINI.getDisplayName();

    // Claude documents both spend-limit variants by this prefix ("…API usage limits" and "…workspace API usage limits").
    private static final String CLAUDE_USAGE_LIMIT_PREFIX = "You have reached your specified";

    static final String KEY_SUGGESTION = "Check your API key in Settings → Tools → OpenSpec";
    static final String RATE_LIMIT_SUGGESTION = "Rate limited — wait a moment and retry";
    static final String OVERLOADED_SUGGESTION = "The provider is overloaded or having issues — try again shortly";
    static final String SERVICE_TIER_SUGGESTION = "This OpenAI project doesn't allow the service tier the request "
            + "resolved to. Check the allowed service tiers in the project's settings on the OpenAI platform";

    private DirectApiErrors() {
    }

    /** The {@code error} object of a provider error body, or {@code null} if the body isn't JSON with one. */
    static @Nullable JsonObject errorObject(@Nullable String body) {
        if (body == null) {
            return null;
        }
        try {
            // Parsed whatever the Content-Type: OpenAI sends some JSON error bodies as text/plain.
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonObject() && root.getAsJsonObject().get("error") instanceof JsonObject error) {
                return error;
            }
        } catch (Exception ignored) {
            // Not JSON (a proxy's HTML page, for instance); the status decides.
        }
        return null;
    }

    static ErrorKind classify(String provider, int status, @Nullable JsonObject error) {
        ErrorKind fromBody = error == null ? null : fromBody(provider, status, error);
        return fromBody != null ? fromBody : fromStatus(status);
    }

    static ErrorKind fromStatus(int status) {
        if (status == 401) {
            return ErrorKind.AUTH;
        } else if (status == 403) {
            return ErrorKind.PERMISSION;
        } else if (status == 402) {
            return ErrorKind.BILLING;
        } else if (status == 429) {
            return ErrorKind.RATE_LIMIT;
        } else if (status >= 500) {
            // 529 (Claude overloaded), 503 and other server errors all keep "try again shortly".
            return ErrorKind.OVERLOADED;
        }
        return ErrorKind.UNKNOWN;
    }

    private static @Nullable ErrorKind fromBody(String provider, int status, JsonObject error) {
        if (CLAUDE.equals(provider)) {
            return claude(error);
        } else if (OPENAI.equals(provider)) {
            return openAi(status, error);
        } else if (GEMINI.equals(provider)) {
            return gemini(error);
        }
        return null;
    }

    private static @Nullable ErrorKind claude(JsonObject error) {
        // The spend cap shares rate_limit_error's type; only details.error_code tells them apart, so it goes first.
        if (error.get("details") instanceof JsonObject details
                && "enforced_spend_limit_reached".equals(string(details, "error_code"))) {
            return ErrorKind.SPEND_LIMIT;
        }
        String type = string(error, "type");
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "billing_error" -> ErrorKind.BILLING;
            case "invalid_request_error" -> {
                String message = string(error, "message");
                yield message != null && message.startsWith(CLAUDE_USAGE_LIMIT_PREFIX) ? ErrorKind.SPEND_LIMIT : null;
            }
            case "overloaded_error" -> ErrorKind.OVERLOADED;
            case "authentication_error" -> ErrorKind.AUTH;
            case "permission_error" -> ErrorKind.PERMISSION;
            case "rate_limit_error" -> ErrorKind.RATE_LIMIT;
            default -> null;
        };
    }

    private static @Nullable ErrorKind openAi(int status, JsonObject error) {
        String code = string(error, "code");
        if (code != null) {
            switch (code) {
                case "credit_balance_exhausted":
                    return ErrorKind.BILLING;
                case "organization_spend_limit_exceeded", "project_spend_limit_exceeded",
                     "organization_usage_limit_exceeded":
                    return ErrorKind.SPEND_LIMIT;
                case "server_is_overloaded":
                    return ErrorKind.OVERLOADED;
                default:
                    break;
            }
        }
        // OpenAI says to read the code first; the broader type can still be insufficient_quota.
        if ("insufficient_quota".equals(string(error, "type"))) {
            return ErrorKind.SPEND_LIMIT;
        }
        if (status == 400 && "service_tier".equals(string(error, "param"))) {
            return ErrorKind.SERVICE_TIER;
        }
        return null;
    }

    private static @Nullable ErrorKind gemini(JsonObject error) {
        // A rejected key is a 400 INVALID_ARGUMENT; only the ErrorInfo reason says it was the key.
        if (error.get("details") instanceof JsonArray details) {
            for (JsonElement detail : details) {
                if (detail instanceof JsonObject d && "API_KEY_INVALID".equals(string(d, "reason"))) {
                    return ErrorKind.AUTH;
                }
            }
        }
        String status = string(error, "status");
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "PERMISSION_DENIED" -> ErrorKind.PERMISSION;
            // Google documents even a spend-based 429 as wait-and-retry.
            case "RESOURCE_EXHAUSTED" -> ErrorKind.RATE_LIMIT;
            case "UNAVAILABLE" -> ErrorKind.OVERLOADED;
            default -> null;
        };
    }

    /** The suggestion for a kind, worded for the provider where its settings live somewhere specific. */
    static @Nullable String suggestion(String provider, ErrorKind kind) {
        return switch (kind) {
            case AUTH -> KEY_SUGGESTION;
            case PERMISSION -> GEMINI.equals(provider)
                    ? KEY_SUGGESTION + ". " + DirectApiService.GEMINI_KEY_RESTRICTION_NOTE
                    : KEY_SUGGESTION;
            case BILLING -> {
                if (CLAUDE.equals(provider)) {
                    yield "Check your billing and payment details in the Claude Console → Settings → Billing";
                } else if (OPENAI.equals(provider)) {
                    yield "Your OpenAI credit balance is used up. Add credits in the OpenAI platform → Settings → Billing";
                } else if (GEMINI.equals(provider)) {
                    yield "Add credits or check billing for this key's project in Google AI Studio → Billing";
                }
                yield "Check the billing details of your provider account";
            }
            case SPEND_LIMIT -> {
                if (CLAUDE.equals(provider)) {
                    yield "A spend or usage limit is reached. Raise it in the Claude Console → Settings → Limits, "
                            + "or wait until the time the message gives";
                } else if (OPENAI.equals(provider)) {
                    yield "A quota or spend limit is reached. Check usage and limits in the OpenAI platform → "
                            + "Settings → Limits, or add credits under Billing";
                }
                yield "A quota or spend limit is reached. Check usage and limits in your provider account";
            }
            case RATE_LIMIT -> RATE_LIMIT_SUGGESTION;
            case OVERLOADED -> OVERLOADED_SUGGESTION;
            case SERVICE_TIER -> SERVICE_TIER_SUGGESTION;
            case UNKNOWN -> null;
        };
    }

    /**
     * The provider's request ID for a failed response: Claude's {@code request-id} header, else the body's
     * {@code request_id}; OpenAI's {@code x-request-id} header. Gemini documents none.
     */
    static @Nullable String requestId(String provider, @Nullable HttpHeaders headers, @Nullable String body) {
        if (CLAUDE.equals(provider)) {
            String header = header(headers, "request-id");
            if (header != null) {
                return header;
            }
            try {
                JsonElement root = body == null ? null : JsonParser.parseString(body);
                if (root != null && root.isJsonObject()) {
                    return blankToNull(string(root.getAsJsonObject(), "request_id"));
                }
            } catch (Exception ignored) {
                // Not JSON; no body ID.
            }
            return null;
        } else if (OPENAI.equals(provider)) {
            return header(headers, "x-request-id");
        }
        return null;
    }

    private static @Nullable String header(@Nullable HttpHeaders headers, String name) {
        // A mocked HttpResponse returns null headers.
        return headers == null ? null : blankToNull(headers.firstValue(name).orElse(null));
    }

    private static @Nullable String string(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
