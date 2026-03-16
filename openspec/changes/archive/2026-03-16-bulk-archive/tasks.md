## 1. Conflict Detection

- [x] 1.1 Add `detectConflicts(List<String> changeNames)` method to `SpecSyncService` that returns a map of capability name to list of conflicting change names
- [x] 1.2 Add tests for conflict detection: no conflicts, single overlap, multiple overlaps

## 2. Bulk Archive Dialog

- [x] 2.1 Create `BulkArchiveDialog` extending `DialogWrapper` with a checkbox list of active changes showing name, artifact completion status, and conflict indicators
- [x] 2.2 Implement dynamic conflict updating: recalculate and display conflicts as checkboxes are toggled
- [x] 2.3 Add warning panel showing overlapping capabilities when conflicts exist
- [x] 2.4 Add progress reporting during sequential archive (per-change status: pending/archiving/done/failed)

## 3. Action and UI Integration

- [x] 3.1 Create `OpenSpecBulkArchiveAction` extending `OpenSpecBaseAction` that opens `BulkArchiveDialog` and orchestrates sequential archive with spec sync
- [x] 3.2 Add Bulk Archive menu item to `plugin.xml` alongside existing Archive action
- [x] 3.3 Add Bulk Archive entry point to `WorkflowActionPanel` (visible when 2+ active changes exist)
- [x] 3.4 Add tests for action enablement and sequential archive flow
