# CI Pipeline

## Purpose
Forgejo Actions CI/CD pipeline for building, testing, and verifying the plugin.

## Requirements

### Requirement: Continuous Integration Pipeline

The project SHALL have a Forgejo Actions CI pipeline that builds, tests, and verifies the plugin on every push and pull request to the main branch.

#### Scenario: Build and test on push
- **WHEN** code is pushed to main or a pull request targets main
- **THEN** the CI pipeline SHALL compile the project with JDK 21
- **AND** run all tests
- **AND** produce the plugin distribution artifact

#### Scenario: Plugin verification on main
- **WHEN** code is pushed to main
- **THEN** the CI pipeline SHALL run `runPluginVerifier` to check compatibility across recommended IDE versions

#### Scenario: Build uses correct JDK version
- **WHEN** the CI pipeline executes
- **THEN** it SHALL use JDK 21, matching the project's `build.gradle.kts` toolchain configuration
