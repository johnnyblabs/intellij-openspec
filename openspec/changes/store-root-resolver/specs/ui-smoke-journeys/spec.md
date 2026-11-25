## MODIFIED Requirements

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
- **THEN** it SHALL assert the Browse tree lists the store's change, that a change created in the store outside the IDE appears when the user returns to the IDE (the platform's window-activation sync, not the plugin's Refresh), that a pointer to an unregistered store makes the tree say the store can't be used, and that fixing the pointer brings the store's change back; on a host CLI below 1.5 the journey SHALL be skipped, not failed
