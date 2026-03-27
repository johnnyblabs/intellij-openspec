## MODIFIED Requirements

### Requirement: Context assembly

The plugin SHALL assemble a Markdown-formatted project context from the current OpenSpec project state, including config summary with context and rules, detected AI tools, active changes with full artifact content, and spec domain listings with requirement summaries.

#### Scenario: Full context assembly
- **WHEN** the user triggers context assembly
- **THEN** the service SHALL produce a Markdown document with sections for Project Config, Detected AI Tools, Active Changes, and Specs

#### Scenario: Config section includes context and rules
- **WHEN** context is assembled and `config.yaml` has `context` and `rules` fields
- **THEN** the Project Config section SHALL include the schema, version, context description, and rules as a bulleted list

#### Scenario: Active changes with full artifacts
- **WHEN** an active change has artifact files (proposal.md, design.md, tasks.md, delta specs)
- **THEN** the Active Changes section SHALL include the full content of each artifact under the change heading

#### Scenario: Active changes with missing artifacts
- **WHEN** an active change is missing some artifact files
- **THEN** the Active Changes section SHALL include only the artifacts that exist, silently skipping missing ones

#### Scenario: Spec domain with requirement summaries
- **WHEN** a spec domain has a `spec.md` with `### Requirement:` blocks
- **THEN** the Specs section SHALL list each requirement name with its description text (not scenarios)

#### Scenario: No active changes
- **WHEN** the project has no active changes
- **THEN** the Active Changes section SHALL indicate no changes are in progress
