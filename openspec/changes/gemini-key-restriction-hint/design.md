# Design

## Context

See proposal.md (Why). Direct API failures are classified in `DirectApiErrors`:
- A Gemini body with `error.status` `PERMISSION_DENIED`, or a bare 403, classifies as `ErrorKind.PERMISSION`.
- `suggestion(provider, kind)` maps `AUTH` and `PERMISSION` to the same `KEY_SUGGESTION` for every provider.
- `OpenSpecSettingsPanel.testFailureMessage` replaces any `AUTH` or `PERMISSION` failure with the fixed text "Check your API key", after the OpenAI Organization/Project branch.

Live facts, checked 2026-10-27:

| Key | Created | Format | `generateContent` | `models` |
|---|---|---|---|---|
| Standard key, restricted to Gemini | before 2026-05-28 | `AIza…`, 39 characters | 200 | 200 |
| The same key with its API restriction removed | — | — | 200 on every attempt over 9 minutes | 200 |
| AI Studio key | 2026-10-27 | `AQ.…`, 53 characters (auth key) | 200 | 200 |
| Google Cloud console key | 2026-10-27 | `AQ.…`, 53 characters (auth key, bound to a service account, restricted to Gemini) | 200 | 200 |

All requests used the plugin's own form: `x-goog-api-key` on `v1beta`. Google's API-key page documents no status, reason or message for an unrestricted or blocked key. Both the AI Studio and the Cloud console flows now create only auth keys, restricted to the Gemini API. So only keys created before late May 2026 can be unrestricted.

## Goals / Non-Goals

**Goals:**
- A Gemini permission failure points at the fix that works for an old unrestricted key: a new key from Google AI Studio.
- Settings Test shows the same note.

**Non-Goals:**
- A new `ErrorKind`, or matching on an undocumented reason code.
- Any check of the key's format. Both formats work, and Google may introduce others.

## Decisions

### D1. A provider-specific suggestion for `PERMISSION`, not a new kind

`suggestion(GEMINI, PERMISSION)` returns `KEY_SUGGESTION` followed by the note. `AUTH` for Gemini, which is `400 API_KEY_INVALID`, keeps the plain `KEY_SUGGESTION`: a key that Google calls invalid isn't the restriction problem.

- *Alternative: a new `KEY_RESTRICTION` kind.* Rejected. Nothing in the body can tell it apart from any other permission error, so a new kind would be a second name for `PERMISSION` on Gemini.
- *Alternative: match a reason code such as `API_KEY_SERVICE_BLOCKED`.* Rejected for now. It's a guess, and the contract-test rule forbids writing a rule against a shape that wasn't captured. If a blocked key's real error is captured later, a reason-specific rule can sit ahead of this fallback.

### D2. The note is worded as a possibility

A Gemini `403` has other causes, such as the API not being enabled in the key's project. So the note reads as "Google rejects older keys that aren't restricted to the Gemini API…", not "your key is unrestricted". It recommends a new AI Studio key rather than re-restricting the old key: the Cloud console now requires a service-account binding to restrict a key to the Gemini API, which turns it into an auth key anyway.

### D3. Test reuses the same note

`testFailureMessage` adds a branch for Gemini `PERMISSION`. It returns "Check your API key" followed by the same note, taken from one shared constant so the two can't drift. The branch comes after the OpenAI scope branch, which can't match Gemini, and before the generic `AUTH`/`PERMISSION` branch. `AiApiException` already carries the provider, so no new field is needed.

## Risks / Trade-offs

- [The note shows for Gemini 403s that have another cause] → It's worded as a possibility and keeps the provider's own message, which names the real cause when Google gives one.
- [Google changes the rejection to another status, such as 400 or 401] → The plain key suggestion still applies. Once a real blocked-key error is captured, it becomes a fixture and gets its own rule.
- [The "late May 2026" boundary may be unclear to users] → The user-facing text avoids dates and says "older keys". The docs give the date.

## Migration Plan

None. There are no settings or stored data. Roll back by reverting the commit.
