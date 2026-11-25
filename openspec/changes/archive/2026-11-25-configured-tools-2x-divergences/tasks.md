# Tasks

## 1. CLI 2.x

- [x] 1.1 Expose `Detector2x`'s `isListedToolPresent` port and its `Context` package-private, and count a tool as set up under 2.x rules when it returns true, after the folder-group rule (design decision 2).
- [x] 1.2 Search detection-path folders only below CLI 1.7 (design decision 1).

## 2. CLI 1.7 to 1.x

- [x] 2.1 From 1.8, reuse `AiToolDetector`'s port of `reconcileSharedSkillTargets` (design decision 3): a shared folder's skills count only for its owner; the marker's tool counts without a skill of its own.
- [x] 2.2 Show a tool the verdict counts as set up even when detection didn't find it (design decision 4).

## 3. Tests

- [x] 3.0 Captures: `CAPTURE_CASES` filter and `rmtree` step in the capture script; the 1.6-era and own-folder cases on 1.6.0 and 1.7.0; `own-files-links-removed` on 2.0.0, 2.1.0 and 2.1.1.
- [x] 3.1 `ConfiguredToolsContractTest`: releases 1.6.0, 1.7.0, 1.17.0, 2.0.0, 2.1.0 and 2.1.1, `KNOWN_DIVERGENCES` removed; every captured case matches the CLI's `configured.json`.
- [x] 3.2 Focused tests from the captured layouts:
  - the list-only Codex (1.17 and 2.x);
  - GSD owning `.agents` on 1.17;
  - `.kimi` and `.windsurf` detected but not set up from 1.7 (asserting DETECTED, not just absence);
  - `.codex` and `.agent` still set up;
  - a tool listed `own-files` with nothing of its own detected but not set up on 2.x (`own-files-links-removed`);
  - `.kimi` and `.windsurf` set up on 1.6;
  - no CLI detected follows the newest 1.x rules.
- [x] 3.3 Mutations:
  - drop the 1.7 boundary in either direction;
  - drop the `own-files` branch;
  - pick the first group member instead of the marker's tool;
  - skip the undetected set-up tools.
  
  Each turns a test red.

## 4. Docs and finish

- [x] 4.1 CHANGELOG `## Unreleased`; the Manage AI Tools section of `docs/feature-reference.md` where it describes set-up status. Verify: `DocumentationHygieneTest` and the vendor-neutral grep.
- [x] 4.2 Final checks, with the outcome recorded here:
  - `./gradlew build`;
  - `./gradlew verifyPlugin`;
  - `openspec validate --all --strict`;
  - the full `uiSmoke` under `caffeinate -dimsu`;
  - test-engineer AUDIT and intellij-code-reviewer on the diff, with findings fixed or recorded.

  Outcome:
  - `./gradlew build`: green, including the coverage floor and documentation checks.
  - `./gradlew verifyPlugin`: compatible with every target IDE, no scheduled-for-removal usage.
  - `openspec validate --all --strict`: 39 passed.
  - Audit: the two layouts edited after laying them out became captures (`*-skills-only-from-1.6`). The 1.8 marker boundary gained a capture and a test, and its mutation fails. The sanity floor was raised, and the 1.6.0 alias path is asserted.
  - Code review: no platform issues; the only caller of the full status list runs off the UI thread. Repeated per-tool reads are now done once per verdict (folder owners, the tool list, shared folders).
  - Mutations (each fails a test): the 1.7 boundary both ways, the 1.8 boundary, the `own-files` branch, the marker replaced by the group's first tool, undetected tools skipped, the ownership filter, and the legacy-folder check.
  - `uiSmoke` under `caffeinate -dimsu`: 18 tests, 0 failed.

## Workflow follow-up

- Merge by rebase, archive with spec sync, close the tracker entries.
