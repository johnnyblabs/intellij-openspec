## 1. Model and path resolution

- [x] 1.1 Add immutable model records for the three collections: `WorkspaceEntry` (name, resolves-locally, path), `ContextStoreEntry` (id, root, metadataPath, optional doctor health), and `InitiativeEntry` (id, title, summary, status enum, created, owners, store root).
- [x] 1.2 Add an `InitiativeStatus` enum (`EXPLORING`, `ACTIVE`, `COMPLETE`, `ARCHIVED`) with parse-from-string and a display label.
- [x] 1.3 Add a `CoordinationPaths` helper that resolves the global data dir (`$XDG_DATA_HOME/openspec` → `~/.local/share/openspec` on Unix/macOS → `%LOCALAPPDATA%\openspec` on Windows) and the `managed-workspaces/`, `context-stores/`, and per-store initiative locations.

## 2. CoordinationService (read + fallback)

- [x] 2.1 Create `CoordinationService` registered as a `projectService` in `plugin.xml`.
- [x] 2.2 Implement CLI-first resolution: `resolveWorkspaces()`, `resolveContextStores()`, `resolveInitiatives()` via `CliRunner.run(project, …, "--json")` parsed with Gson, gated on `CliDetectionService` reporting CLI ≥ 1.3 floor.
- [x] 2.3 Implement the on-disk fallback: read the registries and `initiative.yaml` files under `CoordinationPaths` when the CLI is absent/below floor or a `--json` parse fails.
- [x] 2.4 Add lazy doctor lookups (`workspace doctor`, `context-store doctor`) used only on entry expansion/selection.
- [x] 2.5 Ensure all resolution runs off the EDT (`executeOnPooledThread`); the service returns data only (no UI).

## 3. Tier resolution

- [x] 3.1 Add a `CoordinationTier` enum (`HIDDEN`, `AWARENESS`, `FULL`) and a resolver that combines detected-state non-emptiness, active mode from `WorkflowSchemaContextService`, and CLI availability/floor from `CliDetectionService`.
- [x] 3.2 Unit-test the tier matrix: no state + non-coordination mode → HIDDEN; state present + no/old CLI → AWARENESS; state present + CLI ≥ floor → FULL.

## 4. Coordination tool-window surface

- [x] 4.1 Add `CoordinationPanel` with three grouped sections (Workspaces, Context Stores, Initiatives), a manual refresh, and an empty state per section.
- [x] 4.2 Render entries with a cell renderer that shows initiative status badges and workspace/context-store health indicators (reuse `SpecTreeCellRenderer` conventions).
- [x] 4.3 Register a conditional "Coordination" tab in `OpenSpecToolWindowFactory` (same pattern as the Explore tab), created only when the tier is not HIDDEN.
- [x] 4.4 Wire refresh: resolution on `executeOnPooledThread`, model/UI updates via `invokeLater`; disable write actions in the AWARENESS tier with guidance text.

## 5. Initiative artifact navigation

- [x] 5.1 Add an action to open a selected initiative artifact (`initiative.yaml`, `requirements.md`, `design.md`, `decisions.md`, `questions.md`, `tasks.md`) from `<store>/<id>/` in the editor.
- [x] 5.2 Handle the missing-artifact case: no editor opened, indicate the artifact is not yet created.

## 6. Coordination write actions (Full tier)

- [x] 6.1 Add a create-initiative action that collects the minimal required fields and runs `openspec initiative create` via `CliRunner`, refreshing the initiative section on success.
- [x] 6.2 Add set-up/register context-store and set-up/link workspace actions delegating to the CLI.
- [x] 6.3 Enable write actions only in the FULL tier; `update()` reads cached tier/selection only (no CLI/IO); surface `CliResult.stderr` on failure and leave listings intact.

## 7. Tests

- [x] 7.1 `CoordinationService` tests: CLI JSON parsing for each collection, on-disk fallback using a fixture data dir via `XDG_DATA_HOME`, and empty/missing-dir → empty (not error).
- [x] 7.2 `InitiativeStatus` parse/label tests and tier-resolver tests (from 3.2).
- [x] 7.3 Action enablement tests: write actions disabled in AWARENESS/HIDDEN, enabled in FULL; artifact-open handles missing files.

## 8. Documentation

- [x] 8.1 Update README with the Coordination surface (vendor-neutral).
- [x] 8.2 Update CHANGELOG under the unreleased v0.3.0 section.
- [x] 8.3 Update `docs/feature-reference.md` (and any wiki source) with the new actions and service.
