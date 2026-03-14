## Context

The plugin has a `tracking/` package with 7 classes that integrate with Forgejo and Plane APIs during the change lifecycle (propose → apply → archive). These services are registered in `plugin.xml` and called from the three workflow actions plus `WorkflowActionPanel`. However, tracker configuration was never exposed in the settings UI, and the same tracker operations are performed externally by the AI-assisted workflow (Claude Code skills, Copilot prompts).

## Goals / Non-Goals

**Goals:**
- Remove all tracker-related code from the plugin
- Clean up all references in actions, UI, settings, model, and plugin.xml
- Leave no dead code or orphaned registrations

**Non-Goals:**
- Changing external workflow scripts or AI tool configurations
- Modifying `.openspec.yaml` file format (existing `tracking:` blocks are silently ignored)
- Adding any replacement tracker mechanism

## Decisions

### Decision 1: Delete entire tracking package

Delete all 7 files in `src/main/java/com/johnnyb/openspec/tracking/` and both test files. The package is self-contained — no other code depends on it, only calls into it.

### Decision 2: Remove settings fields without migration

Remove the 8 tracker fields from `OpenSpecSettings.State`. IntelliJ's `PersistentStateComponent` silently ignores XML keys that no longer map to fields, so no migration is needed. Existing project settings files will have orphaned keys that are harmlessly ignored and cleaned up on next settings save.

### Decision 3: Remove TrackingMetadata from ChangeMetadata

Remove the `TrackingMetadata`, `ForgejoRef`, and `PlaneRef` inner classes and the `tracking` field. SnakeYAML/Jackson ignores unknown YAML keys during deserialization, so `.openspec.yaml` files with `tracking:` blocks will still parse correctly — the data is simply not loaded.

### Decision 4: Remove sync UI from WorkflowActionPanel

Remove the `showSyncOutcome()` method, `onRetrySyncAction()` method, sync retry button, and all `ArchiveSyncService` references. The post-archive state display simplifies to just showing archive success without sync status.

## Risks / Trade-offs

- **[Low risk] Orphaned settings XML keys** → IntelliJ handles this gracefully. Keys are dropped on next save.
- **[Low risk] Orphaned tracking data in archived .openspec.yaml files** → Data remains in files but is never read. Not harmful.
- **[Trade-off] No in-plugin tracker integration** → Acceptable because the external workflow handles this, and the in-plugin version was never user-configurable.
