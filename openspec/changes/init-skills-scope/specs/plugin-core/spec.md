## MODIFIED Requirements

### Requirement: Project detection and initialization

The plugin SHALL detect OpenSpec projects by checking for `openspec/`, and initialize them through CLI delegation with a built-in fallback. A CLI init SHALL keep the skills in the project, and a failed one SHALL be reported. The fallback SHALL honor the Default schema preference, falling back to `spec-driven`, and SHALL write only the fields upstream `openspec init` writes: a `schema:` line. It SHALL NOT write plugin-invented or empty upstream fields.

#### Scenario: Project detection
- **WHEN** a project is opened in the IDE
- **THEN** the plugin SHALL check for `openspec/` directory and enable/disable features accordingly

#### Scenario: Init delegates to CLI when available
- **WHEN** `ScaffoldingService.initOpenSpec()` is called and the CLI is detected
- **THEN** it SHALL run `openspec init --tools <detected-tools>` and perform a synchronous VFS refresh

#### Scenario: Init keeps the skills in the project on CLI 2.1 and later
- **WHEN** the wizard initializes a project with CLI 2.1.0 or later
- **THEN** the init SHALL pass `--scope project` and set `OPENSPEC_SKILLS_SCOPE=project`, and the project SHALL then hold its own skills under `.agents/skills/`

#### Scenario: Init below CLI 2.1 passes no scope flag
- **WHEN** the wizard initializes a project with a CLI from 1.3.0 to 2.0.x
- **THEN** the init SHALL NOT pass `--scope`, which those releases reject or lack, and SHALL otherwise run the same arguments as before

#### Scenario: Init falls back to built-in
- **WHEN** the CLI is unavailable or fails
- **THEN** the service SHALL create `openspec/config.yaml`, `specs/`, `changes/`, and `changes/archive/` via VFS

#### Scenario: A failed CLI init is reported
- **WHEN** the CLI init exits with an error
- **THEN** the plugin SHALL show a warning carrying the CLI's error text as the CLI wrote it, including the reason lines of its summary
- **AND** when the detected CLI is 2.1.0, the warning SHALL add that this release needs Git and network access during init

#### Scenario: The CLI's init runs off the UI thread
- **WHEN** the plugin initializes a project with the CLI, from the setup wizard, the Init action or the tool window
- **THEN** the CLI SHALL run in a background task, and the IDE SHALL stay responsive while it runs, including while CLI 2.1.0 downloads its skills
- **AND** the result SHALL come back to the caller under the caller's modality, so the setup wizard shows it while open

#### Scenario: A failed CLI init that already wrote openspec
- **WHEN** the CLI init exits with an error after creating `openspec/`, as 2.1.0 does when it can't fetch the skills
- **THEN** the plugin SHALL keep that `openspec/` as the CLI wrote it and SHALL NOT run the built-in init
- **AND** when the CLI wrote no `openspec/`, the built-in init SHALL run as before

#### Scenario: Default schema honored on built-in init
- **WHEN** the user has set Default schema to a value other than empty (e.g., `workspace-planning`) in Settings → Tools → OpenSpec, and the built-in init path runs
- **THEN** the generated `openspec/config.yaml` SHALL contain `schema:` matching the user's chosen value, not the hardcoded literal `spec-driven`

#### Scenario: Default schema fallback when unset
- **WHEN** the user has not set Default schema (the setting is empty), and the built-in init path runs
- **THEN** the generated `openspec/config.yaml` SHALL contain `schema: spec-driven` (preserving the prior default behavior)

#### Scenario: Built-in init writes a schema-only config
- **WHEN** the built-in init path generates `openspec/config.yaml`
- **THEN** the file SHALL contain a `schema:` line and SHALL NOT contain a `version:` field, a `profile:` block, or empty `context:`/`rules:` entries — matching upstream `openspec init`, which writes only `schema:`

#### Scenario: Legacy config with plugin-invented fields still reads
- **WHEN** an existing `openspec/config.yaml` (from a prior plugin version) contains `version:` and/or `profile:`
- **THEN** the plugin SHALL still parse it and still surface those values in the tree view — only newly-scaffolded configs omit them

#### Scenario: Legacy fields
- **WHEN** an existing legacy `config.yaml` carries `version:` or `profile:`
- **THEN** the plugin MAY read them for display, but SHALL NOT emit them into a config it generates

## ADDED Requirements

### Requirement: Adding a tool keeps the project's skills scope

With CLI 2.1.0 or later, adding or configuring a tool from Manage AI Tools SHALL pass `--scope project` when the project holds its own skills or has none installed anywhere. When the project's skills are installed only for the machine, it SHALL pass no scope, so the CLI keeps them there. Below 2.1.0 it SHALL pass the same arguments as before.

