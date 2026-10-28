# Tasks

## 1. Capture

- [x] 1.1 Make `defaultRunAndCapture` redirect stdout to a temporary file (deleted afterwards) and stderr to `DISCARD`, keeping the timeout and `destroyForcibly`. Verify with tests against a real `/bin/sh` (skipped on Windows):
  - a command that prints and leaves `sleep 30 &` holding stdout returns its output well within the timeout;
  - a non-zero exit returns null;
  - a command that runs past the timeout returns null;
  - the command's stdin redirect is kept;
  - the temporary file is removed.

## 2. Shell PATH reuse

- [x] 2.1 Cache the resolved shell PATH with its time. `detect()` re-resolves only when there is no PATH or it is older than 10 minutes. `detect(true)` always re-resolves. A failed resolution keeps the old PATH. Verify with a counting `ProcessRunner` and a clock seam:
  - two detections start one shell;
  - an expired PATH starts another;
  - `detect(true)` starts one regardless;
  - a failure keeps the old PATH.
- [x] 2.2 Make Settings' Detect call `detect(true)`. Verify with a test that the Settings detection forces a fresh resolution.

## 3. Docs and checks

- [x] 3.1 Extend the existing Unreleased CHANGELOG entry for interactive-shell detection (the hang and the per-run shell were never released, so there is no Fixed entry), and add a feature-reference note that the shell PATH is reused for 10 minutes and refreshed by Detect in Settings. Verify with the leak grep and `DocumentationHygieneTest`.
- [x] 3.2 Run `./gradlew build` (exit 0) and `openspec validate cli-detection-shell-hardening --strict`. The change adds `com.intellij.*` references, so let the pre-push verifyPlugin gate run.

## Workflow follow-up

- Archive the change, then close the trackers.
