## Why

Fast-Forward creates a change and generates all artifacts end-to-end — but this only works meaningfully with Direct API configured. Without it, FF falls back to one-at-a-time clipboard/editor delivery, which is just Propose + Continue with extra steps. Gating FF on Direct API removes a confusing option and makes the action's promise ("all artifacts in one step") match reality.

## What Changes

- FF menu action disabled with tooltip when Direct API is not configured
- FF link in the "no changes" card hidden when Direct API is not configured
- FF input form shows an inline message directing users to configure an API provider if activated without Direct API (defensive — shouldn't normally be reachable)

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `workflow`: FF action and FF input card require Direct API to be enabled/visible
- `ai-integration`: Direct API configuration state gates FF availability

## Impact

- `OpenSpecFfAction.java` — override `update()` to check `DirectApiService.isConfigured()`
- `WorkflowActionPanel.java` — conditionally show/hide FF link in no-changes card; guard `activateFfInput()` with API check
- No API changes, no dependency changes, no breaking changes