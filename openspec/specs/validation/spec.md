# Validation

## Purpose
Spec format validation, config verification, artifact file watching, and post-archive workflow enforcement.

## Requirements

### Requirement: Spec format validation

The plugin SHALL validate spec files for title headings, requirement structure, RFC 2119 keywords, and Given-When-Then scenario format.

#### Scenario: Validation rules
- **WHEN** validation runs on a spec file
- **THEN** it SHALL check for `# Title` heading, `### Requirement:` headings, RFC 2119 keywords, and scenario structure

### Requirement: Config validation

The plugin SHALL validate `config.yaml` presence and YAML syntax, reporting clear errors for missing or malformed configuration.

#### Scenario: Config checks
- **WHEN** validation runs
- **THEN** it SHALL verify config.yaml exists and parses correctly

### Requirement: Artifact file watching

The plugin SHALL auto-detect artifact file changes after clipboard or editor delivery and refresh the pipeline status.

#### Scenario: File change detection
- **WHEN** a user saves an artifact file after pasting AI-generated content
- **THEN** the plugin SHALL detect the change and update the artifact's status in the pipeline

### Requirement: Scaffolding content detection

The plugin SHALL distinguish between scaffolding placeholder content and real authored content, overriding artifact status accordingly.

#### Scenario: Placeholder detection
- **WHEN** an artifact file contains only template placeholders
- **THEN** the plugin SHALL report it as scaffolding and override status to READY (not DONE)

### Requirement: Post-archive workflow

After archiving, the plugin SHALL commit, push via branch+PR (main is protected), close matching Forgejo issues, update Plane work items to Done, assign to matching cycle, and cross-link trackers.

#### Scenario: Post-archive steps
- **WHEN** a change is archived
- **THEN** the tool SHALL execute commit, PR creation (with labels + milestone), and tracker updates automatically
