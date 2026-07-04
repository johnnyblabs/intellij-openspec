# Delta — ui-smoke-journeys

## ADDED Requirements

### Requirement: Rendered-UI smoke journeys exist

The project SHALL maintain a small suite (2–3) of automated UI smoke journeys that drive a real sandbox IDE with the plugin installed, asserting presence and wiring of rendered surfaces: the tool window opens with the seeded tree and workflow chips, the Update action's cleanup notification appears in a legacy-seeded project, and the Settings schema section renders. Journeys SHALL assert component presence and wiring, not textual prose or pixels.

#### Scenario: Open-and-render journey
- **WHEN** the smoke suite opens a seeded demo project
- **THEN** it SHALL assert the OpenSpec tool window opens and the Browse tree shows the seeded spec and change

#### Scenario: Update cleanup journey
- **WHEN** the smoke suite triggers the Update action in a project seeded with legacy files
- **THEN** it SHALL assert the review notification appears with its action

#### Scenario: Settings journey
- **WHEN** the smoke suite opens the plugin's Settings page
- **THEN** it SHALL assert the Schemas section renders with the built-in schema row

### Requirement: Smoke journeys never gate ordinary PRs

The UI smoke job SHALL run on manual dispatch and as part of the release pipeline, and SHALL NOT be a required check on ordinary pull requests. A release tag SHALL require green smoke journeys before publishing.

#### Scenario: PR unaffected
- **WHEN** an ordinary pull request runs CI
- **THEN** the UI smoke job SHALL NOT run and SHALL NOT block the merge

#### Scenario: Release gated
- **WHEN** a release tag is pushed
- **THEN** the pipeline SHALL require the smoke journeys to pass for that commit before publishing

### Requirement: Failure diagnosability

Each failed journey SHALL upload diagnosable artifacts — the sandbox IDE log and a full-screen screenshot — and a journey exceeding its timeout SHALL fail with artifacts rather than hang the job.

#### Scenario: Journey failure artifacts
- **WHEN** a smoke journey fails or times out
- **THEN** the job SHALL attach the IDE log and a screenshot for that journey

### Requirement: Shared seeding with the manual test-drive

The smoke journeys' demo-project seeding SHALL share a single source with the manual test-drive tooling, so the automated and human walkthrough environments cannot drift apart.

#### Scenario: One seeding source
- **WHEN** the demo-project seeding recipe changes
- **THEN** both the manual test-drive and the smoke journeys SHALL pick up the change from the same source
