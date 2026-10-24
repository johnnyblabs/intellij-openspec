# Proposal

## Why

OpenSpec CLI 1.15.0 ships a standalone binary that needs no Node.js. Its installers put it where the plugin never looks, so a user who installs it the recommended way sees "OpenSpec CLI not found". Tracked by the linked tracker entry.

- **macOS and Linux:** `install.sh` writes `~/.local/bin/openspec` and leaves PATH alone; it only prints "Add `<dir>` to your PATH". A GUI-launched IDE doesn't inherit the terminal PATH. The login-shell lookup (`$SHELL -lc 'which openspec'`) doesn't read `.zshrc`, which is where macOS users usually add that line. None of the fixed fallbacks (`/opt/homebrew/bin`, `/usr/local/bin`, `/usr/bin`) is `~/.local/bin`, and none is the Homebrew formula's Linux prefix.
- **Windows:** `install.ps1` writes `%LOCALAPPDATA%\Programs\openspec\openspec.exe` and adds that directory to the user PATH. Only processes started afterwards see that PATH, so an IDE that was already running misses it. The Windows fallbacks cover only the npm and winget shims.
- **Overrides:** both installers honour `OPENSPEC_INSTALL_DIR` and `OPENSPEC_INSTALL_NAME`.

## What Changes

- **Probe the standalone install locations.** These are added after the existing fixed locations, so a current install is still found first and in the same order:
  - Unix: `~/.local/bin/openspec`.
  - Windows: `%LOCALAPPDATA%\Programs\openspec\openspec.exe`.
- **Probe Homebrew on Linux.** OpenSpec's official Homebrew formula installs to `$(brew --prefix)/bin`. That is already covered on macOS (`/opt/homebrew/bin`, `/usr/local/bin`), but not on Linux, where it is `/home/linuxbrew/.linuxbrew/bin/openspec`. This location joins the fixed list right after `/usr/bin`.
- **Honour the installer overrides** when the IDE's environment carries them:
  - `OPENSPEC_INSTALL_DIR` combined with `OPENSPEC_INSTALL_NAME`, which defaults to `openspec` on Unix and `openspec.exe` on Windows.
  - This candidate is probed before the default standalone location.
- **Skip fixed-location candidates that don't exist.** A fixed install location, whether an existing one or a new one, is probed only when a regular, executable file is there. Detection no longer starts a process for each missing file, so the longer list costs nothing on a machine without those installs.
- **No change** to the settings path, the bare-name probe, the login-shell or `where.exe` lookup, or the version parse. The standalone binary's bare `1.15.0` output already parses.
- **Docs:** the troubleshooting and Settings sections, plus the README's install note, name the standalone locations.
- **Off-model:** the plugin never runs an installer, edits PATH, or removes an npm install.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: "Cross-platform CLI detection" probes the standalone install locations and the installer overrides on every OS. It no longer says the macOS and Linux paths are unchanged.

## Impact

- **Code:** `CliDetectionService` gets the Unix common-path list as a function of the environment and home directory, mirroring `windowsCommonPaths(envLookup)`, and adds the standalone and override candidates to both lists.
- **Tests:** `CliDetectionServiceTest`:
  - path-list tests through the env and home seams;
  - a `detect()` test through the `ProcessRunner` seam, showing that the standalone path is found when every earlier branch misses;
  - the version is parsed from the captured standalone `version.txt` fixture.
- **Docs:** `docs/feature-reference.md`, `docs/getting-started-copilot.md`, `README.md`, and a `CHANGELOG.md` `## Unreleased` Fixed entry.
- **Cost:** with the existence check, a missing candidate costs a file-system check instead of a process spawn. That makes detection with no CLI installed cheaper than it is today.
- **Out of scope:** the login-shell lookup's blind spot for `.zshrc`, and reusing the IDE's own shell environment and PATH search. A separate tracker entry covers both.
- **Platform:** no new platform API. The plugin still supports IntelliJ IDEA 2024.2 and later. Windows behaviour is covered by unit tests only, because there's no Windows host. The GitHub mirror's Windows CI leg runs the unit tests.
