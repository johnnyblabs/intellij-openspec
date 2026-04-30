## Why

GitHub issue [#11](https://github.com/johnnyblabs/intellij-openspec/issues/11) reports that on Windows the Setup Wizard, Settings panel, and "Detect" button all report "OpenSpec CLI not detected" even when `openspec` runs successfully from PowerShell or `cmd`. `CliDetectionService` was written exclusively for macOS/Linux: bare-name invocation cannot find `openspec.cmd` (Java's `ProcessBuilder` does not auto-append Windows extensions), shell-PATH resolution is hard-coded to `/bin/zsh -lc`, and the common-paths list contains only Unix prefixes (`/opt/homebrew/bin`, `/usr/local/bin`, `/usr/bin`). The result is that Windows users cannot use any CLI-backed feature out of the box and have no obvious manual workaround — the file path the user *would* need to enter (`%APPDATA%\npm\openspec.cmd`) is not discoverable from any of the in-app messaging.

## What Changes

- `CliDetectionService.tryPath` SHALL, on Windows, attempt the candidate path with `.cmd`, `.bat`, and `.exe` suffixes when the bare candidate fails.
- `CliDetectionService.resolveLoginShellPath` and `tryLoginShellWhich` SHALL detect the host OS and, on Windows, use `where.exe openspec` via `cmd.exe /c` instead of `/bin/zsh -lc`. The Unix path is unchanged.
- The common-paths list SHALL include Windows npm install locations resolved from `%APPDATA%` and `%LOCALAPPDATA%` (e.g. `%APPDATA%\npm\openspec.cmd`, `%LOCALAPPDATA%\npm\openspec.cmd`).
- The user-supplied path field in Settings SHALL accept either a full path with extension (e.g. `C:\Users\me\AppData\Roaming\npm\openspec.cmd`) or a bare path the plugin extends with `.cmd`/`.bat`/`.exe`, matching the same resolution rules used during automatic detection.
- No behavior change on macOS or Linux.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: The "CLI execution threading compliance" and project-detection requirements are not changing, but a new requirement is added covering cross-platform CLI detection so that detection succeeds on Windows under the same scenarios that work on macOS/Linux.

## Impact

- Code: `src/main/java/com/johnnyblabs/openspec/services/CliDetectionService.java` (primary). No public-API changes; method signatures stable.
- Tests: `src/test/java/com/johnnyblabs/openspec/services/CliDetectionServiceTest.java` gains Windows-branch coverage (mocked OS detection). New tests verify suffix-fallback ordering and `where.exe` invocation.
- Settings UI: no schema change. Settings panel help text gets a small Windows hint.
- Docs: README "Setup" section gets a Windows note.
- Risk: low. All changes are additive within an `if (Windows)` branch; the macOS/Linux code path is unchanged.

## References

- GitHub: johnnyblabs/intellij-openspec#11
- Forgejo: johnb/intellij-openspec#191
- Plane: openspec/issue/211 (`abebfb96-137e-4b14-bff9-89fa79b5a5b5`)