# coordination-surfaces Specification

## Purpose

Resolve and present OpenSpec's 1.4 coordination collections — workspaces, context stores, and initiatives — in the IDE, invoking the CLI only inside the `[1.4.0, 1.5.0)` window where those commands exist and otherwise reading the on-disk global data directory as read-only state.

## Requirements

### Requirement: Coordination collection resolution

The plugin SHALL invoke the CLI to resolve the coordination collections (workspaces, context stores and initiatives) only when the detected CLI version is within `[1.4.0, 1.5.0)`. Outside that window, or when the CLI is absent, it SHALL NOT invoke those commands, and SHALL read the global data dir directly, presenting any state on disk as read-only.

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

#### Scenario: Data dir layout
- **WHEN** the global data dir is resolved
- **THEN** it SHALL be `$XDG_DATA_HOME/openspec` when set, otherwise `~/.local/share/openspec` on Unix and macOS and `%LOCALAPPDATA%\openspec` on Windows
- **AND** workspaces SHALL be under `managed-workspaces/`, context stores under `context-stores/`, and initiatives nested within their context store

### Requirement: Tiered coordination presentation

The coordination surface SHALL present one of three tiers: Hidden (no coordination state and no coordination mode), Awareness (a read-only listing when state exists but no CLI supports writes), or Full (state or a coordination mode with a CLI that supports writes). The mode SHALL come from the workflow-schema-context signals, and a marker for the removed non-default coordination mode SHALL NOT force a non-Hidden tier.

#### Scenario: Hidden tier
- **WHEN** no coordination state (legacy or store/workset) is detected and the active mode is not a coordination mode
- **THEN** the coordination surface SHALL remain hidden or show a single non-intrusive empty state

#### Scenario: Awareness tier when no write-capable CLI is available
- **WHEN** coordination state is detected on disk but no CLI capable of a write path is available (the CLI is missing or below `1.4.0`)
- **THEN** the coordination surface SHALL display a read-only listing of the detected collections and SHALL disable write actions with guidance

#### Scenario: Full tier inside the 1.4 window
- **WHEN** coordination state is present and the detected CLI is within `[1.4.0, 1.5.0)`
- **THEN** the coordination surface SHALL display the collections, enable initiative-artifact navigation, and enable the legacy 1.4 coordination write actions

#### Scenario: Full tier at or above 1.5.0
- **WHEN** store/workset state is present and the detected CLI is at or above `1.5.0`
- **THEN** the coordination surface SHALL reach the Full tier and enable the store/workset write actions, while the legacy 1.4 coordination write actions SHALL NOT be offered (their commands were removed in `1.5.0`)

#### Scenario: Full in each CLI regime
- **WHEN** the CLI is within `[1.4.0, 1.5.0)`
- **THEN** Full SHALL enable initiative-artifact navigation and the legacy 1.4 write actions
- **WHEN** the CLI is at or above `1.5.0`
- **THEN** Full SHALL enable the store/workset write actions

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

At the Full tier within `[1.4.0, 1.5.0)`, the plugin SHALL expose IDE write actions delegating to the CLI: New Initiative, Set Up Context Store and Set Up Workspace. They SHALL be enabled only within that window, so a 1.5 upgrade retires them, and outside it SHALL return a failure without invoking the removed command. They SHALL run off the EDT, refresh the listing on success, and show the CLI's error output on failure.

#### Scenario: Create an initiative via the CLI inside the window
- **WHEN** the detected CLI is within `[1.4.0, 1.5.0)` and the user invokes the New Initiative action with the required input
- **THEN** the plugin SHALL run `openspec initiative create` on a background thread and refresh the initiative listing on success

#### Scenario: Write actions enabled only inside the window
- **WHEN** the detected CLI is within `[1.4.0, 1.5.0)` and the surface is at the Full tier
- **THEN** the New Initiative, Set Up Context Store, and Set Up Workspace actions SHALL be enabled, and SHALL be hidden or disabled on any CLI outside that window

