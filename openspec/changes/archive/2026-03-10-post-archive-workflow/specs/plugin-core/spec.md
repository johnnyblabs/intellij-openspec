## ADDED Requirements

### Requirement: Post-archive convention in config rules

The project's `openspec/config.yaml` SHALL include a `post-archive` rule declaring the post-archive workflow steps as a project convention visible to all AI tools.

#### Scenario: Config contains post-archive rule
- **WHEN** any AI tool reads the project's config.yaml rules
- **THEN** a `post-archive` rule SHALL be present describing the commit, push, and tracker update steps
