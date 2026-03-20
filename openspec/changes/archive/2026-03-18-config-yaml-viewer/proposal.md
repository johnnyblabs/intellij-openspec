## Why

The `openspec/config.yaml` defines the project's schema, profile, context, and rules — but there's no way to see it from the plugin UI. Users have to find and open the file manually.

## What Changes

Add a **Config** section to the existing Browse tree that displays config.yaml contents as flat, read-only nodes. Double-clicking any Config node opens config.yaml in the editor for editing.

```
v OpenSpec
  > Specs
  v Changes
    > ...
  > Archive
  v Config
    schema: spec-driven
    version: 1.2.0
    profile: OpenSpecPlugin
    context: IntelliJ Platform plugin...
    rules: 5 defined
```

### Design Decisions

- **Flat display** — top-level keys only, no nested expansion
- **Maps summarized** — `profile` shows the `name` value, `rules` shows count
- **Long strings truncated** — `context` gets clipped
- **Double-click to edit** — opens config.yaml in the IntelliJ editor (same pattern as spec files)
- **No new tab or panel** — reuses the existing tree in the Browse tab

## Capabilities

### Modified Capabilities

- Browse tree (SpecTreeModel + renderer + double-click handling)

## Impact

- `SpecTreeModel.java` — new `buildConfigNode()` method, new `CONFIG` / `CONFIG_ENTRY` node types
- `SpecTreeCellRenderer.java` — icon for config nodes
- `OpenSpecToolWindowPanel.java` — double-click handling for config nodes
- `ConfigService.java` — already parses config, no changes needed