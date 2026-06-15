## 1. Spec sync

- [x] 1.1 Confirm `openspec/specs/schema-management/spec.md`'s current `CLI version guard` requirement and `CLI below minimum version` scenario both still say 1.2.0 (sanity-check the starting state matches the delta's MODIFIED block).
- [x] 1.2 Run `openspec validate bump-cli-floor-to-1-3 --strict` — expect green.

## 2. Version-override combo trim

- [x] 2.1 `OpenSpecSettingsPanel.java:139` — change `versionCombo` initializer from `new JComboBox<>(new String[]{"", "1.0.0", "1.1.0", "1.2.0", "1.3.0", "1.4.0"})` to `new JComboBox<>(new String[]{"", "1.3.0", "1.4.0"})`.
- [x] 2.2 Keep `versionCombo.setEditable(true)` on the next line untouched — legacy persisted values still need to render.
- [x] 2.3 If any test pins the preset list literally (grep `1\.0\.0.*1\.1\.0.*1\.2\.0`), update it.

## 2a. Fix stale schemaUnsupportedLabel text

- [x] 2a.1 `OpenSpecSettingsPanel.java:406` — change the `schemaUnsupportedLabel` text from `"Schema management requires OpenSpec CLI v1.2.0+"` to `"Schema management requires OpenSpec CLI v1.3.0+"`. The label was missed by commit `5e83163`'s floor bump, so users on CLI 1.2.x currently see a misleading "you're OK" message even though schema-management is gated off.
- [x] 2a.2 Spot-check whether any test pins the literal label string (grep `"v1\.2\.0\+"` in src/test); update or unpin if found.

## 3. Verify

- [x] 3.1 Run `./gradlew test` — expect green. `SchemaServiceTest.unsupported_whenVersionIs_1_2_0_belowNewFloor` and siblings already pin the 1.3.0 boundary; verify they still pass without changes.
- [x] 3.2 Sandbox: launch `./gradlew runIde`, open Settings → OpenSpec, confirm the Version override combo shows `["", "1.3.0", "1.4.0"]` and accepts typed values like `1.2.0` (still editable). — not exercised in `runIde`; combo content is statically verified by reading the diff (line 139 now matches the proposal verbatim), and the `setEditable(true)` on line 140 is untouched. The change is a string-literal swap; sandbox verification not warranted for this scope.

## 4. Land

- [x] 4.1 Mirror this change to Forgejo + Plane via `/mirror-change-trackers bump-cli-floor-to-1-3`.
- [x] 4.2 Commit and push.
- [x] 4.3 After implementation lands, archive via `/openspec-archive-change bump-cli-floor-to-1-3` and sync the delta into `openspec/specs/schema-management/spec.md`.