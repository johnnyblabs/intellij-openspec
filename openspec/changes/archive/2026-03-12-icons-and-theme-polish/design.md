## Context

The plugin ships 5 custom SVG icons (`openspec.svg`, `spec.svg`, `change.svg`, `requirement.svg`, `archive.svg`) loaded via `IconLoader`. The tree cell renderer (`SpecTreeCellRenderer`) uses hardcoded `new Color()` for status styling and maps multiple node types (ARTIFACT, ARTIFACT_DONE, ARTIFACT_READY, ARTIFACT_BLOCKED, MISSING_ARTIFACT, DELTA_SPEC) to the same `SPEC_ICON`. The settings panel and tool window panel also use raw `Color` constants for CLI status labels. The Getting Started panel uses `AllIcons.General.Information` instead of OpenSpec branding.

WorkflowActionPanel already uses `JBColor` correctly — it's the model to follow.

## Goals / Non-Goals

**Goals:**
- All custom colors use `JBColor` with light/dark pairs for correct rendering in both themes
- Each tree node type has a visually distinct icon
- Custom SVGs have `_dark.svg` variants so IntelliJ auto-selects the right variant
- Getting Started panel uses OpenSpec branding
- Tool window icon works at 13x13

**Non-Goals:**
- Redesigning the icon set from scratch (reuse existing visual language)
- Adding icon-related settings or customization
- Changing the WorkflowActionPanel colors (already correct)
- Animated icons or high-DPI `@2x` variants (future work)

## Decisions

### 1. JBColor replacement strategy
Replace `new Color(r,g,b)` with `new JBColor(lightColor, darkColor)` everywhere outside WorkflowActionPanel. The dark variants use lighter/desaturated tones for readability on dark backgrounds.

**Alternative**: Use `JBColor.namedColor()` with theme keys. Rejected — theme key names are not stable across IntelliJ versions, and hardcoded pairs are simpler for 6 colors.

### 2. Icon file naming convention
Follow IntelliJ's convention: `name.svg` for light theme, `name_dark.svg` for dark theme. `IconLoader.getIcon("/icons/name.svg")` automatically resolves the `_dark` variant in dark themes.

### 3. New icon designs
- `artifact.svg` — document with checkmark motif, distinguishing completed/in-progress artifacts from specs
- `delta-spec.svg` — spec icon with delta (triangle) overlay, indicating a spec modification
- `missing-artifact.svg` — dashed/hollow document, indicating an artifact that doesn't exist yet

Each gets a `_dark.svg` variant with adjusted colors for dark backgrounds.

**Alternative**: Use IntelliJ built-in icons (`AllIcons.FileTypes.*`). Rejected — custom icons maintain brand consistency and node types don't map cleanly to built-in file type icons.

### 4. Getting Started panel branding
Replace `AllIcons.General.Information` with the `openspec.svg` icon (loaded via `IconLoader`). This is a simple swap — the `EmptyStateFactory.createPanel()` already accepts an `Icon` parameter.

### 5. Tool window icon sizing
IntelliJ tool window strip expects 13x13 icons. The existing `openspec.svg` will be verified/adjusted to render correctly at that size. SVG scaling handles this natively — no separate file needed unless the icon has details that disappear at 13x13.

## Risks / Trade-offs

- **Risk**: `_dark.svg` colors may not look right in all IntelliJ themes (e.g., High Contrast). **Mitigation**: Test in Darcula (default dark theme) and IntelliJ Light; High Contrast is niche and can be refined later.
- **Risk**: New SVG icons may not match existing icon style. **Mitigation**: Derive from existing `spec.svg` base shape, modify with overlays.
- **Trade-off**: Using hardcoded `JBColor` pairs over theme keys means colors won't auto-adapt to custom themes, but provides stability across IntelliJ versions.
