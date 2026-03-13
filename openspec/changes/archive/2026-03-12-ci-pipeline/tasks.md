## 1. Update Build Workflow

- [x] 1.1 Fix JDK version from 17 to 21 in `.forgejo/workflows/build.yaml`
- [x] 1.2 Add Gradle caching comment (already handled by `setup-gradle` action)
- [x] 1.3 Add test results upload step using JUnit XML from `build/test-results/test/`

## 2. Add Plugin Verification Job

- [x] 2.1 Add `verify` job that runs `./gradlew runPluginVerifier` on main branch only
- [x] 2.2 Add `needs: build` dependency so verify runs after successful build

## 3. Verification

- [x] 3.1 Validate YAML syntax of the workflow file
- [x] 3.2 Build compiles locally with no errors
