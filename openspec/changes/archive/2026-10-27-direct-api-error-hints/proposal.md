# Proposal

## Why

When a Direct API request fails, the plugin picks its suggestion from the HTTP status alone. Billing failures get the wrong advice. OpenAI's quota and spend-limit errors arrive as 429, so the plugin says "wait a moment and retry", but OpenAI documents them as not retryable. Claude's and Gemini's payment errors (402) and Claude's spend-limit errors get no hint at all. Errors also drop the provider's request ID, which both Claude and OpenAI ask for in a support case. Explore and Verify show the provider message but drop the suggestion entirely. The Direct API release gate's provider audit found these gaps. Tracked by the linked tracker entry.

## What Changes

- **Classify failures by the error body, not only the status.** Each provider's documented error `type`, `code` or `status` field decides the hint, and the HTTP status is the fallback when the body has no recognized field. An error class is one of:
  - authentication or permission;
  - billing or payment;
  - quota or spend limit;
  - rate limit;
  - overloaded or unavailable;
  - other.
- **Billing, quota and spend limits never say "retry".** They point the user at the provider's billing or limits settings instead. This covers:
  - OpenAI's 429 quota and spend codes;
  - Claude's 402 `billing_error`, its spend-limit 400 and its tier spend-cap 429;
  - Gemini's 402 payment error.
- **A rejected Gemini key gets the key hint.** Gemini rejects an invalid key with 400 and reason `API_KEY_INVALID`, not 401, so today it gets no "check your API key" hint.
- **A disallowed OpenAI service tier says where to fix it.** OpenAI can reject a request with 400 because the project doesn't allow the service tier the request resolved to, even when the request names no tier (the plugin names none). The hint points at the project's service-tier settings.
- **Only a real rate limit says "wait and retry".** A plain rate-limit 429 keeps that advice, and so does an overloaded or unavailable response (Claude 529, 503). This change adds no automatic retry; that is separate, later work.
- **Show the provider's request ID.** When a response carries one, the error message ends with it (Claude `request-id`, OpenAI `x-request-id`), so the user can quote it to the provider's support. Gemini documents none, so nothing is added there. The ID is also logged alongside the existing warning.
- **The suggestion reaches every Direct API surface.** Generate, Generate All and Fast-Forward already show it. Explore and Verify gain it, and so does Settings → Test, which today replaces every 401/403 with "Check your API key" and shows only the raw message for everything else.
- **Existing mapped messages are kept.** The Claude workspace messages and the OpenAI Organization/Project 401 suggestion keep their wording and precedence.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: adds requirements for:
  - classifying Direct API errors body-first;
  - billing, quota and spend-limit hints that never advise retrying;
  - rate-limit and overload hints;
  - key errors per provider, including Gemini's 400;
  - the OpenAI service-tier rejection;
  - the provider request ID in errors;
  - suggestions on every Direct API surface.

  The existing Claude workspace and OpenAI Organization/Project requirements are unchanged.

## Impact

- **Code:**
  - `DirectApiService`:
    - `buildApiError` takes the response headers and classifies the body;
    - a small classifier replaces `suggestionForStatus`;
    - the three generation paths and `listModels` pass the headers.
  - `AiApiException` carries the request ID and the error class.
  - `OpenSpecSettingsPanel.testFailureMessage` shows the classified suggestion.
  - `ExploreContextAction` and `VerificationService` append the suggestion.
- **Tests:**
  - contract tests that classify captured real error bodies and headers per provider;
  - documented example bodies where a live capture can't be produced on demand (billing and spend limits), each marked as from the provider's docs. Where the docs give a code but no body, the fixture is the live-captured envelope with the documented code swapped in, marked as derived;
  - the existing workspace and 401-scope tests keep passing unchanged.
- **Docs:** the feature reference and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. No new platform APIs.
- **Out of scope:**
  - automatic retry and `Retry-After` handling (tracked separately);
  - sending a `service_tier`, or any new setting. The service-tier hint only explains the rejection.
