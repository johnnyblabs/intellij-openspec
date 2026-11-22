## 1. Capture 1.17.0 cleanup plans (real CLI output, isolated env, sanitized to /fixture)

- [x] 1.1 Add the `legacy-unedited`, `legacy-one-edited` and `earlier-release` cases to `scripts/capture-update-dry-run-fixtures.py`. Seed them with 0.12.0 and 1.6.0 through `npx` with a scratch cache, and run the CLI under test through its installed package file.
- [x] 1.2 Capture the three cases with 1.17.0 into `fixtures/cli/1.17.0/update-dry-run/`, plus the forced plan for `legacy-one-edited`. Check there are no cache or machine paths. Record the cases and their provenance in the fixtures README.

## 2. Contract tests

- [x] 2.1 In `UpdatePlanContractTest`, assert from the captures that:
  - `legacy-unedited` deletes the legacy files with no `legacy-artifacts` note;
  - `legacy-one-edited` has the note and no legacy deletes;
  - `earlier-release` carries changes with that tool value.
- [x] 2.2 In `UpdatePreviewModelTest`, assert from the captures that:
  - the unedited deletes sit in their tool's group with Cancel the default;
  - the `earlier-release` group is labelled "Left by an earlier OpenSpec release (N)" with its count;
  - other tool ids keep their raw label.

## 3. Earlier-release label

- [x] 3.1 Map the `earlier-release` tool value to its label in `UpdatePreviewModel.Group.label()`, and check that the dialog shows the model's label.

## 4. Read-only cleanup verification

- [x] 4.1 Add the read-only verification to `UpdatePreviewService`:
  - run `update --dry-run --json`, and if the note remains, run `update --dry-run --force --json` for the pending set;
  - start both non-interactively;
  - report whether other changes remain;
  - treat a failed run as unverified.
- [x] 4.2 In `UpdateLegacyCleanupService.verifyCleanup`, use it when `supportsPreview()` is true, and keep the real re-run below 1.15. Feed both into `Verification` so the regeneration-loop and reporting logic is unchanged.
- [x] 4.3 When a read-only verification is clean and the plan still has changes, add an "Update…" action to the completion notification that invokes the registered `OpenSpec.Update` action.
- [x] 4.4 Tests:
  - on 1.15+ the verification never runs `update` without `--dry-run`;
  - no note means clean;
  - a remaining note yields the forced plan's in-project deletes as pending;
  - a failed dry run or forced dry run is unverified;
  - the regeneration-loop check reads the read-only pending set;
  - the below-1.15 path still re-runs `update`;
  - the Update offer appears only when changes remain.

## 5. Documentation

- [x] 5.1 Update the update preview section of `docs/feature-reference.md`. Cover 1.17's unasked removal of unedited legacy files, the earlier-release group, and read-only verification with the Update offer.
- [x] 5.2 Update `docs/cli-versions/1.17.md` row 3 and its notes to say the preview and cleanup now handle 1.17's cleanup.
- [x] 5.3 Add a user-facing `## Unreleased` entry to `CHANGELOG.md`.

## 6. Verify

- [x] 6.1 `./gradlew build` passes, including the coverage floor.
- [x] 6.2 `openspec validate --all --strict` passes.
