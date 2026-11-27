## ADDED Requirements

### Requirement: CI jobs

On every pull request targeting main, the pipeline SHALL run a **build** job and a **cli-fixtures** job in parallel, and a **coverage** job after both. On every push to main it SHALL also run the **verify** job (Plugin Verifier), which a pull request gets only on manual dispatch. Each job SHALL report on every pull request, so it can be a required check.

#### Scenario: Pull request
- **WHEN** a pull request targets main
- **THEN** build and cli-fixtures SHALL run in parallel, and coverage SHALL run once both finish

#### Scenario: Push to main
- **WHEN** code is pushed to main
- **THEN** build, cli-fixtures and coverage SHALL run as on a pull request, and verify SHALL run in parallel with them

#### Scenario: Bypass-only pull request
- **WHEN** a pull request touches only bypass paths (OpenSpec content, agent configuration, hooks)
- **THEN** every job SHALL report success without building

### Requirement: CLI-fixture test suite

Plain-JUnit test classes that replay captured CLI releases SHALL form their own suite, `cliFixtureTest`, run with the same classpath, JVM arguments and coverage agent as `test`, which SHALL exclude them. `check` SHALL run both suites. A test SHALL fail when such a class is not in the suite.

#### Scenario: Local build runs both suites
- **WHEN** a developer runs `./gradlew build`
- **THEN** both `test` and `cliFixtureTest` SHALL run, and the coverage floor SHALL read both suites' data

#### Scenario: An untagged fixture class fails the guard
- **WHEN** a plain-JUnit test class reads captured CLI releases without the CLI-fixture suite's tag
- **THEN** the guard test in `test` SHALL fail and name the class

#### Scenario: The pre-push gate runs both suites
- **WHEN** pushed commits touch `src/`
- **THEN** the pre-push hook SHALL run `test` and `cliFixtureTest`

## MODIFIED Requirements

### Requirement: Build job

The build job SHALL compile the project, run the `test` suite (every test except the CLI-fixture suite and the real-CLI matrix), and upload its test results and coverage data as build artifacts. On the main branch, the build job SHALL additionally sign the plugin and upload the signed artifact.

#### Scenario: Build and test
- **WHEN** the build job runs
- **THEN** it SHALL execute `gradle build` without `cliFixtureTest` and without the coverage floor, which the coverage job enforces, and upload its test results and coverage data regardless of pass or fail

#### Scenario: Signing on main
- **WHEN** the build job runs on the main branch
- **THEN** it SHALL execute `gradle signPlugin` and `gradle verifyPluginSignature` using signing credentials from repository secrets

#### Scenario: Signing skipped on PR
- **WHEN** the build job runs on a pull request branch
- **THEN** the signing and signature verification steps SHALL be skipped

### Requirement: Test coverage regression floor

The build SHALL enforce a JaCoCo coverage regression floor, wired into `check`, covering the instruction, line and branch counters, measured over the `test` and CLI-fixture suites together. In CI the coverage job enforces it. Each minimum SHALL be set just below the recorded baseline, and the baseline (measured value and date) SHALL be recorded with the floor. The floor SHALL be ratcheted upward as coverage improves and SHALL NOT be lowered without a recorded justification.

#### Scenario: Regression below the floor fails the build
- **WHEN** a change reduces measured coverage for a governed counter below its configured minimum
- **THEN** `jacocoTestCoverageVerification` SHALL fail, and because it is wired into `check`, `./gradlew build` SHALL fail, as SHALL CI's coverage job

#### Scenario: Branch coverage is governed
- **WHEN** the coverage floor is evaluated
- **THEN** it SHALL include a branch-coverage minimum in addition to instruction and line

#### Scenario: The floor is not silently lowered
- **WHEN** a change lowers a coverage minimum
- **THEN** the change SHALL record a justification; lowering a floor to make an unrelated change pass is not permitted

## REMOVED Requirements

### Requirement: Dual-job pipeline

**Reason**: It described two jobs, both started by every pull request. The verify job runs only on pushes to main and manual dispatch, and the pipeline now has build, cli-fixtures and coverage jobs as well.

**Migration**: Replaced by "CI jobs", which lists the jobs and when each runs.
