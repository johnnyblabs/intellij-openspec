## 1. Path resolution for the 1.5.0 layout

- [ ] 1.1 Extend `CoordinationPaths` with `stores/` and `worksets/` directory names and accessors: `storesDir()`, `storeRegistryFile()` (`stores/registry.yaml`), `worksetsDir()`, and `worksetsFile()` (`worksets/worksets.yaml`). Resolve them under the same global data dir as the existing 1.4 collections.
- [ ] 1.2 Reuse the existing backend-local-path parser for the store registry — the `stores/registry.yaml` shape (`version: 1` / `stores: {<id>: {backend: {type, local_path}}}`) is byte-identical to `context-stores/registry.yaml`, so do NOT fork a second parser; extract the existing one to a shared, package-visible helper if it currently lives inside the context-store read path.

## 2. Models

- [ ] 2.1 Add an immutable `StoreEntry` record: `id`, `root`, plus nullable `store doctor` health fields (metadata present/valid, git repository, openspec-root healthy) mirroring the `ContextStoreEntry.withDoctor(...)` copy-with pattern; include a `basic(...)` factory (no doctor detail) and a `withDoctor(...)` copy method.
- [ ] 2.2 Add an immutable `WorksetEntry` record: `name` and `List<Member>`, defensively copying the member list in the compact constructor.
- [ ] 2.3 Add a nested immutable `Member` record: `name`, `path`.

## 3. Version gating

- [ ] 3.1 Add `STORE_CLI_FLOOR = "1.5.0"` to `CoordinationService` and a `cliStoreAvailable()` guard using `CliVersion.atLeast(detectedVersion, STORE_CLI_FLOOR)`. Do NOT add any enum value to `VersionSupport` (that axis is the deliberately-pinned config-format axis, not the CLI axis).

## 4. Parsers (read-only, contract-shaped)

- [ ] 4.1 `parseStores(json)` for `store list --json` → `{"stores":[{"id","root"}],"status":[]}`; produce `StoreEntry.basic(...)` per entry; tolerate an empty `stores` array (return empty list, not error).
- [ ] 4.2 `parseStoreDoctor(json)` for `store doctor --json` → `{"stores":[{id,root,metadata_path,openspec_root:{present,config:{present,path},specs:{present},changes:{present},archive:{present},healthy,status:[]},metadata:{present,valid,id,remote},git:{is_repository,has_commits,has_uncommitted_changes,has_remote,origin_url},status:[]}],status:[]}`; enrich the matching `StoreEntry` with health. Treat any `git` subfield as nullable (non-git stores emit null) — never NPE.
- [ ] 4.3 `parseWorksets(json)` for `workset list --json` → `{"worksets":[{"name","members":[{"name","path"}]}],"status":[]}`; produce `WorksetEntry` with its `Member` list; tolerate empty `worksets`/`members` arrays.
- [ ] 4.4 Parse the uniform diagnostic envelope `status: [{severity, code, message, target, fix}]` on every command and retain each diagnostic (including its ready-made `fix` string) so the UI can display it against the affected store/workset. Read-only: do not execute the `fix`.

## 5. Read service wiring

- [ ] 5.1 Add `resolveStores()` and `resolveWorksets()` to `CoordinationService`: CLI-first when `cliStoreAvailable()`, parsing `store list --json` / `workset list --json`; on a parse failure, log and fall back to the on-disk registry/`worksets.yaml` readers rather than throwing (beta-guard).
- [ ] 5.2 Add on-disk fallback readers `readStoresFromDisk(paths)` (reusing the shared backend-local-path parser from 1.2) and `readWorksetsFromDisk(paths)` (reading `worksets: {<name>: {members: [{name, path}]}}`).
- [ ] 5.3 Add a lazy `fetchStoreDoctor(StoreEntry)` that runs `store doctor <id> --json` off the EDT and returns the entry enriched via `parseStoreDoctor`, returning the entry unchanged when the CLI is unavailable or the lookup fails.
- [ ] 5.4 Include stores and worksets in the off-EDT coordination snapshot; the snapshot carries whether it was sourced from the CLI vs the on-disk fallback.

## 6. Root canonicalization

- [ ] 6.1 Add a `canonicalize(Path)` helper: `toRealPath()` first, falling back to `toAbsolutePath().normalize()` when the path does not exist (or `toRealPath` throws). Use it on BOTH the current project root and each registered store root before comparing them — string comparison alone would miss symlinked or Windows 8.3 short-path roots that the CLI canonicalizes.
- [ ] 6.2 Expose a "store matching the current project root" lookup built on the canonicalized comparison, for the panel to highlight the active store.

## 7. Coexistence and legacy demotion (no migration)

