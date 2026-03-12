## Why

The plugin's tree view and panels use hardcoded `new Color()` constants that render poorly (or invisibly) in dark themes. Multiple tree node types share the same generic `spec.svg` icon, making it hard to distinguish artifacts, delta specs, and missing items at a glance. The Getting Started panel uses a generic IntelliJ `Information` icon instead of OpenSpec branding. Custom SVGs lack `_dark.svg` variants, so they don't adapt to the IDE theme. These issues make the plugin feel unpolished and hurt usability — fixing them before v0.1.0 is essential for a credible first release.

## What Changes

- Replace all `new Color()` constants in `SpecTreeCellRenderer` and `OpenSpecToolWindowPanel` with `JBColor` pairs (light/dark)
- Replace `new Color()` in `OpenSpecSettingsPanel` CLI status and API test labels with `JBColor`
- Add new SVG icons for distinct tree node types: `artifact.svg`, `delta-spec.svg`, `missing-artifact.svg`
- Add `_dark.svg` variants for all custom icons (`openspec_dark.svg`, `spec_dark.svg`, `change_dark.svg`, `requirement_dark.svg`, `archive_dark.svg`, `artifact_dark.svg`, `delta-spec_dark.svg`, `missing-artifact_dark.svg`)
- Update `SpecTreeCellRenderer.getIconForType()` to use distinct icons per node type instead of sharing `SPEC_ICON`
- Replace `AllIcons.General.Information` in `GettingStartedPanel` with the OpenSpec branded icon
- Ensure tool window icon renders correctly at 13x13 (IntelliJ tool window strip size)

## Capabilities

### New Capabilities
- `icon-theme-support`: Dark-theme-aware icon system with `_dark.svg` variants and `JBColor` usage across all UI components

### Modified Capabilities
- `tool-window`: Tree cell renderer uses `JBColor` and distinct icons per node type; Getting Started panel uses OpenSpec branding
- `settings-panel-sections`: CLI status and API test result labels use `JBColor` for theme compatibility

## Impact

- **Files modified**: `SpecTreeCellRenderer.java`, `OpenSpecToolWindowPanel.java`, `OpenSpecSettingsPanel.java`, `GettingStartedPanel.java`
- **Assets added**: 3 new SVG icons + 8 dark variants under `src/main/resources/icons/`
- **No API changes**: All changes are visual/cosmetic
- **No dependency changes**: `JBColor` is already part of the IntelliJ Platform SDK
