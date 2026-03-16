## Why

When a change includes delta specs (ADDED/MODIFIED/REMOVED/RENAMED requirements), those deltas currently stay in the change archive with no automated way to merge them into the project's main specs under `openspec/specs/`. This means the canonical spec files drift from the implemented reality after every change. A Sync Specs action closes the loop so main specs always reflect what was actually built.

## What Changes

- New `SpecSyncService` that parses delta spec sections and applies each operation (ADDED, MODIFIED, REMOVED, RENAMED) to the corresponding main spec file
- New `SyncPreviewDialog` showing a side-by-side diff of each affected main spec before and after the merge
- New `OpenSpecSyncAction` wired to a "Sync Specs" button in the `WorkflowActionPanel`
- Registration of `OpenSpec.SyncSpecs` action in `plugin.xml`

## Capabilities

### New Capabilities
- `spec-sync`: Parsing, applying, and previewing delta spec merges into main specs

### Modified Capabilities
- `workflow`: Adds Sync Specs button to the workflow action panel alongside existing archive controls

## Impact

- **Code**: New service, dialog, and action classes; `WorkflowActionPanel` gains a Sync Specs button; `plugin.xml` updated
- **Specs**: `openspec/specs/workflow/spec.md` gains requirements for the Sync Specs action
- **Dependencies**: Uses IntelliJ `DiffManager` for side-by-side preview; no new external dependencies
