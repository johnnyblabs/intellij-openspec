# UI Smoke Journeys

## Purpose
Automated rendered-UI smoke coverage: scope, journeys, execution policy, and flakiness handling for the Starter/Driver-based suite.

## Requirements

### Requirement: Rendered-UI smoke journeys exist

The project SHALL maintain a small suite of automated UI smoke journeys that drive a real sandbox IDE with the plugin installed, asserting presence and wiring of rendered surfaces, not prose or pixels. Journeys SHALL NOT mutate durable user state. A journey that exercises state-writing actions SHALL isolate that state to journey-scoped temporary locations.

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
- **WHEN** the smoke suite opens the seeded lowercase-header spec, a seeded keyword-in-header-only spec, and a seeded spec whose only keyword sits inside a fenced code block
- **THEN** it SHALL assert via the highlighting daemon that the lowercase header draws no requirement-recognition complaint, that the keyword-in-header spec draws the targeted move-the-keyword diagnostic, and that the fenced-keyword spec draws the missing-keyword diagnostic (CLI 1.6 fence masking)

#### Scenario: Archive guard journey
- **WHEN** the smoke suite invokes Archive on the seeded incomplete change
- **THEN** it SHALL assert the incomplete-change confirmation surface appears, cancel it, and assert the change directory was not moved

#### Scenario: Store-health journey (CLI 1.6 semantics)
- **WHEN** the smoke suite runs against a host CLI at 1.6+ with an isolated OpenSpec data directory, a pre-registered fresh/config-only store, and prepared store roots (a pointer-declaring root, a never-a-store root, and a fresh root with store identity)
- **THEN** it SHALL assert the fresh store's row renders with no unhealthy or metadata error marker, that registering the pointer-declaring and never-a-store roots surfaces the CLI's refusal/confirmation message and fix in the write-failure dialog (dismissed without confirming), and that registering the identified fresh root succeeds and lists a new row with no error marker; on a host CLI below 1.6 the journey SHALL be skipped, not failed

#### Scenario: Validate-results journey (CLI-parsed errors reach the rendered results)
- **WHEN** the smoke suite, on a 1.6+ host CLI, seeds a spec whose requirement lacks SHALL/MUST, opens the plugin tool window, and triggers the Validate action
- **THEN** it SHALL assert the validation summary notification reports the failure and that the plugin's Console surface renders the CLI-parsed error line for that spec (identified by the CLI parser's `type/id` path form, not satisfiable by the built-in validator's duplicate); on a host CLI below 1.6 the journey SHALL be skipped, not failed

#### Scenario: Spec-viewer preview journey
- **WHEN** the smoke suite selects a spec's requirement node in the Browse tree (via the tree model API)
- **THEN** it SHALL assert the preview pane renders the selected spec's markdown, detected by the preview pane's accessible name flipping to its rendered state (a surface visible only after the selection → pooled-read → render wiring fires)

#### Scenario: Change-deltas preview journey
- **WHEN** the smoke suite, on a 1.6+ host CLI, selects a change node whose seeded delta the consolidated view can badge
- **THEN** it SHALL assert the preview pane renders the change's CLI-sourced deltas grouped by capability with operation badges, detected by the pane's badged-deltas accessible-name state; on a host CLI below 1.6 the journey SHALL be skipped, not failed

#### Scenario: Grouped validation report journey
- **WHEN** the smoke suite seeds a project with a deliberate spec error and a schemaless config, opens the tool window, and triggers the Validate action
- **THEN** it SHALL assert the Console renders the structured report — the FAILED verdict line naming the target, the error/warning/info count line, the per-capability group headers, a severity label on a grouped row, and a clickable line-number token on the resolvable config warning — so the grouped, per-severity, hyperlinked format is exercised end to end

#### Scenario: Validate (Strict) disclosure journey
- **WHEN** the smoke suite opens the tool window and invokes the `OpenSpec.ValidateStrict` action
- **THEN** it SHALL assert the strict run discloses itself in surfaces a default Validate never produces — the summary balloon title `Validate (strict)` and the Console echo of the `--strict` command flag — targeting the strict-specific wiring rather than the verdict

#### Scenario: Journey state isolation
- **WHEN** a journey exercises state-writing actions
- **THEN** it SHALL isolate that state, for example to an isolated OpenSpec data directory injected through the IDE process environment
- **AND** dialog journeys SHALL exit via cancel, performing no archive

#### Scenario: Store-root journey (a project pointing at a store)
- **WHEN** the smoke suite, on a host CLI at 1.5+ with isolated OpenSpec data and config directories, sets up a store with the real CLI and opens a project whose config holds only a pointer to it
- **THEN** it SHALL assert the Browse tree lists the store's change under a root labelled with the store and its source, that opening a store file shows the store banner, that a change created in the store outside the IDE appears when the user returns to the IDE (the platform's window-activation sync, not the plugin's Refresh), that a pointer to an unregistered store makes the tree say the store can't be used, and that fixing the pointer brings the store's change back; on a host CLI below 1.5 the journey SHALL be skipped, not failed

#### Scenario: Store-spec inspection journey
- **WHEN** the smoke suite, on a host CLI at 1.5+, opens a spec that lives in a store outside the project and whose requirement has no RFC 2119 keyword
- **THEN** it SHALL assert the missing-keyword inspection reports it in the Problems view, proving the IDE's highlighting rules don't skip a store spec; on a host CLI below 1.5 the journey SHALL be skipped, not failed

#### Scenario: CLI 2.x layout journey
- **WHEN** the smoke suite, on CLI 2.1.1 from the capture cache, initializes a project with project-scoped skills and opens Manage AI Tools
- **THEN** it SHALL assert a tool reads as set up through the shared `.agents` folder, and that a project set up by CLI 1.17.0 shows the migration banner in the update preview; when CLI 2.1.1 isn't in the capture cache the journey SHALL fail with how to install it, not be skipped

### Requirement: Smoke journeys never gate ordinary PRs

The UI smoke workflow SHALL run on manual dispatch only. It SHALL NOT be a required check on ordinary pull requests, and SHALL NOT run on a release tag push. The release gate SHALL be the maintainer's local `caffeinate -dimsu ./gradlew uiSmoke` run with real rendered-frame passes, before the release tag is pushed. The CI ui-smoke workflow SHALL NOT be relied on as the release gate.

#### Scenario: PR unaffected
- **WHEN** an ordinary pull request runs CI
- **THEN** the UI smoke job SHALL NOT run and SHALL NOT block the merge

#### Scenario: Release gated by the local run
- **WHEN** a release is prepared
- **THEN** release preparation SHALL require the local headful `caffeinate -dimsu ./gradlew uiSmoke` journeys to pass for the release commit before the release tag is pushed

#### Scenario: No CI ui-smoke on a tag push
- **WHEN** a release tag is pushed
- **THEN** no CI ui-smoke job SHALL run automatically — the workflow is manual-dispatch-only, since the self-hosted runner cannot boot a headful IDE and would otherwise report a permanent false-red

#### Scenario: Why the release gate is local
- **WHEN** the self-hosted CI runner cannot boot a headful IDE
- **THEN** the release gate is the local run during release preparation
- **AND** an environmental black-frame failure SHALL NOT count as a product failure

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
