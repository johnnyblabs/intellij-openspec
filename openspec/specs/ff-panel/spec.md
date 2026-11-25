# FF Panel

## Purpose
Inline Fast-Forward input form within the WorkflowActionPanel, providing description input, name override, schema selection, and delivery-aware generation.

## Requirements

### Requirement: Inline FF input form

The WorkflowActionPanel SHALL display an inline Fast-Forward input form when the user activates FF, containing a description textarea, an optional name override text field, and a schema selector combo box (visible only when multiple schemas exist).

#### Scenario: FF input form display
- **WHEN** the user runs the Fast-Forward toolbar button or the OpenSpec > Fast-Forward menu action
- **THEN** the panel SHALL switch to the FF_INPUT card showing the description textarea, name override field, and schema combo (if applicable)

#### Scenario: FF input validation
- **WHEN** the user clicks "Go" with an empty description
- **THEN** the panel SHALL display a validation error and prevent submission

#### Scenario: FF input cancel
- **WHEN** the user clicks "Cancel" on the FF input form
- **THEN** the panel SHALL return to the previous card (NO_CHANGES or PIPELINE) without creating a change

### Requirement: CardLayout view management

The WorkflowActionPanel content area SHALL use CardLayout to manage three cards: NO_CHANGES, FF_INPUT, and PIPELINE.

#### Scenario: Card switching
- **WHEN** the panel state changes (no changes, FF activated, change selected)
- **THEN** the appropriate card SHALL be shown via CardLayout without manual component visibility toggling

#### Scenario: Change selector interaction during FF
- **WHEN** the user changes the selected change while the FF_INPUT card is visible
- **THEN** the panel SHALL cancel the FF input and switch to the PIPELINE card for the selected change

### Requirement: FF change creation

When the user submits the FF input form, the panel SHALL create a new change via CLI, update the change selector, and transition to the PIPELINE card.

#### Scenario: Successful change creation
- **WHEN** the user clicks "Go" with a valid description
- **THEN** the panel SHALL call `openspec new change <name>` via CLI, set the new change as active, switch to the PIPELINE card, and trigger generation

#### Scenario: CLI failure during change creation
- **WHEN** the CLI `new change` command fails
- **THEN** the panel SHALL display the error in the FF input form status area and allow the user to retry or cancel

#### Scenario: Name derivation
- **WHEN** no name override is provided
- **THEN** the panel SHALL derive a kebab-case name from the description (max 5 words)

### Requirement: Delivery-aware FF generation

Fast-Forward SHALL do what OpenSpec's `ff` workflow does in every delivery method. With Direct API the panel SHALL create the change and generate it, stopping once the artifacts needed before apply are done. With Clipboard or Editor Tab the action SHALL hand the description to the AI tool's own ff workflow and SHALL NOT create the change itself.

#### Scenario: Direct API delivery
- **WHEN** the delivery method is Direct API
- **THEN** the panel SHALL generate the new change's artifacts with progress bar and pipeline chips, stopping once every artifact needed before apply is done

#### Scenario: Clipboard delivery
- **WHEN** the delivery method is Clipboard and the project has the AI tool's ff skill or command
- **THEN** Fast-Forward SHALL copy that tool's ff invocation followed by the description, and SHALL NOT create a change

#### Scenario: Editor Tab delivery
- **WHEN** the delivery method is Editor Tab and the project has the AI tool's ff skill or command
- **THEN** Fast-Forward SHALL open that tool's ff invocation followed by the description in an editor tab, and SHALL NOT create a change

### Requirement: FF activation from menu action

With Direct API delivery, the OpenSpecFfAction SHALL focus the OpenSpec tool window and activate the FF input form instead of opening a modal dialog. With Clipboard or Editor Tab it SHALL ask for the description and hand it to the AI tool's ff workflow; when no candidate tool has that workflow, it SHALL warn and offer Update OpenSpec….

#### Scenario: Menu action invocation
- **WHEN** the user invokes OpenSpec > Fast-Forward from the menu with Direct API as the delivery method
- **THEN** the tool window SHALL be activated and the WorkflowActionPanel SHALL switch to the FF_INPUT card

#### Scenario: Menu action in a handoff
- **WHEN** the user invokes OpenSpec > Fast-Forward with Clipboard or Editor Tab as the delivery method
- **THEN** a dialog SHALL ask only for the description, and Fast-Forward SHALL deliver the AI tool's ff invocation with it

### Requirement: FF generation uses the user's description as the request

Fast-Forward SHALL give generation the description the user entered as the request the change is for, not only use it to derive the change name. The description SHALL appear in the generation prompt as its own headed section after the artifact's instructions, never as a regeneration note, and SHALL NOT be written to the change directory.

#### Scenario: Direct API Fast-Forward
- **WHEN** the user submits the Fast-Forward form with a description and the delivery method is Direct API
- **THEN** every artifact generated by that Fast-Forward run SHALL be generated from a prompt that includes the description as the request

#### Scenario: Clipboard or Editor Tab Fast-Forward
- **WHEN** the user runs Fast-Forward with a description and the delivery method is Clipboard or Editor Tab
- **THEN** the handoff delivered to the AI tool SHALL carry the description as the ff workflow's input

#### Scenario: Later generations are unaffected
- **WHEN** an artifact of that change is generated or regenerated later, outside the Fast-Forward run
- **THEN** its prompt SHALL NOT include the Fast-Forward description, because the artifacts already written carry the request forward

#### Scenario: Name override keeps the request
- **WHEN** the user enters a description and also overrides the change name
- **THEN** the change SHALL be created with the overridden name and generation SHALL still receive the description as the request

#### Scenario: Nothing extra written
- **WHEN** Fast-Forward creates the change
- **THEN** the plugin SHALL run `openspec new change <name>` (with `--schema` when one is chosen) and SHALL NOT pass `--description` or write the description to any file
