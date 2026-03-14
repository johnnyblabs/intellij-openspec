## Context

The plugin has 4 call sites that invoke `ScaffoldingService.initOpenSpec()` (wizard, getting started panel, tree panel, menu action). Currently this method creates directories and a config.yaml directly via VFS. The CLI's `openspec init --tools <list>` does the same plus generates 11 skill files and 11 command files per selected tool — the main value of init.

The plugin already has `CliRunner` and `CliDetectionService` for running CLI commands. The pattern of "try CLI, fall back to built-in" is already used by validation.

## Goals / Non-Goals

**Goals:**
- Delegate init to CLI when available, getting full skill/command generation
- Keep built-in fallback for CLI-absent environments
- Match CLI's 24-tool detection list
- Fix archive path in built-in fallback
- Non-interactive CLI execution (no user prompts from CLI)

**Non-Goals:**
- Adding a tool selection UI to the wizard (the CLI handles tool selection via `--tools` flag with detected tools)
- Generating skills/commands from Java code (CLI's responsibility)
- Running `openspec init` interactively through the plugin

## Decisions

### Delegate init inside `ScaffoldingService.initOpenSpec()`

The method will check `CliDetectionService.isAvailable()`. If true, run `openspec init --tools <comma-separated-detected-tools>` via `CliRunner`. If the CLI call fails or CLI is unavailable, fall back to the existing built-in directory creation.

This keeps all 4 call sites unchanged — they don't need to know about CLI delegation.

**Alternative considered**: Adding a separate `initWithCli()` method — rejected because callers shouldn't need to choose. The service should pick the best strategy automatically.

### Map detected tool display names to CLI tool IDs

The CLI uses kebab-case IDs (`github-copilot`, `roocode`, `amazon-q`) while the plugin uses display names (`GitHub Copilot`, `Roo Code`, `Amazon Q`). Add a `CLI_TOOL_IDS` map in `AiToolDetectionService` to translate display names to CLI IDs for the `--tools` flag.

### Use `Map.ofEntries()` for larger maps

`Map.of()` only supports up to 10 entries. With 24 tools, use `Map.ofEntries(Map.entry(...))` for `TOOL_TYPES` and `TOOL_GUIDANCE`.

### VFS refresh after CLI init

The CLI writes files via Node.js (outside VFS). After `CliRunner.run()` completes, call `VfsUtil.markDirtyAndRefresh()` on the project base directory so IntelliJ picks up the new files immediately.

## Risks / Trade-offs

- **[Medium] CLI init may create files the plugin doesn't expect** → Low concern since all created files are under well-known tool directories (`.claude/`, `.github/`, etc.) and `openspec/`. No plugin behavior depends on these files being absent.
- **[Low] CLI version differences** → The `--tools` flag and non-interactive mode have been stable since CLI v1.0. If a future CLI changes the flag, the fallback ensures init still works.
- **[Low] CLI init is slower than built-in** → CLI spawns a Node.js process and does more work. Acceptable since init is a one-time operation and the extra time generates valuable files.
