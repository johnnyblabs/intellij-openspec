# Tasks

## 1. The check

- [x] 1.0 Capture the live Gemini model list (one `GET v1beta/models?pageSize=1000`, the key read from the environment and never written) into `fixtures/ai/gemini/live/models-list.json`, and document it in `fixtures/ai/README.md`. Verify: the file holds `gemini-flash-latest` and `gemini-2.5-pro`, and no `nextPageToken`.
- [x] 1.1 Report completeness from `listModels` (Gemini's `nextPageToken`, Anthropic's `has_more`), and add `AiModelOptions.notListed(provider, model, fetched, fetchOk, complete)` (design decision 1).
  - Verify with plain tests over the captured lists, parsed by each provider's real parser (the OpenAI fixture is doc-sourced, with placeholder ids, so its case only checks membership mechanics):
    - a model from each list is listed;
    - `gemini-flash-latest` is listed in the live Gemini list;
    - `claude-sonnet-4-5` is listed against the live Claude list, through its dated snapshot;
    - an id absent from the list (`gemini-2.5-pro` against the doc Gemini list) is not;
    - `models/` prefixes compare equal;
    - each of these gives false: a failed fetch, an incomplete page, an empty list, a blank model.
  - Verify the parsers' completeness against a response carrying `nextPageToken` / `has_more: true` built from the captured fixture's own fields.
  - Mutations: drop the `fetchOk` guard, the completeness guard, the prefix strip, or the Claude snapshot rule; each turns a case red.

## 2. The hint in Settings

- [x] 2.1 Add the hint label by the Model field in `OpenSpecSettingsPanel`, driven by the last successful fetch for the selected provider and refreshed on selection changes (design decision 2).
  - Verify with a light test through the panel's existing fetch path, with a seam returning the captured Gemini list:
    - selecting `gemini-2.5-pro` shows the hint with its wording;
    - selecting a listed model hides it;
    - a failed fetch shows none;
    - a fresh panel with no fetch shows none;
    - switching provider after a fetch clears it;
    - editing the key or the Workspace field clears it;
    - a fetch finishing after a provider switch doesn't apply;
    - a fetch finishing after a key edit doesn't apply.
  - Run `verifyPlugin`.
  - Outcome: 10 mutations were run on the panel, and 8 turned the tests red. Two survived because they were redundant: a provider check in the Test callback (the fetch generation covers it) and a "fetched for this provider" field (a provider switch already clears the list). Both were removed, and the generation's two mutations turn the tests red. The explicit hint refresh after the selector is refilled survives by design: the selector's own selection event refreshes it too.

## 3. Docs and finish

- [x] 3.1 Update the CHANGELOG (`## Unreleased`) and the Direct API section of `docs/feature-reference.md`. Verify: `DocumentationHygieneTest`, and the vendor-neutral grep.
- [x] 3.2 Final checks:
  - `./gradlew build`;
  - `./gradlew verifyPlugin`;
  - `openspec validate --all --strict`;
  - the full `uiSmoke` under `caffeinate -dimsu`;
  - test-engineer AUDIT and intellij-code-reviewer on the diff, with findings fixed or recorded.
  - Verify: all pass, and the outcome is recorded here.
  - Outcome, on the final code:
    - `./gradlew build` passed, including the coverage floor;
    - `verifyPlugin` reported all 6 target IDEs compatible, with no deprecated API in this change;
    - `uiSmoke` passed 16 of 16;
    - `openspec validate --all --strict` passed 39 of 39.
  - Review findings, all fixed:
    - code review: the hint now wraps instead of widening the dialog; a discarded late fetch leaves a newer Test's result line alone; the saved-key placeholder no longer counts as a key edit; a page marker of an unexpected type counts as a partial list instead of failing Test; the hint is the model field's accessible description;
    - test audit: a partial page is now tested end to end through Test; OpenAI Organization and Project edits, a `models/` prefix in Settings, and setup isolation are covered; a README table row is fixed.
  - The 9 mutations added after the reviews each turn a test red.

## Workflow follow-up

- Merge by rebase, archive with spec sync, close the tracker entries.
