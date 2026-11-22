## 1. Captures

- [x] 1.1 Commit `scripts/capture-model-not-found.py` and the live OpenAI and Gemini 404 captures (retired and never-existing model) under `fixtures/ai/{openai,gemini}/errors/`, after checking they carry no key, organization, project or account details.
- [x] 1.2 Add the derived Claude 404 (`fixtures/ai/claude/errors/messages-404-model-not-found`), built from the live `messages-401-invalid-key` capture with only the documented fields changed.
- [x] 1.3 Record all of them, with their provenance and the re-capture note for Claude, in the AI fixtures README.

## 2. Classification

- [x] 2.1 Add `ErrorKind.MODEL_NOT_FOUND` and classify it in `DirectApiErrors`: OpenAI `error.code` `model_not_found` at any status; Gemini 404 `NOT_FOUND` with a message naming `models/`; Claude 404 `not_found_error` that isn't the workspace 404, tested by one shared predicate that the workspace message mapping also uses.
- [x] 2.2 Give it the model-not-found suggestion, pointing at the Direct API model setting and the tool window's model selector.

## 3. Tests

- [x] 3.1 In `DirectApiErrorsContractTest`, read every new capture and assert:
  - each 404 is `MODEL_NOT_FOUND` with the suggestion, for both the retired and the never-existing id;
  - the provider's message is kept and names the model;
  - the Claude workspace-not-found 404 keeps its message and gets no model suggestion;
  - a 404 whose body has no model field stays without that suggestion.
- [x] 3.2 Through `DirectApiService.buildApiError`, assert that the generation error carries the suggestion and the kind for each provider.

## 4. Documentation

- [x] 4.1 Add the retired-model hint to the Direct API errors section of `docs/feature-reference.md`.
- [x] 4.2 Add a user-facing `## Unreleased` entry to `CHANGELOG.md`.

## 5. Verify

- [x] 5.1 `./gradlew build` passes, including the coverage floor.
- [x] 5.2 `openspec validate --all --strict` passes.
