## Context

The existing archive flow handles one change at a time: the user clicks Archive in the WorkflowActionPanel, the change is moved to `openspec/changes/archive/YYYY-MM-DD-<name>/`, and post-archive steps run. When multiple changes are ready, the user must repeat this process for each one. There is also no mechanism to detect when two changes have delta specs targeting the same capability, which could cause one sync to overwrite another.

The `SpecSyncService` (from the spec-sync change) already handles parsing and applying delta specs. The `ChangeService.archiveChange()` method handles the directory move. Bulk Archive composes these existing services.

## Goals / Non-Goals

**Goals:**
- Provide a dialog listing all active changes with checkboxes and completion status
- Detect and warn when selected changes have overlapping delta spec domains
- Archive selected changes sequentially, applying spec sync for each
- Allow the user to reorder changes to control sync priority

**Non-Goals:**
- Merging conflicting delta specs automatically (the user resolves by choosing archive order)
- Parallel archive execution (sequential is simpler and avoids write conflicts)
- Undo/rollback of a bulk archive (use git revert)

## Decisions

### Decision 1: Dialog with checkbox list and status column

Use a `DialogWrapper` with a `JBTable` (or `CheckBoxList`) showing each active change with columns: checkbox, name, artifact status (complete/incomplete), and conflict indicator. This gives the user full control over which changes to archive and in what order.

**Alternative considered**: A simple confirmation popup listing changes. Rejected because it doesn't allow selective archiving or conflict visibility.

### Decision 2: Conflict detection by capability overlap

Two changes conflict when both have delta specs under `specs/<capability>/spec.md` for the same capability name. Detection scans each selected change's `specs/` directory and builds a map of capability → list of changes. Any capability with 2+ changes is flagged.

Conflicts are warnings, not blockers — the user can proceed. The archive order determines which sync "wins" (last write wins for MODIFIED operations on the same requirement).

### Decision 3: Sequential archive with per-change spec sync

Each selected change is archived in list order:
1. Run `SpecSyncService.computeSync(changeName)` and apply if deltas exist
2. Move the change to archive via `ChangeService.archiveChange()`
3. Report per-change success/failure in the dialog

If one change fails, remaining changes still attempt to archive (best-effort).

### Decision 4: Reuse existing ChangeService and SpecSyncService

No new service needed — the action and dialog orchestrate calls to `ChangeService.archiveChange()` and `SpecSyncService.applySync()`. This keeps the new code surface small.

## Risks / Trade-offs

- **[Last-write-wins on conflicts]** → If two changes MODIFY the same requirement, the last-archived change's version wins. Mitigation: conflict warnings in the dialog clearly state which capabilities overlap, and the user controls ordering.
- **[Partial failure]** → If one archive fails mid-batch, earlier changes are already archived. Mitigation: best-effort approach with per-change status reporting; user can re-run for failed changes.
- **[Large batch performance]** → Archiving many changes sequentially with spec sync could be slow. Mitigation: progress indicator in the dialog showing current change being archived.
