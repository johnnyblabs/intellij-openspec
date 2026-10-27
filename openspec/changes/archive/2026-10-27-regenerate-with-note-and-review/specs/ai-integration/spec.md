## ADDED Requirements

### Requirement: Review before Direct API Regenerate writes

When Regenerate runs through Direct API, the plugin SHALL show the generated result against the current files before writing anything, and SHALL write only when the user accepts. The review SHALL cover every file the result would write. With a note, the prompt SHALL include the artifact's current content so the model revises it rather than starting over.

#### Scenario: Accept writes the result
- **WHEN** Direct API returns a regenerated `design.md` and the user accepts the review
- **THEN** the plugin SHALL write `design.md` and report that it was regenerated

#### Scenario: Reject writes nothing
- **WHEN** the user rejects the review, or closes it without accepting
- **THEN** the plugin SHALL write no file, the artifact's files SHALL stay byte-identical, and the plugin SHALL report that the regeneration was discarded

#### Scenario: A multi-file artifact is reviewed as one
- **WHEN** Direct API regenerates `specs` and the result has files for `auth` and `billing`, where `billing` has no file yet
- **THEN** the review SHALL show both files, `billing` against an empty side, and accepting SHALL write both while rejecting SHALL write neither

#### Scenario: A file changed during the review
- **WHEN** a file the review shows is changed, in the editor or on disk, before the user accepts
- **THEN** the plugin SHALL write no file and SHALL report that the file changed since the review, so edits the user did not see are never overwritten

#### Scenario: Accepting can be undone
- **WHEN** the user accepts the review
- **THEN** the write SHALL be one undoable action named after the artifact

#### Scenario: A rejected response is not reviewed
- **WHEN** the result would be refused by the multi-file rules (an invalid path, or no marked files)
- **THEN** the plugin SHALL report the refusal as it does today and SHALL NOT open a review

#### Scenario: The current content goes with a note
- **WHEN** the user regenerates a single-file artifact over Direct API with a note
- **THEN** the prompt SHALL include the artifact's current content, marked as the version to revise

#### Scenario: Only Regenerate is reviewed
- **WHEN** an artifact is generated over Direct API from a READY chip, Generate All Remaining or Fast-Forward
- **THEN** the plugin SHALL write the result without a review, as today
