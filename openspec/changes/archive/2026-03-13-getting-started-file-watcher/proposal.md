## Why

The GettingStartedPanel only transitions to the tree view when the user creates their first change via the panel's own "Propose a Change" button. If the user creates a change externally (via CLI, Claude Code, Copilot, or any AI tool), the panel stays frozen — it has no file watcher to detect the state change. This breaks the expected first-time experience for users who create changes outside the IDE.

## What Changes

- Add a VFS file listener to `GettingStartedPanel` that watches the `openspec/` directory for state changes
- When the listener detects the state has advanced (e.g., `openspec/changes/` now has an active change), automatically rebuild the panel or transition to the tree view
- Dispose the listener when the panel is no longer displayed

## Capabilities

### New Capabilities

_None — this is a bug fix to existing behavior._

### Modified Capabilities

- `getting-started-guide`: Add requirement that the GettingStartedPanel reacts to external filesystem changes

## Impact

- **GettingStartedPanel.java**: Add VFS listener, implement `Disposable` for cleanup
- **OpenSpecToolWindowFactory.java**: May need to pass disposable parent for listener lifecycle
