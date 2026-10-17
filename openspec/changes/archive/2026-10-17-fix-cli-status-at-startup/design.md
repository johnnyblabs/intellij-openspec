## Context

See proposal.md, Why. `CliDetectionService` holds `available`, `detectedPath`, `detectedVersion` and `lastDetectionTime` in separate volatile fields. `detect()` resets the first three at its start, then probes the settings path, `openspec` on PATH, the login-shell PATH and Windows suffixes through `tryPath`, setting the fields as it succeeds. `detectIfStale()` skips if the last detection was under 30 s ago.

- `OpenSpecToolWindowPanel.updateCliStatus()` reads `isAvailable()` and `getDetectedVersion()`, and is called at construction and from its own refresh paths.
- `OpenSpecToolWindowFactory.refreshCliStatus` calls `detectIfStale()` on a pooled thread and redraws only if detection ran.
- `StartupDetection` (`postStartupActivity`) calls `detect()` directly.

## Goals / Non-Goals

**Goals:** no transient "unavailable"; the status follows every completed detection; a detecting state until the first result.

**Non-Goals:**
- Changing how detection finds the CLI (order, login-shell PATH, Windows suffixes) or the 30 s throttle.
- The Settings page, which already redraws after its own detection.

## Decisions

### D1. Compute into locals, publish once

`detect()` and `tryPath` work into a local result: available, path and version. At the end of `detect()`, an immutable snapshot record is published through a single volatile reference, together with `lastDetectionTime` and a `detectedOnce` flag. `isAvailable()`, `getDetectedPath()` and `getDetectedVersion()` read from the current snapshot, so the three values are always consistent with each other. `tryPath(String)` is public (only the detection itself and the test helper call it). It keeps its signature and publishes its own snapshot when it succeeds.

*Alternative considered:* a lock around detection. Readers would block on a slow probe, which can take a login shell several seconds, and some readers run on the EDT.

### D2. A project-level "detection completed" topic

`CliDetectionService` declares `Topic<CliDetectionListener>` with `detectionCompleted(boolean available, @Nullable String version)`. It publishes on the project message bus after every snapshot is published, whether the CLI was found or not. `OpenSpecToolWindowPanel` subscribes with a connection tied to its own disposable and redraws on the EDT via `invokeLater` (the tool window isn't modal). The factory's throttled re-check no longer needs its own redraw, because a detection it runs announces itself.

*Alternative considered:* polling in the panel. That's wasteful, and still racy.

### D3. "CLI: detecting…" before the first result

`isDetectedOnce()` is false until the first `detect()` completes. While it's false, `updateCliStatus()` shows "CLI: detecting…" in the neutral label colour. Once a result exists, it shows available or not found exactly as today.

## Risks / Trade-offs

- **Readers relying on the old reset-to-false.** → None were found by grep. "Available" during a re-run now means "available as of the last completed detection", which is what callers want.
- **A message published after the project is disposed.** → Publishing checks `project.isDisposed()`; the panel's connection is disposed with it.

## Migration Plan

Code-only. To roll back, revert the commit.
