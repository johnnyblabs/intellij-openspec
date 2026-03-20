## MODIFIED Requirements

### Requirement: Propose action

The plugin SHALL create a new change with all required artifacts (proposal.md, design.md, tasks.md, specs/) via built-in scaffolding matching the OpenSpec 1.2.0 template structure. When multiple schemas are available, the propose dialog SHALL include a schema selector and pass the selected schema to the CLI.

#### Scenario: Change creation
- **WHEN** the user proposes a change
- **THEN** the plugin SHALL create the change directory with all artifacts and refresh the tool window synchronously

#### Scenario: Schema selection during propose
- **WHEN** multiple schemas are available and the user proposes a change
- **THEN** the ProposeChangeDialog SHALL display a schema combo box and pass `--schema "<schema>"` to the `openspec new change` command
