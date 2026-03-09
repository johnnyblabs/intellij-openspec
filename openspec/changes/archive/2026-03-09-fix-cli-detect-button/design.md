## Context

`OpenSpecSettingsPanel.detectCli()` runs `detection.detect()` on a pooled thread and schedules a UI update via `invokeLater`. If `detect()` throws an uncaught exception, the UI update never fires and the label stays at "Detecting..." permanently. The user sees a broken settings panel with no indication of what went wrong.

## Goals / Non-Goals

**Goals:**
1. The status label must always resolve to a terminal state (found, not found, or error)
2. Errors should be visible in the UI and in idea.log
3. Keep the fix minimal — no architectural changes

**Non-Goals:**
- Redesigning the CLI detection strategy (that works fine)
- Adding progress indicators or spinners
- Changing the detection timeout values

## Decisions

### Decision 1: Wrap pooled thread body in try/catch

**Choice:** Add a try/catch around `detection.detect()` in the pooled thread lambda. On exception, schedule an `invokeLater` that sets the status label to an error message.

**Rationale:** Simplest fix that guarantees the UI always resolves. The catch block mirrors the existing `invokeLater` pattern.

**Alternative rejected:** Moving detection to a `Task.Backgroundable` with `ProgressIndicator` — overkill for a settings panel operation.

### Decision 2: Force repaint after setText

**Choice:** Call `revalidate()` and `repaint()` on the status label and path field after updating text, to handle edge cases where `TextFieldWithBrowseButton` doesn't repaint automatically.

**Rationale:** Belt-and-suspenders. Low cost, prevents a class of subtle display bugs.

## Risks / Trade-offs

**Risk:** None significant — this is a straightforward bug fix with no behavioral changes to the happy path.
