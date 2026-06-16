## MODIFIED Requirements

### Requirement: Profile switch via CLI delegation

The plugin SHALL delegate profile changes to the OpenSpec CLI via `openspec config profile <name>` to ensure the CLI's config remains the source of truth. After a successful profile switch, the plugin SHALL prompt the user to run `openspec update` so the workflow set's skill/command files are installed for the user's AI tools (the OpenSpec two-step profile change process).

#### Scenario: Successful profile switch
- **WHEN** the user selects a different profile and clicks Apply
- **THEN** the plugin SHALL run `openspec config profile <name>` via CliRunner and persist the new profile to OpenSpecSettings upon success

#### Scenario: Post-switch update prompt
- **WHEN** the profile switch via CLI succeeds
- **THEN** the plugin SHALL display a one-shot dialog prompting the user to run `openspec update` now or later
- **AND IF** the user chooses "Yes", the plugin SHALL invoke `openspec update` via CliRunner inside the project directory and report success or failure
- **AND IF** the user chooses "Later", the plugin SHALL not run update and SHALL not re-prompt for the same switch

#### Scenario: Profile switch CLI failure
- **WHEN** the CLI command `openspec config profile <name>` fails (non-zero exit code or CLI unavailable)
- **THEN** the plugin SHALL show a warning notification with the error message, retain the previous profile value in OpenSpecSettings, and SHALL NOT show the post-switch update prompt

#### Scenario: Profile switch without CLI
- **WHEN** the CLI is not detected and the user changes the profile
- **THEN** the plugin SHALL persist the profile value locally to OpenSpecSettings without CLI delegation, show an informational notification that the change is local-only, and SHALL NOT show the post-switch update prompt (since `openspec update` requires the CLI)

#### Scenario: Profile switch initiated from status bar widget
- **WHEN** the profile switch is triggered from the status bar widget popup rather than the Settings panel
- **THEN** the same delegation, persistence, and post-switch update prompt flow SHALL apply

## ADDED Requirements

### Requirement: Workflow profile combo replaces vestigial schema-flavored combo

The pre-existing combo at `OpenSpecSettingsPanel.java:117` (label "Schema profile:", values `["", "spec-driven"]`, wired to the workflow profile CLI API) SHALL be removed and replaced with a new combo whose label is "Workflow profile:" and whose values are the OpenSpec workflow profile presets. The replacement is a deliberate UI break; the previous combo had no coherent semantics and persisted values are migrated implicitly via the orphan-handling case below.

#### Scenario: Old combo and label removed
- **WHEN** the user opens the Settings panel
- **THEN** there SHALL NOT be a combo or label using the text "Schema profile" anywhere in the panel

#### Scenario: New "Workflow profile" combo present
- **WHEN** the user opens the Settings panel
- **THEN** there SHALL be a combo labeled "Workflow profile:" populated with workflow profile presets (`core`, `custom`) and an explicit default entry

### Requirement: Dynamic profile combo from CLI presets

The new "Workflow profile:" combo SHALL be populated with the OpenSpec CLI's known presets (currently `core` and `custom`). The combo SHALL be non-editable to prevent users from entering profile names the CLI does not recognize. Each combo entry SHALL display the profile name and a summary of its workflows.

#### Scenario: Combo populated with known presets
- **WHEN** the user opens the Settings panel
- **THEN** the profile combo SHALL list `core` and `custom`, each rendered with its name and a workflow summary (core lists the 5 essentials; custom shows the user's selected workflow count)

#### Scenario: Combo non-editable
- **WHEN** the user interacts with the profile combo
- **THEN** the combo SHALL NOT accept free-text entry; only CLI-known presets SHALL be selectable

#### Scenario: CLI unavailable
- **WHEN** the user opens the Settings panel and the CLI is not available
- **THEN** the combo SHALL be disabled and an inline message SHALL explain that the CLI must be installed to switch profiles

#### Scenario: Persisted profile not in CLI list
- **WHEN** the persisted profile name is not present in the CLI's current preset list
- **THEN** the combo SHALL include the orphan profile with a "(not found in CLI)" suffix in a warning color, and applying the panel SHALL prompt the user to revert or remove the orphan selection

#### Scenario: Default/empty profile rendered explicitly
- **WHEN** the persisted profile is the empty string (default)
- **THEN** the combo SHALL display a literal "(default — uses CLI's active profile)" entry rather than an empty selection

### Requirement: Settings panel context help for workflow profiles

The Settings panel SHALL provide a `ContextHelpLabel` adjacent to the "Workflow profile:" field with copy framing core and custom as scope-different (not better/worse), citing the AI-context-preservation rationale, and including a link out to the workflow profiles documentation.

#### Scenario: Context help available next to workflow profile field
- **WHEN** the user opens the Settings panel and locates the "Workflow profile:" field
- **THEN** an IntelliJ-standard `?` context help icon SHALL be present next to the field, surfacing copy that explains workflow profiles in scope-neutral terms and cites why core is the minimal default
