# Delta — ui-smoke-journeys

## MODIFIED Requirements

### Requirement: Rendered-UI smoke journeys exist

The project SHALL maintain a small suite (currently six) of automated UI smoke journeys that drive a real sandbox IDE with the plugin installed, asserting presence and wiring of rendered surfaces. Journeys SHALL assert component presence and wiring, not textual prose or pixels, and SHALL NOT mutate durable state (dialog journeys exit via cancel; no archive is performed, no feedback is sent).

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

#### Scenario: Feedback dialog guard journey
- **WHEN** the smoke suite invokes the Send OpenSpec Feedback action
- **THEN** it SHALL assert the dialog renders and an empty message blocks submission, and SHALL leave via cancel without sending anything

#### Scenario: Archive guard journey
- **WHEN** the smoke suite invokes Archive on the seeded incomplete change
- **THEN** it SHALL assert the incomplete-change confirmation surface appears, cancel it, and assert the change directory was not moved
