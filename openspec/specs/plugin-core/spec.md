# Plugin Core

## Domain
Core plugin infrastructure: project detection, configuration parsing, and service lifecycle.

## Requirements

### Requirement: Project Detection

The plugin SHALL detect whether a project contains an OpenSpec configuration by checking for the presence of an `openspec/` directory at the project root.

**Keyword:** SHALL

#### Scenarios

**Scenario: Project with OpenSpec directory**
- GIVEN a project with an `openspec/` directory containing `config.yaml`
- WHEN the project is opened in the IDE
- THEN the plugin SHALL recognize it as an OpenSpec project
- AND enable all OpenSpec features

**Scenario: Project without OpenSpec directory**
- GIVEN a project without an `openspec/` directory
- WHEN the project is opened in the IDE
- THEN the plugin SHALL NOT enable OpenSpec features
- AND the OpenSpec tool window SHOULD be hidden

### Requirement: Config Parsing

The plugin SHALL parse the `openspec/config.yaml` file and make its contents available to all plugin services.

**Keyword:** SHALL

#### Scenarios

**Scenario: Valid config file**
- GIVEN a valid `openspec/config.yaml` with schema, profile, and rules
- WHEN the config service initializes
- THEN all config values SHALL be accessible via the ConfigService API

**Scenario: Missing config file**
- GIVEN an `openspec/` directory without a `config.yaml` file
- WHEN the config service initializes
- THEN the service SHALL report an error
- AND the plugin SHOULD notify the user

### Requirement: Spec File Discovery

The plugin SHALL discover all spec files under the `openspec/specs/` directory tree.

**Keyword:** SHALL

#### Scenarios

**Scenario: Multiple spec domains**
- GIVEN an `openspec/specs/` directory with subdirectories for each domain
- WHEN the spec parsing service scans for specs
- THEN it SHALL return a SpecFile for each `spec.md` found
- AND each SpecFile SHALL include its domain name derived from the parent directory
