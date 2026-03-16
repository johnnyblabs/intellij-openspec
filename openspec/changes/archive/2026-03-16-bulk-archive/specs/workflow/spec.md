## MODIFIED Requirements

### Requirement: Archive action

The plugin SHALL move completed changes to `openspec/changes/archive/YYYY-MM-DD-<name>/` with post-archive commit, push, and tracker updates. A Bulk Archive option SHALL be available when multiple active changes exist.

#### Scenario: Archive flow
- **WHEN** the user archives a change
- **THEN** the change SHALL be moved to the archive directory and post-archive steps SHALL execute

#### Scenario: Bulk archive entry point
- **WHEN** multiple active changes exist
- **THEN** a Bulk Archive action SHALL be available in the menu and toolbar
