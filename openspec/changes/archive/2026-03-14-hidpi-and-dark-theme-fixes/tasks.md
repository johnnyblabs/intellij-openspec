## 1. Fix HiDPI hardcoded widths

- [x] 1.1 Wrap `style='width:400px'` with `JBUI.scale(400)` in `SetupWizardDialog` (4 occurrences)
- [x] 1.2 Wrap `style='width:380px'` with `JBUI.scale(380)` in `ProposeChangeDialog` (1 occurrence)
- [x] 1.3 Wrap `style='width:400px'` with `JBUI.scale(400)` in `OpenSpecSettingsPanel` (1 occurrence)

## 2. Adjust dark theme colors in SpecTreeCellRenderer

- [x] 2.1 Brighten dark theme green, blue, and gray status colors for better contrast

## 3. Verify

- [x] 3.1 Build plugin and confirm zero compilation errors
