## Context

`CliDetectionService.detect()` tries candidates in a fixed order and stops at the first one whose `--version` exits 0:
1. the Settings path;
2. the bare name `openspec`;
3. the login-shell `which` (Unix) or `where.exe` (Windows);
4. the fixed install locations: `COMMON_PATHS` on Unix, or `windowsCommonPaths(envLookup)` on Windows.

Every candidate is probed by starting a process through the `ProcessRunner` seam. That includes fixed locations with no file, which fail inside the runner. `windowsCommonPaths` already takes an env lookup, and its tests use it. The Unix list is a constant with no seam.

The upstream installers at v1.15.0 work as follows:

| | `install.sh` (macOS/Linux) | `install.ps1` (Windows) |
|---|---|---|
| Directory | `${OPENSPEC_INSTALL_DIR:-$HOME/.local/bin}` | `$env:OPENSPEC_INSTALL_DIR`, else `%LOCALAPPDATA%\Programs\openspec` |
| File name | `${OPENSPEC_INSTALL_NAME:-openspec}` | `$env:OPENSPEC_INSTALL_NAME`, else `openspec.exe` |
| PATH | unchanged; prints advice | adds the directory to the user PATH |

The standalone binary prints a bare `1.15.0`. The captured fixture is `fixtures/cli/1.15.0/standalone/version.txt`.

## Goals / Non-Goals

**Goals:**
- Find a default standalone install on every OS, plus one installed with the overrides when the IDE's environment carries them.
- Keep every existing candidate in its current order.
- Stop starting processes for fixed locations that have no file.

**Non-Goals:**
- Fixing the login-shell lookup's `.zshrc` blind spot (`-lc` versus `-ilc`), and reusing the IDE's own shell environment and PATH search. A separate tracker entry covers both, after a platform feasibility check.
- Reading the Windows registry for the updated user PATH.
- Install hints or guidance in the not-found notification.
- Running, repairing or removing any install.

## Decisions

1. **Append the standalone candidates; don't reorder.**
   - **Unix:** the list is `/opt/homebrew/bin/openspec`, `/usr/local/bin/openspec`, `/usr/bin/openspec`, then the override candidate, then `<home>/.local/bin/openspec`.
   - **Windows:** the list is the three npm and winget shims, then the override candidate, then `%LOCALAPPDATA%\Programs\openspec\openspec.exe`.
   - **Why:** a user with both an npm copy and a standalone copy keeps getting the one detection finds today. Detection also stays compatible for everyone already detected.
   - *Alternative:* put the standalone binary first because it's newer. Rejected: that would silently switch an existing user's CLI.
2. **Honour the installer overrides, read from the IDE's environment.**
   - **Candidate:** when `OPENSPEC_INSTALL_DIR` is non-empty, add `<dir>/<name>` (with `\` on Windows). The name is `OPENSPEC_INSTALL_NAME` when it's non-empty, otherwise the OS default.
   - **Order:** it goes before the default standalone location, because it's the more specific of the two.
   - **Limits:** the installer reads these only at install time, and a GUI-launched IDE often lacks them. They cost nothing when absent, and they help a terminal-launched IDE or a user who exports them. Anyone else uses the Settings path.
3. **Make the Unix list a function, like the Windows one.**
   - **Signature:** `unixCommonPaths(Function<String,String> envLookup, String home)`, mirroring `windowsCommonPaths(envLookup)`, and `windowsCommonPaths` gains the override and standalone entries.
   - **Wiring:** the no-argument forms pass `System::getenv` and `System.getProperty("user.home")`, and a blank home adds no `.local/bin` candidate.
   - **Why:** tests can then assert the exact list without touching the real environment.
4. **Gate fixed-location candidates on a file check, through a seam.**
   - **Check:** before probing a fixed-location candidate, `detect()` asks a `Predicate<String>`. By default it is `Files.isRegularFile && Files.isExecutable` on Unix, and `Files.isRegularFile` on Windows, where the execute bit means nothing.
   - **Scope:** only step 4 uses the check. The Settings path, the bare name and the shell-resolved path keep going straight to the probe. They may be names resolved through PATH, or Windows paths without a suffix that the suffix fallback extends.
   - **Seam:** tests replace the predicate with `setExecutableCheckForTest`, so `detect()` can be driven with fake paths.
   - **Why:** a missing file now costs a stat instead of a process spawn, which keeps a growing list cheap.
5. **Contract-test the version parse against the captured standalone output.** A test runs `detect()` with every branch before the standalone candidate missing. That candidate returns the contents of `standalone/version.txt` and must report `1.15.0` and the `.local/bin` path. This pins both the new candidate and the parse against real output, not a hand-written string.

## Risks / Trade-offs

- **Windows can't be checked by hand.** There's no Windows host. → The path lists and the `detect()` order are unit-tested with `setIsWindowsForTest(true)`, and the GitHub mirror's Windows leg runs the suite.
- **`Files.isExecutable` on a symlink** follows the link. A dangling link reads as missing, which is right, since it couldn't run anyway.
- **The file check could hide a candidate the old code found.** That would take a file that runs but isn't a regular executable file, which can't happen for an install location. The Settings path stays the escape hatch.
