# Tasks: config-yaml-viewer

## Implementation Tasks

- [x] Add `CONFIG` and `CONFIG_ENTRY` to `TreeNodeType` enum in `SpecTreeModel.java`
- [x] Add `buildConfigNode()` method to `SpecTreeModel` that reads from `ConfigService` and creates flat key-value child nodes (schema, version, profile name, context truncated, rules count)
- [x] Wire `buildConfigNode()` into `buildModel()` — add Config node after Archive, include in search filtering
- [x] Add renderer cases in `SpecTreeCellRenderer` — `AllIcons.General.Settings` for CONFIG, plain text style for CONFIG_ENTRY
- [x] Add double-click handling in `OpenSpecToolWindowPanel.handleDoubleClick()` for CONFIG/CONFIG_ENTRY types — open `openspec/config.yaml` in the editor (already works: existing handler opens any node with a filePath)

## Testing Tasks

- [x] Write unit tests for `buildConfigNode()` — verify node structure, label formatting, null/empty field handling
- [x] Manual test: verify Config section appears in tree, double-click opens config.yaml, search filters config entries (user verification needed)