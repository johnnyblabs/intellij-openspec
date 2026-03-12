## 1. JBColor Migration

- [x] 1.1 Replace all `new Color()` constants in `SpecTreeCellRenderer` with `JBColor` light/dark pairs
- [x] 1.2 Replace `Color.GRAY` usages in `SpecTreeCellRenderer` HINT case with `JBColor`
- [x] 1.3 Replace `new Color()` in `OpenSpecToolWindowPanel` status labels with `JBColor`
- [x] 1.4 Replace `new Color()` in `OpenSpecSettingsPanel` CLI status and API test result labels with `JBColor`

## 2. New Icon Assets

- [x] 2.1 Create `artifact.svg` icon (document with checkmark motif)
- [x] 2.2 Create `delta-spec.svg` icon (spec icon with delta overlay)
- [x] 2.3 Create `missing-artifact.svg` icon (dashed/hollow document)

## 3. Dark Theme Icon Variants

- [x] 3.1 Create `openspec_dark.svg`
- [x] 3.2 Create `spec_dark.svg`
- [x] 3.3 Create `change_dark.svg`
- [x] 3.4 Create `requirement_dark.svg`
- [x] 3.5 Create `archive_dark.svg`
- [x] 3.6 Create `artifact_dark.svg`
- [x] 3.7 Create `delta-spec_dark.svg`
- [x] 3.8 Create `missing-artifact_dark.svg`

## 4. Tree Cell Renderer Icon Mapping

- [x] 4.1 Load new icon constants (`ARTIFACT_ICON`, `DELTA_SPEC_ICON`, `MISSING_ARTIFACT_ICON`) in `SpecTreeCellRenderer`
- [x] 4.2 Update `getIconForType()` to return distinct icons for ARTIFACT/ARTIFACT_DONE/ARTIFACT_READY/ARTIFACT_BLOCKED, DELTA_SPEC, and MISSING_ARTIFACT

## 5. Getting Started Panel Branding

- [x] 5.1 Replace `AllIcons.General.Information` with OpenSpec icon in `GettingStartedPanel`

## 6. Tool Window Icon Verification

- [x] 6.1 Verify `openspec.svg` renders legibly at 13x13 in tool window strip; adjust viewBox if needed

## 7. Testing

- [x] 7.1 Verify tree renders correctly in IntelliJ Light theme
- [x] 7.2 Verify tree renders correctly in Darcula dark theme
- [x] 7.3 Verify settings panel CLI status and API test labels in both themes
- [x] 7.4 Verify Getting Started panel displays OpenSpec icon
