## ADDED Requirements

### Requirement: Archive target directory

When the plugin archives a change itself, it SHALL move the change to `openspec/changes/archive/YYYY-MM-DD-<name>/`, using the local date and keeping a name that already starts with a date unchanged. It SHALL refuse, before writing any spec, when that target already exists. It SHALL create `openspec/changes/archive/` when missing and SHALL NOT create `openspec/archive/`, though it SHALL still read archived changes from an existing legacy `openspec/archive/`.

#### Scenario: Dated archive name
- **WHEN** the user archives change `add-login` on 2026-10-17
- **THEN** the change SHALL be moved to `openspec/changes/archive/2026-10-17-add-login/`

#### Scenario: Target already exists
- **WHEN** `openspec/changes/archive/2026-10-17-add-login/` already exists
- **THEN** the archive SHALL be refused with no spec written and the change left active

#### Scenario: No archive directory yet
- **WHEN** `openspec/changes/archive/` does not exist
- **THEN** the plugin SHALL create it and archive into it, and SHALL NOT create `openspec/archive/`
