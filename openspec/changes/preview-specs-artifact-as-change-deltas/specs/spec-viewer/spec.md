## ADDED Requirements

### Requirement: A change's specs artifact previews its deltas

When the user selects a change's artifact whose output path is a glob under that change's `specs/` directory, the preview SHALL render the change's consolidated deltas view, exactly as when the change node itself is selected. The preview SHALL never read a glob output path as a file. Any other artifact whose output path is a glob SHALL show the no-preview empty state.

#### Scenario: Selecting the specs artifact shows the change's deltas
- **WHEN** the user selects the `specs` artifact under a change, whose output path is `specs/**/*.md`
- **THEN** the preview SHALL render that change's consolidated deltas view, sourced from the CLI as for the change node
- **AND** each capability's diff link SHALL resolve against that change's directory

#### Scenario: Another glob artifact shows no preview
- **WHEN** the user selects an artifact whose output path is a glob outside the change's `specs/` directory
- **THEN** the preview SHALL show the no-preview empty state, and no file lookup SHALL be attempted on the glob
