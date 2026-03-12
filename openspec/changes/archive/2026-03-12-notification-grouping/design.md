## Context

The plugin currently uses a single `notificationGroup` (`"OpenSpec Notifications"`, `BALLOON` type) registered in `plugin.xml`. All ~45 notification call sites go through `OpenSpecNotifier.info/warn/error`, which creates untitled balloons with plain text content. During busy operations like Generate All, the user can receive 5+ balloons in quick succession with no way to distinguish them at a glance.

IntelliJ's notification system supports multiple groups (each with its own display type and log category), titled notifications, HTML content, and clickable `NotificationAction` buttons embedded in the balloon.

## Goals / Non-Goals

**Goals:**
- Organize notifications into logical groups by concern
- Every notification has a scannable title (operation name)
- Important notifications (errors, warnings) use `STICKY_BALLOON` so they don't auto-dismiss
- Generation success notifications include "Open File" actions
- CLI/API failure notifications include "Open Settings" actions
- Generate All collapses per-artifact balloons into a single summary

**Non-Goals:**
- Custom notification panel or Event Log UI (use IntelliJ's built-in)
- Notification preferences/settings (use IntelliJ's built-in per-group settings)
- Sound effects or OS-level notification integration
- Rate limiting or debouncing beyond the Generate All collapse

## Decisions

### 1. Notification group structure

Register 5 groups in `plugin.xml`:

| Group ID | Display Type | Purpose |
|----------|-------------|---------|
| `OpenSpec.Workflow` | `BALLOON` | Propose, apply, archive actions |
| `OpenSpec.Generation` | `BALLOON` | Artifact generation, Generate All summary |
| `OpenSpec.Validation` | `BALLOON` | Validate results |
| `OpenSpec.System` | `STICKY_BALLOON` | CLI detection, config errors, API failures |
| `OpenSpec.Tracker` | `BALLOON` | Forgejo/Plane issue tracking (internal) |

**Alternative**: Single group with per-notification `isImportant()` flag. Rejected — separate groups let users configure (enable/disable/change display type) per category in IDE settings, which is more powerful and follows IntelliJ conventions.

**Alternative**: Keep old group ID for backwards compatibility. Rejected — the old group name doesn't follow IntelliJ conventions (dot-separated prefix), and there's no user state tied to the old name. Clean break is simpler.

### 2. OpenSpecNotifier API expansion

Expand `OpenSpecNotifier` with:
- `notify(project, group, title, content, type, actions...)` — core method
- Convenience methods keep backward compatibility: `info(project, content)` still works, defaults to `OpenSpec.Workflow` group with no title
- New convenience: `info(project, title, content)`, `error(project, title, content)`
- Action factories: `openFileAction(VirtualFile)`, `openSettingsAction()`
- `generateAllSummary(project, count, elapsed)` — dedicated method for the collapsed summary

### 3. Generate All notification collapse

During Generate All, `WorkflowActionPanel` currently fires per-artifact `OpenSpecNotifier.info(project, "Generated " + artifactId)` notifications. Replace these with:
- **No per-artifact balloons** during Generate All — the panel's progress bar and chip animations already provide real-time feedback
- **One summary balloon** on completion: "Generated 4 artifacts in 12s" with the Generate All group
- Error notifications still fire immediately (from `onError` callback)

### 4. Notification titles follow operation name

Every notification gets a title matching the operation: "Propose", "Archive", "Generate", "Validate", "CLI Detection", etc. This lets users scan balloon headers without reading body text.

### 5. HTML content for multi-line notifications

Validation results and error details use HTML content for readability: `<b>artifact</b>: error message`. IntelliJ's balloon renderer supports basic HTML.

## Risks / Trade-offs

- **Risk**: Removing the old `"OpenSpec Notifications"` group breaks any user who customized its settings. **Mitigation**: Acceptable for pre-v1.0; no public release has shipped yet.
- **Risk**: 5 notification groups is a lot for users to configure individually. **Mitigation**: IntelliJ groups them under the plugin name in Settings > Notifications. Users who don't customize won't notice.
- **Trade-off**: Suppressing per-artifact notifications during Generate All means the Event Log won't show individual completions. Acceptable because the panel provides real-time progress and the summary captures the outcome.
