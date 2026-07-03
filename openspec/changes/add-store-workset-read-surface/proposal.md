## Why

OpenSpec CLI 1.5.0 introduces **stores** and **worksets** as a simplified organizing model that **replaces the previous workspace-and-initiative coordination model**. Stores are standalone OpenSpec repos registered on the machine; worksets are purely local, composed working views over them. The plugin's coordination surface (the tool-window tab, its models, path resolver, and read service) only knows the 1.4 collections — `workspace`, `context-store`, and `initiative` — so a user who upgrades to 1.5.0 and runs `openspec store setup` / `openspec workset create` from the terminal sees nothing in the IDE. `workset` has zero references anywhere in the plugin today.

Because stores and worksets are flagged very-early-beta with promised breaking changes, this change delivers the **read-only core**: faithfully reflect 1.5.0 store and workset state in the IDE, gated on the resolved CLI version, without any write actions. It builds on `guard-legacy-coordination-on-cli-1-5`, which does the shared gating cleanup that lets the surface key its behavior off the resolved CLI version rather than off directory shape alone.

## What Changes

- Extend `CoordinationPaths` with the 1.5.0 on-disk locations — a `stores/` dir with its `registry.yaml` and a `worksets/` dir with its `worksets.yaml`. The store `registry.yaml` is **byte-identical in shape** to the existing `context-stores/registry.yaml` (`version: 1` / `stores: {<id>: {backend: {type, local_path}}}`), only the directory name changed, so the existing backend-local-path parser is reused rather than duplicated.
- Add immutable models: `StoreEntry` (`id`, `root`, plus nullable `store doctor` health detail) and `WorksetEntry` (`name`, `List<Member>`) with a nested `Member` (`name`, `path`).
- Add parsers for `store list --json`, `store doctor --json`, and `workset list --json`, contract-tested against captured real 1.5.0 fixtures. The parsers and the CLI-first path are gated behind a new `STORE_CLI_FLOOR = "1.5.0"` compared via `CliVersion.atLeast` **in the service**. `VersionSupport` is a deliberately-pinned config-format axis and is **not** touched.
- Read the uniform 1.5.0 diagnostic envelope — every command returns `status: [{severity, code, message, target, fix}]` — and retain each diagnostic's ready-made `fix` suggestion for display alongside the affected store/workset (read-only; no execution of the fix in this change).
- Present the new collections read-only in the tool window: a **Stores** group and a **Worksets** group become the lead model when the CLI is at or above `1.5.0`, with per-store health badges derived from `store doctor`. Legacy `workspace`/`initiative` state is demoted to a muted, read-only **Legacy (pre-1.5)** group, shown **only when such legacy state actually exists on disk**. The existing tiered Hidden/Awareness model is reused.
- **Canonicalize roots** on both sides of any project-root-to-store-root match: `Path.toRealPath()`, falling back to `toAbsolutePath().normalize()` when the path does not exist. The CLI canonicalizes store roots (resolving symlinks and Windows 8.3 short paths), so raw string comparison would miss matches.
- **Coexistence, no migration:** when both legacy (`workspaces`/`context-stores`) and new (`stores`/`worksets`) state exist on disk, behavior keys off the resolved CLI version — at `1.5.0`+, stores/worksets are canonical and legacy is read-only/demoted. The plugin never migrates anything; the CLI owns migration and the plugin only reflects state.
- **Beta-guarded degradation:** a `store`/`workset` parse failure degrades to the on-disk fallback (Awareness at most) and never throws into the UI.

## Capabilities

### New Capabilities
- `store-workset-surface`: read-only IDE presentation of the OpenSpec 1.5.0 store and workset model — `CoordinationPaths` extensions for `stores/` and `worksets/`, `StoreEntry`/`WorksetEntry`/`Member` models, `store list` / `store doctor` / `workset list` parsers gated at the `1.5.0` CLI floor, root canonicalization for project-to-store matching, per-store health badges, and demotion of legacy pre-1.5 coordination state to a muted read-only group.

### Modified Capabilities
<!-- None. The 1.4 `coordination-surfaces` capability is unchanged here; legacy state is reflected read-only via the new surface's demoted group. Shared gating cleanup lands in `guard-legacy-coordination-on-cli-1-5`. -->

## Impact

- **Code:** `CoordinationPaths` gains `stores/` + `worksets/` accessors and reuses the existing backend-local-path parser; new `StoreEntry`, `WorksetEntry`, and `Member` models; new `store list` / `store doctor` / `workset list` parsers and read paths in `CoordinationService` gated by `STORE_CLI_FLOOR`; a root-canonicalization helper; and a read-only Stores/Worksets/Legacy presentation in the coordination tool-window panel. No write actions.
- **CLI contract:** relies on `openspec store list --json`, `openspec store doctor --json`, and `openspec workset list --json` (1.5.0); the on-disk fallback reads `$XDG_DATA_HOME/openspec/stores/registry.yaml` and `worksets/worksets.yaml` (with `~/.local/share/openspec` on Unix/macOS and `%LOCALAPPDATA%\openspec` on Windows). `git` subfields in `store doctor` may be null for non-git stores and are handled as such.
- **Dependency:** builds on `guard-legacy-coordination-on-cli-1-5` (shared version-gating cleanup).
- **Platform compatibility:** no change — continues to support the JetBrains IDE family from IntelliJ IDEA 2024.2 onward; all CLI/IO runs off the EDT with UI updates via `invokeLater`; the read service is a `projectService`. Language-agnostic — no file-type or language assumptions.
- **Out of scope (deferred, referenced by name):**
  - Store/workset **write actions** and the aggregate **health strip** — `add-store-workset-write-actions`.
  - **Cross-platform CI matrix** for the new on-disk/path behavior — `add-cross-platform-ci-matrix`.
  - Consuming `context --json` / `doctor --json` **agent-brief** output — deferred: the `context.members[]` shape could not be captured non-empty from the real 1.5.0 binary, and hand-authoring the shape would violate the contract-test-against-captured-output rule. Left as future work once a non-empty capture exists.
- **Docs:** README, CHANGELOG, feature-reference, and the coverage matrix (`docs/openspec-support.md`) updated (vendor-neutral) to describe the read-only store/workset surface, the 1.5.x version-support line, and the legacy demotion.
- **Tracker:** the linked entry.
