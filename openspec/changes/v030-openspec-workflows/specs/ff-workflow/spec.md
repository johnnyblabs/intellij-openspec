## ADDED Requirements

### Requirement: Fast-Forward change creation

The plugin SHALL provide a Fast-Forward action that creates a new change and generates all artifacts via Direct API in a single operation, requiring only a description of what the user wants to build.

#### Scenario: FF from description
- **WHEN** the user triggers Fast-Forward and enters a description
- **THEN** the plugin SHALL derive a kebab-case name, create the change via `openspec new change`, and begin generating artifacts

#### Scenario: FF artifact generation
- **WHEN** the change is created
- **THEN** the plugin SHALL walk the artifact DAG in dependency order, generating each artifact via Direct API with progress feedback in the dialog

#### Scenario: FF completion
- **WHEN** all `applyRequires` artifacts are generated
- **THEN** the plugin SHALL refresh the tool window, select the new change, and display a summary with a prompt to run Apply

### Requirement: FF dialog

The plugin SHALL display an FF dialog with a description field, optional name override, schema selector (when multiple schemas exist), and a progress panel showing artifact generation status.

#### Scenario: Name derivation
- **WHEN** the user enters a description without specifying a name
- **THEN** the dialog SHALL derive a kebab-case name from the description (e.g., "add user authentication" becomes "add-user-auth")

#### Scenario: Schema selection
- **WHEN** multiple workflow schemas are available
- **THEN** the dialog SHALL show a schema dropdown defaulting to the project's configured schema

#### Scenario: Cancellation
- **WHEN** the user cancels during generation
- **THEN** the plugin SHALL stop generation but preserve already-created artifacts and the change directory
