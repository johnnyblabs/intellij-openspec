## Context

Fast-Forward (FF) creates a change and immediately generates all artifacts end-to-end. This only delivers full value when Direct API is configured — without it, the workflow degrades to sequential clipboard/editor delivery, identical to Propose + Continue. The action currently appears regardless of API state, which is misleading.

The Continue action already requires Direct API and shows an error if not configured. The profile-based visibility system already gates FF on profile membership. This change adds a second gate: Direct API configuration.

## Goals / Non-Goals

**Goals:**
- Disable FF menu action when Direct API is not configured, with an informative tooltip
- Hide the FF link in the WorkflowActionPanel "no changes" card when Direct API is not configured
- Guard `activateFfInput()` defensively in case it's reached without API configured

**Non-Goals:**
- Changing how FF works when Direct API IS configured (no behavior change to the happy path)
- Removing non-API delivery modes from FF internals (the code stays; it's just not reachable via UI)
- Adding new settings or configuration options

## Decisions

**Gate at both action level and panel level.** The FF action (`OpenSpecFfAction`) is reachable from the menu/toolbar, while the FF link in `WorkflowActionPanel` is reachable from the tool window. Both entry points need the gate.

*Alternative: Gate only at the panel level.* Rejected — the menu action would still appear enabled, confusing users who click it and see nothing happen.

**Disable (not hide) the menu action.** Greyed-out with a tooltip ("Requires AI provider…") signals the feature exists but needs configuration.

*Alternative: Hide the action entirely.* Rejected — discoverable-but-disabled is better UX for a feature the user might want to enable.

**Hide (not disable) the panel FF link.** In the "no changes" card, showing a greyed-out "Fast-Forward" link alongside "Propose" adds clutter. Simply omitting it keeps the card clean.

**Check `DirectApiService.isConfigured()` directly.** This existing method checks both provider selection and API key presence — exactly the right predicate.

## Risks / Trade-offs

**[Risk] User configures API after opening the tool window** → The FF link won't appear until the panel refreshes. Mitigation: The panel already refreshes on settings changes via `OpenSpecSettings` listeners; no additional work needed.

**[Risk] `isConfigured()` called on BGT in `update()`** → `isConfigured()` reads in-memory settings state (no I/O), and `getActionUpdateThread()` already returns `BGT`. No threading concern.