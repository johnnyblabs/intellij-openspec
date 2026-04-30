## Context

`CliDetectionService` (`src/main/java/com/johnnyblabs/openspec/services/CliDetectionService.java`) was written for macOS/Linux. On Windows the service produces a false negative even when the CLI is installed and on `PATH`:

1. `tryPath("openspec")` calls `new GeneralCommandLine(path, "--version")`. Java's `ProcessBuilder` does not consult `PATHEXT` when launching processes — invoking the literal name `openspec` fails to find `openspec.cmd`, which is the real npm-shim on Windows.
2. `resolveLoginShellPath` and `tryLoginShellWhich` are hard-coded to `/bin/zsh -lc` (with a fallback also pointing at `zsh`). Neither shell exists on a default Windows install.
3. `COMMON_PATHS` lists `/opt/homebrew/bin`, `/usr/local/bin`, `/usr/bin` — none of which exist on Windows.
4. The Settings panel accepts a manual path, but no in-app messaging tells a Windows user that the path needs to end in `.cmd`.

The fix must add a Windows code path to each of those four points without changing macOS/Linux behavior. There is no architectural restructuring — the service stays a project-level service with the same public methods (`detect`, `detectIfStale`, `isAvailable`, `getDetectedPath`, `getDetectedVersion`, `getLoginShellPath`, `applyLoginShellPath`).

## Goals / Non-Goals

**Goals:**
- Auto-detection succeeds on Windows when `openspec` is installed via npm global, scoop, or any installer that puts a launcher (`openspec.cmd`/`.bat`/`.exe`) on `PATH`.
- A user-supplied Settings path works whether the user types `openspec`, `C:\path\openspec`, or `C:\path\openspec.cmd`.
- macOS/Linux paths remain bit-for-bit unchanged.
- Existing public API of `CliDetectionService` stays the same — no new dependencies on the service from callers.

**Non-Goals:**
- Bundling or auto-installing the CLI on Windows (out of scope; would warrant its own change).
- Supporting non-launcher Windows installs (e.g. `node openspec.js` invocations). Windows users without a launcher must point Settings at a `.cmd`/`.bat`/`.exe`.
- Refactoring `CliRunner` to mirror these changes. `CliRunner` only invokes the path that detection already resolved, so once detection returns a working absolute path with extension, downstream invocations work without code change. We will verify this by integration test rather than by code change.
- Localizing or changing the Settings panel UI.

## Decisions

### Decision 1: Use `SystemInfo.isWindows` for OS detection

**Choice:** Use `com.intellij.openapi.util.SystemInfo.isWindows` (already a Platform dependency).
**Alternatives considered:**
- `System.getProperty("os.name").toLowerCase().contains("win")` — works but reinvents what the platform already provides; harder to mock in tests.
- A custom `OsType` enum — overkill for one branch.

`SystemInfo` is the IntelliJ-canonical answer and is what the rest of our codebase already uses for platform checks.

### Decision 2: Extension fallback inside `tryPath`, not at call sites

**Choice:** Make `tryPath` itself responsible for trying `.cmd`, `.bat`, `.exe` suffixes on Windows when the bare path fails.

```
tryPath(p):
  if try(p) succeeds: return true
  if isWindows && p has no extension:
    for ext in [.cmd, .bat, .exe]:
      if try(p + ext) succeeds: return true
  return false
```

**Alternatives considered:**
- Have each caller (`detect()`, settings-path, login-shell-which) do its own extension expansion. Rejected: four duplicate copies of the same loop, easy to drift out of sync, and `detect()` is already long.
- Pre-compute a candidate list and iterate. Equivalent in behavior; the inline approach reads more naturally and short-circuits cleanly.

The "no extension" guard prevents pathological double-extension (`openspec.cmd.cmd`) when a user pastes a fully-qualified path.

### Decision 3: Use `where.exe` directly, not `cmd.exe /c where`

**Choice:** On Windows, run `where.exe openspec` directly via `GeneralCommandLine("where.exe", "openspec")`. No `cmd.exe /c` wrapper.

**Alternatives considered:**
- `cmd.exe /c where openspec` — adds a layer of shell parsing for no benefit. `where.exe` is a real PE binary at `%SystemRoot%\System32\where.exe`; `ProcessBuilder` can launch it directly.
- `powershell -Command Get-Command openspec` — slower (PowerShell startup cost), output-format mismatch with how we parse stdout on Unix.

`where.exe` may print multiple matches (one per line). The detection service already calls `runAndCapture(...)` which trims output; we will take the first non-empty line.

### Decision 4: Resolve Windows common paths from environment, not hard-coded literals

**Choice:** Build the Windows common-path list at runtime from `%APPDATA%` and `%LOCALAPPDATA%`:

```
%APPDATA%\npm\openspec.cmd            // npm default on Windows
%LOCALAPPDATA%\npm\openspec.cmd       // npm with --prefix=$LOCALAPPDATA
%LOCALAPPDATA%\Microsoft\WinGet\Links\openspec.cmd  // winget shim
```

**Alternatives considered:**
- Hard-coded `C:\Users\<user>\AppData\Roaming\npm\openspec.cmd` — wrong for non-default user profile dirs and non-default drive letters.
- Probe the registry for the npm install location — heavy, requires JNA, and 95% of users use the default.

Falling back through env-derived paths handles the common case and degrades gracefully when the env var is missing (the candidate is simply skipped).

### Decision 5: Skip Windows-style PATH propagation

On Unix, `applyLoginShellPath` overwrites `PATH` for downstream commands so that scripts with `#!/usr/bin/env node` shebangs can find `node`. On Windows this is unnecessary — GUI apps inherit the user's `PATH` correctly, and `.cmd` shims don't use shebangs. So `resolveLoginShellPath` will simply no-op on Windows; `applyLoginShellPath` will continue to be called but does nothing when `loginShellPath` is null.

## Risks / Trade-offs

- **Risk:** A user installs `openspec` somewhere off the npm/winget paths and then uninstalls and reinstalls, leaving a stale `.cmd` on `PATH`. → Mitigation: `tryPath` runs `--version` before considering the candidate valid; a broken shim will fail the version check and we fall through.
- **Risk:** `where.exe openspec` returns multiple matches in different drives, and the first one is broken. → Mitigation: same as above — `--version` gates acceptance. If the first fails we currently do not iterate to the next; the bare-name + extension fallback below handles most of these cases. Iterating across multiple `where` results is a possible future enhancement but not needed to fix the reported issue.
- **Risk:** Windows tests run on a non-Windows CI machine and falsely pass because the Windows branch is dead. → Mitigation: tests use a small `OsTypeProvider` seam (or `SystemInfo` mocked via `@TestOnly` setter) so the Windows branch is exercised on macOS/Linux CI.
- **Trade-off:** We do not add registry-based npm-prefix detection. A user with a non-standard `npm config set prefix` on Windows will need to set the path manually. We accept this as the long-tail case; the npm/scoop/winget defaults cover the issue reporter and the >90% mainstream install.

## Migration Plan

- No data migration. No persisted-state changes. No public API changes.
- Rollout: ship in the next patch release of the plugin (v0.2.10 or v0.3.0 depending on what else lands). No feature flag needed; the change is internal to the service and only activates on Windows.
- Rollback: revert the commit. No external systems are touched.

## Open Questions

- Should we surface a "Windows detection diagnostics" link in the Setup Wizard ("the plugin tried these paths: …")? Not in this change; if Windows users still hit problems after this lands, file a follow-up.