## 1. VersionSupport — delete legacy enum entries

- [x] 1.1 Delete the `V1_0` enum entry from `VersionSupport.java`.
- [x] 1.2 Delete the `V1_1` enum entry from `VersionSupport.java`.
- [x] 1.3 Verify `VersionSupport.fromString` still routes legacy version strings (`"1.0.0"`, `"1.1.0"`) to `V1_2` via the existing fallback path. — confirmed; added explanatory comment above the V1_2 entry.
- [x] 1.4 Grep entire `src/` for `V1_0` and `V1_1` references. — only the new explanatory comment in `VersionSupport.java` remains; production-code clean.

## 2. CliVersion utility extraction

- [x] 2.1 Create new `src/main/java/com/johnnyblabs/openspec/util/CliVersion.java`. Single static method: `public static boolean atLeast(String detected, String required)`. Returns `false` for null/empty `detected`; performs semver-style comparison.
- [x] 2.2 Replace `SchemaService`'s private version-comparison logic with calls to `CliVersion.atLeast`. — `compareVersions` and `parseVersionPart` deleted; `isSchemaSupported` now calls `CliVersion.atLeast`.

## 3. SchemaService — raise floor

- [x] 3.1 Change `SchemaService.MIN_CLI_VERSION` from `"1.2.0"` to `"1.3.0"`.
- [x] 3.2 Verify `isSchemaSupported()` now returns `false` for detected CLI version `"1.2.0"` and `true` for `"1.3.0"`. — covered by new `unsupported_whenVersionIs_1_2_0_belowNewFloor` test.

## 4. Tests — update for V1_2-only baseline

- [x] 4.1 In `VersionSupportTest`: deleted V1_0/V1_1 enum-property tests; replaced with `legacyVersion_1_0_0_routesToV1_2` and `legacyVersion_1_1_0_routesToV1_2`. Updated `allVersions_returnsOnlyV1_2`.
- [x] 4.2 In `ConfigVersionValidationTest`: dropped V1_0/V1_1 parameterizations; added `legacyVersion_1_0_0_routesToV1_2_andAcceptsSpecDriven` and sibling for 1.1.0.
- [x] 4.3 New `CliVersionAtLeastTest` — 15 test cases covering exact match, above/below, null/empty, garbage, suffix stripping, short version strings, plus parameterized sweeps over supported (1.3+) and pre-1.3 versions.
- [x] 4.4 `SchemaServiceTest` — bumped all "schema-supported" test stubs from `1.2.0` → `1.3.0`; added two new boundary tests (`1.2.0` and `1.2.99` below floor). Deleted the inline `compareVersions_*` tests (logic moved to `CliVersionAtLeastTest`).

## 5. Startup floor notification

- [x] 5.1 Added `OpenSpecNotifier.cliBelowFloor(Project, String)` with title "OpenSpec CLI is older than 1.3.0" and body containing the detected version + upgrade command.
- [x] 5.2 Modified `OpenSpecProjectService.StartupDetection.execute` to call `cliBelowFloor` when `isAvailable() && !CliVersion.atLeast(version, "1.3.0")`.
- [x] 5.3 Verified the existing `cliMissing` path is unchanged.
- [x] 5.4 New `CliFloorNotificationTest` — 7 routing scenarios covering not-an-OpenSpec-project, CLI missing, CLI below floor (1.0.0, 1.2.0), CLI at floor (1.3.0), CLI above floor (1.4.1), and null version.
- [x] 5.5 Sibling test `cliAtFloor_1_3_0_firesNoNotification` confirms no notification at the boundary.

## 6. Docs — README, CHANGELOG, Marketplace

- [x] 6.1 README updated — minimum CLI 1.3.0, recommended 1.4.x for full feature parity, plus note about the startup notification.
- [x] 6.2 CHANGELOG updated with a `### ⚠️ Breaking` section under `## v0.3.0` containing the BREAKING bullet.
- [x] 6.3 `docs/marketplace-page.md` checked — does not mention any specific CLI versions. Skipped per task plan.

## 7. Spec sync at archive time

- [ ] 7.1 Sync the `validation` delta into `openspec/specs/validation/spec.md` — the MODIFIED "Config validation" requirement gains language about V1_2 baseline + the 3 new legacy-routing scenarios.
- [ ] 7.2 Sync the `plugin-core` delta into `openspec/specs/plugin-core/spec.md` — the ADDED "CLI version floor notification" requirement with 4 scenarios.

## 8. Verification

- [x] 8.1 `./gradlew test` — all 820+ tests pass.
- [x] 8.2 `openspec validate bump-cli-floor-to-1-3 --strict` — change validates cleanly.
- [x] 8.3 Grep `V1_0` and `V1_1` across the whole repo — only hit is the explanatory comment in `VersionSupport.java` itself.
- [ ] 8.4 Manual spot-check deferred to release-prep: in IDE sandbox, install CLI 1.2.0, open an OpenSpec project, confirm the floor notification appears with the correct text and only fires once. Then upgrade CLI to 1.3.0, restart, confirm notification no longer fires.

## 9. Archive-time tracker closure

- [ ] 9.1 Close the linked tracker issue with archival comment.
- [ ] 9.2 Move the linked tracker item to Done.
