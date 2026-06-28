## 1. Schema context model

- [x] 1.1 Define a `WorkflowSchemaContext` value type capturing active schema name, `actionContext.mode`, `sourceOfTruth`, and `allowedEditRoots`
- [x] 1.2 Parse `actionContext` from `openspec status --json`, consulting `openspec instructions --json` only where artifact-level detail is needed
- [x] 1.3 Implement the built-in fallback: when the CLI is unavailable or below the 1.3 floor, resolve to `spec-driven` / repo-local so current behavior is preserved

## 2. Version-axis resolution

- [x] 2.1 Model the two axes separately: CLI version (floor 1.3, baseline 1.4) and config-format `version:`
- [x] 2.2 Route capability/feature gating through the CLI-version axis
- [x] 2.3 Keep effective-version resolution reading the config-format `version:` field (do not substitute the CLI version) — guard against the known conflation regression

## 3. Caching and invalidation

- [x] 3.1 Cache the resolved context per change selection
- [x] 3.2 Invalidate the cache on change-selection change and on propose / apply / archive
- [x] 3.3 Ensure context resolution stays off the EDT (pooled thread), consistent with existing workflow refresh discipline

## 4. Wire workflow surfaces to the context

- [x] 4.1 Have the action panel, pipeline view, and status strip read mode/source-of-truth from the resolved context instead of inferring from on-disk layout
- [x] 4.2 Preserve identical rendering/behavior for the `spec-driven` repo-local case
- [x] 4.3 For a non-default mode (e.g. `workspace-planning`), reflect the mode and suppress spec-driven-only affordances rather than presenting them as applicable

## 5. Tests

- [x] 5.1 Unit-test context derivation from representative `openspec status --json` payloads (spec-driven and a non-default mode)
- [x] 5.2 Test the built-in fallback path (CLI absent / below floor → spec-driven repo-local)
- [x] 5.3 Test version-axis separation (CLI-version gating vs config-format effective-version preserved)
- [x] 5.4 Test cache hit and invalidation on selection change and propose/apply/archive
- [x] 5.5 Surface tests: spec-driven case unchanged; non-default mode adapts

## 6. Documentation

- [x] 6.1 Update the OpenSpec client coverage matrix (`docs/openspec-support.md`) to reflect the now schema/version-aware workflow surfaces
- [x] 6.2 Note the foundation in the change's surfaces so Phase 2 (Verify) and Phase 3 (coordination layers) can build on it; keep public docs vendor-neutral
