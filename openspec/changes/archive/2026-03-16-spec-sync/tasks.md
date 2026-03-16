## 1. Models

- [x] 1.1 Create `DeltaSpecOperation` record with fields: type (ADDED/MODIFIED/REMOVED/RENAMED), capability name, requirement name, content, and rename metadata (fromName/toName)
- [x] 1.2 Create `SpecSyncResult` record with fields: capability name, main spec path, original content, projected content, list of applied operations

## 2. Delta Spec Parsing

- [x] 2.1 Create `SpecSyncService` as `@Service(Service.Level.PROJECT)` with `parseDeltaSpecs(changeName)` method
- [x] 2.2 Implement section-level parsing: split delta spec files by `## (ADDED|MODIFIED|REMOVED|RENAMED) Requirements` headings
- [x] 2.3 Implement requirement-level parsing: extract `### Requirement: <name>` blocks within each section
- [x] 2.4 Handle RENAMED format parsing: extract FROM:/TO: name pairs
- [x] 2.5 Add tests for parsing each section type (ADDED, MODIFIED, REMOVED, RENAMED) and edge cases (empty sections, no delta sections)

## 3. Sync Computation

- [x] 3.1 Implement `computeSync(changeName)` that reads main spec files and applies operations in order (REMOVED → RENAMED → MODIFIED → ADDED)
- [x] 3.2 Implement ADDED application: append requirement block to main spec content, or create new file content with header
- [x] 3.3 Implement MODIFIED application: locate requirement block by name (case-insensitive) and replace entire block
- [x] 3.4 Implement REMOVED application: locate requirement block by name and remove it
- [x] 3.5 Implement RENAMED application: find requirement header and update the name text
- [x] 3.6 Handle unmatched requirement names: collect warnings for MODIFIED/REMOVED/RENAMED operations that don't match
- [x] 3.7 Add tests for each operation type applied to sample spec content, including unmatched name warnings

## 4. Preview Dialog

- [x] 4.1 Create `SyncPreviewDialog` extending `DialogWrapper` that accepts a list of `SpecSyncResult`
- [x] 4.2 Display a tab or list for each affected capability with IntelliJ `DiffManager` side-by-side view
- [x] 4.3 Show warnings for unmatched operations in the dialog
- [x] 4.4 Return confirmation result (OK/Cancel) to the caller

## 5. Action and UI Integration

- [x] 5.1 Create `OpenSpecSyncAction` extending `AnAction` that invokes `SpecSyncService.computeSync`, opens `SyncPreviewDialog`, and applies on confirm
- [x] 5.2 Implement `applySync(List<SpecSyncResult>)` in `SpecSyncService`: write files via `WriteAction` + VFS refresh
- [x] 5.3 Add Sync Specs button to `WorkflowActionPanel` visible when all artifacts are done and delta specs exist
- [x] 5.4 Register `OpenSpec.SyncSpecs` action in `plugin.xml` with menu and toolbar references
- [x] 5.5 Add tests for action enablement logic and sync apply write behavior