#### Scenario: Write actions stand down on CLI 1.5.0 or later
- **WHEN** the detected CLI version is at or above `1.5.0` and a coordination write action is invoked
- **THEN** the plugin SHALL return a failure result and SHALL NOT invoke the removed `initiative`, `context-store`, or `workspace` command

#### Scenario: Write action reports CLI failure
- **WHEN** a coordination write action's CLI invocation fails inside the window
- **THEN** the plugin SHALL surface the CLI's error message and leave the existing listing intact

#### Scenario: Guidance by version
- **WHEN** a write action is invoked outside the window
- **THEN** its guidance SHALL NOT tell an in-window 1.4.x user that they need a different CLI version

### Requirement: CLI version-window helper

The plugin SHALL provide a version-comparison helper that expresses an upper bound. Beside the at-least comparison, it SHALL offer a strictly-below comparison and a half-open range check `[floorInclusive, ceilingExclusive)`. A null or empty detected version SHALL satisfy no bound.

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

### Requirement: CLI-version behavior contract

The per-CLI-version coordination behaviour for `1.3.x`, `1.4.x`, `1.5.x` and `1.6.x` SHALL be explicit and enforced by per-version tests, defining for each version which capabilities are live: read surfaces, presentation tier and write path. A change altering a supported version's behaviour SHALL update this contract and its tests together, and SHALL NOT silently add, remove or re-gate a capability.

#### Scenario: 1.4.x enables the legacy coordination write path
- **WHEN** the detected CLI version is in `[1.4.0, 1.5.0)` and the surface is at the Full tier
- **THEN** the coordination write path (create initiative / set up context store / set up workspace) SHALL be enabled, and a per-version test SHALL assert this so its removal or mis-gating fails the build

#### Scenario: 1.5.x disables the legacy coordination write path
- **WHEN** the detected CLI version is at or above `1.5.0`
- **THEN** the legacy coordination write path SHALL be disabled (the store/workset write path leads instead), and a per-version test SHALL assert this

#### Scenario: 1.6.x preserves the 1.5 surface with 1.6 semantics
- **WHEN** the detected CLI version is at or above `1.6.0`
- **THEN** the store/workset surface, tiers, and write paths SHALL be those of `1.5.x`, the generation-aware health/registration semantics SHALL apply, and per-generation contract tests SHALL assert the 1.6 behaviors

#### Scenario: Changing a supported version's behavior updates the contract
- **WHEN** a change alters the enabled capabilities for a supported CLI version
- **THEN** the change SHALL update this per-version contract and the corresponding per-version tests in the same change

#### Scenario: Below the coordination floor (1.3.x)
- **WHEN** the CLI is 1.3.x
- **THEN** there SHALL be no coordination reads via the CLI and no store reads, and the surface SHALL be Hidden or read-only Awareness
- **AND** no write path SHALL be enabled, and every coordination and store write SHALL short-circuit with guidance without shelling out

#### Scenario: Inside the window (1.4.x)
- **WHEN** the CLI is 1.4.x
- **THEN** coordination collections SHALL resolve via the CLI, and at the Full tier the legacy write path (create initiative, set up context store, set up workspace) SHALL be enabled
- **AND** the 1.5 store write path SHALL NOT be enabled

#### Scenario: At or above 1.5.0 (1.5.x)
- **WHEN** the CLI is 1.5.x
- **THEN** the store/workset model SHALL lead, and at the Full tier the store/workset write path SHALL be enabled
- **AND** the legacy write path SHALL be disabled, because its commands were removed in 1.5.0

#### Scenario: 1.6.x
- **WHEN** the CLI is 1.6.x
- **THEN** the surface, tiers and write paths SHALL be identical to 1.5.x, because the store/workset JSON shapes are unchanged
- **AND** generation-aware store-health and registration semantics SHALL apply as specified in the store-workset capabilities (healthy-empty stores present as healthy; the 1.6 register refusal and identity-confirmation outcomes show the CLI's message and fix verbatim)
- **AND** no new version gate SHALL be introduced; the 1.5.0 store floor is unchanged
