## ADDED Requirements

### Requirement: Qodana configuration

The project SHALL include a `qodana.yaml` configuration file specifying the `qodana-jvm` linter, the source directory scope, and a baseline file for tracking existing issues.

#### Scenario: Configuration file present
- **WHEN** Qodana runs analysis on the project
- **THEN** it SHALL use the `qodana-jvm` linter with analysis scoped to `src/main/java`

#### Scenario: Baseline excludes existing issues
- **WHEN** Qodana analysis runs on a PR
- **THEN** only issues not present in the committed baseline SHALL be reported as failures

### Requirement: Qodana CI analysis

The CI pipeline SHALL run Qodana analysis on PRs targeting main. The analysis SHALL run in parallel with other CI jobs. New issues (not in baseline) SHALL fail the job.

#### Scenario: Analysis runs on PRs
- **WHEN** a PR targets main
- **THEN** the Qodana job SHALL run analysis and report findings as a CI status check

#### Scenario: New issues fail the build
- **WHEN** Qodana detects issues not present in the baseline
- **THEN** the Qodana job SHALL fail, blocking the PR

#### Scenario: Clean PR passes
- **WHEN** Qodana detects no new issues beyond the baseline
- **THEN** the Qodana job SHALL pass
