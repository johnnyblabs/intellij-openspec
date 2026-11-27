# Design

## Context

The refusal comes from two places with the same text. The CLI's `archive --json` gives `status[0].fix`, and the plugin's merge (`SpecMerge.retirement`) builds the same message and fix word for word (contract-tested in `RetirementContractTest`). Upstream names the marker only when it is the one thing missing (`archive.js`, `retirementHint`), so the fix text is the signal. The plugin doesn't re-derive the condition.

## Decisions

### D1. The gate reads the refusal text

`RetireMarkerFix.capabilities(String refusal)` returns the capability ids the action would retire, or none. It reads each id from "This change removes the last requirement '<id>' has." when the text contains "add `retire_capabilities: true`". When the text ends in "The marker present now cannot be honored (<reason>).", it returns ids only if the reason starts with `retire_capabilities:`. Pure and string-based, so it applies to CLI and plugin refusals alike and is tested against every captured refusal. Releases below 1.8 never print the hint, so they never match.

### D2. The edit is checked before it is written

`RetireMarkerFix.withMarker(String yaml)`:
- If a top-level `retire_capabilities:` line exists, it replaces that line, and any indented lines continuing it, with `retire_capabilities: true`.
- Otherwise it appends the line, adding a final newline if the file lacks one.
- Everything else stays byte for byte.

The result is then read with `RetirementMarkers.parse`, so the CLI's rules apply. The edit is written only if that read declares the marker. Otherwise the plugin says the marker can't be added automatically and leaves the file as it is. A rewrite never makes a file worse.

### D3. Confirm, write, re-preview

The confirmation is a `Messages` yes/no dialog with a warning icon: "Retire capability 'foo'? Adding retire_capabilities: true to <change>/.openspec.yaml lets Sync and Archive delete specs/foo/spec.md. You'll see a preview first. Restore a committed spec with git checkout." The buttons are "Add Marker" and "Cancel", and the dialog lists every id.

The write is a `WriteCommandAction` on the file's `Document`, so Undo covers it and an open editor stays in sync. The file is saved afterwards, because the CLI reads disk.

The preview is then recomputed in a background task, and a fresh `SyncPreviewDialog` opens. The action deletes nothing.

### D4. Surfaces

- `SyncPreviewDialog`'s refused panel gets an `ActionLink`. It closes the dialog, then runs D3.
- The archive-failure notification, raised by the tool window, Archive and Verify, gets a `NotificationAction` through `OpenSpecNotifier`. It runs D3. The archive is not re-run, so archiving stays a separate step.
- `BulkArchiveDialog` row problems matching D1 get a hint: "Use Sync Specs on this change to add the marker." A batch dialog doesn't write a marker that leads to a deletion.
- `ArchiveSyncPrompt` is unchanged: it is a three-button `Messages` dialog, and the two surfaces above cover every path.

## Risks

- **Upstream rewords the hint.** The gate then stops matching, and the action is no longer offered, which is the safe failure. The contract test over captured refusals fails on re-capture, which flags it.
- **Flow-style or anchored YAML.** If the D2 rewrite isn't honored, nothing is written.
