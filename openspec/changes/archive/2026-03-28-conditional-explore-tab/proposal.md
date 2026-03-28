## Why

The Explore tab always appears in the tool window regardless of delivery mode configuration. Its inline input and response rendering only function with Direct API delivery — for clipboard and editor tab modes, submitting a topic disables the input permanently (never re-enabled) and provides no visual feedback. Users on CLI-based tools like Claude Code see a panel that looks interactive but is a dead-end, creating confusion about how to use Explore.

## What Changes

- **Conditionally create the Explore tab** in `OpenSpecToolWindowFactory` — only add it when `DirectApiService.isConfigured()` returns true.
- **Support lazy tab creation** in `ExplorePanelService.getAndActivate()` so the Explore tab appears on-demand if the user configures a Direct API provider after project open.
- **Add `runExploreDirect()` to `ExploreContextAction`** so the panel's `submitTopic()` always uses Direct API delivery, bypassing the delivery mode resolver that could route to clipboard.
- **Keep the Explore menu action working for all delivery modes** — only the tab visibility changes. Clipboard and editor tab users still get the topic dialog and prompt assembly.

## Capabilities

### New Capabilities
_None — this change modifies existing explore behavior._

### Modified Capabilities
- `explore-context`: The Explore tab becomes conditional on Direct API configuration. The panel's submit path is hardwired to Direct API delivery. Lazy creation ensures the tab appears when the user configures a provider mid-session.

## Impact

- **UI**: `OpenSpecToolWindowFactory` — Explore tab creation gated on `DirectApiService.isConfigured()`.
- **Services**: `ExplorePanelService` — gains lazy panel/tab creation logic in `getAndActivate()`.
- **Actions**: `ExploreContextAction` — new `runExploreDirect()` method; `ExplorePanel.submitTopic()` calls it instead of `runExplore()`.
- **No breaking changes**: Clipboard and editor tab delivery for Explore continue working via the menu action and topic dialog.