## MODIFIED Requirements

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
