## Why

When `DeltaSpecInspection` flags a MODIFIED requirement as missing scenarios, the user must manually find the matching requirement in the main spec, copy the entire block, and paste it into the delta spec. This is mechanical and error-prone — the plugin already knows which capability the delta spec belongs to and can locate the main spec automatically.

## What Changes

- Add a `LocalQuickFix` to `DeltaSpecInspection` for the MODIFIED-requirement-missing-scenarios error
- The quick-fix reads the matching requirement block from `openspec/specs/<capability>/spec.md` and inserts it after the `### Requirement:` heading in the delta spec
- The user can then edit the pasted content to reflect the intended changes

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `validation`: Add a quick-fix action to the delta spec inspection for MODIFIED requirements missing scenarios

## Impact

- `DeltaSpecInspection.java` — add `LocalQuickFix` implementation and pass it instead of `null` for the MODIFIED case
- No new dependencies, no API changes
