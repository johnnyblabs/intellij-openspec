## ADDED Requirements

### Requirement: Inline tool/delivery selector

The WorkflowActionPanel SHALL display a tool/delivery selector dropdown that lets users choose their AI tool or delivery method without leaving the workflow panel.

#### Scenario: Selector shows detected tools
- **WHEN** the workflow panel is displayed with detected AI tools
- **THEN** the tool selector SHALL list each detected tool by name with a type indicator (CLI or IDE)

#### Scenario: Selector shows delivery options
- **WHEN** the workflow panel is displayed
- **THEN** the tool selector SHALL include Direct API (if configured), Editor Tab, and generic Clipboard options below the detected tools

#### Scenario: Tool selection sets delivery mode implicitly
- **WHEN** the user selects a tool from the dropdown
- **THEN** the delivery mode SHALL be set automatically (CLI/IDE tools use clipboard, Direct API uses API)
- **AND** the Generate button label SHALL update to reflect the selection

#### Scenario: Selection is persisted
- **WHEN** the user selects a tool or delivery option
- **THEN** the selection SHALL be saved and restored on next panel load

#### Scenario: No tools and no API configured
- **WHEN** no AI tools are detected and no API key is configured
- **THEN** the panel SHALL display inline help text guiding the user to configure an AI tool or API key

## MODIFIED Requirements

### Requirement: Workflow Action Panel Display

The tool window SHALL display a Workflow Action Panel between the tree and the status bar that shows the selected change's name via a change selector, artifact pipeline status, tool/delivery selector, and generation controls.

#### Scenario: Active change with ready artifacts
- **WHEN** an active change is selected with ready artifacts
- **THEN** the panel SHALL display the change selector, a pipeline status row, a tool/delivery selector, and a "Generate [artifact-name]" button for the next ready artifact

#### Scenario: No active change
- **WHEN** no active change exists
- **THEN** the panel SHALL display guidance text such as "No active change" with a hint to use Propose

#### Scenario: All artifacts complete
- **WHEN** all artifacts for the selected change are done
- **THEN** the panel SHALL display "All complete" in the pipeline with guidance to apply or archive

#### Scenario: Generate All button visibility
- **WHEN** Direct API is configured and 2 or more artifacts remain to be generated
- **THEN** the panel SHALL display a "Generate All" button alongside the existing Generate button

#### Scenario: Generate All button hidden without API
- **WHEN** Direct API is not configured
- **THEN** the panel SHALL NOT display the "Generate All" button

#### Scenario: Generate All button hidden with single artifact
- **WHEN** only 1 artifact remains to be generated
- **THEN** the panel SHALL NOT display the "Generate All" button

## REMOVED Requirements

### Requirement: Post-Generation Guidance Card
**Reason:** The first-run setup card is replaced by the always-visible tool selector. No special first-run flow needed.
**Migration:** Tool selection happens via the inline dropdown instead of a one-time card.
