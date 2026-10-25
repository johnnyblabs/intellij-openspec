# Proposal

## Why

When the Browse preview can't show a change's deltas, it says "Consolidated deltas require the OpenSpec CLI. Install or configure the CLI…". It says this even when the CLI is installed and has just answered. Any non-zero exit from `openspec show <change> --type change --json`, and any failure to parse its answer, is treated as "no CLI". Tracked by the linked tracker entry.

Captured CLI output shows the two common causes:

- **A change with no proposal yet.** CLI 1.15.0 answers with an error envelope: `Change "<name>" has no proposal.md yet. Run "openspec status --change <name>" to see which artifact comes next.` This is every change between "New change" and writing its proposal, so a user who has just created a change is told to install a CLI they already have.
- **A renamed or deleted change directory.** 1.15.0 answers `Change "<name>" not found at …/proposal.md`. CLI 1.3.1, which the plugin still supports, gives no JSON. Its reason is on stderr as `✖ Error: …`, after a harmless `Warning: Ignoring flags not applicable to change: scenarios` line.

The workflow panel already shows the CLI's reason in this situation. The Browse preview was missed.

## What Changes

- **Two different placeholders.**
  - **When the CLI couldn't be run** (none is detected, or the process can't start): the preview keeps today's "requires the OpenSpec CLI" placeholder.
  - **When the CLI ran but returned no usable answer**, the preview shows a new **Can't read this change** placeholder, with the CLI's own reason. That covers an error envelope, an unreadable answer or a non-zero exit.
- **The reason is the CLI's, cleaned up.**
  - **CLI with a JSON envelope:** its `message`, plus its `fix` when it gives one.
  - **CLI that writes to stderr instead:** the `✖ Error:` line without that prefix, with the known "Ignoring flags not applicable" warning dropped.
  - **Fallback:** "The OpenSpec CLI exited with code N and no readable output."
- **Same pane as the other states.** The new placeholder carries the pane's "Change Deltas" header and the open-in-editor link like the other states, and its own marker. Its text is HTML-escaped.
- **Not cached.** The reason may be fixed a moment later (for example by writing the proposal), so the next selection asks the CLI again.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `spec-viewer`:
  - **Consolidated change-deltas view:** gains the "CLI rejects the change" behaviour, and the "CLI unavailable" scenario now means only that the CLI can't be run.
  - **The preview pane states its purpose:** lists the new placeholder among the states that carry the header.

## Impact

- **Code:**
  - `OpenSpecToolWindowPanel.renderChangeDeltasPreview`: routes the CLI result through `CliJson.read`, the shared reader the workflow panel uses.
  - `SpecPreviewRenderer`: gains the new placeholder and its marker.
  - A small reason cleaner for stderr-only CLIs.
- **Tests:**
  - **Contract tests** against captured `show <change> --type change --json` failures: `fixtures/cli/1.15.0/errors/show-change-{missing,noproposal}.*` (error envelopes) and `fixtures/cli/1.3.1/errors/show-change-missing.*` (stderr only). Re-capture them with `scripts/capture-error-fixtures.py`.
  - **Renderer tests.**
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported; no platform APIs change.
- **Out of scope:** the issue's malformed `.openspec.yaml` case. Captured 1.15.0 output shows `show` succeeds there, so it needs no handling.
