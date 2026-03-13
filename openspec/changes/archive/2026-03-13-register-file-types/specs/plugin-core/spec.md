## ADDED Requirements

### Requirement: File type extension declarations

The plugin's `plugin.xml` SHALL declare file type extensions for OpenSpec-specific file types and an `IconProvider` for path-based icon resolution.

#### Scenario: plugin.xml registers .openspec.yaml file type
- **WHEN** the plugin loads in the IDE
- **THEN** `plugin.xml` SHALL contain a `<fileType>` extension mapping `.openspec.yaml` to the custom OpenSpec YAML file type

#### Scenario: plugin.xml registers IconProvider
- **WHEN** the plugin loads in the IDE
- **THEN** `plugin.xml` SHALL contain an `<iconProvider>` extension for OpenSpec spec file icon resolution
