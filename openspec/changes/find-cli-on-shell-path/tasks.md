## 1. Fixtures

- [x] 1.1 Capture the real stdout of the marker command (design decision 2) under `src/test/resources/fixtures/shell/`. Run each shell with `env -i` and a throwaway HOME, with stdin closed. Sanitize the throwaway path to `/fixture/home`. Cases:
  - `zsh-ilc-noisy.txt`: `/bin/zsh -l -i -c`, with a `.zshrc` that adds a PATH entry and prints a banner;
  - `bash-ilc-noisy.txt`: `/bin/bash -l -i -c`, with a `.bashrc` doing the same, sourced from `.bash_profile`;
  - `zsh-lc-misses.txt`: `/bin/zsh -l -c` with the same HOME, showing the entry missing.

  Document the recipe in a `fixtures/shell/README.md`.

## 2. Detection

- [x] 2.1 Add a pure `parseMarkedPath(stdout)`, the shell-flag choice `shellFlags(shellPath)` and a pure `mergePath(base, extra)`. Verify:
  - **Parsing:** the zsh and bash fixtures parse to a PATH containing the rc-added entry, ignoring the banner. The `-lc` fixture's PATH lacks it. Output with no markers, or only a start marker, gives null. A marker echoed before the real output doesn't confuse the parse.
  - **Flags:** `/bin/zsh` and `/usr/bin/bash` give `-l -i -c`, `/opt/homebrew/bin/fish` gives `-l -c`, `/bin/tcsh` and `/bin/csh` give `-i -c`, and an unknown shell gives `-l -i -c`.
  - **Merge:** missing segments are appended in order and duplicates skipped. When nothing is missing, the PATH is unchanged. A null or empty base gives the shell PATH.
- [x] 2.2 Replace `resolveLoginShellPath` and `tryLoginShellWhich` with the single marker call. Search the parsed PATH for an executable `openspec` through the existing file-check seam. Pass the flags and the two env variables, and close stdin. Windows keeps `where.exe` and runs no shell call. Verify through the `ProcessRunner` seam:
  - one shell process per detection, with the expected argv and env;
  - the CLI is found in an rc-only PATH directory;
  - no `which` process remains;
  - a shell timeout or a missing marker falls through to the fixed locations.
- [x] 2.3 Add the IDE-PATH lookup after Settings, macOS and Linux only, through a `Function<String, File>` seam that defaults to `PathEnvironmentVariableUtil::findInPath`. A hit is probed by its absolute path. Verify:
  - it is tried after Settings and before the bare name;
  - a hit stops detection before any shell process;
  - on Windows it is never called;
  - a miss goes on to the bare name.
- [x] 2.4 Make `applyLoginShellPath` merge with `mergePath(currentChildPath, shellPath)`, never replacing. Verify on a `GeneralCommandLine`:
  - a PATH that already contains every shell segment is unchanged;
  - missing segments are appended after the existing PATH.
- [x] 2.5 Mutation-check by hand. Each must make a test fail:
  - go back to `-lc`;
  - use `-i` for fish;
  - parse the first line instead of the markers;
  - replace PATH instead of merging;
  - move the IDE-PATH lookup after the bare name;
  - call it on Windows.

## 3. Docs and verify

- [x] 3.1 Docs:
  - `docs/feature-reference.md` troubleshooting: the plugin reads the PATH your interactive shell sets, including `~/.zshrc`;
  - drop the `.zprofile` advice in `docs/getting-started-copilot.md`'s tip;
  - the internal wiki's detection-cascade lists in `scripts/docs/wiki/Installation.md` and `Troubleshooting.md`;
  - a CHANGELOG `## Unreleased` Fixed entry.
- [x] 3.2 `./gradlew build` and `./gradlew verifyPlugin` are green, because of the new `com.intellij.*` references, and `openspec validate find-cli-on-shell-path --strict` is clean.
- [ ] 3.3 Manual check: with the npm CLI hidden behind a `.zshrc`-only PATH entry (for example a symlink to it in a directory added only in `.zshrc`, with the Settings path cleared), launch the sandbox from Finder (`open -a` on the sandbox app, not a terminal). Settings → Detect should find it. Restore `.zshrc` afterwards.

## Workflow follow-up

- Archive the change, then close the tracker entry.
