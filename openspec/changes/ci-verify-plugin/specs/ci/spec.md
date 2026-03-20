## MODIFIED Requirements

### Requirement: CI pipeline

The plugin SHALL have a Forgejo Actions pipeline that builds, runs tests, and runs Plugin Verifier on every push and PR targeting main. The build and verify jobs SHALL run in parallel.

#### Scenario: Build pipeline
- **WHEN** code is pushed to any branch or a PR targets main
- **THEN** the pipeline SHALL compile, run tests, and report results

#### Scenario: Plugin Verifier on every push
- **WHEN** code is pushed to any branch or a PR targets main
- **THEN** the pipeline SHALL run Plugin Verifier across recommended IDE versions in parallel with the build job

#### Scenario: Verify blocks merge on failure
- **WHEN** Plugin Verifier detects binary compatibility issues
- **THEN** the verify job SHALL fail, preventing merge of the PR
