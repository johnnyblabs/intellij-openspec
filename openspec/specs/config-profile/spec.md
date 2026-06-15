# Config Profile

## Purpose
Display and management of OpenSpec workflow profiles (`core`, `custom`) from the Settings panel, with CLI delegation for profile switching and a post-switch prompt to run `openspec update` (the OpenSpec two-step profile change process).

## Requirements

### Requirement: Config profile display in Settings

The plugin SHALL display the active OpenSpec config profile in a dedicated "Config Profile" section within the Settings panel, showing the profile name, description, and active workflow list.

#### Scenario: Profile section with CLI available
- **WHEN** the user opens OpenSpec Settings and the CLI is detected
- **THEN** the Config Profile section SHALL display the profile name, description, and a list of active workflows retrieved via `openspec config profile --json`

#### Scenario: Profile section without CLI
- **WHEN** the user opens OpenSpec Settings and the CLI is not detected
- **THEN** the Config Profile section SHALL display the locally-stored profile name with a label indicating "CLI required for profile details"

#### Scenario: Profile section updates on selection change
- **WHEN** the user selects a different profile in the workflow profile combo
- **THEN** the Config Profile section SHALL refresh to show the selected profile's details

### Requirement: Profile switch via CLI delegation

The plugin SHALL delegate profile changes to the OpenSpec CLI via `openspec config profile <name>` to ensure the CLI's config.yaml remains the source of truth.

#### Scenario: Successful profile switch
- **WHEN** the user selects a different profile and clicks Apply
- **THEN** the plugin SHALL run `openspec config profile <name>` via CliRunner and persist the new profile to OpenSpecSettings upon success

#### Scenario: Profile switch CLI failure
- **WHEN** the CLI command `openspec config profile <name>` fails (non-zero exit code or CLI unavailable)
- **THEN** the plugin SHALL show a warning notification with the error message and retain the previous profile value in OpenSpecSettings

#### Scenario: Profile switch without CLI
- **WHEN** the CLI is not detected and the user changes the profile
- **THEN** the plugin SHALL persist the profile value locally to OpenSpecSettings without CLI delegation and show an informational notification that the change is local-only

### Requirement: Workflow toggles display

The plugin SHALL display active workflows for the current profile as a read-only list in the Config Profile section.

#### Scenario: Workflow list rendering
- **WHEN** the Config Profile section loads with CLI-provided profile data
- **THEN** it SHALL display each active workflow name as a read-only labeled item

#### Scenario: Empty workflow list
- **WHEN** the profile has no active workflows or the data is unavailable
- **THEN** the workflow list SHALL display "No workflow information available"

### Requirement: Workflow profile combo limited to CLI-known presets

The plugin's workflow profile combo SHALL contain only the empty string (default — uses CLI's active profile) and named presets the OpenSpec CLI accepts via `openspec config profile <preset>`. Today the only such preset is `core`. The combo SHALL NOT contain `custom` as a switchable target, because `custom` is an implicit display label for "active workflows do not match any named preset," not a preset switch destination.

#### Scenario: Combo population matches CLI-accepted presets
- **WHEN** the plugin populates the workflow profile combo
- **THEN** the combo's preset list SHALL be sourced from `OpenSpecSettingsPanel.WORKFLOW_PROFILE_PRESETS` and SHALL contain exactly `[""]` plus the CLI-accepted preset names (today: `["", "core"]`); a `"custom"` entry SHALL NOT be present

#### Scenario: Apply with orphan still selected is a no-op
- **WHEN** OpenSpecSettings holds a `profile` value not in the CLI-accepted preset list (e.g., a legacy `"custom"` from a prior plugin version) and the user clicks Apply without changing the combo selection
- **THEN** the plugin SHALL NOT invoke `openspec config profile <orphan>`, the persisted profile value SHALL remain unchanged, and no CLI rejection notification SHALL appear

#### Scenario: Switch to a real preset clears the orphan
- **WHEN** OpenSpecSettings holds an orphan `profile` value, the user selects `"core"` in the combo, and clicks Apply
- **THEN** the plugin SHALL run `openspec config profile core`, persist `"core"`, and remove the orphan entry from the combo on next render

### Requirement: Orphan profile entry guides user to recovery

When OpenSpecSettings holds a `profile` value not in the CLI-accepted preset list, the Settings panel SHALL render the orphan entry with both the existing red `(not found in CLI)` suffix AND inline help text directing the user to a recovery action. The Apply button SHALL be disabled while an orphan entry is the selected combo value, so the no-op behavior is not silent.

#### Scenario: Orphan entry shows recovery guidance
- **WHEN** the workflow profile combo's selected entry is an orphan (e.g., a legacy `"custom"` value)
- **THEN** the Settings panel SHALL display inline help text near the combo guiding the user to a recovery action (the implemented copy: *"This entry is from a previous plugin version. Pick \"core\" to switch to the supported preset."*)

