## MODIFIED Requirements

### Requirement: Tree view display

The plugin SHALL display a tree with Specs, Changes, Archive, and Config sections. Double-clicking navigates to files. The tree auto-refreshes on filesystem changes.

#### Scenario: Config section in tree
- **WHEN** the tool window opens on an initialized project with `openspec/config.yaml`
- **THEN** the tree SHALL include a Config section after Archive showing flat, read-only key-value entries for top-level config fields (schema, version, profile name, context truncated to ~60 chars, rules count)

#### Scenario: Config section without config file
- **WHEN** no `openspec/config.yaml` exists or the config is not loaded
- **THEN** a hint node SHALL appear under Config indicating no config.yaml was found

#### Scenario: Config node double-click
- **WHEN** the user double-clicks any Config or Config entry node
- **THEN** the plugin SHALL open `openspec/config.yaml` in the editor

#### Scenario: Config entries filtered by search
- **WHEN** the user types a search term matching a config entry label
- **THEN** the Config section SHALL filter to show only matching entries, consistent with existing tree filtering behavior

## ADDED Requirements

### Requirement: Config node types

The tree model SHALL define `CONFIG` and `CONFIG_ENTRY` node types for the config section header and its key-value leaf nodes respectively.

#### Scenario: Config node rendering
- **WHEN** the Config section renders in the tree
- **THEN** the CONFIG node SHALL display with a settings gear icon (`AllIcons.General.Settings`) and CONFIG_ENTRY nodes SHALL display as plain text without icons

#### Scenario: Null or empty config fields
- **WHEN** a top-level config field is null or empty
- **THEN** the corresponding CONFIG_ENTRY node SHALL be omitted from the tree