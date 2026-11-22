## Why

OpenSpec CLI 1.17 changed how `openspec update` treats files an earlier release left behind. It now removes unedited legacy files without `--force`, and its dry-run plan lists them as ordinary deletes. Before 1.17 the plan showed only a `legacy-artifacts` note. A new sweep also removes untouched files that no tool reads any more, under the new plan tool value `earlier-release`. The update preview already shows these deletes correctly. The legacy-cleanup flow does not. After the user removes the files they chose, it verifies by running a real `openspec update` with no preview. On 1.17 that run silently deletes the remaining unedited legacy files and still prints its "Files to remove" list, so the plugin reports files that are already gone as still pending. That run also breaks the rule that every update the plugin starts goes through the preview. This change is tracked in the linked tracker entry.

## What Changes

- **Read-only cleanup verification on CLI 1.15+.** After a cleanup, the plugin verifies with `openspec update --dry-run --json`, which writes nothing, instead of a real update. A plan without the `legacy-artifacts` note means the cleanup is complete. If the note remains, the pending files come from the read-only forced plan, as when the cleanup was first offered. Below 1.15, where there is no dry run, the real re-run stays as it is.
- **Finishing goes through the preview.** When verification finds the cleanup complete but the plan still has changes (for example, skills that were never set up because the CLI refused the cleanup), the plugin offers to run Update, which opens the usual preview.
- **Only legacy files are offered.** The forced plan's deletes from other tools (stale skills, the earlier-release sweep) are no longer offered as legacy files, and a read-only check never reports a kept file as regenerated.
- **Earlier-release group.** The preview labels changes whose tool is `earlier-release` as files left by an earlier OpenSpec release, so the group reads in words rather than as a tool id.
- **Real 1.17 captures** for the update-preview cases:
  - a project created by an old release with every legacy file unedited, where the plan deletes them with no note;
  - the same project with one file edited, where the plan has only the note;
  - an `earlier-release` sweep.
  Contract tests read the plugin's parser and preview model against them.
- **Documentation** of 1.17's cleanup in the update preview section of the feature reference and the CLI 1.17 notes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cli-update`:
  - Cleanup verification is read-only on CLI 1.15+ and offers Update through the preview.
  - The regeneration-loop check applies only to the re-run below 1.15; a read-only check is never a loop.
  - The cleanup flow offers only OpenSpec's own legacy deletes (tool `openspec`) from the forced plan.
  - The preview names the earlier-release group and shows unasked legacy deletes as ordinary deletes.

## Impact

- `UpdateLegacyCleanupService` (the verification step), `UpdatePreviewModel` and `UpdatePreviewDialog` (the group label), and their tests.
- `scripts/capture-update-dry-run-fixtures.py` gains the new cases; new fixtures go under `fixtures/cli/1.17.0/update-dry-run/`.
- `docs/feature-reference.md` (update preview), `docs/cli-versions/1.17.md` and `CHANGELOG.md`.
- No change for CLIs below 1.15. Platform compatibility is unaffected: the plugin still supports IntelliJ IDEA 2024.2 and later.
