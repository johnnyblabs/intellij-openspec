## ADDED Requirements

### Requirement: Coordination collection resolution

The plugin SHALL resolve the three OpenSpec 1.4 coordination collections — workspaces, context stores, and initiatives — preferring the CLI's JSON output and falling back to reading the global data dir directly when the CLI is unavailable or below the 1.3 floor. The global data dir SHALL be resolved as `$XDG_DATA_HOME/openspec` when set, otherwise `~/.local/share/openspec` on Unix/macOS and `%LOCALAPPDATA%\openspec` on Windows, with workspaces under `managed-workspaces/`, context stores under `context-stores/`, and initiatives nested within their context store.

#### Scenario: Resolve via CLI when available
- **WHEN** the coordination surface refreshes and the CLI is detected at or above the 1.3 floor
- **THEN** the plugin SHALL populate workspaces, context stores, and initiatives from `openspec workspace list --json`, `openspec context-store list --json`, and `openspec initiative list --json` respectively

#### Scenario: Fall back to on-disk state without the CLI
- **WHEN** the coordination surface refreshes and the CLI is not detected or is below the 1.3 floor
- **THEN** the plugin SHALL read the global data dir directly and present whatever coordination state exists on disk as read-only

#### Scenario: Resolution runs off the EDT
- **WHEN** the coordination collections are resolved via the CLI or by reading the global data dir
- **THEN** the work SHALL execute on a background thread and the resulting UI update SHALL be applied via `invokeLater`

### Requirement: Tiered coordination presentation

The plugin SHALL present the coordination surface at one of three tiers determined by detected state and CLI availability: Hidden when no coordination state is present and the active mode is not a coordination mode; Awareness as a read-only listing with status indicators when coordination state is detected; and Full when the CLI is available at or above the floor, additionally enabling artifact navigation and write actions. The active mode SHALL be obtained from the existing workflow-schema-context rather than inferred from the directory structure.

#### Scenario: Hidden tier
- **WHEN** no workspaces, context stores, or initiatives are detected and the active mode is not a coordination mode
- **THEN** the coordination surface SHALL remain hidden or show a single non-intrusive empty state

#### Scenario: Awareness tier without the CLI
- **WHEN** coordination state is detected on disk but the CLI is unavailable or below the floor
- **THEN** the coordination surface SHALL display a read-only listing of the detected collections and SHALL disable write actions with guidance explaining the CLI requirement

#### Scenario: Full tier with the CLI
- **WHEN** coordination state is present and the CLI is available at or above the floor
- **THEN** the coordination surface SHALL display the collections, enable initiative-artifact navigation, and enable coordination write actions

### Requirement: Workspace listing

The plugin SHALL display known coordination workspaces with their resolution health. Each workspace entry SHALL show its name and whether it resolves on this machine, derived from `openspec workspace list --json` and, where deeper detail is requested, `openspec workspace doctor`.

#### Scenario: Display resolvable and unresolvable workspaces
- **WHEN** the workspace list is populated
- **THEN** each workspace SHALL be shown with its name and a resolution indicator distinguishing workspaces that resolve locally from those that do not

#### Scenario: Empty workspace list
- **WHEN** `openspec workspace list --json` returns an empty `workspaces` array
- **THEN** the workspace section SHALL show an empty state rather than an error

### Requirement: Context store listing

The plugin SHALL display locally registered context stores. Each context store entry SHALL show its `id` and `root`, and SHALL surface registration health (metadata presence/validity and whether the store root is a git repository) when that detail is available from `openspec context-store doctor`.

#### Scenario: Display registered context stores
- **WHEN** the context store list is populated from `openspec context-store list --json`
- **THEN** each store SHALL be shown with its `id` and `root`

#### Scenario: Surface registration diagnostics
- **WHEN** doctor detail is available for a context store
- **THEN** the store entry SHALL indicate whether its metadata is present and valid and whether its root is a git repository

### Requirement: Initiative listing with status

The plugin SHALL display initiatives across registered context stores, showing each initiative's `id`, `title`, and `status`, where `status` is one of `exploring`, `active`, `complete`, or `archived`. Status SHALL be rendered as a distinct visual indicator so initiatives can be scanned by state.

#### Scenario: Display initiatives with status badges
- **WHEN** the initiative list is populated from `openspec initiative list --json`
- **THEN** each initiative SHALL be shown with its `id`, `title`, and a status indicator reflecting one of the four initiative statuses

#### Scenario: No context store registered
- **WHEN** `openspec initiative list --json` reports no registered context store
- **THEN** the initiative section SHALL show an empty state with guidance to register or set up a context store rather than an error

### Requirement: Initiative artifact navigation

In the Full tier, the plugin SHALL let the user open an initiative's artifacts in the editor. An initiative's artifacts are the files at `<store>/<id>/`: `initiative.yaml` and the markdown documents `requirements.md`, `design.md`, `decisions.md`, `questions.md`, and `tasks.md`.

#### Scenario: Open an existing initiative artifact
- **WHEN** the user selects an initiative artifact that exists on disk
- **THEN** the plugin SHALL open that file in the editor

#### Scenario: Missing initiative artifact
- **WHEN** the user selects an initiative artifact that does not exist on disk
- **THEN** the plugin SHALL not open an editor and SHALL indicate that the artifact has not been created

### Requirement: Coordination write actions

In the Full tier, the plugin SHALL expose write actions that delegate to the CLI: creating an initiative, setting up or registering a context store, and setting up or linking a workspace. Write actions SHALL run off the EDT, SHALL refresh the affected listing on success, and SHALL surface the CLI's error output on failure without leaving the surface in a stale state.

#### Scenario: Create an initiative via the CLI
- **WHEN** the user invokes the create-initiative action and provides the required input
- **THEN** the plugin SHALL run the corresponding `openspec initiative create` command on a background thread and refresh the initiative listing on success

#### Scenario: Write action reports CLI failure
- **WHEN** a coordination write action's CLI invocation fails
- **THEN** the plugin SHALL surface the CLI's error message and leave the existing listing intact
