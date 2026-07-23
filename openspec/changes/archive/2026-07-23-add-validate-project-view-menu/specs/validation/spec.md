## ADDED Requirements

### Requirement: Validate from the Project View context menu

The plugin SHALL offer a Validate action in the IDE's Project View context menu, visible only when the current selection is under an `openspec/` directory. Invoking it SHALL validate the change or spec that owns the clicked file, resolved by mapping the file's path up to its owning directory: a selection under `openspec/specs/<capability>/` SHALL validate that spec; a selection under `openspec/changes/<name>/` for an active (non-archived) change SHALL validate that change; a selection under `openspec/changes/archive/`, at the `openspec/` root, or on a non-item file such as `config.yaml` SHALL fall back to validating the whole project. The action SHALL reuse the existing validation pipeline (built-in validator always, CLI enhancement when available) scoped to the resolved target, SHALL NOT fabricate a per-file valid/invalid verdict, and its visibility check SHALL be inexpensive (a path check, no blocking I/O or CLI call).

#### Scenario: Menu item hidden outside openspec
- **WHEN** the user right-clicks a file that is not under an `openspec/` directory
- **THEN** the Validate OpenSpec menu item SHALL NOT be shown

#### Scenario: Validating a spec from the tree
- **WHEN** the user right-clicks a file under `openspec/specs/<capability>/` and invokes Validate
- **THEN** the plugin SHALL validate that spec (not the whole project) and report the result to the console

#### Scenario: Validating a change from the tree
- **WHEN** the user right-clicks a file under an active change's `openspec/changes/<name>/` and invokes Validate
- **THEN** the plugin SHALL validate that change and report the result

#### Scenario: Non-item selection falls back to whole-project
- **WHEN** the user invokes Validate on a selection under `openspec/changes/archive/`, at the `openspec/` root, or on `config.yaml`
- **THEN** the plugin SHALL validate the whole project rather than fabricating a single-item verdict for a non-validatable target
