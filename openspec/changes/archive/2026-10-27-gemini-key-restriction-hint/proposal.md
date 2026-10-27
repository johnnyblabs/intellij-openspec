# Proposal

## Why

Google's Gemini API now rejects standard API keys that aren't restricted to the Gemini API, and blocks such keys after they've gone unused for a while. When Gemini refuses a key with `403 PERMISSION_DENIED`, the plugin says only "Check your API key", which doesn't help someone whose key is valid but old and unrestricted. New keys, from Google AI Studio or the Google Cloud console, are now service-account-bound "auth keys", restricted to the Gemini API by default, so creating a new key is the reliable fix. Tracked by the linked tracker entry.

## What Changes

- **A Gemini permission error names the likely cause and the fix.** When Gemini refuses a request with `403` (`PERMISSION_DENIED`), the suggestion keeps "Check your API key in Settings → Tools → OpenSpec" and adds that Google rejects older keys that aren't restricted to the Gemini API, and blocks them when unused, and that creating a new key in Google AI Studio fixes it.
- **Settings → Test says the same.** A Gemini permission failure in Test adds the same note to "Check your API key". Test keeps the plain wording for every other key error, including Gemini's `400 API_KEY_INVALID`, and for other providers.
- **Matched on the status, not on a reason code.** Google doesn't document the error for an unrestricted or blocked key, and it couldn't be captured: an unrestricted standard key kept working throughout a live probe. The note is therefore attached to every Gemini permission error and worded as a possibility, not a diagnosis.
- **Auth keys need no change.** Auth keys look different from older keys (they don't start with `AIza`), and they work with the `x-goog-api-key` header the plugin sends on `v1beta`, for both generation and the model list (checked live). The plugin doesn't check key format, so they already work. The docs now mention both key formats.
- Other providers, Gemini's other errors, and the Claude and OpenAI mappings are unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: the "Key and permission errors per provider" requirement gains the Gemini permission-error note, and "Error suggestions on every Direct API surface" gains a Test scenario for it.

## Impact

- **Code:**
  - `DirectApiErrors.suggestion`: a Gemini-specific suggestion for `ErrorKind.PERMISSION`.
  - `OpenSpecSettingsPanel.testFailureMessage`: the Gemini permission note after "Check your API key".
- **Tests:** contract tests over the existing live-captured Gemini `403 PERMISSION_DENIED` fixtures, plus the existing Gemini `400 API_KEY_INVALID` fixture as a negative control. No new fixture: the error for an unrestricted or blocked key couldn't be produced.
- **Docs:** the feature reference (error table and the API key troubleshooting line) and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. No new platform APIs.
- **Out of scope:**
  - a reason-specific match for a blocked or unrestricted key, until a real error can be captured;
  - other Gemini `403` causes, such as the API not enabled in the key's project.
