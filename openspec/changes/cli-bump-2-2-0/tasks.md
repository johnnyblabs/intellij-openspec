# Tasks

## 1. Captures

- [x] 1.1 Install 2.2.0 into the capture cache, then run every capture script for 2.2.0 into `src/test/resources/fixtures/cli/2.2.0/` (and `archive-2.2.0/`):
  - `capture-cli-generation.py`, `capture-help-sweep.py`, `capture-ai-tools-fixtures.py`;
  - `capture-workflow-ids.py`, `capture-workflow-invocations.py`;
  - `capture-update-dry-run-fixtures.py`, `capture-update-migration-fixtures.py`;
  - `capture-validator-parity-2x.py`, `capture-store-root.py`, `capture-skills-scope.py`, `capture-archive-fixtures.py`;
  - `capture-skill-spot-verdicts.py`, `capture-explore-fixtures.py`, `capture-explore-context-fixtures.py`, `capture-error-fixtures.py`, `capture-new-change-fixtures.py`;
  - `capture-released-fingerprints.py` in full: it rewrites the shipped fingerprint set from the target, so diff its count and checksum.

  Extend `capture-skills-scope.py` `RELEASES` and `capture-store-root.py` `DEFAULT_VERSIONS` with 2.2.0. `capture-skills-scope.py` rewrites the machine-markers resource from its last release, so diff it. Record the runs in the fixtures README (D1).
- [x] 1.2 Add router cases to `capture-update-dry-run-fixtures.py` and `capture-update-migration-fixtures.py`: the router added to a 2.1.1 project, an unedited router, an edited router reference, and the router's tool-folder link. Capture them on 2.2.0 (D3). Capture the router's released reference fingerprints with `scripts/capture-router-references.py`, so an untouched reference is never listed at risk.
- [x] 1.3 Diff each 2.2.0 capture with 2.1.1's. Every difference beyond the router and the version is investigated, not edited.

## 2. Code

- [x] 2.1 `AiToolRegistry.SKILL_NAMES` gains `openspec` (D2).
- [x] 2.2 `UpdateMigration` link and in-skill patterns cover `skills/openspec`, so the router's link shows as a link and edited router files, including `references/*.md`, are at risk (D3).
- [x] 2.3 `openspecTargetVersion=2.2.0` (D4).

## 3. Tests

- [x] 3.1 Version guards: 2.2.0 in `ValidatorVerdictVersionStabilityTest.FLOOR` and `CliVersionAtLeastTest`, with an anti-lexical pair; `TargetVersionSingleSourceTest` green. Tests that find releases on their own get 2.2.0 in their required sets; tests with a hard-coded release list get 2.2.0 added.
- [x] 3.2 Contract tests on the 2.2.0 captures:
  - the update preview shows `.claude/skills/openspec` as a link;
  - an edited router reference is listed at risk, and an unedited router is not;
  - `openspec` is counted among the skill folders and is not a workflow;
  - all release-iterating contract tests pass with 2.2.0 included.
- [ ] 3.3 The uiSmoke 2.x layout journey runs on CLI 2.2.0 from the cache (D6).
- [x] 3.4 Mutations, each turning a test red:
  - `openspec` dropped from `SKILL_NAMES`;
  - the link pattern reverted to `openspec-` only;
  - the in-skill pattern reverted to `openspec-` only.
  - the released router-reference check removed.

## 4. This repository

- [x] 4.1 Install CLI 2.2.0 globally so `openspec --version` equals the target. Run `openspec update`, and commit the router skill and its link separately (D5). Confirm the custom skills are untouched.

## 5. Docs and verification

- [x] 5.1 "OpenSpec CLI 1.3.0 through 2.2.x" on the README, `openspec-support.md`, the `cli-versions` notes, Marketplace page, feature reference and comparison matrix, and a CHANGELOG entry. The feature reference, `openspec-support.md` and the `cli-versions/2.x` note say the agent has an `openspec` skill for when the next step is unclear, and that under a custom profile missing a workflow skill it can still do that step through the CLI while the plugin hides the gated action.
- [x] 5.2 Retake the affected screenshots (Manage AI Tools, migration preview) with the screenshot tour, if the router changes them. Not needed: no captured surface changes (Manage AI Tools has no per-skill list).
- [ ] 5.3 `./gradlew build`, `./gradlew verifyPlugin`, the full `caffeinate -dimsu ./gradlew uiSmoke`, and `openspec validate --all --strict`.
- [x] 5.4 Workflow ↔ UI parity audit for the bump (openspec-guru and plugin-ui-specialist), with the router decision recorded on its tracker entry (done: no router action; D2).
