## ADDED Requirements

### Requirement: Concurrency cancellation for in-flight runs

The CI pipeline SHALL declare a top-level `concurrency:` block in every workflow file to bound runner waste from rapid successive pushes. The block's policy SHALL differ between build/verify workflows (cancel-in-progress) and release workflows (serialize-without-cancel) to reflect their different operational invariants.

#### Scenario: Build workflow cancels in-flight run on new push to same ref
- **WHEN** a commit is pushed to a branch (or a pull request's source branch) that already has a build run in progress
- **THEN** the in-flight build run for that ref SHALL be cancelled, and the new run SHALL start immediately

#### Scenario: Build workflow concurrency is scoped per workflow + ref
- **WHEN** two different workflow files are both triggered by pushes to the same ref (e.g., a hypothetical lint workflow and the build workflow)
- **THEN** they SHALL NOT cancel each other — the concurrency group key SHALL include both the workflow name and the ref

#### Scenario: Release workflow serializes, does not cancel
- **WHEN** a `v*` tag is pushed while a previous release workflow run is still in progress
- **THEN** the new release run SHALL queue behind the in-flight run, NOT cancel it — concurrent `publishPlugin` invocations against the JetBrains Marketplace API are unsafe

#### Scenario: Release concurrency group is workflow-scoped, not ref-scoped
- **WHEN** the release workflow's concurrency group is declared
- **THEN** the group key SHALL be derived from the workflow identity (not the ref or tag name) so all release runs share a single serialization queue regardless of which tag they correspond to

#### Scenario: Forgejo workflow honors the same concurrency contract
- **WHEN** the `.forgejo/workflows/build.yaml` workflow is triggered
- **THEN** it SHALL declare the same concurrency block as the GitHub `build.yml` workflow (group keyed by workflow + ref, cancel-in-progress true) so Forgejo Actions exhibits identical cancellation behavior
