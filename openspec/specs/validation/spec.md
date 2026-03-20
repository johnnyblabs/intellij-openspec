# Validation

## Purpose
Built-in validation of OpenSpec project structure, spec format, config integrity, delta spec correctness, and artifact completeness — with real-time IDE inspections and file watching.

## Requirements

### Requirement: Config validation

The plugin SHALL validate `openspec/config.yaml` presence, YAML parse-ability, and required fields on project load and on demand.

#### Scenario: Missing config
- **WHEN** the project has no `openspec/config.yaml` file
- **THEN** the validator SHALL report an ERROR with code `config-missing`

#### Scenario: Missing schema field
- **WHEN** `config.yaml` exists but has no `schema` field
- **THEN** the validator SHALL report an ERROR with code `config-schema-required`

#### Scenario: Unrecognized schema value
- **WHEN** `config.yaml` has a `schema` field with a value not in the recognized set
- **THEN** the validator SHALL report a WARNING with code `config-schema-invalid`

#### Scenario: Missing profile
- **WHEN** `config.yaml` has no `profile` section
- **THEN** the validator SHALL report a WARNING with code `config-profile-recommended`

### Requirement: Spec format validation

The plugin SHALL validate spec files for structural completeness: title heading, requirement blocks, RFC 2119 keywords, and scenario format.

#### Scenario: Missing title heading
- **WHEN** a spec file has no `# Title` heading
- **THEN** the validator SHALL report an ERROR with code `spec-title-required`

#### Scenario: Missing requirement block
- **WHEN** a spec file has no `### Requirement:` section
- **THEN** the validator SHALL report an ERROR with code `spec-requirement-required`

#### Scenario: Missing RFC 2119 keywords
- **WHEN** a requirement block contains no RFC 2119 keywords (SHALL, SHOULD, MAY, SHALL NOT, SHOULD NOT)
- **THEN** the validator SHALL report an ERROR with code `spec-rfc-keywords`

#### Scenario: Missing scenario
- **WHEN** a requirement block has no `#### Scenario:` section
- **THEN** the validator SHALL report an ERROR with code `spec-scenario-required`

#### Scenario: Scenario missing clauses
- **WHEN** a scenario block is missing a WHEN clause, a THEN clause, or both
- **THEN** the validator SHALL report an ERROR with code `spec-scenario-clauses`

### Requirement: Change validation

The plugin SHALL validate active changes for required artifacts: proposal, design, specs, and tasks — reporting missing artifacts based on the project's schema configuration.

#### Scenario: Missing proposal
- **WHEN** an active change has no `proposal.md` file
- **THEN** the validator SHALL report an ERROR with code `change-proposal-required`

#### Scenario: Missing artifacts
- **WHEN** an active change is missing one or more required artifact files (design.md, specs/, tasks.md)
- **THEN** the validator SHALL report a finding with code `change-artifact-missing` at the severity appropriate to the schema's strictness

### Requirement: Delta spec validation

The plugin SHALL validate delta spec files for structural correctness, including section headings (ADDED, MODIFIED, REMOVED), removal metadata, and scenario coverage.

#### Scenario: Missing delta sections
- **WHEN** a delta spec file under a change's `specs/` directory has no ADDED, MODIFIED, or REMOVED sections
- **THEN** the validator SHALL report a WARNING with code `delta-spec-sections`

#### Scenario: Removed requirement missing metadata
- **WHEN** a REMOVED requirement block is missing a **Reason** field or a **Migration** field
- **THEN** the validator SHALL report an ERROR with code `delta-removed-fields`

#### Scenario: Added requirement missing scenario
- **WHEN** an ADDED requirement block has no `#### Scenario:` section
- **THEN** the validator SHALL report an ERROR with code `delta-requirement-scenario`

#### Scenario: Modified requirement missing scenario
- **WHEN** a MODIFIED requirement block has no `#### Scenario:` section with updated content
- **THEN** the validator SHALL report an ERROR with code `delta-requirement-scenario`

### Requirement: Delta spec IDE inspection

The plugin SHALL provide a real-time IDE inspection for delta spec files, highlighting structural problems as the user edits.

#### Scenario: Inspection scope
- **WHEN** a file is located under `openspec/changes/<change>/specs/` and is named `spec.md`
- **THEN** the plugin SHALL apply delta spec inspections to that file

#### Scenario: Inspection highlights errors inline
- **WHEN** a delta spec file has a REMOVED requirement missing metadata or an ADDED requirement missing a scenario
- **THEN** the IDE SHALL display inline error highlights at the relevant locations

### Requirement: Scaffolding content detection

The plugin SHALL distinguish between scaffolding placeholder content and real authored content, overriding artifact status accordingly.

#### Scenario: Placeholder detection
- **WHEN** an artifact file contains only template placeholders or boilerplate headings with no substantive content
- **THEN** the plugin SHALL report the artifact as scaffolding and override its status to READY (not DONE)

### Requirement: Artifact file watching

The plugin SHALL auto-detect artifact file changes via VFS listeners and refresh pipeline status after clipboard or editor delivery.

#### Scenario: File save triggers refresh
- **WHEN** a user saves an artifact file after pasting AI-generated content
- **THEN** the plugin SHALL detect the VFS change and update the artifact's pipeline status
