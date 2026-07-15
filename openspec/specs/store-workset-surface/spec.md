# store-workset-surface Specification

## Purpose
TBD - created by archiving change add-store-workset-read-surface. Update Purpose after archive.

## Requirements
### Requirement: Store and workset path resolution

The plugin SHALL resolve the OpenSpec 1.5.0 on-disk store and workset locations under the same global data dir it already resolves for the 1.4 collections: `stores/` with a `registry.yaml`, and `worksets/` with a `worksets.yaml`. The store registry SHALL be parsed with the same backend-local-path logic used for the existing context-store registry, because the two files are byte-identical in shape (`version: 1` / `stores: {<id>: {backend: {type, local_path}}}`) and differ only in directory name. The global data dir SHALL continue to resolve as `$XDG_DATA_HOME/openspec` when set, otherwise `~/.local/share/openspec` on Unix/macOS and `%LOCALAPPDATA%\openspec` on Windows.

#### Scenario: Resolve the store registry path
- **WHEN** the plugin resolves the store on-disk location
- **THEN** it SHALL point at `<global-data-dir>/stores/registry.yaml`

#### Scenario: Resolve the workset file path
- **WHEN** the plugin resolves the workset on-disk location
- **THEN** it SHALL point at `<global-data-dir>/worksets/worksets.yaml`

#### Scenario: Store registry reuses the context-store backend parser
- **WHEN** the plugin reads a store registry entry `stores: {<id>: {backend: {type, local_path}}}` from disk
- **THEN** it SHALL extract the store root using the same backend-local-path parser used for the context-store registry, without a second parser implementation

### Requirement: Store and workset resolution gated at the 1.5.0 CLI floor

The plugin SHALL resolve stores and worksets, preferring the CLI's JSON output when the detected CLI is at or above a `1.5.0` floor and falling back to reading the global data dir directly otherwise or when a `--json` parse fails. The `1.5.0` floor SHALL be evaluated in the read service via the shared CLI-version comparison and SHALL NOT be modeled on the deliberately-pinned config-format version axis.

#### Scenario: Resolve stores and worksets via the CLI at or above the floor
- **WHEN** the surface refreshes and the detected CLI is at or above `1.5.0`
- **THEN** the plugin SHALL populate stores from `openspec store list --json` and worksets from `openspec workset list --json`

#### Scenario: Fall back to on-disk state below the floor
- **WHEN** the surface refreshes and the CLI is absent or below `1.5.0`
- **THEN** the plugin SHALL read `stores/registry.yaml` and `worksets/worksets.yaml` directly and present whatever store and workset state exists on disk as read-only

#### Scenario: Config-format version axis is not consulted for the store gate
- **WHEN** the plugin decides whether store and workset CLI resolution is available
- **THEN** the decision SHALL derive from the detected CLI version against the `1.5.0` floor and SHALL NOT read or extend the config-format version axis

### Requirement: Store listing with health

The plugin SHALL display locally registered stores. Each store entry SHALL show its `id` and `root` from `openspec store list --json` (`{"stores":[{"id","root"}],"status":[]}`), and SHALL surface registration health — metadata present/valid, whether the store root is a git repository, and whether its openspec-root is healthy — when that detail is available from `openspec store doctor --json`. Openspec-root health SHALL be determined solely by the doctor report's `healthy` flag: a store whose openspec-root is healthy but whose planning directories (`openspec/specs`, `openspec/changes`, `openspec/changes/archive`) are not yet present — reported by CLI 1.6+ as per-directory `present: false` detail alongside `healthy: true` — SHALL be presented as healthy, and the plugin SHALL NOT infer, compute, or display unhealthiness from the absence of those directories. The plugin SHALL NOT depend on the retired 1.5-generation diagnostic codes `openspec_specs_missing`, `openspec_changes_missing`, or `openspec_archive_missing` in any parsing or presentation path. Health lookups SHALL be lazy and run off the EDT. Git subfields that are null for non-git stores SHALL be handled without error.

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

### Requirement: Workset listing with members

The plugin SHALL display local worksets from `openspec workset list --json` (`{"worksets":[{"name","members":[{"name","path"}]}],"status":[]}`). Each workset SHALL show its `name`, and each of its members SHALL show the member `name` and `path`.

