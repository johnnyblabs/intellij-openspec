## 1. Baseline and Target Version Planning

- [x] 1.1 Inventory all direct dependency and plugin versions in `build.gradle.kts` and `settings.gradle.kts`.
- [x] 1.2 Decide target versions using the "latest stable compatible" policy for Java 21 and IntelliJ IDEA 2024.2+.
- [x] 1.3 Record target versions and compatibility notes in the change artifacts before editing build files.

## 2. Build and Tooling Upgrades

- [x] 2.1 Upgrade build and IntelliJ tooling dependencies (including IntelliJ Platform Gradle plugin coordinates as needed).
- [x] 2.2 Evaluate whether a Gradle wrapper bump is required and apply it only if compatibility gates pass.
- [x] 2.3 Run build plus plugin verification checks and fix tooling-related migration issues before continuing.

## 3. Test Stack Upgrades and Migration

- [x] 3.1 Upgrade test framework dependencies (JUnit BOM/Jupiter/Vintage and Mockito) to latest compatible versions.
- [x] 3.2 Migrate test code/configuration for any API or runtime behavior changes introduced by test dependency updates.
- [x] 3.3 Confirm the full test suite executes successfully on Java 21 after test stack upgrades.

## 4. Runtime Dependency Upgrades and Code Migration

- [x] 4.1 Upgrade runtime libraries (for example Gson) to latest compatible stable versions.
- [x] 4.2 Migrate production code paths affected by runtime dependency API or behavior changes.
- [x] 4.3 Resolve transitive version conflicts with explicit pinning where required.

## 5. CI Gates, Regression Coverage, and Final Validation

- [x] 5.1 Update CI pipeline configuration so dependency-update changes run build, test, and plugin verification gates.
- [x] 5.2 Add or update regression tests covering migrated code paths changed by dependency upgrades.
- [x] 5.3 Validate phased upgrade flow in CI (tooling first, then test stack, then runtime libraries).
- [x] 5.4 Run final verification (`build`, `test`, and plugin verification) and confirm no existing behavior regressions.
- [x] 5.5 Document final dependency versions, migration decisions, and any deferred follow-up items in change artifacts.
