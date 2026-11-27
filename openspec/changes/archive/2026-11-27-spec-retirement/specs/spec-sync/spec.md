## ADDED Requirements

### Requirement: Retiring a capability follows the release

When a change sets `retire_capabilities: true` and removes a capability's last requirement, the plugin's merge SHALL retire the capability as the followed release's `openspec archive` does: delete the main spec and the folders it leaves empty, or refuse in that release's words. Below 1.8.0 it SHALL refuse without retiring. It SHALL never write a spec with no requirements.

#### Scenario: A clean retirement
- **WHEN** the marker is set, the change removes the last requirement, and the spec holds nothing but its title, Purpose and that requirement
- **THEN** Sync Specs and the plugin's archive SHALL delete `spec.md` and each parent folder left empty, stopping before `specs/`

#### Scenario: A folder with other files is kept
- **WHEN** a retired capability's folder holds another file
- **THEN** only `spec.md` SHALL be deleted, and the folder and the other file SHALL remain

#### Scenario: Content the merge can't account for
- **WHEN** the marker is set and the spec holds content outside its title, Purpose and requirements, as the followed release's check finds it
- **THEN** the change SHALL be refused with that release's message naming the lines, and nothing SHALL be written

#### Scenario: The check follows the release
- **WHEN** a requirement's statement holds a line starting `<args>`
- **THEN** it SHALL be content the merge can't account for when following a release before 2.1.1, and prose from 2.1.1

#### Scenario: No marker
- **WHEN** a change removes a capability's last requirement without the marker
- **THEN** it SHALL be refused with `Spec must have at least one requirement` and the hint the followed release gives, and nothing SHALL be written

### Requirement: A retirement is shown before and after

The sync preview SHALL show a retirement as the capability's deletion, the archive prompt SHALL name the specs a retirement would delete, and the result SHALL name the retired capabilities with how to restore a committed spec. When the CLI archives, the plugin SHALL read the retired capabilities from the CLI's output.

#### Scenario: Preview
- **WHEN** a sync would retire a capability
- **THEN** its tab SHALL be titled `<id> (retire)`, and a banner SHALL say its `spec.md` will be deleted

#### Scenario: Archive prompt
- **WHEN** archiving would retire a capability
- **THEN** the prompt SHALL name the spec files it deletes

#### Scenario: Result from the CLI
- **WHEN** `openspec archive` prints `Retiring openspec/specs/<id>/spec.md: all requirements removed.`
- **THEN** the archive result SHALL say `<id>` was retired, with the restore command
