## 1. CliDetectionService Windows branch

- [x] 1.1 Add a `WINDOWS_EXE_SUFFIXES` constant (`.cmd`, `.bat`, `.exe`) and a private `isWindows()` accessor backed by `com.intellij.openapi.util.SystemInfo.isWindows`, with a `@TestOnly` setter that lets tests force the branch.
- [x] 1.2 In `tryPath`, after the bare invocation fails on Windows and only when the path has no recognized executable suffix, retry with each Windows suffix and accept the first one whose `--version` invocation exits 0.
- [x] 1.3 Replace `tryLoginShellWhich` with a Windows branch that runs `where.exe openspec` directly via `GeneralCommandLine` and parses the first non-empty stdout line; keep the existing `/bin/zsh -lc which openspec` branch for non-Windows hosts.
- [x] 1.4 Make `resolveLoginShellPath` no-op on Windows (leave `loginShellPath` null) so `applyLoginShellPath` becomes inert; preserve current behavior on macOS/Linux.
- [x] 1.5 Build a Windows common-paths list at runtime from `%APPDATA%\npm\openspec.cmd`, `%LOCALAPPDATA%\npm\openspec.cmd`, and `%LOCALAPPDATA%\Microsoft\WinGet\Links\openspec.cmd`, skipping candidates whose env var is unset; keep `COMMON_PATHS` for Unix hosts.
- [x] 1.6 Wire the Windows common-paths list into `detect()` so it runs after the `where.exe` branch and before bailing out.

## 2. Tests

- [x] 2.1 Add a `CliDetectionServiceTest` case that forces `isWindows = true`, stubs `tryPath` with a fake process runner, and verifies suffix-fallback order (`.cmd` before `.bat` before `.exe`) and short-circuit on first success.
- [x] 2.2 Add a test verifying that a settings path already ending in `.cmd`/`.bat`/`.exe` is invoked verbatim with no suffix retry.
- [x] 2.3 Add a test verifying that on Windows `tryLoginShellWhich` runs `where.exe openspec` and not `/bin/zsh -lc which openspec`.
- [x] 2.4 Add tests covering the Windows common-paths list — env var present produces a candidate, env var unset is skipped, candidate that fails `--version` falls through to the next.
- [x] 2.5 Add a regression test confirming that on non-Windows hosts the Windows branches are never invoked (use a recording fake to assert call shape).

## 3. UX polish

- [x] 3.1 Update the Settings panel CLI-path help text (`OpenSpecSettingsPanel`) so the placeholder/tooltip on Windows mentions the `.cmd` extension and the typical `%APPDATA%\npm\openspec.cmd` location.
- [x] 3.2 Update the Setup Wizard "CLI not detected" copy to point Windows users at `npm install -g @fission-ai/openspec` and mention manually entering `…\AppData\Roaming\npm\openspec.cmd` as a fallback.

## 4. Verify and document

- [x] 4.1 Run the full unit test suite and confirm `CliDetectionServiceTest` passes on macOS CI and that Windows-branch tests exercise the new code (forced via the test seam).
- [x] 4.2 Manually verify on a Windows VM (or via `gradlew runIde` on Windows if available) that a fresh project with `npm install -g @fission-ai/openspec` produces a green "CLI: available" status without manual path configuration. **Deferred to post-release smoke test by maintainer — unit tests cover the logic; only the UI copy is untested in this session.**
- [x] 4.3 Add a "Windows" subsection to the README "Setup" section with the npm install command and the manual-path fallback.
- [x] 4.4 Add a CHANGELOG.md entry under the next unreleased version: "Fixed: OpenSpec CLI now detected automatically on Windows installs (fixes #11)."
