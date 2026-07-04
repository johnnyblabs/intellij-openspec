## ADDED Requirements

### Requirement: CLI-version behavior contract

The plugin's per-CLI-version behavior across the supported coordination versions — `1.3.x`, `1.4.x`, and `1.5.x` — SHALL be explicit and enforced by per-version tests. For each supported version the contract SHALL define which coordination capabilities are live: read surfaces, the presentation tier, and which write path (if any) is enabled. A change that alters a supported version's behavior SHALL update this contract and its per-version tests in the same change; a change SHALL NOT silently add, remove, or re-gate a version-gated capability without a corresponding contract and test update.

The contract SHALL hold:
- `1.3.x` (below the coordination floor): no coordination reads via the CLI and no store reads; the surface is Hidden or read-only Awareness only; no write path is enabled; every coordination and store write short-circuits with guidance and does not shell out.
- `1.4.x` (inside the window `[1.4.0, 1.5.0)`): coordination collections resolve via the CLI; at the Full tier the legacy coordination write path (create initiative / set up context store / set up workspace) is enabled; the 1.5 store write path is not.
- `1.5.x` (at or above `1.5.0`): the store/workset model leads; at the Full tier the store/workset write path is enabled; the legacy coordination write path is disabled because its commands were removed in `1.5.0`.

#### Scenario: 1.4.x enables the legacy coordination write path
- **WHEN** the detected CLI version is in `[1.4.0, 1.5.0)` and the surface is at the Full tier
- **THEN** the coordination write path (create initiative / set up context store / set up workspace) SHALL be enabled, and a per-version test SHALL assert this so its removal or mis-gating fails the build

#### Scenario: 1.5.x disables the legacy coordination write path
- **WHEN** the detected CLI version is at or above `1.5.0`
- **THEN** the legacy coordination write path SHALL be disabled (the store/workset write path leads instead), and a per-version test SHALL assert this

#### Scenario: Changing a supported version's behavior updates the contract
- **WHEN** a change alters the enabled capabilities for a supported CLI version
- **THEN** the change SHALL update this per-version contract and the corresponding per-version tests in the same change

## MODIFIED Requirements

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
