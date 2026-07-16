## MODIFIED Requirements

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