- [ ] 7.1 Key the surface's lead model off the resolved CLI version: at CLI ≥ `1.5.0`, stores/worksets are canonical; below it, retain the legacy 1.4 presentation. The plugin performs NO migration — it only reflects on-disk state.
- [ ] 7.2 Detect whether legacy `workspaces`/`context-stores` state exists on disk; only when it does AND stores/worksets are the lead model, render a muted, read-only "Legacy (pre-1.5)" group. Suppress the legacy group entirely when no legacy state exists.

## 8. Read-only tool-window presentation

- [ ] 8.1 Add read-only **Stores** and **Worksets** groups to the coordination panel, reusing the existing tiered Hidden/Awareness model (no Full/write tier introduced here). Worksets render their members as child rows (`name` + `path`).
- [ ] 8.2 Render per-store health badges from `store doctor` (e.g. metadata issue / not-a-git-repo / unhealthy openspec-root), and surface the retained diagnostic `fix` text as read-only guidance on the affected row.
- [ ] 8.3 Render the demoted legacy group in muted styling when present (per 7.2); no write buttons for stores or worksets in this change.
- [ ] 8.4 Ensure all resolution runs off the EDT (`executeOnPooledThread`) with the tree rebuilt via `invokeLater`; a parse failure degrades to Awareness/on-disk and never throws into the UI.

## 9. Documentation

- [ ] 9.1 Update README with the read-only store/workset surface and the legacy demotion (vendor-neutral, language-agnostic).
- [ ] 9.2 Update CHANGELOG under the unreleased section.
- [ ] 9.3 Update `docs/feature-reference.md` (and any wiki source) to describe stores, worksets, the `1.5.0` floor, and that write actions are deferred.
- [ ] 9.4 Update the coverage matrix `docs/openspec-support.md`: add a **Stores & worksets (1.5)** section with `store` and `workset` rows — CLI column `1.5+` for the CLI-sourced listing and `built-in` for the on-disk fallback; note read-only in this change (write actions deferred). Extend the **Version support** section to add the 1.5.x line and the store/workset floor, and reflect stores/worksets in the plugin-original / IDE value-add table where appropriate. Keep vendor-neutral.

## Tests

Every parser and disk reader added here parses output the plugin does not control, so each is contract-tested against **captured real 1.5.0 CLI output**, never a hand-authored shape. Capture once from the real 1.5.0 binary under an isolated `XDG_DATA_HOME` (leaving the real global dir untouched), sanitize machine-specific paths, and commit the fixtures **version-namespaced** under `src/test/resources/fixtures/cli/1.5.0/`. Each test below must FAIL if the code it covers regresses.

- [ ] T.1 Commit captured fixtures: `1.5.0/store-list.json`, `1.5.0/store-doctor.json` (including at least one non-git store with null `git` subfields), and `1.5.0/workset-list.json` (at least one workset with members). Sanitize absolute paths.
- [ ] T.2 `parseStores` contract test against `1.5.0/store-list.json`: asserts the exact `id`/`root` of each `StoreEntry`; fails if the `stores` array key or entry field names drift.
- [ ] T.3 `parseStoreDoctor` contract test against `1.5.0/store-doctor.json`: asserts metadata present/valid, `git.is_repository`, and `openspec_root.healthy` are read from the exact nested keys; a separate assertion proves a non-git store (null `git`) parses without NPE and yields null git health.
- [ ] T.4 `parseWorksets` contract test against `1.5.0/workset-list.json`: asserts workset `name` and each `Member` `{name, path}`; fails if the `worksets`/`members` nesting drifts.
- [ ] T.5 Diagnostic-envelope test: parse a fixture whose `status: []` carries a `{severity, code, message, target, fix}` entry and assert the `fix` string is retained on the parsed model.
- [ ] T.6 On-disk fallback tests via a fixture data dir (`XDG_DATA_HOME`): `readStoresFromDisk` parses `stores/registry.yaml` (proving the reused backend-local-path parser handles the store dir), and `readWorksetsFromDisk` parses `worksets/worksets.yaml`; an empty/missing dir yields an empty list, not an error.
- [ ] T.7 Version-gating test: `resolveStores`/`resolveWorksets` use the CLI path only at detected version ≥ `1.5.0` and fall back to disk below it; assert `VersionSupport` is not consulted.
- [ ] T.8 Root-canonicalization test: a store root and the project root that differ only by symlink / trailing `.` / non-normalized segments match after canonicalization, and a genuinely different root does not; include the non-existent-path fallback branch.
- [ ] T.9 Legacy-demotion test: with both legacy and new state present at CLI ≥ `1.5.0`, stores/worksets are the lead model and the legacy group is present-but-muted; with no legacy state on disk, the legacy group is absent.
- [ ] T.10 Beta-guard test: a malformed `store list` / `workset list` payload degrades to the on-disk fallback (Awareness) and does not throw.
