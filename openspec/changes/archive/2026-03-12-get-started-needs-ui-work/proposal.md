## Why

The Getting Started panel has two UI bugs that break the first-time experience:

1. **Text doesn't wrap**: The description text in empty-state cards is a plain `JBLabel` which doesn't wrap. In a narrow tool window, long descriptions get clipped or force horizontal scrolling.
2. **Panel doesn't transition after propose**: When a user clicks "Propose a Change" and successfully creates their first change, the Getting Started panel stays visible showing "Create your first change" instead of transitioning to the normal tree view. The user must manually close and reopen the tool window.

## What Changes

- Fix `EmptyStateFactory` to use HTML-wrapped `JBLabel` so description text wraps at the panel width
- Add post-propose transition in `GettingStartedPanel`: after a successful propose action, rebuild the tool window content to show the normal Browse + Console tabs
- Pass the `ToolWindow` reference into `GettingStartedPanel` so it can trigger the content transition

## Capabilities

### New Capabilities

### Modified Capabilities
- `tool-window`: Fix Getting Started panel text wrapping and add auto-transition to tree view after first proposal

## Impact

- `EmptyStateFactory.java` — HTML-wrap description label for text wrapping
- `GettingStartedPanel.java` — accept ToolWindow, rebuild content after propose
- `OpenSpecToolWindowFactory.java` — pass ToolWindow to GettingStartedPanel
