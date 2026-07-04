# Graceful legacy-file cleanup in the Update action

## Why

OpenSpec's CLI migrated agent integrations from command files to agent skills, and `openspec update` now detects leftover legacy files (e.g. `.junie/commands/opsx-*.md`), certifies them as safe to remove ("No user content to preserve"), and asks the user to either run interactively or re-run with `--force`. Run from the plugin's Update action, neither path exists: the CLI runs non-interactively into a read-only console and exits 0, so the plugin reports success while the update is actually incomplete — and every subsequent Update re-prints the same migration wall of text with no in-IDE way out. Reported live from plugin use on CLI 1.4.1.

## What Changes

- **Detect the "legacy files pending" outcome** of `openspec update` by recognizing the migration block in the CLI output (files-to-remove list + the `--force` guidance), contract-tested against captured real CLI output.
- **Offer a graceful, non-destructive resolution** instead of silent success:
  - An actionable notice explaining the skills migration, listing the CLI-certified files as clickable links with a checkbox per file (all checked — the CLI vouched for them, but any can be held back).
  - **Remove selected** — the plugin deletes exactly the checked files through the IDE's VFS inside an undoable write command (Local History covers it; git covers tracked files), then re-runs `openspec update` to confirm the clean state. No `--force` involved, so no blanket instruction-file refresh and no risk to customizations.
  - **Run interactively in terminal** — hands off to the existing OpenSpec terminal launcher for the CLI's own prompt flow.
  - **Not now** — dismisses without re-nagging on subsequent Updates until the pending file set changes.
- **The plugin never runs `update --force` on the user's behalf.** The bundled force behavior (cleanup + refresh-everything) remains a deliberate non-surface; the surgical path above achieves the cleanup without the destructive half.
- **Safety invariant:** the plugin only ever deletes files that both appeared in the CLI's own "Files to remove" list and exist on disk — a partial parse degrades to offering fewer files, never more.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `cli-update`: the Update action additionally recognizes the CLI's legacy-cleanup-pending outcome and resolves it through a consented, previewable, IDE-native cleanup flow (or terminal handoff), instead of reporting bare success.

## Impact

- **Code:** `actions/OpenSpecUpdateAction.java` (outcome detection on the existing result path), a new output parser (pure, testable), a cleanup dialog/notification component, deletion via VFS `WriteCommandAction`, re-run + confirmation; terminal handoff via the existing `OpenSpecTerminalLauncher`; per-project dismissal memory keyed on the pending file set.
- **Tests:** contract test parsing a fixture captured from real `openspec update` output with legacy files present (per the contract-testing rule); parser degradation tests (no migration block, partially recognizable block); deletion-scope tests (only listed-and-existing files); dismissal-memory tests.
- **Docs:** `docs/feature-reference.md` Update action section; `docs/openspec-support.md` update row note; CHANGELOG.
- **Compatibility:** no new platform APIs beyond `WriteCommandAction`/VFS already in use; IntelliJ 2024.2+ unaffected. Behavior is additive to the existing Update flow; when no migration block is present, nothing changes.
