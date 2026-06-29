## Context

OpenSpec CLI 1.4.x added a coordination layer for multi-repo / multi-folder work: `workspace`, `context-store`, and `initiative` commands. Their state lives outside any single repo, in the global data dir (`$XDG_DATA_HOME/openspec` or `~/.local/share/openspec`; Windows `%LOCALAPPDATA%\openspec`), with workspaces under `managed-workspaces/`, context stores under `context-stores/`, and initiatives nested within their context store as `<store>/<id>/{initiative.yaml,requirements.md,design.md,decisions.md,questions.md,tasks.md}`.

The plugin already has the building blocks this surface needs:
- `util/CliRunner` (`CliRunner.run(project, args…) → CliResult` with `stdout`/`stderr`) for off-EDT CLI invocation, with `CliDetectionService` reporting availability and version.
- `WorkflowSchemaContextService` / `WorkflowSchemaContext` (Phase 1) — the resolved active mode and version axes, already mode-aware (`workspace-planning`, non-repo-local).
- Gson for JSON parsing (as in `SchemaService`).
- `OpenSpecToolWindowFactory`, which already registers tabs conditionally (the Explore tab appears only when applicable) — the precedent for a conditionally-shown Coordination tab.

What is missing is any read or presentation of the three coordination collections. This change adds that surface.

## Goals / Non-Goals

**Goals:**
- A single read service that resolves workspaces, context stores, and initiatives, CLI-first with an on-disk fallback, returning plugin-side model objects.
- A tool-window surface that presents the collections at a tier appropriate to detected state and CLI availability (Hidden / Awareness / Full).
- Initiative-artifact navigation and a small set of CLI-delegated write actions (create initiative, set up/register context store, set up/link workspace) in the Full tier.
- Strict EDT discipline and graceful degradation, consistent with the rest of the plugin.

**Non-Goals:**
- Editing `initiative.yaml` or coordination metadata through dedicated form UI — artifacts open in the editor as files; structured editing is out of scope.
- Re-implementing coordination semantics (resolution, linking rules, git backend) in the plugin — the CLI remains the source of truth; the plugin reads and delegates.
- `init --profile <core|custom>`. Considered and descoped: the plugin's init is built-in-only by deliberate design (no `openspec init` call to attach the flag to), and profile selection is tracked separately under the profile-alignment work. Belongs in its own focused change against the incident-prone config/profile area.
- Bumping the CLI floor. The floor stays 1.3 / baseline 1.4 (the floor-to-1.4 effort was cancelled deliberately); coordination features simply require 1.4 to be *fully* enabled and degrade below it.
- Multi-project IDE workspace support (a separate, unrelated backlog item).

## Decisions

### Decision 1: One `CoordinationService` (projectService), CLI-first with on-disk fallback
A single `CoordinationService` registered as a `projectService` exposes `resolveWorkspaces()`, `resolveContextStores()`, and `resolveInitiatives()`, each returning immutable model records. When `CliDetectionService` reports the CLI present and ≥ 1.3, it shells `openspec {workspace,context-store,initiative} list --json` via `CliRunner` and parses with Gson. Otherwise it resolves the global data dir and reads the on-disk registries/`initiative.yaml` files directly.

*Why:* mirrors the established CLI-first-with-fallback pattern (`SchemaService`, `WorkflowSchemaContextService`). One service keeps the global-data-dir path resolution (and its platform branches) in a single place.
*Alternative considered:* three separate services — rejected as needless fan-out; the collections share path resolution and a refresh lifecycle.

### Decision 2: Tier resolved from `WorkflowSchemaContext` + detected state, not the directory tree
A `CoordinationTier` (`HIDDEN`, `AWARENESS`, `FULL`) is computed from (a) whether any collection is non-empty, (b) the active mode from `WorkflowSchemaContextService`, and (c) CLI availability/floor from `CliDetectionService`. `FULL` requires the CLI ≥ floor; `AWARENESS` is the read-only on-disk view; `HIDDEN` when nothing is detected and the mode is not a coordination mode.

*Why:* reuses the Phase 1 source of truth instead of re-deriving mode from disk, satisfying the proposal's "build on the foundation" intent.
*Alternative considered:* always show the tab — rejected; it would clutter the tool window for the spec-driven repo-local majority.

### Decision 3: Conditional tab in `OpenSpecToolWindowFactory`, lazy + refreshable
A `CoordinationPanel` is added as a "Coordination" tab using the same conditional `ContentFactory.addContent` path the Explore tab uses; it is created only when the tier is not `HIDDEN`. The panel renders three grouped sections (Workspaces, Context Stores, Initiatives) with a manual refresh; resolution runs on `executeOnPooledThread` and updates the model via `invokeLater`.

*Why:* consistent with existing tool-window construction and EDT rules; lazy creation avoids paying for resolution when there is nothing to show.

### Decision 4: Status and health as visual indicators
Initiative `status` (`exploring|active|complete|archived`) renders as a badge/colored label in the cell renderer (reusing the tree/list renderer conventions in `SpecTreeCellRenderer`). Workspace resolution health and context-store doctor diagnostics render as secondary indicators. Doctor detail is fetched lazily (only when an entry is expanded/selected) to keep the initial refresh cheap.

*Why:* keeps the common list refresh to the three cheap `list --json` calls; the heavier `doctor` calls are on demand.

### Decision 5: Write actions delegate to the CLI, gated on the Full tier
Create-initiative, set-up/register-context-store, and set-up/link-workspace are `AnAction`s enabled only in the Full tier. Each runs the corresponding `openspec …` command via `CliRunner` on a background thread, refreshes the affected section on success, and surfaces `CliResult.stderr` on failure. `update()` methods only read cached tier/selection state — no CLI calls.

*Why:* the plugin never reimplements coordination mutations; it stays a faithful client. Gating on Full tier guarantees a CLI is present to honor the action.

## Risks / Trade-offs

- **Undocumented `--json` on the coordination commands** → The 1.4.1 CLI accepts `--json` on `workspace/context-store/initiative list` even though `--help` does not advertise it. Mitigation: the on-disk fallback parser is authoritative-capable on its own, so a future CLI that drops/renames `--json` degrades to Awareness rather than breaking; parse failures are caught and fall through to the on-disk read.
- **Global-data-dir drift across platforms/CLI versions** → Path layout (`managed-workspaces/`, `context-stores/`, XDG vs. `~/.local/share`) could change upstream. Mitigation: centralize resolution in `CoordinationService`, cover it with tests using a fixture data dir via `XDG_DATA_HOME`, and treat a missing/empty dir as Hidden, not error.
- **Stale listings after external CLI changes** → State can change from the terminal outside the IDE. Mitigation: manual refresh plus refresh-on-tab-activation; no attempt to file-watch the global dir in this phase.
- **Tool-window clutter** → Mitigation: the Hidden tier keeps the tab absent for the spec-driven repo-local majority.

## Migration Plan

Additive change — no migration. The Coordination tab appears only when coordination state or mode is detected; existing repo-local spec-driven projects see no difference. Rollback is removal of the tab/service registration; no persisted plugin state is introduced beyond existing settings. README, CHANGELOG, and feature-reference are updated in the same change.

## Resolved Decisions (formerly open questions)

- **Workspace `open` is deferred.** This phase covers list/doctor/navigate plus create/setup/link/register; `workspace open` (launching an external agent/editor) is out of scope — low value from inside the IDE and it shells out to external tools.
- **Initiative create collects minimal input.** The create action collects only the required fields (id/title), runs `openspec initiative create` with them as flags, and then opens the created `initiative.yaml` in the editor for the user to complete summary/status/owners. No full in-IDE form.
