## ADDED Requirements

### Requirement: Local History label before Direct API writes

Before every Direct API write of an artifact, the plugin SHALL save unsaved editor changes to the files it will write, then put an IDE Local History label naming the artifact ("OpenSpec: before generating `<artifact>`", or "before regenerating" for Regenerate). The plugin SHALL NOT store any history of its own.

#### Scenario: Regenerate is labelled before it writes
- **WHEN** the user accepts a Direct API Regenerate of `design`
- **THEN** a Local History label "OpenSpec: before regenerating design" SHALL be put before `design.md` is written

#### Scenario: Generate is labelled before it writes
- **WHEN** Direct API generates `tasks` from a READY chip, Generate All Remaining, Fast-Forward or Continue
- **THEN** a Local History label "OpenSpec: before generating tasks" SHALL be put before the file is written

#### Scenario: Unsaved edits are recorded first
- **WHEN** a file the write will replace has unsaved changes in an editor
- **THEN** those changes SHALL be saved before the label is put, so the label's earlier content is the text the user had

#### Scenario: Nothing is labelled when nothing is written
- **WHEN** the user rejects a Regenerate review, or the write is refused
- **THEN** no label SHALL be put

#### Scenario: Nothing is stored by the plugin
- **WHEN** any Direct API write completes
- **THEN** the plugin SHALL NOT have written any copy of the earlier content to the OpenSpec tree, settings or any other file

### Requirement: Compare with previous after Regenerate

After an accepted Direct API Regenerate that changed at least one existing file, the confirmation popover SHALL offer "Compare with previous", which opens a diff of the text the user reviewed against the text written, one file after another when the artifact has several. The earlier text SHALL be kept only in memory for that diff. For the specs artifact, the popover SHALL keep its "Diff vs current spec" link as well.

#### Scenario: Compare a regenerated file
- **WHEN** the user accepts a Regenerate of `design` and chooses "Compare with previous"
- **THEN** a diff SHALL open with the reviewed `design.md` text on the left and the written text on the right

#### Scenario: A regeneration that only created files
- **WHEN** every file an accepted Regenerate wrote is new
- **THEN** the popover SHALL NOT offer "Compare with previous"

#### Scenario: Specs keep their diff link
- **WHEN** the user accepts a Regenerate of `specs` that changed an existing delta spec
- **THEN** the popover SHALL offer both "Compare with previous" and "Diff vs current spec"
