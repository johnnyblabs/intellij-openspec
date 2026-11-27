## ADDED Requirements

### Requirement: The retirement marker can be added from a refusal that asks for it

When a sync or archive is refused with a fix that names `retire_capabilities: true`, and any "cannot be honored" reason concerns the marker's own value, the plugin SHALL offer to add the marker. After the user confirms the deletion of each named spec, it SHALL write `retire_capabilities: true` into the change's `.openspec.yaml` as one undoable edit, keeping all other content, and then show the Sync preview. It SHALL NOT delete any spec itself.

#### Scenario: The marker is missing
- **WHEN** Sync Specs is refused because a change removes the last requirement of `foo` and has no marker
- **THEN** the refusal panel offers "Add retire_capabilities: true…"
- **AND** after the user confirms that `specs/foo/spec.md` will be deletable, the change's `.openspec.yaml` gains `retire_capabilities: true`, its other lines unchanged
- **AND** the Sync preview opens again, showing `foo (retire)`

#### Scenario: The marker is not a boolean
- **WHEN** the refusal's reason is `retire_capabilities: Invalid input: expected boolean, received number`
- **THEN** the action is offered, and applying it replaces the value with `true`

#### Scenario: Adding the marker would not fix the refusal
- **WHEN** the refusal names content the deletion would take, or its reason is that the file is not valid YAML, a required `schema:` is missing, `created:` is malformed, or the schema is unknown or invalid
- **THEN** no action is offered and the refusal is shown as before

#### Scenario: The user cancels
- **WHEN** the user declines the confirmation
- **THEN** the change's `.openspec.yaml` is unchanged

#### Scenario: From a failed archive
- **WHEN** an archive fails with a refusal that offers the action and the user applies it from the notification
- **THEN** the marker is written and the Sync preview opens; the archive is not run again

#### Scenario: In Bulk Archive
- **WHEN** a Bulk Archive row is refused for the marker only
- **THEN** its problem text adds a hint to use Sync Specs on that change, and no marker is written from the dialog
