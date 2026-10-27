# Tasks

## 1. Gemini permission suggestion

- [x] 1.1 Add the Gemini key-restriction note as one public constant in `DirectApiService` (Settings is in another package), and make `suggestion(GEMINI, PERMISSION)` return `KEY_SUGGESTION` followed by it, with `AUTH` and other providers unchanged; verify with `DirectApiErrorsContractTest`: both live-captured Gemini `403-no-key` fixtures get the noted suggestion and keep the provider's message, the Gemini `400-api-key-invalid` fixture gets the plain `KEY_SUGGESTION`, and a bare Gemini 403 with a non-JSON body gets the note through the status fallback
- [x] 1.2 Add a test that Claude and OpenAI permission errors still get the plain `KEY_SUGGESTION`: a new DERIVED Claude `403 permission_error` fixture (no Claude or OpenAI 403 capture exists) plus bare non-JSON 403s, each asserted to classify as `PERMISSION` first so a leak can't hide behind another kind; verify it fails if the Gemini guard is dropped
- [x] 1.3 Record in `fixtures/ai/README.md` the derived Claude 403 row and that no blocked or unrestricted Gemini key error could be captured (the 2026-10-27 probe returned 200), so the note is matched on status; verify `DirectApiErrorsContractTest`'s hygiene check passes with the new fixture

## 2. Settings Test

- [x] 2.1 In `OpenSpecSettingsPanel.testFailureMessage`, return "Check your API key" plus the shared note for a Gemini `PERMISSION` failure, after the OpenAI scope branch and before the generic key branch; verify in `OpenSpecSettingsPanelWorkspaceIdTest` that a Gemini 403 `AiApiException` shows the note, and that a Gemini `AUTH` failure and Claude/OpenAI 401/403 still show plain "Check your API key"

## 3. Docs

- [x] 3.1 Update `docs/feature-reference.md`: add a Gemini permission row (or extend the key row) to the Direct API error table, and change the troubleshooting key-format line to say Gemini keys are either the older `AIza…` form or the newer auth keys, and that keys created before late May 2026 must be restricted to the Gemini API or replaced; verify by reading both passages against the spec
- [x] 3.2 Add a CHANGELOG `## Unreleased` → `### Fixed` entry for users, vendor-neutral, with no tracker IDs; verify with `./gradlew test --tests '*DocumentationHygiene*'` and the leak grep from CLAUDE.md over the staged files

## 4. Integration

- [x] 4.1 Run `./gradlew build` (tests plus the coverage floor) and `openspec validate gemini-key-restriction-hint --strict`; both pass

## Workflow follow-up

- Record on the tracker entry that auth keys (`AQ.…`) work with `x-goog-api-key` on `v1beta`, and that an unrestricted pre-May key wasn't rejected on 2026-10-27, so a reason-specific rule waits for a real blocked-key capture.
- Archive the change after review, then close its tracker entries.
