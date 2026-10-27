# Tasks

## 1. Fixtures

- [x] 1.1 Commit the 2026-10-27 probe captures under `src/test/resources/fixtures/ai/gemini/store/`: the top-level `store: false` request and its 200 response, and the `generationConfig.store` request and its 400 response. Record their provenance in the fixtures README (date, key type, models, the control result). Verify: the files parse as JSON, and grepping them for the API key finds nothing.

## 2. Request

- [x] 2.1 Add a top-level `"store": false` to the body in `DirectApiService.buildGeminiRequest`. Verify with unit tests that the body has `store` as a top-level boolean false, `generationConfig` has no `store`, and the Claude and OpenAI bodies have no `store`.
- [x] 2.2 Add a contract test: the field the plugin's body carries matches the accepted probe request's top-level `store`, and the rejected probe's 400 names `store` at `generation_config`. Verify the test fails if `store` is moved into `generationConfig`.

## 3. Live re-capture

- [x] 3.1 Update `scripts/capture-gemini-generatecontent.py` to send the plugin's new body and re-capture `gemini/live/`. Verify the existing live response contract tests still pass and the README's capture date is updated.

## 4. Docs and checks

- [x] 4.1 Update the feature reference's Direct API provider section (what the plugin sends to Gemini, and where Google's logging is controlled) and add a CHANGELOG Unreleased entry. Verify with the leak grep and `DocumentationHygieneTest`.
- [x] 4.2 Run `./gradlew build` (exit 0) and `openspec validate gemini-request-storage-opt-out --strict`.

## Workflow follow-up

- Re-probe with an auth key (`AQ.…`) when one is available.
- Archive the change, then close the trackers.
