## ADDED Requirements

### Requirement: The retirement marker can be added from a refusal that asks for it

When a sync or archive refusal's fix names `retire_capabilities: true`, any "cannot be honored" reason concerns the marker's own value, and the metadata with the marker added would be honored, the plugin SHALL offer to add it. After the user confirms every spec the marker would make deletable, it SHALL write `retire_capabilities: true` into the change's `.openspec.yaml` as one undoable edit, keeping all other content, then show the Sync preview. It SHALL NOT delete any spec itself.

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

#### Scenario: The plugin's refusal gives no reason, but the marker would still not count
- **WHEN** the plugin refuses without a reason and the metadata, with the marker added, would still not be honored (for example, a project schema override the CLI does not validate)
- **THEN** no action is offered

#### Scenario: The change retires more than one capability
- **WHEN** a change removes the last requirement of both `foo` and `bar`, and the archive's refusal names only `foo`
- **THEN** the confirmation names both `specs/foo/spec.md` and `specs/bar/spec.md`

#### Scenario: The user cancels
- **WHEN** the user declines the confirmation
- **THEN** the change's `.openspec.yaml` is unchanged

#### Scenario: From a failed archive
- **WHEN** an archive fails with a refusal that offers the action and the user applies it from the notification
- **THEN** the marker is written and the Sync preview opens; the archive is not run again

#### Scenario: In Bulk Archive
- **WHEN** a Bulk Archive row is refused for the marker only
- **THEN** its problem text adds a hint to use Sync Specs on that change, and no marker is written from the dialog
