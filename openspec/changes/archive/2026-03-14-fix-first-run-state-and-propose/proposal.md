## Why

Two bugs in the first-run experience:
1. `GettingStartedPanel.detectState()` shows "Create your first change" even when the project has archived changes — it only checks for *active* changes, ignoring archives
2. The setup wizard's "Create Your First Change" button opens `ProposeChangeDialog` directly with `dialog.show()` but never reads the result or creates files. The `OpenSpecProposeAction` (which actually calls `ScaffoldingService.createChange()`) is bypassed entirely.

## What Changes

- Fix `detectState()` to return `READY` when archived changes exist (project has been used before)
- Fix wizard's "Create Your First Change" to invoke the `OpenSpec.Propose` action instead of opening the dialog directly — this runs the full propose flow including file creation and tool window refresh

## Capabilities

### New Capabilities
_None_

### Modified Capabilities
_None — bug fixes only, no spec-level behavior changes_

## Impact

- `GettingStartedPanel.java` — `detectState()` checks archive directory
- `SetupWizardDialog.java` — wizard done step invokes action instead of raw dialog