#### Scenario: Apply disabled while orphan selected
- **WHEN** the workflow profile combo's selected entry is an orphan
- **THEN** the Apply button on the Settings dialog SHALL be disabled until the user picks a non-orphan combo entry

### Requirement: Customize workflows affordance

The Settings panel SHALL provide a "Customize workflows…" control adjacent to the workflow profile combo on the same row. Its placement next to the affordance is what conveys secondary status — explicit link styling is not required so long as the button is not visually competing with the dialog's primary action (Apply). Activating it SHALL launch the OpenSpec CLI's interactive workflow picker (`openspec config profile` with no arguments) in the IntelliJ Terminal tool window. The plugin SHALL NOT mirror the universe of available workflows in plugin-side code — workflow management is delegated wholly to the CLI's interactive picker.

After launching the picker, the Settings panel SHALL display a non-modal completion banner with an "I'm done" action. Activating "I'm done" SHALL trigger `WorkflowProfileService.refresh()` and update the combo and Config Profile section inline. The refresh MAY run off the EDT (a CLI call must not block the UI); "inline" describes the UX (no separate dialog, no full panel reload), not the threading. A confirmation toast SHALL surface the new active profile and workflow count after refresh.

#### Scenario: Customize button launches CLI picker
- **WHEN** the user clicks "Customize workflows…" in OpenSpec Settings and the IntelliJ Terminal tool window is available
- **THEN** the Terminal tool window SHALL open or focus, `openspec config profile` SHALL be issued in a terminal session, and the Settings panel SHALL remain open with a non-modal banner reading "Waiting for picker — click I'm done when finished."

#### Scenario: I'm done triggers inline refresh
- **WHEN** the user clicks "I'm done" in the post-launch banner
- **THEN** the plugin SHALL call `WorkflowProfileService.refresh()` (off the EDT), update the combo and Config Profile section inline once the call returns, and surface a confirmation toast such as "Now on `custom · 7 workflows`."

#### Scenario: Two-step update prompt fires only when the set actually changed
- **WHEN** the post-"I'm done" refresh completes and `WorkflowProfileService.hasChangedSinceLastRefresh()` reports the active workflow set differs from the previously cached set
- **THEN** the plugin SHALL invoke `WorkflowProfileSwitchService.promptAndRunUpdateIfConfirmed(activeProfileName)` so the existing two-step prompt offers `openspec update`; if the set is unchanged, no prompt SHALL fire

#### Scenario: Implicit refresh on Settings interaction
- **WHEN** the user reopens OpenSpec Settings, opens the status bar profile widget popup, brings up the OpenSpec tool window, applies Settings, or reopens the project after using the CLI picker without clicking "I'm done"
- **THEN** the plugin SHALL refresh `WorkflowProfileService` (best-effort, off the EDT) so the displayed profile name and active workflow list reflect the user's latest CLI-side selection — these surfaces are the fallback path when the user dismisses the inline banner

#### Scenario: Terminal tool window unavailable
- **WHEN** the user clicks "Customize workflows…" but the IntelliJ Terminal tool window is unavailable (plugin disabled or unsupported IDE variant)
- **THEN** the plugin SHALL show a notification copying `openspec config profile` to the clipboard with explicit guidance: "Open a terminal in your project directory and paste — the command has been copied. See [About profiles] for details," with action buttons linking to the docs

### Requirement: Settings panel surfaces use CLI runtime data

The Settings panel's workflow profile combo entries, active profile readout, and Config Profile section SHALL be sourced from the OpenSpec CLI's runtime output (`openspec config list --json` and `openspec config profile <name> --json`) rather than from detected CLI version or hardcoded preset assumptions. Inline copy that describes profile semantics (e.g., the ContextHelpLabel) SHALL NOT enumerate specific workflow names; it SHALL link to the canonical workflow profiles documentation page for current workflow lists.

#### Scenario: Active profile readout from CLI
- **WHEN** the Settings panel renders the "Active profile:" label in the Config Profile section
- **THEN** the value SHALL come from the `profile` field of `openspec config list --json`, not from a plugin-side preset map

#### Scenario: ContextHelpLabel copy avoids workflow enumeration
- **WHEN** a user hovers the workflow profile `?` icon to read the ContextHelpLabel
- **THEN** the copy SHALL describe profiles in general terms (core ships an essential set; Customize workflows… picks additional workflows the CLI offers) without listing specific workflow names like `verify`, `ff`, `sync`, etc.

#### Scenario: Version detection scope
- **WHEN** the plugin needs to decide whether to expose a profile-related Settings panel surface
- **THEN** version detection SHALL be used only for gating availability of CLI features (e.g., a CLI subcommand existing in version X+), not for inferring preset names, workflow membership, or default profile behavior