## 1. Contract

- [x] 1.1 Capture `openspec archive c1 --yes --json` from CLI 1.5.0 (via `npx -y @fission-ai/openspec@1.5.0`, isolated `HOME`/`XDG_*`) for one success and one refusal, into `fixtures/cli/archive-1.5.0/`, extending `scripts/capture-archive-fixtures.py` with a case filter. Add `--skip-specs` success captures for 1.14.3 and 1.5.0. Verify: shapes recorded and sanitized; the fixtures README gives provenance.
- [x] 1.2 `ArchiveJson.parse(stdout, exitCode)` with the success and refusal records (D2). Verify: a contract test over every `archive-1.14.3` and `archive-1.5.0` case maps `archivedAs`, `specsUpdated`, warnings, and `message`/`fix`; non-zero exit with junk becomes a refusal carrying stderr. Mutation check: ignoring the exit code fails the refusal cases.

## 2. Archive strategy

- [x] 2.1 `ChangeService.archiveChange(Change, boolean syncSpecs)` returning `ArchiveOutcome`, throwing `ArchiveRefusedException`; the CLI path on 1.5+ with `--skip-specs` when not syncing, then a VFS refresh of `openspec/`; the fallback (plugin sync plus dated move) otherwise; asserts it is off the UI thread (D1, D4). Verify: tests with a stubbed runner assert the exact argv for both flags, the 1.4.x and no-CLI fallback, and a refusal surfacing `message`/`fix` with the change left active. Mutation check: dropping `--skip-specs` or the version check fails a test.

## 3. Callers

- [x] 3.1 The panel's Archive, `OpenSpecArchiveAction` and `OpenSpecVerifyAction` run the archive in a background task and report through `invokeLater`; "Archive Without Syncing" passes `syncSpecs=false` (D3). Verify: a threading test that the archive never runs on the UI thread (it fails on the current code); the modality guard tests still pass.
- [x] 3.2 Bulk Archive's production archiver: one `archiveChange(change, true)` per change off the UI thread, `sync` a no-op on the CLI path, and a CLI refusal shown as Failed with its message (D3). Verify: `BulkArchiveProductionArchiverTest` with a stubbed CLI covers Done, Failed-with-CLI-message, and the fallback still syncing first.

## 4. Docs and verify

- [x] 4.1 CHANGELOG (`### Changed`: archives use your OpenSpec CLI when it is 1.5 or later, with its validation and messages) and the `docs/feature-reference.md` Archive and Bulk Archive entries. Verify: entries present; a leak grep over the diff is clean.
- [x] 4.2 Sandbox pass with CLI 1.14.3: archive from the panel (dated folder, notification names it); archive without syncing (main spec unchanged); a change the CLI refuses (its message shown, change still active); Bulk Archive of two changes, one refused. Record results here.
  - Recorded 2026-10-17 (real CLI 1.14.3 detected in an IDEA 2024.2 sandbox):
    - **Bulk Archive:** a change dropping a scenario showed Blocked from the plugin's preview before any CLI ran. `cli-archive` and `bulk-ok` came out Done, archived by the CLI into `changes/archive/2026-10-17-*`, and their requirements were merged into `notes/spec.md`. `bulk-invalid` (a requirement with no scenario, which the plugin's merge accepts) came out Failed with the CLI's validation reason and stayed active.
    - **Panel, Archive Without Syncing:** archived `skip-archive` to `2026-10-17-skip-archive` and left `notes/spec.md` unchanged, so `--skip-specs` reached the CLI. No `openspec/archive/` was created.
    - **The panel's refusal path** is covered by the stubbed-CLI tests rather than the sandbox: with `--skip-specs` the CLI doesn't merge specs, so the demo's refusals don't trigger from that button.
    - **The post-archive VFS refresh** is covered by `ArchiveCliVfsRefreshTest`, a real-disk test in which a stubbed CLI moves the folder behind the IDE's back. It fails without the refresh. It was not separately checked by eye in the sandbox.
    - **Demo note:** the panel's Archive stays disabled while the plugin's scaffolding check treats an artifact with under 20 characters of text as unwritten. The first demo's one-line `design.md`/`tasks.md` tripped it; the realistic files used here did not.
- [x] 4.3 Run `./gradlew build`, `./gradlew verifyPlugin` and `openspec validate delegate-archive-to-cli --strict`. Verify: all pass.
