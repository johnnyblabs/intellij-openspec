# Design: config-yaml-viewer

## Approach

Extend the existing Browse tree to include a Config section. The ConfigService already parses `openspec/config.yaml` into an `OpenSpecConfig` model — we just need to present it as tree nodes.

### Tree Node Structure

New node types in `TreeNodeType` enum:
- `CONFIG` — the section header ("Config")
- `CONFIG_ENTRY` — a key-value leaf ("schema: spec-driven")

The `buildConfigNode()` method in `SpecTreeModel`:
1. Gets `ConfigService.getConfig()`
2. If config is null or not loaded, shows a HINT node ("No config.yaml found")
3. Otherwise, creates flat child nodes for each top-level field:
   - `schema` → "schema: {value}"
   - `version` → "version: {value}"
   - `profile` → "profile: {profile.name}" (shows the `name` key from the map)
   - `context` → "context: {truncated to ~60 chars}..."
   - `rules` → "rules: {count} defined"
4. Each CONFIG_ENTRY node stores the config.yaml file path for double-click navigation
5. Null/empty fields are skipped

### Renderer

- `CONFIG` node: use the settings gear icon (`AllIcons.General.Settings`) — fits semantically and avoids creating a new SVG
- `CONFIG_ENTRY` nodes: no icon, plain text (same approach as HINT nodes but normal style, not italic)

### Double-Click

In `OpenSpecToolWindowPanel.handleDoubleClick()`:
- `CONFIG` or `CONFIG_ENTRY` type → open `openspec/config.yaml` in the editor

### Search/Filter

Config nodes participate in the existing tree filter. The `filterNode()` method already handles arbitrary node types — config entries will match on their label text (e.g., searching "rules" finds the rules entry).

## Components Affected

- `SpecTreeModel` — add `buildConfigNode()`, add `CONFIG`/`CONFIG_ENTRY` to enum
- `SpecTreeCellRenderer` — add icon case for CONFIG, plain style for CONFIG_ENTRY
- `OpenSpecToolWindowPanel` — add double-click case for config nodes

## Trade-offs

- **Flat vs. nested**: Flat is simpler and sufficient — users who want to edit will double-click to open the full YAML. Nested tree would add complexity for little benefit.
- **Reusing AllIcons vs. custom SVG**: Using `AllIcons.General.Settings` avoids asset creation. Can always add a custom icon later.
- **No inline editing**: Editing happens in the editor, keeping the tree read-only and simple.