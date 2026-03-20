## MODIFIED Requirements

### Requirement: CI pipeline

The plugin SHALL have a Forgejo Actions pipeline that builds, runs tests, runs Plugin Verifier, and signs the plugin ZIP on main. The signed archive SHALL be uploaded as the release artifact.

#### Scenario: Build pipeline
- **WHEN** code is pushed to main or a PR targets main
- **THEN** the pipeline SHALL compile, run tests, and report results

#### Scenario: Plugin Verifier on main
- **WHEN** code is merged to main
- **THEN** the pipeline SHALL run Plugin Verifier across supported IDE versions

#### Scenario: Plugin signing on main
- **WHEN** the build job succeeds on the main branch
- **THEN** the pipeline SHALL sign the plugin ZIP and verify the signature before uploading the artifact
