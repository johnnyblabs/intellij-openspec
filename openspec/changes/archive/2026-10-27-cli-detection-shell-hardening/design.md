# Design

## Context

See proposal.md for why. `CliDetectionService` runs every detection process through `ProcessRunner.runAndCapture`. That covers the `--version` probes, `where.exe` and the login-shell PATH read. The default implementation, `defaultRunAndCapture`, starts the process, drains stdout and stderr on common-pool futures, `waitFor`s with `TIMEOUT_MS` (10 s), and then calls `stdoutFuture.join()` with no bound. A pipe only reaches EOF once every process holding its write end has exited. So a background child of an interactive shell that inherits stdout keeps `join()` waiting forever, after the shell itself has exited.

`detect()` calls `resolveLoginShellPath()` first, every time. Before this release it returned early once the PATH was cached. That early return was removed, so a shell change would be picked up. `detectIfStale()` limits the tool-window path to once every 30 s, but `detect()` is also called directly from Explore context assembly, the workflow panel, project open, the Setup Wizard and Settings.

## Goals / Non-Goals

**Goals:**
- no detection step can outlast the per-command timeout;
- most detections start no shell;
- an explicit user detection still sees a shell change.

**Non-Goals:**
- changing the probe order, the PATH-merge rule, the shell flags or the marker parsing;
- making Explore's own CLI calls cheaper (tracked separately).

## Decisions

**D1. Capture to temporary files, not pipes.**
- **How:** `defaultRunAndCapture` builds a `ProcessBuilder` from `cmd.toProcessBuilder()` (public on 2024.2), redirects stdout to a temporary file and stderr to `Redirect.DISCARD`, starts it, and `waitFor`s with `TIMEOUT_MS`. On timeout it calls `destroyForcibly()` and returns null, as today. On exit 0 it reads the file, then deletes it in a `finally`.
- **Why it can't hang:** a background child that inherited stdout can keep writing to the file, but nothing waits for it. The read happens once the shell exits.
- **Rejected:**
  - `stdoutFuture.get(remaining)`: it bounds the wait, but leaks a common-pool thread blocked on the pipe for every lingering child, and loses output already written;
  - killing the process's descendants: that would kill the user's agents.
- **Preserved:** the existing stdin rule (`withInput(/dev/null)` for the shell) carries over, because `toProcessBuilder()` keeps the command line's input file. A test pins it: it feeds a file to `cat` and checks the output, which would fail if the input were dropped.

**D2. Cache the shell PATH for 10 minutes; Settings refreshes it.**
- **How:** `loginShellPath` gets a resolved-at timestamp. `detect()` resolves only when there is no PATH or it is older than `SHELL_PATH_TTL` (10 minutes). A new `detect(boolean freshShellPath)` overload, used by Settings' Detect, resolves unconditionally. A failed resolution keeps the previous PATH and still sets the timestamp, so a broken rc file isn't retried on every detection.
- **Why 10 minutes:** a PATH changes when the user edits an rc file or installs a version manager, which is rare and usually followed by restarting the IDE or pressing Detect in Settings. Ten minutes bounds the cost to one shell per 10 minutes, whoever calls.
- **Rejected:**
  - restoring the old cache for the whole service lifetime: it never sees a shell change without a restart;
  - resolving only when the IDE-PATH lookup misses, as the earlier design described: every CLI process gets the shell's extra entries through `applyLoginShellPath`, so skipping the resolution would drop them for the `node` the CLI's shebang needs.

**D3. Seams.**
- The `ProcessRunner` seam stays for the order tests.
- `defaultRunAndCapture` becomes package-private static, so a test can run it against a real `/bin/sh` that leaves a child holding the output.
- The clock is a `Supplier<Instant>` seam for the expiry test.

## Risks / Trade-offs

- [The temporary file can't be created (a full or read-only temp directory)] → It is created with `Files.createTempFile` in the JVM temp directory. A failure is an exception the existing `catch` already logs, and detection goes on to the next candidate.
- [A shell change made within 10 minutes isn't seen by background detections] → Settings → Detect refreshes it, and the CHANGELOG and feature reference say so.
- [Windows] → `where.exe` and the probes use the same capture. Temporary files work the same there, and Windows has no login shell, so D2 is a no-op.
