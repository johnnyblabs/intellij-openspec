# Design

## Context

The refusal comes from two places with the same text. The CLI's `archive --json` gives `status[0].fix`, and the plugin's merge (`SpecMerge.retirement`) builds the same message and fix word for word (contract-tested in `RetirementContractTest`). Upstream names the marker only when it is the one thing missing (`archive.js`, `retirementHint`), so the fix text is the signal. The plugin doesn't re-derive the condition.

## Decisions

### D1. The offer needs the refusal's hint and an edit that would be honored

Two checks, both required:

1. **The text gate.** `RetireMarkerFix.asksForMarker(String refusal)` is true when the text contains "This change removes the last requirement '<id>' has." and "add `retire_capabilities: true`". When the text ends in "The marker present now cannot be honored (<reason>).", the reason must start with `retire_capabilities:`. This check is pure and string-based, so it applies to CLI and plugin refusals alike and is tested against every captured refusal. Releases below 1.8 never print the hint, so they never match.
2. **The edit check.** The change's `.openspec.yaml`, rewritten as in D2, must be read as declaring the marker (`RetirementMarkers.parse` with the CLI's schema lookup).

The text gate alone is not enough. The plugin's own refusal leaves the reason out where no capture pins its wording: an invalid project schema override, an override with no CLI to validate it, or a metadata field failing in an uncaptured way. Its text then looks like a plain "marker missing", and the edit check is what keeps the action from being offered there.

The edit check may run the CLI (`schema validate`), so the offer is decided off the UI thread, beside the work that produced the refusal:
- the sync computation before the preview opens;
- `ArchiveDispatch.run`'s background step before the failure is reported;
- `BulkArchiveDialog`'s per-change check.

### D1a. The confirmation names every spec the marker makes deletable

The marker applies to the whole change, but the CLI throws at the first spec it refuses (`archive.js`, `ArchiveBlockedError` inside the per-spec loop), so its refusal names one capability. The ids in the confirmation come from the plugin's merge instead. It runs each of the change's delta specs with the marker as declared, and lists every capability that would then retire. That runs in the same background step, so a two-capability change names both specs, from either surface.

### D2. The edit is checked before it is written

`RetireMarkerFix.withMarker(String yaml)`:
- If a top-level `retire_capabilities:` line exists, it replaces that line, and any indented lines continuing it, with `retire_capabilities: true`.
- Otherwise it appends the line, adding a final newline if the file lacks one.
- Everything else stays byte for byte.

The result is then read with `RetirementMarkers.parse`, so the CLI's rules apply. The edit is written only if that read declares the marker. Otherwise the plugin says the marker can't be added automatically and leaves the file as it is. A rewrite never makes a file worse.

### D3. Confirm, write, re-preview

Just before writing, D2 runs again on the file's current text. If the check no longer passes, nothing is written, and the plugin says why.

The confirmation is a `Messages` yes/no dialog with a warning icon, with "Add Marker" and "Cancel" buttons. OpenSpec leaves the deletion to the author, since it is "recoverable only from git", so the dialog states everything the marker does:
- each spec it makes deletable, by Sync Specs, archive and OpenSpec's agent skills;
- that it replaces an explicit `retire_capabilities: false` (OpenSpec prints the same hint then; captured as `marker-false`);
- that it covers the whole change and stays, so a later edit that empties another capability retires that one too;
- that it is written now, Apply does the deleting, and cancelling the preview leaves it in place;
- that a deleted spec can be restored from git only if it was committed.

A trailing comment on a replaced line is kept.

The write is a `WriteCommandAction` on the file's `Document`, so Undo covers it and an open editor stays in sync. The file is saved afterwards, because the CLI reads disk.

The preview is then recomputed in a background task, and a fresh `SyncPreviewDialog` opens. The action deletes nothing.

### D4. Surfaces

- `SyncPreviewDialog`'s refused panel gets an `ActionLink`. It closes the dialog, then runs D3.
- The archive-failure notification, raised by the tool window, Archive and Verify, gets a `NotificationAction` through `OpenSpecNotifier`. It runs D3. The archive is not re-run, so archiving stays a separate step.
- `BulkArchiveDialog` row problems matching D1 get a hint: "Use Sync Specs on this change to add the marker." A batch dialog doesn't write a marker that leads to a deletion.
- `ArchiveSyncPrompt` is unchanged: it is a three-button `Messages` dialog, and the two surfaces above cover every path.

## Risks

- **Upstream rewords the hint.** The gate then stops matching, and the action is no longer offered, which is the safe failure. The contract test over captured refusals fails on re-capture, which flags it.
- **Flow-style or anchored YAML, or a quoted key.** D2 matches `retire_capabilities:`, `'retire_capabilities':` and `"retire_capabilities":` at column 0. Any other spelling isn't honored after the rewrite, so the action is not offered.
- **Several capabilities in one change.** D1a names each one, so the user never confirms one deletion and gets two.
- **The fix differs from upstream's "then rerun".** The plugin opens the Sync preview, and its own merge deletes the spec. A later `openspec archive` of the change then takes the already-retired path and succeeds from 1.8 (captured as `already-retired`). If the marker is removed in between, that archive refuses, as it would for any REMOVED-only delta with no main spec.
- **Upstream's hint can name a marker that wouldn't help.** With `retire_capabilities: true` and an unknown schema, the CLI still says "add `retire_capabilities: true`", with the schema as the reason it can't be honored (`marker-unknown-schema`). The text gate requires a reason about the marker's own value, so the plugin doesn't offer the action there.
