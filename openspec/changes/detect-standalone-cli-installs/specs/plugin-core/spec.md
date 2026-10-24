## MODIFIED Requirements

### Requirement: Cross-platform CLI detection

`CliDetectionService` SHALL detect the OpenSpec CLI on Windows under the same scenarios that succeed on macOS and Linux. On Windows the service SHALL extend bare candidate paths through `.cmd`, `.bat`, and `.exe` suffixes when direct invocation fails, SHALL resolve the executable via `where.exe` instead of a POSIX login shell, and SHALL probe Windows-specific install locations derived from `%APPDATA%` and `%LOCALAPPDATA%`. On every OS, after the existing fixed install locations, the service SHALL probe Homebrew's Linux prefix (`/home/linuxbrew/.linuxbrew/bin/openspec`), then the location the upstream standalone-binary installer writes to: the installer's `OPENSPEC_INSTALL_DIR` and `OPENSPEC_INSTALL_NAME` overrides when the IDE's environment carries them, then the installer's default location. A fixed install-location candidate SHALL be probed only when a regular, executable file exists at it (on Windows, a regular file). The settings path, the bare name and the shell-resolved path SHALL be probed as before, without that check. The plugin SHALL NOT run an installer, edit PATH, or remove another install.

#### Scenario: Windows bare-name resolution falls back through executable suffixes
- **WHEN** the host OS is Windows and `tryPath("openspec")` is called and bare invocation fails
- **THEN** the service SHALL retry the candidate with `.cmd`, `.bat`, and `.exe` suffixes in that order, accepting the first variant whose `--version` invocation exits 0

#### Scenario: Windows extension fallback only applies when no extension is present
- **WHEN** the host OS is Windows and the candidate path already ends in `.cmd`, `.bat`, or `.exe`
- **THEN** the service SHALL invoke that exact path without appending further suffixes

#### Scenario: Windows shell PATH is resolved via where.exe
- **WHEN** the host OS is Windows and the bare-name and settings-path branches have not produced a hit
- **THEN** the service SHALL invoke `where.exe openspec` (not `/bin/zsh -lc`) and use the first non-empty stdout line as the next candidate path

#### Scenario: Windows common npm paths are probed
- **WHEN** the host OS is Windows and prior detection branches have not produced a hit
- **THEN** the service SHALL try, in order, `%APPDATA%\npm\openspec.cmd`, `%LOCALAPPDATA%\npm\openspec.cmd`, and `%LOCALAPPDATA%\Microsoft\WinGet\Links\openspec.cmd`, skipping any candidate whose environment variable is unset

#### Scenario: User-supplied path with extension works as-is
- **WHEN** the host OS is Windows and the user has set the Settings CLI path to a path ending in `.cmd`, `.bat`, or `.exe`
- **THEN** the service SHALL invoke that path verbatim and SHALL NOT attempt suffix variants

#### Scenario: User-supplied bare path is extended on Windows
- **WHEN** the host OS is Windows and the user has set the Settings CLI path to a value with no extension
- **THEN** the service SHALL apply the same `.cmd` / `.bat` / `.exe` fallback as for auto-detected bare names

#### Scenario: macOS and Linux behavior unchanged
- **WHEN** the host OS is macOS or Linux
- **THEN** detection SHALL execute the existing branches in their existing order (bare name → `/bin/zsh -lc which openspec` → `/opt/homebrew/bin`, `/usr/local/bin`, `/usr/bin`), followed only by Homebrew on Linux (`/home/linuxbrew/.linuxbrew/bin`) and then the standalone-binary candidates, without invoking any Windows-specific code path

#### Scenario: Windows detection completes within the existing timeout
- **WHEN** detection runs on Windows and traverses the suffix-fallback, `where.exe`, and common-paths branches
- **THEN** the total wall-clock time SHALL be bounded by the existing `TIMEOUT_MS` budget per `runAndCapture` invocation, with no additional global wait introduced

#### Scenario: Standalone binary in its default Unix location
- **WHEN** the host OS is macOS or Linux, the standalone binary is at `~/.local/bin/openspec`, and no earlier branch finds the CLI
- **THEN** detection SHALL find the CLI at `~/.local/bin/openspec` and report the version it prints

#### Scenario: Standalone binary in its default Windows location
- **WHEN** the host OS is Windows, the standalone binary is at `%LOCALAPPDATA%\Programs\openspec\openspec.exe`, and no earlier branch finds the CLI
- **THEN** detection SHALL find the CLI at that path, after the npm and winget candidates and skipping it when `%LOCALAPPDATA%` is unset

#### Scenario: Homebrew formula on Linux
- **WHEN** the host OS is Linux, OpenSpec was installed with the Homebrew formula at `/home/linuxbrew/.linuxbrew/bin/openspec`, and no earlier branch finds the CLI
- **THEN** detection SHALL find the CLI there, before the standalone-binary candidates

#### Scenario: Installer overrides in the IDE's environment
- **WHEN** the IDE's environment sets `OPENSPEC_INSTALL_DIR`, optionally with `OPENSPEC_INSTALL_NAME`
- **THEN** detection SHALL probe that directory joined with that name, or with `openspec` on Unix and `openspec.exe` on Windows when the name is unset, before the default standalone location
- **AND** an unset or empty `OPENSPEC_INSTALL_DIR` SHALL add no candidate

#### Scenario: A missing fixed location is not run
- **WHEN** detection reaches a fixed install-location candidate that has no regular, executable file
- **THEN** detection SHALL skip it without starting a process and SHALL go on to the next candidate

#### Scenario: Standalone version output
- **WHEN** the standalone binary's `--version` prints a bare version such as `1.15.0`
- **THEN** the detected version SHALL be `1.15.0`
