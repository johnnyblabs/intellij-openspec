## Context

`UpdateLegacyCleanupService` offers legacy files for removal. After the user removes the files they picked, `verifyCleanup` re-runs `openspec update` and reads its text output with `UpdateOutputParser.parseLegacyCleanup`, which keys on the "Files to remove" header. Real CLI 1.17.0 runs, each in an isolated environment, show three things:

- When every legacy file is unedited, a plain non-interactive `openspec update` deletes them without `--force`. It still prints the "Files to remove" block, followed by "Cleaned up legacy files:".
- When any one legacy file is edited, the CLI removes nothing, prints the same block and "Run with --force…", and the plan carries the `legacy-artifacts` note. Cleanup is all-or-nothing.
- The dry-run plan lists unedited legacy deletes with `tool:"openspec"`. A separate sweep lists untouched files that no tool reads any more with `tool:"earlier-release"`. One case that reaches the sweep is a former tool root, such as `.agent/workflows/opsx-*.md` from an Antigravity project made by 1.6 and updated on skills-only delivery. 1.17 adds no note code and no action value, and `earlier-release` is its only new tool value.

So on 1.17 the verification re-run can delete files the user never saw, and its "Files to remove" block no longer means the files are still there. The cleanup flow is offered only when the plan has the `legacy-artifacts` note, which means at least one file was edited. That is the case in which the user removes some files and the re-run then deletes the rest.

The update preview path (`UpdatePreviewService`) already starts every CLI run with `OPEN_SPEC_INTERACTIVE=0` and reads the cleanup list from the read-only forced plan (`update --dry-run --force --json`) through `legacyDeletes`.

## Goals / Non-Goals

**Goals:**
- On CLI 1.15.0+, cleanup verification writes nothing, and it reports pending files only when the CLI's plan says they are pending.
- Any real update that follows a cleanup goes through the preview.
- The preview names the `earlier-release` group in words.
- Pin 1.17's cleanup plans with real captures.

**Non-Goals:**
- Any change below CLI 1.15, where there is no dry run. The real re-run stays, and those CLIs never delete legacy files unasked.
- Telling the user which legacy file was edited. The plan doesn't say which one, and the plugin won't fingerprint files itself.
- Changing what `openspec update` does. The plugin only reads its plan.

## Decisions

- **Verify through the preview service.** Add a read-only verification method to `UpdatePreviewService`. It reuses that service's non-interactive dry-run helper and `legacyDeletes`, so verification and the original offer read pending files the same way. The steps:
  1. Run `update --dry-run --json`. If it fails, the cleanup could not be verified.
  2. If the plan has no `legacy-artifacts` note, nothing is pending.
  3. If the note remains, run `update --dry-run --force --json` and take its in-project deletes as the pending set. If that run fails, the cleanup could not be verified.

  The method also reports whether the first plan has any other changes. `UpdateLegacyCleanupService.verifyCleanup` calls it when `supportsPreview()` is true, and keeps today's real re-run otherwise. Both paths feed the same `Verification` record, so the regeneration-loop check and the reporting code stay as they are. Both CLI runs happen off the EDT, and the report goes back through `invokeLater`.
  - *Alternative:* keep the real re-run and parse 1.17's "removed" and "kept" wording. Rejected: the run would stay un-previewed, and it would key on text the CLI already reworded once (1.14).
- **A read-only check is never a regeneration loop.** The loop check compares what the CLI still lists with what was offered. After a real re-run, an overlap means the CLI recreated the files. After a dry run nothing ran, so an overlap means a file was kept or failed to delete. The read-only path reports those files as still pending and never records them as regenerating.
- **Legacy files are OpenSpec's own cleanup deletes.** Every captured forced plan (1.15.0 and 1.17.0) lists the legacy cleanup with `tool:"openspec"`, while stale-skill and `earlier-release` deletes carry other tools. `legacyDeletes` keeps only `openspec` deletes, and one helper reads them for both the original offer and the check. When the note remains but nothing is listed, the plugin says OpenSpec still reports legacy files instead of calling the cleanup complete.
- **Any non-empty plan means Update remains.** Changes, notes or failures after a clean check all lead to the Update offer, so a plan with a failure is never reported as a clean state; the preview shows the failure.
- **Offer Update rather than running it.** When verification is clean but the plan still has changes, the completion notification gets an "Update OpenSpec…" action (the label other notices already use for that action). It invokes the registered `OpenSpec.Update` action, so the user gets the same preview, console output and legacy handling as from the menu. The plugin never runs a real update the user hasn't seen.
- **Label the group in the model.** `UpdatePreviewModel.Group.label()` maps the tool value `earlier-release` to "Left by an earlier OpenSpec release (N)". The model test asserts the label, and the dialog takes it from the model, so no Swing logic is added. Other tool ids are shown as before.
- **Capture with the seed CLIs upstream names.** The new cases in `scripts/capture-update-dry-run-fixtures.py`:
  - `legacy-unedited`: init with 0.12.0 for `claude`. The plan deletes the legacy files with no note.
  - `legacy-one-edited`: the same, with one command file edited. The plan has only the note.
  - `earlier-release`: init with 1.6.0 for `antigravity`, then `config set delivery skills` in the isolated config.

  Seed CLIs run through `npx` with a scratch cache, as the existing stale-skills seed does. The CLI under test runs through the installed package file. Fixtures go to `fixtures/cli/1.17.0/update-dry-run/`.

## Risks / Trade-offs

- [A clean verification on 1.17 no longer runs the update that sets up skills] → The "Update…" action on the notification finishes it through the preview, one click away.
- [An extra dry run when the note remains] → It writes nothing and matches how the cleanup was first offered. Two short CLI calls run off the EDT.
- [The `earlier-release` value could be renamed upstream] → It is a display label only. An unknown tool id falls back to the raw value, and the captured fixture's contract test catches a rename.
