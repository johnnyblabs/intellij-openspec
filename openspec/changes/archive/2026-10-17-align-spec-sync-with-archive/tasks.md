## 1. Capture upstream archive runs

- [x] 1.1 Capture real `openspec archive c1 --yes` and `--yes --json` runs from CLI 1.14.3 into `fixtures/cli/archive-1.14.3/<case>/`, each with `main-before.md`, `delta.md`, `main-after.md` (absent when refused), `archive-human.txt`, `archive.json` and `exit.txt`. Use a fresh base project per case with isolated `HOME`/`XDG_*`. Cases:
  - `fenced-delta`, `fenced-main` (with its unclosed-fence and `~~~` variants);
  - `case-added`, `case-modified`, `scenario-drop`, `unpaired-renamed`;
  - `rename-ok`, `removed-ok`, `removed-case`;
  - `new-cap` (and a variant with a delta `## Purpose`);
  - `archive-naming`: dated name, already-dated name, existing target, and missing `changes/archive/`.

  Sanitize absolute paths, and replace the date with a token. Document provenance in the fixtures README. Verify: no machine paths leak, and each case's exit code and outcome match the expectation in the design.

## 2. Parsing

- [x] 2.1 Add `CodeFences.mask` (D1) and route every structural search in `SpecSyncService` through the masked copy. Verify: unit tests cover backtick and tilde fences, indentation, a longer closing fence, a wrong-character closer, and an unclosed fence running to the end of the file; the `fenced-delta` and `fenced-main` contract cases match `main-after.md`. Mutation check: searching the unmasked text fails both.
- [x] 2.2 Exact name matching via `normalizeName`, with `foldName` only for near-miss detection (D2). Verify: unit tests for the trailing-`#` strip and trim, and no case folding in matching; the `case-modified` contract case is refused as `not found`.

## 3. Refusals and application

- [x] 3.1 `SpecSyncResult.refusals` and the full refusal set with upstream's wording (D3): near-misses (ADDED, REMOVED, RENAMED target), MODIFIED not found, scenario loss, ADDED exists with different content (identical content is a no-op), RENAMED unpaired, missing source or existing target, and duplicates within or across sections. A REMOVED target already gone is a warning. Verify: each refusal contract case is refused, with the message matching the capture's message (allowing for the capability prefix); `removed-ok` and `rename-ok` apply and match `main-after.md`.
- [x] 3.2 All-or-nothing: `applySync` refuses when any result has a refusal, writing nothing. Verify: a two-capability change with one refusal writes neither spec. Mutation check: applying per capability fails it.
- [x] 3.3 Rebuild per D5 (preserved text, blank lines, append order, in-place rename) and the new-capability skeleton with the delta `## Purpose` carried over. Verify: every applying contract case equals its `main-after.md` byte for byte (CRLF normalized), including both `new-cap` variants.

## 4. Consumers

- [x] 4.1 The sync preview dialog lists refusals and disables apply; Bulk Archive's `Archiver.check` returns refusals only (D4). Verify: a headless test of the dialog's apply-enabled state with and without refusals; a Bulk Archive test where a REMOVED-already-gone change is no longer blocked but a scenario-dropping one is.
- [x] 4.2 `ChangeService.archiveChange` uses the dated target, collision refusal and `changes/archive/` creation (D6), with an injectable clock. Verify: tests for a dated name, an already-dated name, a refused existing target (change still active, nothing moved), and a missing `changes/archive/` created under `changes/` with no `openspec/archive/`; legacy `openspec/archive/` entries are still listed.

## 5. Docs and verify

- [x] 5.1 CHANGELOG `### Fixed` (user-facing: Sync and Archive no longer corrupt specs that contain code examples; changes OpenSpec would refuse are refused with the reason instead of being partly applied; archived changes get dated folders like OpenSpec's); update the `docs/feature-reference.md` Sync and Archive entries. Verify: entries present, and a leak grep over the diff is clean.
- [x] 5.2 Sandbox pass: sync a change whose delta contains a fenced example (spec stays intact), preview a scenario-dropping change (refusal shown, apply disabled), and archive a change (dated folder). Verify: each observed, recorded here.
  - Recorded 2026-10-17 (CLI 1.14.3, IDEA 2024.2 sandbox):
    - **Fence:** Sync of a change modifying "Plain note" in a spec whose other requirement contains a fenced `## Notes` / `### Requirement:` example changed only the two Plain-note lines; the fence was intact on disk.
    - **Refusal:** previewing a change that drops the "Note kept" scenario showed OpenSpec's refusal in red with Apply disabled, and nothing was written. Bulk Archive showed the same change as Blocked with the reason.
    - **Dated folder:** Bulk Archive moved `fenced-example` to `openspec/changes/archive/2026-10-17-fenced-example/`, and no `openspec/archive/` was created.
    - **Found and fixed during the pass:** Apply raised "Access is allowed from write thread only" when the Sync action refreshed the tool window from its background task (`refreshToolWindow` now hops to the UI thread; `RefreshToolWindowThreadingTest`). OK on a "No spec changes to apply" preview still ran apply and announced "Specs synced" (it now just closes). Both re-checked in a relaunched sandbox.
- [x] 5.3 Run `./gradlew build`, `./gradlew verifyPlugin` and `openspec validate align-spec-sync-with-archive --strict`, plus `openspec validate --specs` on this repo. Verify: all pass.
