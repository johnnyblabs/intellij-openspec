## MODIFIED Requirements

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

### Requirement: Delivery-aware FF generation

After creating a change, the panel SHALL trigger generation using the currently selected delivery method from the tool selector. With Direct API it SHALL stop once the artifacts needed before apply are done, as upstream's fast-forward does.

#### Scenario: Direct API delivery
- **WHEN** the selected delivery method is Direct API
- **THEN** the panel SHALL generate the new change's artifacts with progress bar and pipeline chips, stopping once every artifact needed before apply is done

#### Scenario: Clipboard delivery
- **WHEN** the selected delivery method is Clipboard
- **THEN** the panel SHALL trigger generation of the first ready artifact via clipboard copy with tool-specific guidance

#### Scenario: Editor Tab delivery
- **WHEN** the selected delivery method is Editor Tab
- **THEN** the panel SHALL trigger generation of the first ready artifact by opening the prompt in an editor tab
