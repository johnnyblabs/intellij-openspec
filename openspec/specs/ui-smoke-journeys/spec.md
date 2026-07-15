# UI Smoke Journeys

## Purpose
Automated rendered-UI smoke coverage: scope, journeys, execution policy, and flakiness handling for the Starter/Driver-based suite.

## Requirements

### Requirement: Rendered-UI smoke journeys exist

The project SHALL maintain a small suite (currently six) of automated UI smoke journeys that drive a real sandbox IDE with the plugin installed, asserting presence and wiring of rendered surfaces. Journeys SHALL assert component presence and wiring, not textual prose or pixels, and SHALL NOT mutate durable user state (dialog journeys exit via cancel; no archive is performed). A journey that must exercise state-writing actions SHALL isolate that state to journey-scoped temporary locations — e.g. an isolated OpenSpec data directory injected via the IDE process environment — so nothing outlives the journey or touches the user's real data.

#### Scenario: Open-and-render journey
- **WHEN** the smoke suite opens a seeded demo project
- **THEN** it SHALL assert the OpenSpec tool window opens and the Browse tree shows the seeded spec and change

#### Scenario: Update cleanup journey
- **WHEN** the smoke suite triggers the Update action in a project seeded with legacy files
- **THEN** it SHALL assert the review notification appears with its action

#### Scenario: Settings journey
- **WHEN** the smoke suite opens the plugin's Settings page
- **THEN** it SHALL assert the Schemas section renders with the built-in schema row

#### Scenario: Editor validator-parity journey
- **WHEN** the smoke suite opens the seeded lowercase-header spec and a seeded keyword-in-header-only spec in the editor
- **THEN** it SHALL assert via the highlighting daemon that the lowercase header draws no requirement-recognition complaint and that the keyword-in-header spec draws the targeted move-the-keyword diagnostic

#### Scenario: Archive guard journey
- **WHEN** the smoke suite invokes Archive on the seeded incomplete change
- **THEN** it SHALL assert the incomplete-change confirmation surface appears, cancel it, and assert the change directory was not moved

#### Scenario: Store-health journey (CLI 1.6 semantics)
- **WHEN** the smoke suite runs against a host CLI at 1.6+ with an isolated OpenSpec data directory, a pre-registered fresh/config-only store, and prepared store roots (a pointer-declaring root, a never-a-store root, and a fresh root with store identity)
- **THEN** it SHALL assert the fresh store's row renders with no unhealthy or metadata error marker, that registering the pointer-declaring and never-a-store roots surfaces the CLI's refusal/confirmation message and fix in the write-failure dialog (dismissed without confirming), and that registering the identified fresh root succeeds and lists a new row with no error marker; on a host CLI below 1.6 the journey SHALL be skipped, not failed

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