#### Scenario: A project that keeps its own skills
- **WHEN** the project holds OpenSpec skills under `.agents/skills/` or a tool folder, the CLI is 2.1.0 or later, and the user adds Cursor
- **THEN** the plugin SHALL run `init` with `--tools cursor --scope project`

#### Scenario: A project using the machine's skills
- **WHEN** the project has `openspec/` and no skills of its own, the home directory holds OpenSpec skills, the CLI is 2.1.0 or later, and the user adds Cursor
- **THEN** the plugin SHALL run `init --tools cursor` without `--scope` and without `OPENSPEC_SKILLS_SCOPE`

#### Scenario: An older CLI
- **WHEN** the CLI is 2.0.0 and the user adds Cursor
- **THEN** the plugin SHALL run `init --tools cursor` with no `--scope`

### Requirement: Recorded skills scope is read, never written

The plugin SHALL read `skillsScope` from `openspec/config.yaml` as the team's recorded choice: `project` or `global`. A missing key SHALL mean no recorded choice. Any other value SHALL be flagged in the `config.yaml` editor with CLI 2.1's warning and treated as no recorded choice, and the rest of the config SHALL load. The plugin SHALL NOT write, change or remove `skillsScope`.

#### Scenario: A recorded project scope
- **WHEN** `openspec/config.yaml` holds `skillsScope: project`, as CLI 2.1 writes it
- **THEN** the plugin SHALL read the recorded choice as project

#### Scenario: No key
- **WHEN** `openspec/config.yaml` has no `skillsScope`, as CLI 1.x and 2.0 always leave it
- **THEN** the plugin SHALL read no recorded choice, and the config SHALL load without a warning

#### Scenario: An unknown value
- **WHEN** `openspec/config.yaml` holds `skillsScope: team`
- **THEN** the `config.yaml` editor SHALL flag the value as CLI 2.1 does: `Invalid 'skillsScope' field in config (must be "project" or "global")`
- **AND** the plugin SHALL read no recorded choice, and SHALL still load the config's other fields

#### Scenario: Never written
- **WHEN** the plugin initializes a project, adds a tool, or saves Settings
- **THEN** it SHALL NOT add, change or remove `skillsScope` in `openspec/config.yaml`

### Requirement: The skills scope is shown

Manage AI Tools and the OpenSpec Settings page SHALL show one read-only line saying where the project's OpenSpec skills are: in this project, on this machine, or not installed, and SHALL say when `skillsScope: project` is recorded. On a project whose skills are only on the machine, Manage AI Tools SHALL offer, on CLI 2.1.0 or later, to install them into the project for tools the user picks, and SHALL offer no action below 2.1.0.

#### Scenario: Skills in the project
- **WHEN** the project holds its own OpenSpec skills under `.agents/skills/`
- **THEN** the line SHALL read "Skills: in this project (.agents/skills)"

#### Scenario: Skills only on the machine
- **WHEN** the project holds no OpenSpec skills and the home directory's `.agents/skills/` does
- **THEN** the line SHALL read "Skills: on this machine (~/.agents/skills)"
- **AND** with CLI 2.1.0 or later, Manage AI Tools SHALL offer "Install into this project"

#### Scenario: Picking the tools to install into the project
- **WHEN** the user chooses "Install into this project"
- **THEN** a tool list SHALL open with ticks set as the CLI's own picker sets them for a project with no configured tools: each tool detected in the project, and each tool the CLI detects as installed on the machine, except the "Other / Universal" entry
- **AND** the preferred AI tool SHALL also be ticked
- **AND** confirming SHALL run `init --tools <ticked ids> --scope project`, and confirming SHALL NOT be possible with no tool ticked

#### Scenario: Cancelling the tool list
- **WHEN** the user closes the tool list without confirming
- **THEN** no command SHALL run and nothing in the project or the home directory SHALL change

#### Scenario: Skills on the machine, older CLI
- **WHEN** the project's skills are only on the machine and the CLI is 2.0.x or 1.x
- **THEN** the line SHALL say the skills are installed for this machine, Manage AI Tools SHALL offer no install action, and the plugin SHALL NOT prompt to configure tools

#### Scenario: A recorded choice
- **WHEN** `openspec/config.yaml` holds `skillsScope: project`
- **THEN** the line SHALL also say the choice is recorded in `openspec/config.yaml`

#### Scenario: The line is read off the UI thread
- **WHEN** Manage AI Tools or Settings opens
- **THEN** the home directory SHALL be read off the UI thread, as the home-directory skills already are
