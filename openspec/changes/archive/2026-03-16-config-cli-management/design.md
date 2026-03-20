## Context

The plugin's `OpenSpecSettings` already persists a `profile` field and the `OpenSpecSettingsPanel` exposes it as an editable combo box. However, the profile field is currently just a text string stored locally -- it has no connection to the CLI's `openspec config profile` command and does not reflect the actual active workflows under that profile. The Settings panel also lacks any display of workflow toggles or profile descriptions.

Separately, the `openspec update` CLI command refreshes agent instruction files (`.claude/`, `.github/`, etc.) but has no IDE integration. Users must open a terminal to run it.

The existing action infrastructure provides two base classes: `OpenSpecBaseAction` for all actions (with `update()` for enable/disable logic) and `OpenSpecCliAction` for read-only CLI-delegated actions (with console output routing, progress indicators, and error handling). The `CliDetectionService` provides `isAvailable()` for guarding CLI-dependent features.

## Goals / Non-Goals

**Goals:**
- Display the active config profile and its workflow toggles in the Settings panel
- Allow switching profiles via `openspec config profile <name>` CLI delegation
- Provide an `OpenSpec.Update` action that runs `openspec update` with console output
- Disable the Update action when CLI is not detected, with an explanatory tooltip
- Cover all new logic with unit tests

**Non-Goals:**
- Editing profile definitions (workflow sets) from the IDE -- profiles are defined by the CLI
- Creating custom profiles from within the plugin -- use CLI directly
- Auto-running `openspec update` on plugin startup or after version changes
- Modifying the existing Settings panel layout beyond adding the profile section

## Decisions

### Decision 1: Config profile section as a read-display panel in Settings

Add a "Config Profile" bordered section to `OpenSpecSettingsPanel` that shows the current profile name and a list of active workflow toggles (read-only checkboxes or labels). The profile combo box already exists in the General section; it will remain there as the selector, but the new section provides richer context.

**Why over alternative (separate dialog):** Profile information is a configuration concern. Keeping it in Settings maintains the existing UX pattern. A separate dialog would add navigation overhead for a rarely-changed setting.

**Implementation:**
- Add a `buildConfigProfileSection()` method to `OpenSpecSettingsPanel`
- On panel creation and profile change, run `openspec config profile --json` via `CliRunner` on a pooled thread
- Parse the JSON response to extract profile name, description, and active workflows
- Display as a titled bordered panel below the General section
- If CLI is unavailable, show "CLI required for profile details" with the current settings value

### Decision 2: Profile switch delegates to CLI then updates local state

When the user selects a different profile in the combo box and clicks Apply, the plugin runs `openspec config profile <name>` via `CliRunner` before persisting to `OpenSpecSettings`. This ensures the CLI's config.yaml is the source of truth.

**Why over alternative (local-only update):** The profile affects CLI behavior across all tools (Claude Code, Cursor, etc.). Changing it only in the plugin's local state would create a split-brain scenario where the IDE thinks one profile is active but the CLI operates on another.

**Implementation:**
- In `OpenSpecConfigurable.apply()`, detect when the profile field has changed
- Before persisting, run `openspec config profile <newProfile>` via `CliRunner`
- If the CLI call fails (CLI unavailable or invalid profile), show a warning notification and keep the previous value
- If successful, persist the new profile to `OpenSpecSettings.State`

### Decision 3: OpenSpecUpdateAction extends OpenSpecCliAction

The Update action is a pure CLI delegation with console output -- exactly the pattern `OpenSpecCliAction` was built for. It runs `openspec update`, streams output to the console, and refreshes the tool window on success.

**Why over alternative (custom action from scratch):** `OpenSpecCliAction` already handles CLI availability checking, background execution, console output routing, and error handling. Reusing it avoids duplicating ~50 lines of boilerplate.

**Implementation:**
- `OpenSpecUpdateAction extends OpenSpecCliAction`
- `getCliArgs()` returns `new String[]{"update"}`
- `getCommandLabel()` returns `"update"`
- Override `update()` to disable when CLI is not detected: check `CliDetectionService.isAvailable()` and set presentation enabled/disabled with tooltip
- Register as `OpenSpec.Update` in `plugin.xml` main menu group after the Refresh action

### Decision 4: CLI guard via update() presentation logic

The Update action uses `AnActionEvent.getPresentation().setEnabled(false)` with `setDescription()` for the tooltip when the CLI is not available. This follows the IntelliJ Platform convention for conditionally-available actions.

**Why over alternative (hiding the action entirely):** A disabled action with a tooltip is more discoverable than a hidden one. Users can see the feature exists and understand what they need to do (install the CLI) to enable it.

**Implementation:**
- Override `update(AnActionEvent e)` in `OpenSpecUpdateAction`
- Call super to check for OpenSpec project
- Additionally check `CliDetectionService.isAvailable()`
- If unavailable: `e.getPresentation().setEnabled(false)` and set description to "Install OpenSpec CLI to use this action"

## Risks / Trade-offs

- **[Risk] `openspec config profile --json` may not exist in older CLI versions** --> Mitigate by catching CLI errors gracefully and falling back to displaying the locally-stored profile name without workflow details.
- **[Risk] Profile switch via CLI could fail silently** --> Mitigate by checking the `CliResult.exitCode()` and showing the stderr output in a notification on failure.
- **[Trade-off] Workflow toggles are read-only** --> Users cannot toggle individual workflows from the IDE. This is intentional: the CLI manages profile definitions. The IDE shows the current state for awareness.
- **[Trade-off] Profile section requires CLI for full display** --> Without the CLI, users only see the stored profile name. This is acceptable since profile management is inherently a CLI feature.
