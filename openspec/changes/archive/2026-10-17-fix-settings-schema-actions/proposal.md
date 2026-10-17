## Why

The schema section of **Settings → Tools → OpenSpec** looks broken: the schema list is always empty, **Validate** and **Open Templates** appear to do nothing, and **Fork** and **New...** never open the schema they create. Three separate defects cause this, and none of them is specific to a CLI version:

1. **Background results wait for the dialog to close.** The settings page runs its CLI calls on a background thread and then updates the UI with `invokeLater` without the dialog's modality. Settings is modal, so the platform holds those updates until the dialog closes. They land on a panel nobody can see, and reopening Settings repeats the cycle. A headless probe confirmed this, and the same file's CLI-detection code already passes the modality correctly.
2. **Fork and New read the CLI's prose as a path.** `schema fork` and `schema init` print several lines of text ("Source: … Destination: …", "Schema created at: …"). The plugin treats the whole text as a file path, so the open always finds nothing.
3. **New Schema ignores two of its inputs.** The dialog collects a description and an artifact selection, but only the name is passed to `schema init`.

Tracked in the linked tracker entries.

## What Changes

- **Settings results appear while the dialog is open.** Every background result on the settings page is delivered with the dialog's modality. That covers the schema list and default-schema choice, Validate's result, Open Templates, Fork and New, the config/profile section, and the workflow-profile refresh.
- **Fork and New open the new schema's `schema.yaml`.** They call the CLI with `--json` and read the path from its structured output (`destinationPath` for fork, `path` for init). Both options and keys exist and match from CLI 1.3.0, the schema-management floor, through 1.14.3.
- **New Schema uses everything the dialog collects.** The description and the selected artifacts are passed to `schema init` (`--description`, `--artifacts`), together with `--no-default`, so the CLI never waits on a "make this the default?" prompt the plugin can't answer.
- **Contract tests** against captured 1.3.0 and 1.14.3 `fork --json` and `init --json` output, and a headless test that enters a modal state and proves the schema list fills without leaving it.
- **Out of scope:** the Bulk Archive dialog uses the same `invokeLater` pattern. Its status loading is affected in the same way, and whether its archive loop can stall still needs checking in the real IDE. That's tracked separately.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `schema-management`: Fork and New open the created `schema.yaml` (read from the CLI's JSON output), New passes the description and artifact selection to the CLI, and a new requirement says schema-management results appear in the open Settings dialog rather than after it closes.

## Impact

- **Code:** `OpenSpecSettingsPanel` (modality on the seven background-result sites; Fork/New open handling) and `SchemaService.forkSchema`/`initSchema` (`--json`, path extraction, description/artifacts/`--no-default`).
- **Tests/fixtures:** new captures under `fixtures/cli/1.3.0/` and `fixtures/cli/1.14.3/` for `schema fork --json` and `schema init --json`; contract tests for path extraction; a headless modal-state test for the schema list; a guard against `invokeLater` without a modality in the settings panel.
- **Docs:** CHANGELOG `### Fixed` entries; `docs/feature-reference.md` if it describes the schema section's behaviour.
- **Platforms:** IntelliJ 2024.2+ compatibility unchanged. `ModalityState.stateForComponent` and `invokeLater(Runnable, ModalityState)` are stable APIs across the whole range.
