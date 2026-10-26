## ADDED Requirements

### Requirement: Diff vs current spec from the specs chip

When the specs artifact is done and the change has delta specs, the specs chip's right-click menu SHALL offer "Diff vs current spec" with one entry per capability. Each entry SHALL open the diff of that capability's delta spec against its current main spec. The wording SHALL describe the change compared with the current spec, never what a generation changed.

#### Scenario: One entry per capability
- **WHEN** the user right-clicks the DONE specs chip of a change with delta specs for `auth` and `billing`
- **THEN** the menu SHALL include "Diff vs current spec" with entries for `auth` and `billing`, after "Open file", "Regenerate" and "Copy prompt"

#### Scenario: An entry opens the capability's diff
- **WHEN** the user chooses the `auth` entry
- **THEN** the plugin SHALL open the diff of `specs/auth/spec.md` in the change against the current main spec

#### Scenario: A new capability
- **WHEN** a capability has no main spec yet
- **THEN** its entry SHALL open the diff against an empty "New capability — no existing spec" side, as Preview Diff does

#### Scenario: Not offered without delta specs or on other chips
- **WHEN** the change has no delta specs, or the chip is not the specs artifact
- **THEN** the menu SHALL NOT include "Diff vs current spec"
