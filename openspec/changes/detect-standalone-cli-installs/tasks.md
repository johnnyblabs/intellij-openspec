## 1. Candidate lists

- [x] 1.1 Replace `COMMON_PATHS` with `unixCommonPaths(envLookup, home)`, plus a no-argument form that passes `System::getenv` and `user.home`. It returns the three existing paths in order, then `/home/linuxbrew/.linuxbrew/bin/openspec` (the Homebrew formula on Linux), then `OPENSPEC_INSTALL_DIR/<OPENSPEC_INSTALL_NAME or openspec>` when the dir is non-empty, then `<home>/.local/bin/openspec` when home is non-blank. Verify in `CliDetectionServiceTest`:
  - with no overrides, the list is the three existing paths, then Linux Homebrew, then `.local/bin`;
  - a dir override comes before `.local/bin`, with the default name;
  - dir plus name overrides combine;
  - an empty dir, or a name with no dir, adds nothing;
  - a blank home adds no `.local/bin` candidate.
- [x] 1.2 Extend `windowsCommonPaths(envLookup)` to return, after the three npm and winget shims:
  - the override candidate, `<OPENSPEC_INSTALL_DIR>\<name or openspec.exe>`;
  - then `%LOCALAPPDATA%\Programs\openspec\openspec.exe`.

  Update the existing exact-list assertions. Add tests for:
  - the standalone path coming last;
  - the override coming before it;
  - an unset `LOCALAPPDATA` dropping the standalone path but keeping a set override.

## 2. File check before probing

- [x] 2.1 Add a `Predicate<String>` executable check. On Unix it is `Files.isRegularFile && Files.isExecutable`; on Windows it is `Files.isRegularFile`, chosen by `isWindows()`. Invalid paths count as missing. Add `@TestOnly setExecutableCheckForTest`. In `detect()`'s fixed-location loop, skip a candidate the check rejects; leave the Settings, bare-name and shell-resolved branches unchanged. Verify through the `ProcessRunner` seam:
  - with the check rejecting everything and every earlier branch missing, no fixed-location `--version` process is started;
  - with the check accepting one path, only that path is probed;
  - the bare name and the shell-resolved path are still probed when the check rejects them. The Settings branch needs a project, and its code is untouched.
- [x] 2.2 Test the real default check against temp files:
  - an executable file passes on Unix;
  - a non-executable file and a directory fail;
  - a missing path fails;
  - a path with invalid characters fails without throwing.

## 3. Detection end to end

- [x] 3.1 A Unix `detect()` test with `user.home` and the env injected through the new seams. Every earlier branch misses and the check accepts only `<home>/.local/bin/openspec`, whose probe returns the captured `fixtures/cli/1.15.0/standalone/version.txt`. Assert:
  - the detected path is the `.local/bin` path;
  - the version is `1.15.0`;
  - Homebrew, `/usr/local/bin` and `/usr/bin` were checked first.

  Do the same on Windows (`setIsWindowsForTest(true)`) for `%LOCALAPPDATA%\Programs\openspec\openspec.exe`, after the shims. Each test must fail when its candidate is removed from the list.
- [x] 3.2 An existing-install regression test. When both `/usr/local/bin/openspec` and `.local/bin/openspec` pass the check, detection picks `/usr/local/bin/openspec`.
- [x] 3.3 Mutation-check the new tests by hand. Each new or changed assertion must fail when one of these is reverted:
  - drop `.local/bin`;
  - drop the Windows standalone path;
  - swap the override and default order;
  - remove the file check;
  - apply the file check to the bare name;
  - apply the file check to the shell-resolved path;
  - have `detect()` read the real environment instead of the seam;
  - drop the execute-bit check on Unix.

## 4. Docs

- [x] 4.1 `docs/feature-reference.md` troubleshooting (the macOS PATH line) and `docs/getting-started-copilot.md` (the Detect row): name `~/.local/bin` and the installer overrides alongside Homebrew and `/usr/local/bin`.
- [x] 4.2 `README.md` Windows note: add `%LOCALAPPDATA%\Programs\openspec\openspec.exe` for the standalone binary, and say that a standalone install on any OS is detected without Node.js.
- [x] 4.3 `CHANGELOG.md` `## Unreleased` Fixed entry: the plugin now finds OpenSpec's standalone CLI where its installer puts it, on macOS, Linux and Windows.

## 5. Verify

- [x] 5.1 `./gradlew build` is green, and `openspec validate detect-standalone-cli-installs --strict` is clean.
- [x] 5.2 Manual check on macOS: skipped by maintainer decision. This machine's npm copy is at `/opt/homebrew/bin`, a fixed location checked first, so a standalone copy can't be reached without moving that install aside. The candidate order and the file check are pinned by `CliDetectionServiceTest`.

## Workflow follow-up

- Archive the change, then close the tracker entry.
