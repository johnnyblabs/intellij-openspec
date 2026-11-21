## MODIFIED Requirements

### Requirement: Store and workset path resolution

The plugin SHALL resolve the CLI 1.5.0 store and workset locations under the same global data dir as the 1.4 collections: `stores/` with a `registry.yaml`, and `worksets/` with a `worksets.yaml`. The store registry SHALL be parsed with the context-store registry's backend-local-path logic. The data dir SHALL resolve as `$XDG_DATA_HOME/openspec` when set, otherwise `~/.local/share/openspec` on Unix and macOS, and `%LOCALAPPDATA%\openspec` on Windows.

#### Scenario: Resolve the store registry path
- **WHEN** the plugin resolves the store on-disk location
- **THEN** it SHALL point at `<global-data-dir>/stores/registry.yaml`

#### Scenario: Resolve the workset file path
- **WHEN** the plugin resolves the workset on-disk location
- **THEN** it SHALL point at `<global-data-dir>/worksets/worksets.yaml`

#### Scenario: Store registry reuses the context-store backend parser
- **WHEN** the plugin reads a store registry entry `stores: {<id>: {backend: {type, local_path}}}` from disk
- **THEN** it SHALL extract the store root using the same backend-local-path parser used for the context-store registry, without a second parser implementation

### Requirement: Store listing with health

The plugin SHALL list locally registered stores with their `id` and `root` from `openspec store list --json`, and SHALL show registration health from `openspec store doctor --json` when available. Openspec-root health SHALL be the doctor report's `healthy` flag alone. Health lookups SHALL be lazy and run off the EDT, and null git fields for non-git stores SHALL be handled without error.

#### Scenario: Display registered stores
- **WHEN** the store list is populated from `openspec store list --json`
- **THEN** each store SHALL be shown with its `id` and `root`

#### Scenario: Surface store diagnostics from doctor
- **WHEN** doctor detail is available for a store
- **THEN** the store entry SHALL indicate whether its metadata is present and valid, whether its root is a git repository, and whether its openspec-root is healthy

#### Scenario: Healthy-empty store rendered as healthy
- **WHEN** `openspec store doctor --json` reports a store's openspec-root as `healthy: true` with `present: false` for its planning directories (a fresh or config-only store on CLI 1.6+)
- **THEN** the store entry SHALL be presented as healthy with no unhealthy or error marker

#### Scenario: Non-git store yields null git health without error
- **WHEN** `openspec store doctor --json` returns a store whose `git` subfields are null (a non-git store)
- **THEN** the plugin SHALL parse the entry without throwing and SHALL treat git health as unknown

#### Scenario: Empty store list
- **WHEN** `openspec store list --json` returns an empty `stores` array
- **THEN** the store section SHALL show an empty state rather than an error

#### Scenario: Planning directories are not unhealthiness
- **WHEN** the doctor reports `healthy: true` while the planning directories (`openspec/specs`, `openspec/changes`, `openspec/changes/archive`) are not present
- **THEN** the store SHALL be presented as healthy, and the plugin SHALL NOT infer unhealthiness from their absence
- **AND** it SHALL NOT depend on the retired codes `openspec_specs_missing`, `openspec_changes_missing` or `openspec_archive_missing`

#### Scenario: Registration health details
- **WHEN** the doctor report is available
- **THEN** the plugin SHALL show whether the store's metadata is present and valid, and whether the store root is a git repository
- **AND** null git fields for a non-git store SHALL NOT be an error

### Requirement: Coexistence and legacy demotion without migration

At CLI 1.5.0 or later, when legacy coordination state and new state both exist, stores and worksets SHALL be the canonical lead model, and legacy state SHALL be a muted, read-only "Legacy (pre-1.5)" group. That group SHALL appear only when legacy state exists on disk. The plugin SHALL NOT migrate state; it reflects only what the CLI owns.

#### Scenario: Stores are canonical at the 1.5.0 floor with legacy demoted
- **WHEN** both legacy and new coordination state exist on disk and the CLI is at or above `1.5.0`
- **THEN** stores and worksets SHALL be the lead model and legacy workspace/initiative state SHALL appear only as a muted read-only group

#### Scenario: No legacy group when no legacy state exists
- **WHEN** only store and workset state exists on disk
- **THEN** the plugin SHALL NOT render a legacy group

#### Scenario: The plugin never migrates state
- **WHEN** both legacy and new coordination state are present
- **THEN** the plugin SHALL leave both on disk unchanged and SHALL NOT convert legacy state into stores or worksets
