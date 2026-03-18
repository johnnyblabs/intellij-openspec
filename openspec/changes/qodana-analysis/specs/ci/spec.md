## MODIFIED Requirements

### Requirement: CI pipeline

The plugin SHALL have a Forgejo Actions pipeline that builds, runs tests, runs Plugin Verifier, and runs Qodana static analysis. Qodana SHALL run on PRs targeting main in parallel with other jobs.

#### Scenario: Build pipeline
- **WHEN** code is pushed to main or a PR targets main
- **THEN** the pipeline SHALL compile, run tests, and report results

#### Scenario: Plugin Verifier on main
- **WHEN** code is merged to main
- **THEN** the pipeline SHALL run Plugin Verifier across supported IDE versions

#### Scenario: Qodana analysis on PRs
- **WHEN** a PR targets main
- **THEN** the pipeline SHALL run Qodana JVM analysis in parallel with build and verify jobs
