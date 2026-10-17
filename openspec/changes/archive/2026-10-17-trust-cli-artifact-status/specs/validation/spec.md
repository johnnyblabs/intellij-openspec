## MODIFIED Requirements

### Requirement: Scaffolding content detection

The plugin SHALL distinguish between scaffolding placeholder content and real authored content as a hint only. It SHALL NOT change an artifact's status: the status is the one `openspec status` reports, where an artifact is done when its file exists.

#### Scenario: Placeholder detection
- **WHEN** an artifact file contains only template placeholders or boilerplate headings with no substantive content
- **THEN** the plugin SHALL keep the status `openspec status` reports (DONE) and mark the artifact as looking like an unfilled template, without disabling any action
