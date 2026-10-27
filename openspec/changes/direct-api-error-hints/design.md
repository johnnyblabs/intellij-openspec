# Design

## Context

See proposal.md (Why). Every Direct API failure goes through one function, `DirectApiService.buildApiError(providerName, status, body[, requestSettings])`. It is called from four places: the Claude, OpenAI and Gemini generation paths, and `listModels` (which Settings Test also uses). It builds an `AiApiException(message, status, provider, suggestion)`.

- **Message:** `extractErrorMessage` reads `error.message`. If the body isn't JSON, it falls back to the first 200 characters.
- **Suggestion:** `suggestionForStatus` looks at the status only.
- **Overrides on top:**
  - the Claude workspace mapper replaces the message and clears the suggestion;
  - the OpenAI Organization/Project 401 rule replaces the suggestion.

Consumers today:
- `WorkflowActionPanel` (Generate, Generate All, Fast-Forward) appends `getSuggestion()`.
- `OpenSpecSettingsPanel.testFailureMessage` maps 401/403 to "Check your API key", and otherwise shows `getMessage()` only.
- `ExploreContextAction` and `VerificationService` show `getMessage()` only.

Live provider facts, all checked 2026-10-27 against the providers' current docs and fake-key probes:

| | Claude | OpenAI | Gemini (`generateContent`) |
|---|---|---|---|
| Body | `{"type":"error","error":{"type","message","details"?},"request_id"?}` | `{"error":{"message","type","param","code"}}`, sometimes with a top-level `status` | `{"error":{"code":int,"message","status":ENUM,"details":[{"@type","reason"}]}}` |
| Request ID | `request-id` header; also `request_id` in the body | `x-request-id` header only | none |
| Invalid key | 401 `authentication_error` | 401, content type `text/plain` with a JSON body | **400** `INVALID_ARGUMENT`, reason `API_KEY_INVALID` |
| Billing | 402 `billing_error` | 429 `code` `credit_balance_exhausted` | 402 (status string undocumented) |
| Spend or quota | 400 with message "You have reached your specified …"; 429 with `details.error_code` `enforced_spend_limit_reached` | 429 `code` `organization_spend_limit_exceeded`, `project_spend_limit_exceeded` or `organization_usage_limit_exceeded`; `type` `insufficient_quota` | Docs say a spend-based 429 `RESOURCE_EXHAUSTED` is retryable |
| Overload | 529 `overloaded_error` | 503 `server_is_overloaded` | 503 `UNAVAILABLE` |

Gemini's `api-errors` page now documents only the Interactions API, which has a different shape. Its code table must not be used for `generateContent`.

## Goals / Non-Goals

**Goals:**
- One classifier with a small, closed set of error classes.
- Each provider's documented fields drive it, and each classification is pinned to a fixture.
- Every consumer shows one consistent message plus suggestion.

**Non-Goals:**
- Retry or backoff. `Retry-After` is not read here, though the header access added here makes it easy to read later.
- Mapping every documented code. Only codes that change the advice the user gets are mapped.
- Localizing provider messages, or rewording them beyond the existing workspace mapper.

## Decisions

### D1. A package-private `DirectApiErrors` classifier with an `ErrorKind` enum

`ErrorKind` has these values:
- `AUTH`
- `PERMISSION`
- `BILLING`
- `SPEND_LIMIT`
- `RATE_LIMIT`
- `OVERLOADED`
- `SERVICE_TIER`
- `UNKNOWN`

`classify(provider, status, JsonObject errorOrNull)` checks body fields first, provider by provider. It then falls back to the status:
- 401 → `AUTH`
- 403 → `PERMISSION`
- 402 → `BILLING`
- 429 → `RATE_LIMIT`
- 529 or 503 → `OVERLOADED`
- other 5xx → `OVERLOADED`, keeping today's "try again shortly" advice

Each kind maps to one suggestion string per provider. A provider-specific string is used only where the settings location differs:
- Claude Console → Billing, or Limits;
- OpenAI Settings → Billing, or Limits;
- Google AI Studio → Billing.

The service-tier string is OpenAI-only. Each kind has a provider-neutral fallback.

- *Why not keep extending `suggestionForStatus`?* The status can't tell a spend cap from a rate limit; only the body can. A separate class keeps `DirectApiService`, which is already long, from growing a provider × code switch, and lets the contract test drive the classifier directly.
- *Why an enum and not just the string?* `testFailureMessage` and the tests need to branch on the class, not compare wording. Today the OpenAI scope check compares suggestion text, which is fragile.

### D2. `buildApiError` takes an `HttpHeaders` (nullable) instead of a request-ID parameter

