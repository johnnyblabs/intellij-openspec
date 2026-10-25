# Proposal

## Why

On macOS and Linux, a CLI whose directory is on PATH only through an interactive rc file isn't found when the IDE is launched from the Dock or a desktop launcher. Version managers (nvm, fnm, volta, asdf) and the standalone installer's advice both usually go in `~/.zshrc`. Tracked by the linked tracker entry.

Three things combine:
- **The shell lookup isn't interactive.** It runs `$SHELL -lc 'which openspec'`, a login shell that isn't interactive. zsh doesn't read `~/.zshrc` there. Checked with a throwaway HOME: `-lc` reports "openspec not found", and `-ilc` finds it.
- **The bare-name probe can't use the IDE's PATH.** On macOS the IDE already loads the user's interactive shell environment at startup (`$SHELL -l -i -c 'env -0'`), and `PathEnvironmentVariableUtil.findInPath` searches it. But the plugin never asks it: a bare `openspec` is looked up on the JVM's own PATH, which for a Dock launch is launchd's minimal one.
- **Every CLI run loses PATH entries.** `applyLoginShellPath` *replaces* each CLI process's PATH with the `-lc` value. That throws away the `.zshrc` entries the IDE had already put there, so a found CLI can still fail to start when its `#!/usr/bin/env node` can't find `node`.

## What Changes

- **Look on the IDE's PATH first (macOS and Linux).** After the Settings path, the plugin checks `PathEnvironmentVariableUtil.findInPath("openspec")` and runs the absolute path it returns. On macOS that covers `.zshrc`-only entries whenever the IDE loaded the shell environment.
- **Make the shell lookup interactive.** One shell call replaces the `which` and `echo $PATH` calls:
  - it runs `$SHELL -l -i -c` and prints PATH between unique markers, so rc-file output can't corrupt it;
  - it leaves out `-i` for fish and `-l` for csh and tcsh, as the platform does;
  - it sets `INTELLIJ_ENVIRONMENT_READER=true` and `DISABLE_AUTO_UPDATE=true`, which rc files and oh-my-zsh honour;
  - the plugin then searches that PATH for an executable `openspec` itself.
- **Merge PATH instead of replacing it.** CLI processes keep the IDE's PATH and append only the shell-PATH entries it lacks.
- **Unchanged:**
  - the Settings path, the bare-name probe (kept, for terminal launches and Windows), the fixed install locations and their order;
  - all Windows behaviour (`where.exe`, the suffix fallback). Windows has no shell environment to load.
- **Off-model:** the plugin never edits the user's rc files.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: "Cross-platform CLI detection" adds the IDE-PATH lookup, the interactive login-shell lookup with marker parsing and per-shell flags, and the PATH merge for CLI runs. The macOS and Linux order changes to Settings, IDE PATH, bare name, shell PATH, then fixed locations.

## Impact

- **Code:** `CliDetectionService`:
  - the detection order;
  - `resolveLoginShellPath` and `tryLoginShellWhich`, merged into one marker-based shell call;
  - `applyLoginShellPath`, which now merges;
  - new seams for the IDE PATH lookup and the platform PATH value.

  `CliRunner` already calls `applyLoginShellPath`.
- **Fixtures:** captured stdout of the real `zsh -l -i -c` marker command, from a throwaway HOME whose `.zshrc` adds a PATH entry and prints a banner, plus the equivalent for bash. Stored under `src/test/resources/fixtures/shell/`.
- **Tests:** `CliDetectionServiceTest` through the `ProcessRunner` seam and the new lookup seams:
  - the order;
  - the marker parse against the captured output, ignoring the banner;
  - the shell flags per shell;
  - PATH merging;
  - Windows untouched.
- **Platform:** new references to `PathEnvironmentVariableUtil.findInPath` and `EnvironmentUtil.getValue`. Both are public and not deprecated from 2024.2 through 2026.2; this was checked against the cached platform jars. `verifyPlugin` must stay green. The plugin still supports IntelliJ IDEA 2024.2 and later.
- **Docs:** the troubleshooting and Settings sections in the feature reference and the getting-started guide, the internal wiki's detection order, and a CHANGELOG `## Unreleased` Fixed entry.
