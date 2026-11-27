# Proposal

## Why

OpenSpec's `verify` workflow reports on a change and must not archive it: its template says "Do not implement tasks or archive the change during verification". The plugin's Verify dialog archives the change when you click OK, from both the Verify menu action and the tool window's Verify button, even on a custom profile that has `verify` but not `archive`. An action named after one workflow does another's job. Tracked in the linked tracker entry.

## What Changes

- **Verify reports and never archives on its own.** From the Verify menu action and the tool window's Verify button, the dialog's default button is **Close**.
- **A separate Archive… button** hands off to archiving when the profile includes `archive`. It reads "Archive anyway…" for a change with open tasks and is disabled when validation blocks archiving. On a profile without `archive` it isn't shown.
- **The Archive action's pre-flight is unchanged.** It shows the same check, and its OK archives, because there the user asked to archive.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `verify-workflow`: the Verify dialog has a report form (Close, plus a gated Archive… hand-off) and an archive pre-flight form (OK archives).

## Impact

- **Code:** `VerifyDialog` (two forms), `OpenSpecVerifyAction`, `WorkflowActionPanel.onVerify`, `OpenSpecArchiveAction` (pre-flight form).
- **Tests:** dialog button contract per form, state and profile; the two Verify entry points never archive without the hand-off.
- **Docs:** feature reference (Verify), CHANGELOG.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
