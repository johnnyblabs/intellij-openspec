## Why

OpenSpec CLI 1.4.x introduced a coordination layer — `workspace`, `context-store`, and `initiative` commands — for work that spans multiple repos and folders. The plugin reaches a faithful 1.4 baseline (tool detection, the `workspace-planning` schema, RENAMED deltas, a schema/version-aware foundation, and a rebuilt Verify), but it has no IDE surface for these three coordination collections: a user on 1.4 can set up workspaces and initiatives from the terminal and the IDE shows nothing. This change closes that gap — the final phase of the workflow-fidelity roadmap (see the linked tracker entry; it builds directly on the Phase 1 workflow-schema-context foundation).

## What Changes

- Add an IDE surface (a tool-window tab) that presents the three 1.4 coordination collections, each sourced from the CLI's JSON output with a built-in fallback that reads the global data dir directly when the CLI is absent or below the floor:
  - **Workspaces** — from `openspec workspace list --json` (`{workspaces, status}`), with resolution health surfaced from `openspec workspace doctor`.
  - **Context stores** — from `openspec context-store list --json` (`{context_stores, status}`); entries carry `{id, root, metadataPath?}`, and doctor adds `metadata.{present,valid}`, `git.isRepository`, and diagnostics.
  - **Initiatives** — from `openspec initiative list --json` (`{context_store, context_stores, initiatives, status}`); each initiative carries `{version, id, title, summary, status, created, owners, metadata}` with status ∈ `exploring | active | complete | archived`, and lives at `<store>/<id>/` alongside `requirements/design/decisions/questions/tasks.md`.
- Adopt a **tiered presentation** keyed off detected state and CLI availability:
  - **Hidden** — no coordination state present and not in a coordination mode: the surface stays out of the way.
  - **Awareness** — coordination state detected: read-only listing with status badges and a mode indicator, derived from the Phase 1 `workflow-schema-context`.
  - **Full** — open an initiative's artifacts in the editor, and invoke create/setup/link/register actions through the CLI.
- Degrade gracefully: when the CLI is unavailable or below the 1.3 floor, the surface shows read-only awareness from on-disk state and disables write actions with guidance. The CLI floor is **not** changed (stays 1.3, baseline 1.4).

> The `init --profile <core|custom>` flag was considered for this change but descoped: the plugin's init is built-in-only by deliberate design (no `openspec init` invocation to attach the flag to), and profile selection is already tracked separately under the profile-alignment work. It is intentionally out of scope here.

## Capabilities

### New Capabilities
- `coordination-surfaces`: IDE presentation and actions for the OpenSpec 1.4 coordination layer (workspaces, context-stores, initiatives) — CLI-sourced with global-data-dir fallback, a tiered Hidden/Awareness/Full UX, and initiative-artifact navigation.

### Modified Capabilities
<!-- None. `init --profile` was considered but descoped (see What Changes); profile work is tracked separately. -->



## Impact

- **Code:** a new coordination read service (CLI JSON + global-data-dir fallback resolver), a new tool-window tab/panel and tree/list model, and consumption of the existing `WorkflowSchemaContext` for mode/version awareness.
- **CLI contract:** relies on `openspec {workspace,context-store,initiative} list --json` and the corresponding `doctor` outputs (1.4.x); built-in fallback reads `$XDG_DATA_HOME/openspec` / `~/.local/share/openspec` (Windows `AppData/Local/openspec`) — `managed-workspaces/`, `context-stores/`, and initiatives within stores.
- **Platform compatibility:** no change — continues to support IntelliJ IDEA 2024.2 and later. All CLI/IO runs off the EDT; UI updates via `invokeLater`; services registered as `projectService`.
- **Docs:** README, CHANGELOG, and feature-reference updated to describe the coordination surface and the `init --profile` option.
- **Tracker:** the linked issue.
