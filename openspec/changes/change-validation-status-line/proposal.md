# Proposal

## Why

The Change Deltas pane shows what a change will do to your specs but not whether the change is healthy, so it can read as a plain markdown preview. A validation line for the selected change, taken from the OpenSpec CLI, tells you at a glance whether it will archive. A one-line legend says what archive does with each delta operation. Tracked by the linked tracker entry.

## What Changes

- **A validation line under the Change Deltas header.** For a selected active change, the pane shows the CLI's verdict for that change: **"Validate: passed"** or **"Validate: N errors, M warnings"**. It comes from `openspec validate <change> --type change --json`, the same run and reading the **Validate** action uses for one change, so the two never disagree.
- **A "Show issues" link** next to the line runs Validate for that change. Its issues appear where Validate always reports them.
- **Cached and refreshed with the deltas.** The verdict is fetched with the deltas the first time a change is selected, and it is cached with them. That cache is already cleared on any file change under `openspec/`, including the main specs, which the CLI verdict depends on. So the line is never stale and it adds no process run on a cached selection.
- **Without the CLI, the plugin's own check is shown, and it says so.** In the "needs the CLI" placeholder, the line reads **"Built-in check: …"**, from the plugin's built-in validator. It is never labelled "Validate", because the built-in check doesn't reproduce some of the CLI's warnings.
- **No line when the CLI can't answer.** When the CLI rejects the change (the pane's "Can't read this change" state), or its validate run fails or returns no verdict, the line is left out rather than guessed.
- **A legend of the delta operations.** When the change has at least one delta, one line says what archive does with each: ADDED adds the requirement to the main spec, MODIFIED replaces the whole requirement (so it must keep the requirement's scenarios), REMOVED deletes it, and RENAMED renames it and keeps its text.
- Nothing is written or stored. There is no per-delta or per-spec status.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `spec-viewer`: "Client-owned state only" allows the built-in check as a labelled fallback when no CLI is available. New requirements cover the change validation line and the delta operation legend.

## Impact

- **Code:**
  - `OpenSpecToolWindowPanel`: the change-deltas render also runs change-scoped validate, and links "Show issues" to a change-scoped Validate run.
  - `SpecPreviewRenderer`: renders the validation line and the legend.
  - `OpenSpecValidateAction`: exposes its existing change-scoped CLI arguments and run for the pane.
- **Tests:**
  - contract tests over newly captured single-change `validate --json` output: valid, an error, and warnings only;
  - unit tests for the line and the legend.
- **Docs:** the feature reference's Change Deltas section and its "On-model" line, the README feature line, and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. No new platform APIs.
- **Out of scope:**
  - a per-delta or per-spec status;
  - strict validation in the pane (the line uses the CLI's default mode);
  - a pane-specific list of issues.
