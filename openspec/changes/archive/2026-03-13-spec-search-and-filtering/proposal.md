## Why

As the number of specs grows, finding a specific requirement or change in the tool window tree becomes tedious — users must manually expand nodes and scan labels. A search and filter bar lets users instantly locate specs, requirements, and changes by typing a few characters, which is critical for projects with dozens of spec domains.

## What Changes

- Add a search/filter text field above the tree in the tool window
- Filter tree nodes in real-time as the user types, showing only matching nodes and their parents
- Match against node labels (spec domain names, requirement names, change names, artifact IDs)
- Preserve tree expansion state when clearing the filter
- Show a "no results" hint when the filter matches nothing
- Add a keyboard shortcut to focus the search field from the tree

## Capabilities

### New Capabilities
- `spec-search`: Real-time search and filtering of the tool window tree by node label text

### Modified Capabilities
- `tool-window`: Add search field to the tool window layout between toolbar and tree

## Impact

- **OpenSpecToolWindowPanel.java**: Add search field component and filter logic
- **SpecTreeModel.java**: Add filtered model building that prunes non-matching subtrees
- **plugin.xml**: No changes needed (no new extensions)
- **No new dependencies**: Uses standard Swing/IntelliJ UI components
