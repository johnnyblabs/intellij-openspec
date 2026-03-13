## Why

Delta specs describe what changes a proposal makes to existing capabilities, but there's no way to preview those changes in context. Users must manually open both the delta spec and main spec side-by-side and mentally reconcile ADDED/MODIFIED/REMOVED sections. A built-in diff viewer shows the delta alongside the current main spec, making it immediately clear what will change at archive time.

## What Changes

- Add a "Preview Diff" context menu action on DELTA_SPEC tree nodes
- Open IntelliJ's built-in diff viewer showing the delta spec (left) vs the corresponding main spec (right)
- Add an "Open File" context menu action on DELTA_SPEC nodes (currently missing)
- Handle the case where no corresponding main spec exists yet (new capability — show delta vs empty)

## Capabilities

### New Capabilities
- `delta-spec-diff`: Side-by-side diff viewer for delta specs against their corresponding main specs

### Modified Capabilities
- `tool-window`: Add context menu actions for DELTA_SPEC nodes (Preview Diff, Open File)

## Impact

- **OpenSpecToolWindowPanel.java**: Add DELTA_SPEC case to context menu switch
- **New action class**: DeltaSpecDiffAction using IntelliJ DiffManager API
- **No new dependencies**: Uses built-in `com.intellij.diff` APIs (DiffManager, SimpleDiffRequest, DiffContentFactory)