#### Scenario: Display worksets and their members
- **WHEN** the workset list is populated from `openspec workset list --json`
- **THEN** each workset SHALL be shown with its `name` and each member SHALL be shown with its `name` and `path`

#### Scenario: Empty workset list
- **WHEN** `openspec workset list --json` returns an empty `worksets` array
- **THEN** the workset section SHALL show an empty state rather than an error

### Requirement: Diagnostic envelope surfacing

The plugin SHALL parse the uniform diagnostic envelope present on every 1.5.0 command — `status: [{severity, code, message, target, fix}]` — and SHALL retain each diagnostic, including its ready-made `fix` suggestion, so it can be presented against the affected store or workset. In this read-only surface the plugin SHALL NOT execute the suggested `fix`.

#### Scenario: Retain and display a diagnostic fix suggestion
- **WHEN** a store or workset command returns a `status` entry carrying a `fix` string
- **THEN** the plugin SHALL retain the `fix` text on the parsed model and present it as read-only guidance against the affected entry

#### Scenario: Fix is not executed
- **WHEN** a diagnostic with a `fix` suggestion is displayed
- **THEN** the plugin SHALL NOT run the suggested action

### Requirement: Project-root-to-store-root canonicalization

The plugin SHALL canonicalize both the current project root and each registered store root before comparing them, using `toRealPath()` and falling back to `toAbsolutePath().normalize()` when the path does not exist or real-path resolution fails. Raw string comparison SHALL NOT be used, because the CLI canonicalizes store roots (resolving symlinks and Windows short paths) and string comparison would miss otherwise-equal roots.

#### Scenario: Match roots that differ only by canonicalization
- **WHEN** the project root and a store root denote the same location but differ by symlink, non-normalized segments, or short-path form
- **THEN** the plugin SHALL treat them as the same store after canonicalizing both sides

#### Scenario: Canonicalization falls back for a non-existent path
- **WHEN** a root path does not exist on disk
- **THEN** the plugin SHALL canonicalize it via `toAbsolutePath().normalize()` rather than failing

### Requirement: Coexistence and legacy demotion without migration

When both legacy coordination state (`workspaces`, `context-stores`) and new state (`stores`, `worksets`) exist on disk, the plugin SHALL key its lead model off the resolved CLI version: at CLI ≥ `1.5.0`, stores and worksets SHALL be the canonical lead model and legacy state SHALL be demoted to a muted, read-only "Legacy (pre-1.5)" group. The legacy group SHALL be shown only when legacy state actually exists on disk. The plugin SHALL perform no migration — it only reflects state that the CLI owns.

#### Scenario: Stores are canonical at the 1.5.0 floor with legacy demoted
- **WHEN** both legacy and new coordination state exist on disk and the CLI is at or above `1.5.0`
- **THEN** stores and worksets SHALL be the lead model and legacy workspace/initiative state SHALL appear only as a muted read-only group

#### Scenario: No legacy group when no legacy state exists
- **WHEN** only store and workset state exists on disk
- **THEN** the plugin SHALL NOT render a legacy group

#### Scenario: The plugin never migrates state
- **WHEN** both legacy and new coordination state are present
- **THEN** the plugin SHALL leave both on disk unchanged and SHALL NOT convert legacy state into stores or worksets

### Requirement: Read-only beta-guarded presentation

The store and workset surface SHALL be read-only in this change: it SHALL reuse the existing tiered Hidden/Awareness model and SHALL NOT expose write actions for stores or worksets. A parse failure of `store` or `workset` output SHALL degrade to the on-disk fallback (Awareness at most) and SHALL never throw into the UI. All resolution SHALL run off the EDT with UI updates applied via `invokeLater`.

#### Scenario: Parse failure degrades to on-disk state
- **WHEN** `openspec store list --json` or `openspec workset list --json` output fails to parse
- **THEN** the plugin SHALL fall back to reading the on-disk registry/workset file and SHALL present read-only awareness without throwing

#### Scenario: No write actions for stores or worksets
- **WHEN** the store and workset surface is displayed at any tier
- **THEN** it SHALL NOT offer create, register, remove, or open write actions for stores or worksets

#### Scenario: Resolution runs off the EDT
- **WHEN** stores and worksets are resolved via the CLI or by reading the global data dir
- **THEN** the work SHALL execute on a background thread and the resulting UI update SHALL be applied via `invokeLater`
