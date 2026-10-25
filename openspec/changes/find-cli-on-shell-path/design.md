## Context

`CliDetectionService` today does the following:
- **Before probing,** `resolveLoginShellPath()` runs `$SHELL -lc 'echo $PATH'` once and caches the result as `loginShellPath`.
- **Probing order:** the Settings path, then the bare `openspec`, then `tryLoginShellWhich()` (`$SHELL -lc 'which openspec'`), then the fixed locations.
- **Every probe and CLI run** goes through `applyLoginShellPath(cmd)`, which sets the child's PATH to `loginShellPath`, replacing whatever was there.

What the platform does, per a jetbrains-platform-guru review against the cached IC 2024.2.6, IC 2025.2.6.3, IU 2025.3.6.1 and IU 2026.2.3 jars:
- **`EnvironmentUtil`** loads the user's shell environment at startup with `$SHELL -l -i -c "'/usr/bin/env' '-0' > <tmp>"`. It leaves out `-l` for csh and tcsh and `-i` for fish, and sets `INTELLIJ_ENVIRONMENT_READER=true` and `DISABLE_AUTO_UPDATE=true`.
  - **Where:** on macOS by default. On Linux only with `-Dij.load.shell.env=true`. Never on Windows. It is skipped when `SHLVL>0`, i.e. a terminal launch, whose inherited env is already complete.
  - **Failure:** it falls back silently to `System.getenv()`.
  - **API:** `getValue`/`getEnvironmentMap` are public and not deprecated on 242–262. On 242 they block until loading finishes, so call them only off the EDT.
- **`GeneralCommandLine`'s default `ParentEnvironmentType.CONSOLE`** copies that environment into the child. But `setExePath("openspec")` is resolved by `ProcessBuilder` on the JVM's own PATH, not the child's.
- **`PathEnvironmentVariableUtil.findInPath(name)`** searches `EnvironmentUtil.getValue("PATH")`. It is public and not deprecated on 242–262. (Avoid `isOnPath`, which is 253+ only.)

## Goals / Non-Goals

**Goals:**
- Find a CLI whose PATH entry is only in an interactive rc file, with or without the IDE's own environment loading.
- Never make a CLI run's PATH worse than the IDE's.
- One shell call instead of two.

**Non-Goals:**
- Windows changes, including reading the registry user PATH.
- WSL and remote (Eel) environments.
- Editing the user's rc files.
- The two-copies warning, which has its own tracker entry.

## Decisions

1. **The IDE PATH lookup comes right after Settings, on macOS and Linux only.**
   - **Probe:** `PathEnvironmentVariableUtil.findInPath("openspec")`, through a `Function<String, File>` seam. A hit is probed by its absolute path.
   - **Not on Windows:** there `findInPath("openspec")` can match npm's extensionless sh shim, which can't run on Windows, and the existing `where.exe` and suffix fallback already cover it.
   - *Alternative:* drop our own shell call and rely on the platform alone. Rejected, because Linux doesn't load the shell env by default, and loading can time out and fall back.
2. **One interactive login shell call, marker-delimited.**
   - **Command:** `$SHELL <flags> -c 'printf "\n%s%s%s\n" "<START>" "$PATH" "<END>"'`, run with `<START>` = `__OPENSPEC_PATH_BEGIN__` and `<END>` = `__OPENSPEC_PATH_END__`.
   - **Flags:** `-l -i` by default; `-l` only for fish; `-i` only for csh and tcsh. The shell is chosen by the basename of `$SHELL`.
   - **Env:** `INTELLIJ_ENVIRONMENT_READER=true` and `DISABLE_AUTO_UPDATE=true`, and stdin closed.
   - **Parsing:** a pure `parseMarkedPath(stdout)` takes the text between the last START and the END that follows it, so a banner, or even a marker echoed earlier, can't confuse it. With no markers, the result is null and nothing is used.
   - **Search:** the plugin then walks that PATH for an executable `openspec`, using the existing file-check seam. This replaces the separate `which` process.
   - **Timeout:** the existing `TIMEOUT_MS` covers slow rc files.
3. **Merge, never replace.**
   - **Rule:** `applyLoginShellPath(cmd)` sets the child's PATH to the child's current PATH (from `EnvironmentUtil` via `GeneralCommandLine`) followed by every shell-PATH segment it doesn't already contain, in shell order.
   - **When nothing is missing:** the PATH is left exactly as it was.
   - **Seam:** a pure `mergePath(base, extra)` holds the rule, so it can be tested on its own.
4. **The order stays explicit and pinned by tests.** On macOS and Linux:
   1. the Settings path;
   2. the IDE PATH (`findInPath`);
   3. the bare name;
   4. a search of the shell PATH;
   5. the fixed locations (Homebrew, system, Linux Homebrew, the override, `~/.local/bin`).

   Windows keeps its order: Settings, bare name with suffixes, `where.exe`, the fixed shims and standalone paths.

## Risks / Trade-offs

- **An interactive shell can hang on a prompt** (a theme wizard, or a "trust this directory" question). → Stdin is closed, `TIMEOUT_MS` kills the process, and `DISABLE_AUTO_UPDATE` prevents oh-my-zsh's update prompt. On a timeout the shell lookup is skipped and the fixed locations still run.
- **rc files can be slow,** for example nvm's init. → This is one call per detection run, already throttled to once every 30 seconds. The IDE-PATH lookup usually answers first on macOS, so the shell call only runs when it misses.
- **`EnvironmentUtil.getValue` blocks until the startup load finishes** on 242. → Detection runs off the EDT, and the call is never made from `update()`.
- **The test environment can't simulate a Dock launch.** → The order and the parsing are unit-tested through seams. The parser is contract-tested against real `zsh -l -i -c` and `bash -l -i -c` output from a throwaway HOME with a noisy rc file. A manual check uses a Dock-launched sandbox.
