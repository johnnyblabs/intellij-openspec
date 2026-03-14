## Why

The plugin's init creates only the bare `openspec/` directory structure, but the CLI's `openspec init` also generates per-tool skills and commands (11 files each) that enable AI tools to work with OpenSpec. Users who have the CLI installed get no benefit from it during init — the plugin ignores it entirely. This means a JetBrains Marketplace reviewer or new user must separately run `openspec init` in terminal after the plugin wizard, which is a poor first experience.

Additionally, tool detection only covers 6 of the CLI's 24 supported tools, and the archive path is wrong (`openspec/archive/` vs `openspec/changes/archive/`).

## What Changes

- **CLI-delegated init**: When the CLI is detected, `ScaffoldingService.initOpenSpec()` delegates to `openspec init --tools <detected>` to get full skill/command generation. Falls back to built-in scaffolding when CLI is unavailable.
- Add 18 missing AI tools to `AiToolDetectionService` to match the CLI's 24-tool list
- Fix archive path to `openspec/changes/archive/` in built-in fallback
- Pass detected tool names to CLI using `--tools` flag for non-interactive execution

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `plugin-core`: Init delegates to CLI when available; archive path fix; 24-tool detection

## Impact

- `ScaffoldingService.java` — new `initWithCli()` method, archive path fix in fallback
- `AiToolDetectionService.java` — expanded tool maps (6 → 24 tools)
- `CliRunner.java` — no changes needed (already supports arbitrary args)
- `SetupWizardDialog.java`, `GettingStartedPanel.java`, `OpenSpecToolWindowPanel.java`, `OpenSpecInitAction.java` — no changes (all call `ScaffoldingService.initOpenSpec()` which handles delegation internally)
