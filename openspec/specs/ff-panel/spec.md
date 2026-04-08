# FF Panel

## Purpose
Inline Fast-Forward input form within the WorkflowActionPanel, providing description input, name override, schema selection, and delivery-aware generation.

## Requirements

### Requirement: Inline FF input form

The WorkflowActionPanel SHALL display an inline Fast-Forward input form when the user activates FF, containing a description textarea, an optional name override text field, and a schema selector combo box (visible only when multiple schemas exist).

#### Scenario: FF input form display
- **WHEN** the user clicks the FF toolbar button, the "Fast-Forward" hyperlink, or the OpenSpec > Fast-Forward menu action
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

After creating a change, the panel SHALL trigger generation using the currently selected delivery method from the tool selector.

#### Scenario: Direct API delivery
- **WHEN** the selected delivery method is Direct API
- **THEN** the panel SHALL automatically trigger GenerateAll for the new change with progress bar and pipeline chips

#### Scenario: Clipboard delivery
- **WHEN** the selected delivery method is Clipboard
- **THEN** the panel SHALL trigger generation of the first ready artifact via clipboard copy with tool-specific guidance

#### Scenario: Editor Tab delivery
- **WHEN** the selected delivery method is Editor Tab
- **THEN** the panel SHALL trigger generation of the first ready artifact by opening the prompt in an editor tab

### Requirement: FF activation from menu action

The OpenSpecFfAction SHALL focus the OpenSpec tool window and activate the FF input form instead of opening a modal dialog.

#### Scenario: Menu action invocation
- **WHEN** the user invokes OpenSpec > Fast-Forward from the menu
- **THEN** the tool window SHALL be activated and the WorkflowActionPanel SHALL switch to the FF_INPUT card