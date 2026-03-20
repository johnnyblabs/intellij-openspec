## ADDED Requirements

### Requirement: GitHub Actions builds and tests on every PR
The GitHub Actions workflow SHALL run `gradle build` (which includes tests) on every push and pull request targeting main.

#### Scenario: PR triggers build
- **WHEN** a contributor opens a pull request targeting main
- **THEN** the build job runs, compiles the plugin, and executes all tests

#### Scenario: Build failure blocks merge
- **WHEN** the build job fails
- **THEN** the PR status check shows as failed

### Requirement: GitHub Actions runs Plugin Verifier
The GitHub Actions workflow SHALL run `gradle verifyPlugin` in parallel with the build job to validate binary compatibility with target IDE versions.

#### Scenario: Verifier runs in parallel with build
- **WHEN** a push or PR triggers the workflow
- **THEN** the build and verify jobs start simultaneously without dependency on each other

### Requirement: GitHub Actions signs plugin on main
The GitHub Actions workflow SHALL sign the plugin ZIP on pushes to main using repository secrets for the signing key, certificate, and password.

#### Scenario: Main branch push signs artifact
- **WHEN** code is pushed to the main branch
- **THEN** the workflow signs the plugin ZIP and uploads the signed artifact

#### Scenario: PR builds do not sign
- **WHEN** a pull request triggers the workflow
- **THEN** the signing step is skipped

### Requirement: Gradle dependencies are cached
The GitHub Actions workflow SHALL cache `~/.gradle/caches` and `~/.gradle/wrapper` between runs to avoid re-downloading dependencies.

#### Scenario: Cache hit speeds up build
- **WHEN** a workflow runs and a matching cache exists from a previous run
- **THEN** the Gradle dependencies are restored from cache instead of re-downloaded
