## ADDED Requirements

### Requirement: OpenSpec YAML file type registration

The plugin SHALL register `.openspec.yaml` as a custom file type that extends YAML language support and displays the OpenSpec icon.

#### Scenario: .openspec.yaml displays OpenSpec icon
- **WHEN** a project contains `.openspec.yaml` files
- **THEN** those files SHALL display the OpenSpec icon (`/icons/openspec.svg`) in the project tree, editor tabs, and search results

#### Scenario: .openspec.yaml retains YAML support
- **WHEN** a user opens a `.openspec.yaml` file in the editor
- **THEN** the file SHALL have YAML syntax highlighting, formatting, and inspections

### Requirement: Spec file icon in project tree

The plugin SHALL provide an `IconProvider` that displays the spec icon for `spec.md` files located under `openspec/specs/`.

#### Scenario: Main spec file shows spec icon
- **WHEN** a `spec.md` file exists under `openspec/specs/<domain>/`
- **THEN** the file SHALL display the spec icon (`/icons/spec.svg`) in the project tree

#### Scenario: Non-OpenSpec spec.md files are unaffected
- **WHEN** a `spec.md` file exists outside the `openspec/` directory
- **THEN** the `IconProvider` SHALL return `null` and the file SHALL display its default Markdown icon

### Requirement: Delta spec file icon in project tree

The plugin SHALL provide an `IconProvider` that displays the delta-spec icon for `spec.md` files located under `openspec/changes/*/specs/`.

#### Scenario: Delta spec file shows delta-spec icon
- **WHEN** a `spec.md` file exists under `openspec/changes/<change-name>/specs/<domain>/`
- **THEN** the file SHALL display the delta-spec icon (`/icons/delta-spec.svg`) in the project tree

#### Scenario: Archived delta specs also show delta-spec icon
- **WHEN** a `spec.md` file exists under `openspec/changes/archive/<archived-change>/specs/<domain>/`
- **THEN** the file SHALL display the delta-spec icon (`/icons/delta-spec.svg`) in the project tree
