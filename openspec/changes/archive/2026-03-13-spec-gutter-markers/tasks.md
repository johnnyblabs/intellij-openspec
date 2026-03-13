## 1. Line Marker Provider

- [x] 1.1 Create `SpecRefLineMarkerProvider` implementing `LineMarkerProvider` that detects `// @spec <domain>:<requirement>` in PsiComment elements and returns a `LineMarkerInfo` with the requirement icon
- [x] 1.2 Add tooltip showing "Spec: <domain> — <requirement>" on hover
- [x] 1.3 Add click handler that navigates to `openspec/specs/<domain>/spec.md`
- [x] 1.4 Guard against non-OpenSpec projects (return null early)

## 2. Registration

- [x] 2.1 Register the `LineMarkerProvider` in `plugin.xml` with `language="JAVA"`

## 3. Verification

- [x] 3.1 Build and verify gutter icon appears on `@spec` comment lines in Java files
- [x] 3.2 Verify tooltip shows domain and requirement name
- [x] 3.3 Verify clicking navigates to the spec file
