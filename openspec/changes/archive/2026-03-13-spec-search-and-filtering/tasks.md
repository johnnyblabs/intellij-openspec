## 1. Filtered Tree Model

- [x] 1.1 Add `buildFilteredModel(String query)` method to `SpecTreeModel` that constructs a pruned tree keeping only nodes whose label (or descendant label) matches the query (case-insensitive substring)
- [x] 1.2 Show a "No results for '<query>'" hint node when no nodes match

## 2. Search Field Integration

- [x] 2.1 Add `SearchTextField` to `OpenSpecToolWindowPanel` between the toolbar and tree scroll pane
- [x] 2.2 Attach a `DocumentListener` that triggers debounced filtering (150ms via `Alarm`) on text changes
- [x] 2.3 Save expansion state before first filter keystroke, restore when filter is cleared

## 3. Auto-Expand and Keyboard Shortcut

- [x] 3.1 Auto-expand all nodes in the filtered tree so matches are immediately visible
- [x] 3.2 Register Ctrl+F / Cmd+F keyboard shortcut on the tree to focus the search field

## 4. Verification

- [x] 4.1 Build and verify search field appears in the tool window layout
- [x] 4.2 Verify filtering works for spec domains, requirements, changes, and artifacts
- [x] 4.3 Verify clearing the filter restores the full tree with previous expansion state
- [x] 4.4 Verify Ctrl+F / Cmd+F focuses the search field
