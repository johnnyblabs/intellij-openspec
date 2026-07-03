## Why

Phases 1–3 shipped the core OpenSpec 1.4.x support (schema/version-aware surfaces, a rebuilt Verify, and the coordination tool-window). Two concrete defects remain in the 1.4 tail, both tracked in the linked tracker entries:

1. **Verify drops `[~]` partial tasks.** The completeness check counts only `- [ ]` (incomplete) and `- [x]` (complete) checkboxes and derives the total as their sum, so an in-progress `- [~]` task is excluded from both the numerator and the denominator — silently under-reporting completeness. OpenSpec 1.4 task lists use `[~]` for in-progress work, so a change can report "clean / ready to archive" while `[~]` work remains.
2. **The "Version override" dropdown offers values the config-format axis doesn't model.** The Settings version-override dropdown offers `1.3.0` and `1.4.0`, but the plugin's config-format version axis is deliberately pinned at `1.2.0` (independent of the auto-detected CLI version, and unchanged across CLI 1.2.x/1.3.x/1.4.x). Selecting `1.3.0`/`1.4.0` is silently ignored — freshly scaffolded config still writes `version: 1.2.0` — and a literal `version: 1.4.0` in config would trip a spurious "unrecognized version" validator warning. The override presents CLI-looking versions as if they were config-format choices, conflating the two axes.

## What Changes

- **Verify — count `[~]` partial tasks as not-done.** Treat `- [~]` checkboxes as incomplete for the "is this ready to archive?" question: they count toward the task total and toward the incomplete/blocking set (same archive-gating effect as `- [ ]`), and are surfaced distinctly (e.g. an "in-progress" count) rather than vanishing. Completeness must no longer report a change as ready while `[~]` work remains.
- **Version override — stop offering config-format values the axis doesn't distinguish.** Remove the misleading `1.3.0`/`1.4.0` presets from the version-override dropdown so it only presents/accepts what the config-format axis actually models. The config-format pin at `1.2.0` and the `VersionSupport` model are intentionally left unchanged — adding `1.3`/`1.4` to that axis would contradict the deliberate pin. The CLI-version axis remains separately auto-detected.
- **CLI floor unchanged.** The CLI floor stays at `1.3.0` (graceful degradation for older clients is deliberate). Bumping the floor to 1.4 is explicitly a followup, not part of this change.

Out of scope (followups): raising the CLI floor to 1.4, reworking the override into a CLI-version knob, surfacing `init --profile`, and workspace open / external-workspace link.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `verify-workflow`: the completeness check's task-counting requirement changes to account for `[~]` partial/in-progress tasks (counted toward the total and treated as not-done/blocking).
- `workflow-schema-context`: the independent-version-axis requirement gains a guarantee that the version-override UI only presents config-format values the axis actually models, rather than CLI-looking values it silently ignores.

## Impact

- **Code:** `VerificationService` (task-checkbox counting + report wording) and the version-override dropdown presets in the settings UI. Plus their unit tests. The `VersionSupport` model and the `1.2.0` config-format pin are intentionally unchanged.
- **CLI contract:** no change — the CLI floor stays `1.3.0`; no new CLI commands are invoked.
- **Platform compatibility:** no change — continues to support IntelliJ IDEA 2024.2 and later. Verification I/O stays off the EDT.
- **Tracker reconciliation (act on at archive):** the Verify-vs-`verify-change` alignment tracker item is behavior-aligned — the plugin's Verify intentionally mirrors `verify-change` in shape rather than delegating to the CLI — and should be closed as done, consistent with its already-Done work-item shadow.
