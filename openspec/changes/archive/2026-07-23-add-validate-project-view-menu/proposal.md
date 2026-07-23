## Why

The Validate action lives only in the OpenSpec tool-window/menu today. When a user is working in the standard Project tree and right-clicks a spec or a change's file, there's no way to validate it in place — they must leave for the tool window and run a whole-project validate. Surfacing Validate in the **Project View context menu**, **scoped to the change or spec that was clicked**, meets the user where they are and gives a targeted result instead of an all-or-nothing project scan.

## What Changes

- Add a **"Validate OpenSpec" entry to the Project View right-click menu**, shown only when the selection is under an `openspec/` directory.
- **Scope validation to the clicked item** by mapping the file up to its owning directory: a file under `openspec/specs/<cap>/` validates that **spec**; a file under `openspec/changes/<name>/` (active, non-archived) validates that **change**; a file under `openspec/changes/archive/**`, `openspec/config.yaml`, or the `openspec/` root falls back to the **whole-project** validate (those are not single-validatable items upstream).
- Results run through the existing validate pipeline (built-in validator always + CLI enhancement when available), scoped to the resolved target, reported to the existing console — no per-file "valid/invalid" verdict is invented.
- The action is `verifyPlugin`-gated (new action registration + platform data-key references).

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `validation`: Adds a requirement that the Validate operation is invokable from the Project View context menu, gated to `openspec/` selections and **scoped to the clicked change or spec** (with the archived/config/root fallback to whole-project), reusing the existing built-in-plus-CLI validate pipeline.

## Impact

- **Affected code:** a new `OpenSpecValidateFromProjectViewAction` (subclasses the existing `OpenSpecValidateAction` to inherit its off-EDT background validation) with a selection-gated `update()` on `ActionUpdateThread.BGT`; `plugin.xml` gains the action + `<add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>`. The existing `OpenSpecValidateAction`'s background body is factored into a `protected` method taking an optional target so both the whole-project and scoped invocations share the built-in+CLI merge logic. Reuses `OpenSpecFileUtil.isUnderOpenSpec` for the gate and `BuiltInValidator.validateChange`/spec-level methods for scoped built-in validation.
- **CLI:** scoped validate uses `openspec validate <id> --type change|spec --json` (the `--type` is derived from the path, avoiding the ambiguous-name branch). The single-item `--json` envelope differs from the bulk shape, so a captured single-item fixture + contract test is required.
- **On-model:** single-target validate is a first-class CLI operation (change ID / spec ID, never a file path); archived changes and non-item files (`config.yaml`, root `.md`) degrade to `--all` rather than fabricating a per-file or per-archived-change verdict.
- **Platform:** all APIs (`ProjectViewPopupMenu`, `ActionUpdateThread.BGT`, `setEnabledAndVisible`, `VIRTUAL_FILE_ARRAY`) are present on 2024.2 and already used elsewhere in the plugin; `anchor="last"` (not a relative anchor) for multi-IDE safety. Min-platform unchanged.
- **User-visible:** a "Validate OpenSpec" item appears when right-clicking `openspec/` files in the Project tree, validating the relevant change/spec.
- **Tracker:** linked to an existing tracker issue via the gitignored `.tracking.yaml` sidecar.
