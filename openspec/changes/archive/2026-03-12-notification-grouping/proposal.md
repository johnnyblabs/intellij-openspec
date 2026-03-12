## Why

The plugin fires ~45 notifications through a single undifferentiated balloon group. During active workflows (propose, generate, archive), users get a rapid stream of indistinguishable popups — "Change proposed", "Generated proposal", "Validation passed", "Archive complete" — all looking identical. There are no titles to scan, no grouping by operation, and no actionable links. This makes it hard to follow what happened and easy to miss errors buried in a flood of info balloons. For v0.1.0, notifications need to be organized and purposeful.

## What Changes

- Register multiple `notificationGroup` entries in `plugin.xml` organized by concern: workflow actions, generation/AI, validation, system/CLI, and tracker sync
- Add a dedicated `NotificationGroup` for sticky/important notifications (errors, warnings) using `STICKY_BALLOON` display type
- Extend `OpenSpecNotifier` with titled notifications, HTML content support, and notification actions (e.g., "Open File", "Retry", "Open Settings")
- Add a notification title to every call site so users can scan by operation (e.g., "Archive", "Generate", "Validate")
- Collapse rapid-fire notifications during Generate All — replace per-artifact "Generated X" balloons with a single summary notification on completion
- Add "Open File" actions on generation/propose success notifications to jump directly to the created artifact
- Add "Open Settings" action on CLI-missing and API-failure notifications

## Capabilities

### New Capabilities
- `notification-system`: Notification group registration, titled notifications with actions, and Generate All collapse logic

### Modified Capabilities
_None — this change modifies implementation across many files but does not change any spec-level requirements. The behavioral contract of each action remains the same; only how notifications are displayed changes._

## Impact

- **Files modified**: `OpenSpecNotifier.java` (major rework), `plugin.xml` (new notification groups), and all ~20 files that call `OpenSpecNotifier.*`
- **No API changes**: All changes are internal notification presentation
- **No new dependencies**: Uses IntelliJ Platform `NotificationGroup`, `NotificationAction`, `AnAction` — all already available
- **Testing**: Notification calls are fire-and-forget side effects; manual verification in IDE required