`buildApiError` takes the response headers. Each call site already has the `HttpResponse` in hand, so it passes `response.headers()`. `DirectApiErrors.requestId(provider, headers, errorBody)` resolves the ID:
- Claude: the `request-id` header, else the body's `request_id`;
- OpenAI: `x-request-id`;
- Gemini: none.

The ID is appended to the user message as `" (request ID: <id>)"`. It goes in the message rather than the suggestion, so that every consumer shows it, including the ones that print `getMessage()` only, and so that it survives the workspace mapper replacing the message. It is also added to the existing `LOG.warn` line. The old two- and three-argument `buildApiError` overloads stay and pass `null` headers. Existing tests call them, and a null-headers call adds nothing.

- *Alternative: a separate `getRequestId()` that each consumer formats.* Rejected: four consumers would each need an edit, and the first consumer that forgot would drop the ID again.
- `AiApiException` still gains `getRequestId()` and `getKind()` getters. Tests and `testFailureMessage` read them.

### D3. Body parsing ignores the content type

The body is parsed as JSON whatever the `Content-Type`, because OpenAI's 401 is labelled `text/plain`. On any parse failure the classifier receives `null` and falls back to the status. `extractErrorMessage` keeps its truncation fallback, so an HTML 413 from a proxy still yields a message.

### D4. Precedence

The Claude workspace mapper runs first. When it matches, it replaces the message, and the suggestion is cleared as today. The OpenAI Organization/Project 401 rule runs next. When it matches, it replaces the suggestion; it is chosen on the status, so classifying the body doesn't change it. Otherwise the classified kind's suggestion is used. In every case the request ID is appended last.

### D5. Matching rules, narrow and fixture-pinned

- **Claude:**
  - `details.error_code == "enforced_spend_limit_reached"` → `SPEND_LIMIT`. This is checked before the `rate_limit_error` type.
  - `type == "billing_error"` → `BILLING`.
  - `type == "invalid_request_error"` with a message that starts with "You have reached your specified" → `SPEND_LIMIT`. The prefix covers both documented variants, the API and the workspace usage limits.
  - `overloaded_error` → `OVERLOADED`.
  - `authentication_error` → `AUTH`.
  - `permission_error` → `PERMISSION`.
  - `rate_limit_error` → `RATE_LIMIT`.
- **OpenAI:** `code` is checked first:
  - the four quota or spend codes → `SPEND_LIMIT`, or `BILLING` for `credit_balance_exhausted`;
  - `server_is_overloaded` → `OVERLOADED`.

  Then `type == "insufficient_quota"` → `SPEND_LIMIT`. Then a 400 with `param == "service_tier"` → `SERVICE_TIER`.
- **Gemini:**
  - a `details[]` entry whose `reason` is `API_KEY_INVALID` → `AUTH`;
  - `status == "PERMISSION_DENIED"` → `PERMISSION`;
  - status 402 → `BILLING`;
  - `RESOURCE_EXHAUSTED` → `RATE_LIMIT`, because Google documents even the spend-based 429 as wait-and-retry;
  - `UNAVAILABLE` → `OVERLOADED`.
- Matching is exact on codes, and on the documented prefix only for Claude's usage-limit message. Nothing is inferred from free text otherwise.

### D6. Consumers

- `testFailureMessage`:
  - keeps the OpenAI scope branch, now checked by the `AUTH` kind plus the scope flag rather than by comparing text;
  - keeps "Check your API key" for `AUTH` and `PERMISSION`;
  - otherwise returns the message, plus " — " and the suggestion when there is one.
- `ExploreContextAction` and `VerificationService` append the suggestion in the same " — " form that Generate All uses.
- One small instance helper, `AiApiException.describe()`, returns message plus suggestion. The consumers then can't drift apart.

## Risks / Trade-offs

- **Most billing and spend bodies can't be captured live.**
  - The Claude spend-cap 429 is a verbatim example from the docs.
  - The OpenAI codes and Claude's 402 and usage-limit 400 are derived: the live-captured envelope with the documented code or message swapped in.
  - Each fixture has a row in the fixtures README that says whether it was captured, came from the docs, or was derived, and names the source page.

  Mitigation: a re-check on each release gate, when the provider audit already reads these pages.
- **Gemini's 402 status string is undocumented.** Matching on the HTTP status alone is the safe choice, and the fallback covers it.
- **Message wording can change** (the Claude usage-limit prefix). → It is pinned by a fixture. A miss falls back to the generic 400 path, which shows the provider's own message and so is no worse than today.
- **An appended request ID makes messages longer.** → It's short and comes last. Notifications already wrap.

## Migration Plan

No migration is needed. There are no settings or stored data. To roll back, revert the commit.

## Open Questions

None. The open service-tier question is resolved: OpenAI documents that requests omitting `service_tier` can be rejected.
