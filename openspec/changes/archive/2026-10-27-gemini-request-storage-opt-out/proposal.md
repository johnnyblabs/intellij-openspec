# Proposal

## Why

Since 2026-10-26, Google's Gemini API stores `generateContent` requests by default for new paid-tier projects. They are kept for 55 days and visible in the project's AI Studio logs. A `store` field on the request overrides the project setting. The plugin sends no `store` field, so for those users every Direct API prompt, along with the project context it carries, is now kept on Google's side without the plugin saying so. Tracked by the linked tracker entry.

## What Changes

- **Every Gemini request opts out of storage.** The `generateContent` body gains a top-level `"store": false`. This covers every Gemini Direct API request: Generate, Generate All Remaining, Fast-Forward, Continue, Regenerate, Explore and Verify. Settings → Test lists models with a request that has no body, so it is unchanged.
- **The placement is pinned by a test.** A live probe on 2026-10-27 found:
  - top-level `"store": false` is accepted (200) on `gemini-3.8-flash` and `gemini-3.6-flash`;
  - `store` inside `generationConfig` is rejected with 400 "Unknown name";
  - so is any unknown top-level field.

  A misplaced field would break every Gemini request, so the probe responses are kept as fixtures and a contract test checks the body's shape.
- **The live Gemini fixtures are re-captured with the new body.** This proves that the exact request the plugin sends still gets a normal reply.
- **No setting.** The plugin always opts out. Turning logging back on for the plugin's requests isn't offered, and no user asked for it.
- Claude and OpenAI requests are unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: a new requirement that Gemini requests ask Google not to store them.

## Impact

- **Code:** `DirectApiService.buildGeminiRequest`, the single place every Gemini generation request is built.
- **Tests and fixtures:**
  - a contract test on the request body;
  - new probe fixtures (top-level `store` accepted, `generationConfig.store` rejected);
  - re-captured `gemini/live/` fixtures, through the updated capture script.
- **Docs:** the feature reference's Direct API provider section and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. No platform APIs are involved.
- **Not verified:** the newer Gemini auth keys (`AQ.…`), because no auth key was available for the probe. The field is part of the request body, not the key, and Google documents no difference by key type.
