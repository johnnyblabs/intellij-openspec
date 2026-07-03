## ADDED Requirements

### Requirement: CLI version-window helper

The plugin SHALL provide a version-comparison helper capable of expressing an upper bound, so a feature that was removed in a later CLI release can be gated to the window in which its commands exist. In addition to the existing at-least (lower-bound) comparison, the helper SHALL offer a strictly-below (upper-bound) comparison and a half-open range check `[floorInclusive, ceilingExclusive)`. A null or empty detected version SHALL be treated as not satisfying any bound.

#### Scenario: Detected version below the ceiling
- **WHEN** the detected CLI version is strictly less than the ceiling (for example `1.4.1` against ceiling `1.5.0`)
- **THEN** the below-ceiling check SHALL return `true`

#### Scenario: Detected version at or above the ceiling
- **WHEN** the detected CLI version equals or exceeds the ceiling (for example `1.5.0` or `1.6.0` against ceiling `1.5.0`)
- **THEN** the below-ceiling check SHALL return `false`

#### Scenario: Half-open window membership
- **WHEN** a detected version is tested against a floor and an exclusive ceiling
- **THEN** the range check SHALL return `true` only for versions in `[floorInclusive, ceilingExclusive)` — `true` for `1.4.0` and `1.4.9`, and `false` for `1.3.9`, `1.5.0`, and `1.6.0` against floor `1.4.0` / ceiling `1.5.0`

#### Scenario: Null or empty detected version
- **WHEN** the detected version is null or empty
- **THEN** every bound and range check SHALL return `false`

### Requirement: Removed schema is not advertised

The plugin's built-in fallback schema set SHALL NOT include a schema that the supported CLI no longer recognizes. Because CLI 1.5.0 lists only `spec-driven`, the built-in valid-schema set SHALL contain `spec-driven` and SHALL NOT contain `workspace-planning`.

#### Scenario: Built-in schema set excludes the removed schema
- **WHEN** the built-in valid-schema set is read (for scaffolding or project-free validation)
- **THEN** it SHALL contain `spec-driven` and SHALL NOT contain `workspace-planning`

## MODIFIED Requirements

### Requirement: Coordination collection resolution

The plugin SHALL resolve the three OpenSpec 1.4 coordination collections — workspaces, context stores, and initiatives — by invoking the CLI **only when the detected CLI version is within the coordination window `[1.4.0, 1.5.0)`**, the window in which the `workspace`, `context-store`, and `initiative` commands exist. When the detected CLI is outside that window — below `1.4.0`, at or above `1.5.0` (which removed those commands), or absent — the plugin SHALL NOT invoke those commands and SHALL instead read the global data dir directly and present whatever coordination state exists on disk as read-only. The global data dir SHALL be resolved as `$XDG_DATA_HOME/openspec` when set, otherwise `~/.local/share/openspec` on Unix/macOS and `%LOCALAPPDATA%\openspec` on Windows, with workspaces under `managed-workspaces/`, context stores under `context-stores/`, and initiatives nested within their context store.

#### Scenario: Resolve via CLI inside the coordination window
- **WHEN** the coordination surface refreshes and the detected CLI version is within `[1.4.0, 1.5.0)`
- **THEN** the plugin SHALL populate workspaces, context stores, and initiatives from `openspec workspace list --json`, `openspec context-store list --json`, and `openspec initiative list --json` respectively

#### Scenario: Do not invoke removed commands on CLI 1.5.0 or later
- **WHEN** the coordination surface refreshes and the detected CLI version is at or above `1.5.0`
- **THEN** the plugin SHALL NOT invoke the `workspace`, `context-store`, or `initiative` commands and SHALL read the global data dir directly, presenting any existing coordination state as read-only

#### Scenario: Fall back to on-disk state below the coordination window
- **WHEN** the coordination surface refreshes and the CLI is not detected or is below `1.4.0`
- **THEN** the plugin SHALL read the global data dir directly and present whatever coordination state exists on disk as read-only

#### Scenario: Resolution runs off the EDT
- **WHEN** the coordination collections are resolved via the CLI or by reading the global data dir
- **THEN** the work SHALL execute on a background thread and the resulting UI update SHALL be applied via `invokeLater`

### Requirement: Tiered coordination presentation

The plugin SHALL present the coordination surface at one of three tiers determined by detected state and CLI availability **within the coordination window**: Hidden when no coordination state is present and the active mode is not a coordination mode; Awareness as a read-only listing with status indicators when coordination state is detected; and Full only when the detected CLI is within `[1.4.0, 1.5.0)`, additionally enabling artifact navigation and write actions. When the detected CLI is at or above `1.5.0`, the surface SHALL NOT resolve to the Full tier: it SHALL show read-only Awareness if legacy on-disk coordination state exists, and Hidden otherwise. The active mode SHALL be obtained from the still-valid workflow-schema-context signals; a marker for the removed non-default coordination mode SHALL NOT force a non-Hidden tier.

#### Scenario: Hidden tier
- **WHEN** no workspaces, context stores, or initiatives are detected and the active mode is not a coordination mode
- **THEN** the coordination surface SHALL remain hidden or show a single non-intrusive empty state

#### Scenario: Awareness tier outside the coordination window
- **WHEN** coordination state is detected on disk but the CLI is unavailable, below `1.4.0`, or at/above `1.5.0`
- **THEN** the coordination surface SHALL display a read-only listing of the detected collections and SHALL disable write actions with guidance

#### Scenario: Full tier only inside the coordination window
- **WHEN** coordination state is present and the detected CLI is within `[1.4.0, 1.5.0)`
- **THEN** the coordination surface SHALL display the collections, enable initiative-artifact navigation, and enable coordination write actions

#### Scenario: No Full tier and no failing write actions on CLI 1.5.0 or later
- **WHEN** the detected CLI version is at or above `1.5.0`
- **THEN** the coordination surface SHALL NOT resolve to the Full tier and SHALL NOT offer any write action that would invoke a removed command — showing read-only Awareness when legacy state exists on disk, and Hidden otherwise

### Requirement: Coordination write actions

Within the coordination window `[1.4.0, 1.5.0)` and in the Full tier, the plugin SHALL expose write actions that delegate to the CLI: creating an initiative, setting up or registering a context store, and setting up or linking a workspace. When the detected CLI is outside that window (in particular at or above `1.5.0`, where the underlying commands were removed), these write actions SHALL NOT be offered as enabled and, if invoked, SHALL return a failure result without invoking the removed command. Write actions that do run SHALL run off the EDT, SHALL refresh the affected listing on success, and SHALL surface the CLI's error output on failure without leaving the surface in a stale state.

#### Scenario: Create an initiative via the CLI inside the window
- **WHEN** the detected CLI is within `[1.4.0, 1.5.0)` and the user invokes the create-initiative action with the required input
- **THEN** the plugin SHALL run the corresponding `openspec initiative create` command on a background thread and refresh the initiative listing on success

#### Scenario: Write actions stand down on CLI 1.5.0 or later
- **WHEN** the detected CLI version is at or above `1.5.0` and a coordination write action is invoked
- **THEN** the plugin SHALL return a failure result and SHALL NOT invoke the removed `initiative`, `context-store`, or `workspace` command

#### Scenario: Write action reports CLI failure
- **WHEN** a coordination write action's CLI invocation fails
- **THEN** the plugin SHALL surface the CLI's error message and leave the existing listing intact
