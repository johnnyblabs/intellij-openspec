## ADDED Requirements

### Requirement: Regenerate with an optional note

Choosing Regenerate on a DONE chip SHALL open one dialog that asks what should change, with an optional note. When the artifact has completed downstream artifacts, the same dialog SHALL warn that they may become inconsistent. The note SHALL be added to the artifact's prompt for that regeneration only, in every delivery mode, and SHALL NOT be saved anywhere.

#### Scenario: Regenerate with a note
- **WHEN** the user chooses Regenerate on the DONE `design` chip, types "drop the caching section" and confirms
- **THEN** the plugin SHALL regenerate `design` through the selected delivery mode with a prompt that includes the note exactly once, after the dependencies, in a section marked as subordinate to the schema's instructions

#### Scenario: Regenerate without a note
- **WHEN** the user confirms the dialog with the note left blank
- **THEN** the prompt SHALL be byte-identical to the prompt Regenerate builds without a note

#### Scenario: Downstream warning in the same dialog
- **WHEN** the user chooses Regenerate on a chip whose artifact has completed downstream artifacts
- **THEN** the dialog SHALL name those artifacts and warn that they may become inconsistent, and no second confirmation SHALL follow

#### Scenario: Cancel
- **WHEN** the user cancels the dialog
- **THEN** nothing SHALL be generated, copied or written

#### Scenario: The note is not saved
- **WHEN** a regeneration with a note completes or is cancelled
- **THEN** the note SHALL NOT be written to settings, `.openspec.yaml`, `config.yaml` or any file other than the artifact's own output, and the next Regenerate dialog SHALL open with an empty note

#### Scenario: Copy prompt is unchanged
- **WHEN** the user chooses Copy prompt on a chip
- **THEN** no note dialog SHALL open and the copied prompt SHALL be unchanged
