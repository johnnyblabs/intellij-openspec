# Design

## Context

See proposal.md for why. Every Gemini generation request is built in `DirectApiService.buildGeminiRequest`, with a body of `contents` plus `generationConfig.maxOutputTokens`. Settings → Test calls `GET /v1beta/models`, which has no body.

Google's logs page documents `store` only in the SDKs' `GenerateContentConfig`, with no raw REST example. In REST, some config fields go at the top level and others under `generationConfig`, so the placement had to be probed.

The probe ran on 2026-10-27 with a standard `AIza…` key against `v1beta`:

| Body | Model | Result |
|---|---|---|
| top-level `"store": false` | gemini-3.8-flash | 200, normal reply (after one 503 overload retry) |
| top-level `"store": false` | gemini-3.6-flash | 200, normal reply |
| `generationConfig.store: false` | gemini-3.8-flash | 400 `Invalid JSON payload received. Unknown name "store" at 'generation_config'` |
| unknown top-level `notAField` (control) | gemini-3.8-flash | 400 `Unknown name "notAField"` |

The control shows Google validates top-level field names, so the 200s prove `store` is a recognised top-level field, not one that is silently ignored.

## Goals / Non-Goals

**Goals:**
- every Gemini generation request opts out;
- the field's placement is pinned against a captured response.

**Non-Goals:**
- a user setting;
- reading back or reporting the project's logging state;
- Claude or OpenAI changes, since neither stores API requests by default in a way a request field controls.

## Decisions

**D1. Always send `"store": false` at the top level.**
- **Where:** the field is added in `buildGeminiRequest`, so every path gets it.
- **Why always:** the plugin's prompts carry project context the user didn't choose to log, and the request-level field is the only control the plugin has. Older projects already default to not storing, so for them the field changes nothing.
- **Rejected:**
  - a setting: no user asked for one, and it would be one more Direct API field;
  - sending the field only for paid tiers: the plugin can't tell a project's tier.

**D2. Contract-test the placement, not just the presence.**
- **Unit tests on the body:** `store` is a top-level boolean false; `generationConfig` has no `store`; the Claude and OpenAI bodies have no `store`.
- **The probe responses are fixtures:** the top-level 200 and the `generationConfig` 400, with the request bodies that produced them. A test asserts that the body the plugin builds has `store` exactly where the accepted probe had it.
- **The capture script** is updated to send the same body. The script also records the request it sent next to each response, and a test asserts it equals the plugin's body, so the script can't drift from the plugin. The `gemini/live/` fixtures were re-captured on `gemini-3.6-flash`, because `gemini-3.8-flash` was overloaded (503) that day; the `gemini/store/` probe covers `gemini-3.8-flash`.

## Risks / Trade-offs

- [Google rejects `store` for some key type or model] → It was probed on two current models with a standard key; auth keys weren't probed. A rejection would surface as a 400 naming `store`, which the error mapping already reports with Google's message. Re-probe when an auth key is available.
- [Google moves or renames the field] → The fixtures pin today's shape. The release provider check reads the logs page every release.
