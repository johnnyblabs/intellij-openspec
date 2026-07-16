# coordination-surfaces Specification

## Purpose
TBD - created by archiving change phase-3-coordination-surfaces. Update Purpose after archive.
## Requirements
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

The plugin SHALL present the coordination surface at one of three tiers determined by detected state and CLI availability: Hidden when no coordination state is present and the active mode is not a coordination mode; Awareness as a read-only listing with status indicators when coordination state is detected but no CLI capable of write actions is available; and Full when coordination state (or an active coordination mode) is present together with a CLI that supports a write path. The Full tier is reachable in **either** coordination CLI regime: within the window `[1.4.0, 1.5.0)`, where Full enables initiative-artifact navigation and the legacy 1.4 write actions; **or** at or above `1.5.0`, where Full enables the store/workset write actions (governed by the store-workset capabilities). The active mode SHALL be obtained from the still-valid workflow-schema-context signals; a marker for the removed non-default coordination mode SHALL NOT force a non-Hidden tier.

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

Within the coordination window `[1.4.0, 1.5.0)` and in the Full tier, the plugin SHALL expose IDE write actions that delegate to the CLI — **New Initiative** (create an initiative), **Set Up Context Store** (set up or register a context store), and **Set Up Workspace** (set up a workspace). These actions are **version-gated and self-retiring**: they are enabled only when the detected CLI is within `[1.4.0, 1.5.0)`, and they are not offered on a CLI at or above `1.5.0`, where the underlying `workspace` / `context-store` / `initiative` commands were removed — so upgrading the CLI to a 1.5 line automatically retires them. When invoked outside the window, a write action SHALL return a failure result without invoking the removed command, and its guidance SHALL NOT tell an in-window (1.4.x) user they need a different CLI version. Write actions that do run SHALL run off the EDT, SHALL refresh the affected listing on success, and SHALL surface the CLI's error output on failure without leaving the surface in a stale state.

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

### Requirement: CLI-version behavior contract

The plugin's per-CLI-version behavior across the supported coordination versions — `1.3.x`, `1.4.x`, `1.5.x`, and `1.6.x` — SHALL be explicit and enforced by per-version tests. For each supported version the contract SHALL define which coordination capabilities are live: read surfaces, the presentation tier, and which write path (if any) is enabled. A change that alters a supported version's behavior SHALL update this contract and its per-version tests in the same change; a change SHALL NOT silently add, remove, or re-gate a version-gated capability without a corresponding contract and test update.

The contract SHALL hold:
- `1.3.x` (below the coordination floor): no coordination reads via the CLI and no store reads; the surface is Hidden or read-only Awareness only; no write path is enabled; every coordination and store write short-circuits with guidance and does not shell out.
- `1.4.x` (inside the window `[1.4.0, 1.5.0)`): coordination collections resolve via the CLI; at the Full tier the legacy coordination write path (create initiative / set up context store / set up workspace) is enabled; the 1.5 store write path is not.
- `1.5.x` (at or above `1.5.0`): the store/workset model leads; at the Full tier the store/workset write path is enabled; the legacy coordination write path is disabled because its commands were removed in `1.5.0`.
- `1.6.x` (at or above `1.6.0`): identical surface, tiers, and write paths to `1.5.x` — the store/workset JSON shapes are unchanged — with the generation-aware store-health and registration-outcome semantics specified in `store-workset-surface` and `store-workset-actions` (healthy-empty stores present as healthy; the 1.6 register refusal and identity-confirmation outcomes surface the CLI's message and fix verbatim). No new version gate is introduced: the `1.5.0` store floor is unchanged.

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

