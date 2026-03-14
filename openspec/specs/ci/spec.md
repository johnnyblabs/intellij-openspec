# CI Pipeline

## Purpose
Continuous integration: build, test, and Plugin Verifier checks via Forgejo Actions.

## Requirements

### Requirement: CI pipeline

The plugin SHALL have a Forgejo Actions pipeline that builds, runs tests, and runs Plugin Verifier on push to main.

#### Scenario: Build pipeline
- **WHEN** code is pushed to main or a PR targets main
- **THEN** the pipeline SHALL compile, run tests, and report results

#### Scenario: Plugin Verifier on main
- **WHEN** code is merged to main
- **THEN** the pipeline SHALL run Plugin Verifier across supported IDE versions

### Requirement: Runner configuration

The pipeline SHALL use the `java-21` runner label mapped to a Docker image with JDK 21, Gradle 9.0.0, Node 20, and git pre-installed. Gradle daemon SHALL be disabled for ephemeral containers.

#### Scenario: Zero-download builds
- **WHEN** the CI build runs
- **THEN** no JDK, Gradle, or Node downloads SHALL occur — all tools are baked into the runner image

### Requirement: Test coverage

The plugin SHALL maintain unit tests for core services (BuiltInValidator, CliOutputParser, SpecParsingService, ArtifactOrchestrationService, DirectApiService) and integration tests for action lifecycle.

#### Scenario: Test suite
- **WHEN** tests run
- **THEN** all core services SHALL have test coverage and no regressions SHALL be introduced
