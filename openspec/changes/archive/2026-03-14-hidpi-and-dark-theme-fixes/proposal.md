## Why

Several UI components use hardcoded pixel widths in HTML body styles (e.g., `style='width:400px'`) without `JBUI.scale()`, causing text to render too narrow on HiDPI/Retina displays. The tree renderer's dark theme status colors also need refinement — the green and blue values could be brighter for better readability on dark backgrounds.

## What Changes

- Wrap hardcoded HTML width values with `JBUI.scale()` in `SetupWizardDialog`, `ProposeChangeDialog`, and `OpenSpecSettingsPanel`
- Adjust dark theme colors in `SpecTreeCellRenderer` for better contrast on dark backgrounds

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

_None — implementation-only fixes, no spec-level behavior changes_

## Impact

- `SetupWizardDialog.java` — 4 hardcoded widths
- `ProposeChangeDialog.java` — 1 hardcoded width
- `OpenSpecSettingsPanel.java` — 1 hardcoded width
- `SpecTreeCellRenderer.java` — dark theme color adjustments
