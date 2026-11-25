# Tasks

## 1. The check

- [x] 1.0 Capture the live Gemini model list (one `GET v1beta/models?pageSize=1000`, the key read from the environment and never written) into `fixtures/ai/gemini/live/models-list.json`, and document it in `fixtures/ai/README.md`. Verify: the file holds `gemini-flash-latest` and `gemini-2.5-pro`, and no `nextPageToken`.
- [ ] 1.1 Report completeness from `listModels` (Gemini's `nextPageToken`, Anthropic's `has_more`), and add `AiModelOptions.notListed(provider, model, fetched, fetchOk, complete)` (design decision 1).
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

- [ ] 2.1 Add the hint label by the Model field in `OpenSpecSettingsPanel`, driven by the last successful fetch for the selected provider and refreshed on selection changes (design decision 2).
  - Verify with a light test through the panel's existing fetch path, with a seam returning the captured Gemini list:
    - selecting `gemini-2.5-pro` shows the hint with its wording;
    - selecting a listed model hides it;
    - a failed fetch shows none;
    - a fresh panel with no fetch shows none;
    - switching provider after a fetch clears it;
    - editing the key or the Workspace field clears it;
    - a fetch finishing after a provider switch doesn't apply.
  - Run `verifyPlugin`.

## 3. Docs and finish

- [ ] 3.1 Update the CHANGELOG (`## Unreleased`) and the Direct API section of `docs/feature-reference.md`. Verify: `DocumentationHygieneTest`, and the vendor-neutral grep.
- [ ] 3.2 Final checks:
  - `./gradlew build`;
  - `./gradlew verifyPlugin`;
  - `openspec validate --all --strict`;
  - the full `uiSmoke` under `caffeinate -dimsu`;
  - test-engineer AUDIT and intellij-code-reviewer on the diff, with findings fixed or recorded.
  - Verify: all pass, and the outcome is recorded here.

## Workflow follow-up

- Merge by rebase, archive with spec sync, close the tracker entries.
