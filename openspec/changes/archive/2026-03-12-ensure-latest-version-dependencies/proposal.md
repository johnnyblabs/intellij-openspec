## Why

I want the plugin to remain compliant and compatible by using the latest stable dependency versions that fit our platform constraints.

## What Changes

Update library, build, and test dependencies to the latest stable compatible versions, including all required code and test migrations.

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

None.

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

- ci-pipeline
- plugin-core
- service-test-coverage

## Impact

<!-- Affected code, APIs, dependencies, systems -->

- `build.gradle.kts` dependency versions and compatibility constraints
- `settings.gradle.kts` IntelliJ Platform Gradle settings plugin version
- Gradle wrapper decision path (`gradle/wrapper/gradle-wrapper.properties`) when required
- CI verification flow for dependency-update changes
- Test and production code migrations required by upgraded APIs/behavior

