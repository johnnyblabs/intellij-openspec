## Context

The tool window currently displays all specs, changes, and archives in a JTree with no filtering. `SpecTreeModel.buildModel()` constructs the full tree on each refresh. `OpenSpecToolWindowPanel` manages layout (toolbar → tree → workflow panel → status bar) and handles refresh, expansion state, and file listeners.

## Goals / Non-Goals

**Goals:**
- Real-time filtering of tree nodes as the user types
- Case-insensitive substring matching on node labels
- Filtered tree preserves parent chain (matching child shows all ancestors)
- Clearing the filter restores the full tree with previous expansion state
- Keyboard shortcut to focus search from tree

**Non-Goals:**
- Full-text search within spec file content (future work)
- Regex or advanced query syntax
- Persisting the filter across IDE restarts

## Decisions

### Decision 1: Filter at the tree model level

Apply filtering in `SpecTreeModel` by adding a `buildFilteredModel(String query)` method that constructs a pruned tree. This keeps the filter logic co-located with tree construction and avoids maintaining a separate filter layer over the rendered tree.

**Alternative considered:** Use `TreeModelFilter` or row sorter. Rejected — JTree doesn't have a built-in filter model like JTable. Custom filtering at build time is simpler and more predictable.

### Decision 2: Use SearchTextField from IntelliJ Platform

Use `com.intellij.ui.SearchTextField` which provides the standard IntelliJ search field UI with clear button, history, and consistent look-and-feel. Attach a `DocumentListener` that triggers debounced re-filtering on each keystroke.

**Alternative considered:** Plain `JTextField`. Rejected — loses the clear button, search icon, and IntelliJ theme integration for free.

### Decision 3: Debounce filtering with Alarm

Reuse the existing `Alarm` pattern (already in `OpenSpecToolWindowPanel` for refresh debouncing) to debounce filter keystrokes at 150ms. This prevents excessive tree rebuilds during fast typing.

### Decision 4: Ctrl+F / Cmd+F to focus search

Register a keyboard shortcut that focuses the search field. Use `Ctrl+F` (Windows/Linux) / `Cmd+F` (macOS) via `registerKeyboardAction` on the tree component, matching the standard find shortcut users expect.

## Risks / Trade-offs

- **[Risk] Large tree rebuild on every keystroke** → Mitigated by 150ms debounce. Tree construction is already fast (runs on pooled thread). For projects with hundreds of specs, could add incremental filtering later.
- **[Risk] Expansion state loss during filtering** → Save expansion state before applying filter, restore when filter is cleared. During active filtering, auto-expand all visible nodes so matches are always visible.
