# Proposal

## Why

This release's CLI detection reads the PATH an interactive login shell sets. A pre-release review found two problems with how that shell is run. Tracked by the linked tracker entry.

- **It can hang for good.** The plugin waits for the shell with a timeout, then reads its output with no timeout. If `~/.zshrc` starts a background process that inherits the shell's output, such as an agent or a tmux server, the output never ends. Detection then never returns and the CLI never reads as detected.
- **It runs far more often than intended.** Every detection starts the interactive shell, including the ones that bypass the 30-second limit, such as each Explore. With nvm or oh-my-zsh in `~/.zshrc`, each run costs one to three seconds.

## What Changes

- **The shell's output can't hold detection.** Every process detection starts writes its output to a temporary file, not a pipe. A background process that keeps the output open can't block the read. The wait is bounded by the existing per-command timeout, and a process still running at the timeout is stopped as before.
- **The shell PATH is reused.** The PATH an interactive login shell sets is resolved once and reused for 10 minutes, so most detections, Explore included, start no shell. Detection from Settings, where the user asks to find the CLI again, always resolves it afresh.
- Unchanged:
  - the probe order;
  - the markers that separate the PATH from rc-file output;
  - the shell flags;
  - the rule that a CLI process keeps the IDE's PATH and only gains the entries it lacks.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: "Cross-platform CLI detection" gains scenarios for a shell that leaves output open, and for reusing the shell PATH.

## Impact

- **Code:** `CliDetectionService`: the default process capture, the shell-PATH cache, and a fresh resolution for the Settings detection. `OpenSpecSettingsPanel`'s Detect calls the fresh variant.
- **Tests:**
  - a real `/bin/sh` that starts a background child holding the output, which must not hang the capture;
  - the shell call count across repeated detections, and when the cached PATH expires;
  - the Settings detection forcing a fresh resolution.
- **Docs:** the existing Unreleased CHANGELOG entry for interactive-shell detection gains the reuse note (both defects are in that unreleased feature), and the feature reference gains a note on reuse and on Detect.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. It uses `GeneralCommandLine.toProcessBuilder()`, which is public on 2024.2. Windows is unaffected: there is no login shell there, and its `where.exe` probe uses the same capture.
