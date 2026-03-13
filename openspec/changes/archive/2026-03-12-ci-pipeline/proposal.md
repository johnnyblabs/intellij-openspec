## Why

The existing `.forgejo/workflows/build.yaml` uses JDK 17 but the project targets Java 21. It also lacks `runPluginVerifier` for IDE compatibility checking and has no test result reporting. Before shipping to the Marketplace, the CI pipeline needs to match the actual build requirements and verify the plugin works across IDE versions.

## What Changes

- Fix JDK version from 17 to 21 in the Forgejo Actions workflow
- Add `runPluginVerifier` step to check compatibility across IntelliJ versions
- Add test result reporting via JUnit XML upload
- Add Gradle caching for faster builds
- Add a verification job that runs `runPluginVerifier` separately (can be slower)

## Capabilities

### New Capabilities

_None — this is infrastructure/configuration, not behavioral._

### Modified Capabilities

_None — no spec-level behavior changes._

## Impact

- `.forgejo/workflows/build.yaml` — updated with correct JDK, caching, test reporting, and plugin verification
- No code changes
- Build times should decrease with Gradle caching
- Plugin verification will catch API compatibility issues before Marketplace submission
