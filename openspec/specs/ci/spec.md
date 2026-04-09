# CI Pipeline

## Purpose
Continuous integration for build, test, plugin verification, signing, and release publishing — ensuring every change is compilable, tested, and compatible across supported IDE versions, and that releases are signed and published exclusively through CI.

## Requirements

### Requirement: Dual-job pipeline

The CI pipeline SHALL run two parallel jobs on every push to main and on every pull request targeting main: a **build** job (compile, test, and optionally sign) and a **verify** job (Plugin Verifier compatibility checks). The jobs SHALL have no dependency on each other and SHALL run concurrently.

#### Scenario: PR triggers both jobs
- **WHEN** a pull request targets main
- **THEN** the pipeline SHALL start both the build and verify jobs in parallel

#### Scenario: Push to main triggers both jobs
- **WHEN** code is pushed to main
- **THEN** the pipeline SHALL start both the build and verify jobs in parallel

### Requirement: Build job

The build job SHALL compile the project, run the full test suite, and upload test results as a build artifact. On the main branch, the build job SHALL additionally sign the plugin and upload the signed artifact.

#### Scenario: Build and test
- **WHEN** the build job runs
- **THEN** it SHALL execute `gradle build`, run all tests, and upload test results regardless of pass or fail

#### Scenario: Signing on main
- **WHEN** the build job runs on the main branch
- **THEN** it SHALL execute `gradle signPlugin` and `gradle verifyPluginSignature` using signing credentials from repository secrets

#### Scenario: Signing skipped on PR
- **WHEN** the build job runs on a pull request branch
- **THEN** the signing and signature verification steps SHALL be skipped

### Requirement: Plugin Verifier compatibility

The verify job SHALL run the IntelliJ Plugin Verifier against all IDE versions in the declared compatibility range, reporting binary compatibility, deprecated API usage, and experimental API usage.

#### Scenario: All declared versions checked
- **WHEN** the verify job runs
- **THEN** it SHALL verify the plugin against every IDE version from the minimum declared version (2024.2) through the latest available release

#### Scenario: Compatible result
- **WHEN** the Plugin Verifier reports "Compatible" for all checked versions
- **THEN** the verify job SHALL pass

#### Scenario: Incompatible result
- **WHEN** the Plugin Verifier reports binary incompatibilities for any checked version
- **THEN** the verify job SHALL fail, blocking the PR from merging

### Requirement: Dependency caching

The pipeline SHALL cache Gradle dependencies between runs using a cache key derived from the build file and wrapper properties, reducing build times for cache-hit runs.

#### Scenario: Cache hit
- **WHEN** the build files have not changed since the last run
- **THEN** the pipeline SHALL restore cached Gradle dependencies and skip downloading them

#### Scenario: Cache miss
- **WHEN** the build files have changed
- **THEN** the pipeline SHALL download dependencies and store them in the cache for subsequent runs

### Requirement: Runner environment

The pipeline SHALL run on a `java-21` runner with JDK 21, Gradle 9, and git pre-installed. The Gradle daemon SHALL be disabled for ephemeral container environments.

#### Scenario: No tool downloads
- **WHEN** the CI build runs
- **THEN** no JDK, Gradle, or Node downloads SHALL occur — all tools SHALL be pre-installed in the runner image

#### Scenario: Gradle daemon disabled
- **WHEN** the pipeline executes Gradle commands
- **THEN** the Gradle daemon SHALL be disabled via `-Dorg.gradle.daemon=false`

### Requirement: Release pipeline

The release pipeline SHALL be the exclusive mechanism for signing and publishing the plugin to JetBrains Marketplace. It SHALL trigger automatically when a `v*` tag is pushed, and SHALL build, sign, publish, and create a GitHub Release in a single workflow run. The build step SHALL fail if `CHANGELOG.md` does not contain an entry matching the tagged version, preventing publication with stale or missing release notes.

#### Scenario: Tag triggers release
- **WHEN** a tag matching `v*` is pushed to the repository
- **THEN** the release pipeline SHALL start automatically

#### Scenario: Build and test before publish
- **WHEN** the release pipeline runs
- **THEN** it SHALL execute `gradle build` (compile and test) before attempting to sign or publish

#### Scenario: Missing changelog entry blocks release
- **WHEN** the release pipeline runs and `CHANGELOG.md` has no entry matching the tagged version
- **THEN** the build SHALL fail before signing or publishing, preventing stale release notes on the Marketplace

#### Scenario: Plugin signing
- **WHEN** the release pipeline runs
- **THEN** it SHALL sign the plugin using `PLUGIN_SIGNING_KEY`, `PLUGIN_SIGNING_CERTIFICATE`, and `PLUGIN_SIGNING_KEY_PASSWORD` from repository secrets

#### Scenario: Marketplace publishing
- **WHEN** the plugin is signed successfully
- **THEN** the pipeline SHALL publish the signed plugin to JetBrains Marketplace using the `JETBRAINS_MARKETPLACE_TOKEN` secret

#### Scenario: GitHub Release creation
- **WHEN** the plugin is published to the Marketplace
- **THEN** the pipeline SHALL create a GitHub Release for the tag, attaching the signed plugin zip and including release notes

### Requirement: Local publish prohibition

The `signPlugin` and `publishPlugin` Gradle tasks SHALL only be executed in CI. Running these tasks locally risks uploading unsigned artifacts that block the CI release pipeline, since the Marketplace rejects duplicate version uploads.

#### Scenario: Local build permitted
- **WHEN** a developer runs `gradle build` or `gradle test` locally
- **THEN** the build SHALL succeed without requiring signing credentials

#### Scenario: Local signPlugin prohibited
- **WHEN** a developer attempts to run `gradle signPlugin` locally
- **THEN** the task SHALL be skipped due to missing signing environment variables, and the developer SHALL NOT work around this by providing local credentials

#### Scenario: Local publishPlugin prohibited
- **WHEN** a developer or automated tool runs `gradle publishPlugin` locally
- **THEN** the unsigned artifact SHALL conflict with the CI-signed release, blocking the official publish pipeline
