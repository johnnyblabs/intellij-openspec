# Tasks — Graceful legacy-file cleanup in the Update action

## 1. Fixture & parser

- [ ] 1.1 Capture real `openspec update` output with legacy files pending (scratch project with seeded `.junie/commands/opsx-*.md`-style leftovers, isolated environment; sanitize paths) and commit under `src/test/resources/fixtures/cli/`; also capture the clean "up to date" output for the negative case
- [ ] 1.2 `UpdateOutputParser.parseLegacyCleanup(stdout)` — pure parser recognizing the migration block's structural markers, returning the pending file list (empty when absent)
- [ ] 1.3 Contract test against the fixtures + degradation tests (no block, partially recognizable block → fewer files, never more)

## 2. Cleanup flow

- [ ] 2.1 Update-action result path: when the parser reports pending files, replace the bare success notification with an actionable "Review legacy cleanup…" notification
- [ ] 2.2 Cleanup dialog: per-file checkboxes (all checked), file links that open in the editor, the CLI's no-user-content statement quoted; buttons Remove selected / Run in terminal / Not now
- [ ] 2.3 Deletion: intersection scope (listed ∩ checked ∩ exists ∩ inside project root), single `WriteCommandAction` VFS delete, then re-run `openspec update` and report the resulting state
- [ ] 2.4 Terminal handoff via `OpenSpecTerminalLauncher` with the command prepared
- [ ] 2.5 Dismissal memory: project-level state keyed on the hash of the sorted pending path set; suppress while unchanged, re-offer when the set changes

## 3. Tests

- [ ] 3.1 Deletion-scope tests: out-of-project path discarded, missing file skipped, unchecked file kept
- [ ] 3.2 Dismissal-memory tests: same set suppressed, changed set re-offered
- [ ] 3.3 Flow test: pending outcome produces the actionable notification instead of bare success; clean outcome unchanged

## 4. Documentation

- [ ] 4.1 `docs/feature-reference.md` Update action section (cleanup flow, escape hatches, never-force guarantee); `docs/openspec-support.md` update row note; CHANGELOG entry under Unreleased
