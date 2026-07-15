## Why

OpenSpec CLI 1.6.0 (current npm `latest`) redefines store-root health: a fresh or config-only store root — one without `openspec/specs`, `openspec/changes`, or `openspec/changes/archive` — now registers successfully and reports `healthy: true` from `store doctor`, with per-directory `present: false` detail. Under 1.5 the same root was refused at registration (`store_register_root_unhealthy`). Users who install the CLI from npm today get 1.6 behavior, so the plugin's store surface must treat "empty but healthy" as a first-class, non-broken state and understand the registration outcomes 1.6 actually produces.

Tracker: this change is linked to an existing tracker issue via the gitignored `.tracking.yaml` sidecar (per the repository's tracker-sidecar convention).

## What Changes

- Treat a fresh/config-only store root as healthy in every store-health presentation path: nothing may render a store as broken/unhealthy merely because its planning directories are absent, and nothing may imply that `openspec_root.healthy == true` means those directories exist.
- Drop any reliance on the retired 1.5 diagnostic codes `openspec_specs_missing`, `openspec_changes_missing`, and `openspec_archive_missing` (only the `*_not_directory` variants survive in 1.6); registration-refusal handling moves to the codes 1.6 actually emits.
- Recognize the two new `store register` refusal codes — `invalid_store_pointer` and `store_root_pointer_declared` (registering a repo whose `openspec/config.yaml` declares `store:` is refused) — wherever register results surface, presenting the CLI's `fix` remediation verbatim per the existing error-presentation convention.
- Capture the store-health contract fixtures this change needs from the real 1.6.0 CLI into `src/test/resources/fixtures/cli/1.6.0/` (register success on a fresh root, healthy-empty doctor output, the new refusal codes) and contract-test the parsing and presentation behavior against them. Existing `1.5.0/` fixtures stay in place — 1.5 remains a supported CLI generation, and the version-specific register test coverage becomes explicitly per-generation (the 1.5 `store_register_root_unhealthy` expectation is correct for 1.5 only, not for the plugin in general).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `store-workset-surface`: the store-health display requirement changes — doctor-backed health detail SHALL treat a store whose openspec-root is healthy but whose planning directories are absent as healthy (optionally showing the empty state), and SHALL NOT infer or display unhealthiness from directory absence.
- `store-workset-actions`: the store registration requirement changes — registration outcome semantics become CLI-generation-aware: a fresh root is a success on 1.6+, and the 1.6 refusal codes (`invalid_store_pointer`, `store_root_pointer_declared`) SHALL be surfaced with their `fix` remediation like every other store-write error.

## Impact

- **Code**: `com.johnnyblabs.openspec.coordination.StoreEntry` (javadoc/semantics of `openspecRootHealthy`), `CoordinationService` doctor/register parsing (expected to be largely shape-compatible already — the uniform `status[]` envelope is unchanged), `CoordinationPanel` store rendering (the `openspecRootHealthy == false` error strip and any healthy-implies-populated messaging).
- **Tests**: `StoreWorksetWriteContractTest` currently asserts the 1.5-only `store_register_root_unhealthy` refusal as the general register-failure behavior; it is re-scoped as 1.5-generation coverage alongside new 1.6-generation contract tests. New fixtures under `src/test/resources/fixtures/cli/1.6.0/`, captured from the real CLI under an isolated `XDG_DATA_HOME` with machine paths sanitized (contract-test discipline; the full fixture re-capture sweep across all commands is a separate tracked item).
- **Docs**: `docs/openspec-support.md` (and the feature matrix where it describes store registration/health) gain the 1.6 store-health semantics.
- **Dependencies/systems**: no new dependencies; no JSON shape changes (1.6 output parses with existing parsers — semantics only). No platform-compatibility impact: no new IntelliJ Platform APIs are introduced, and the plugin continues to support IntelliJ IDEA 2024.2+.
