## Context

The Explore tab is created unconditionally in `OpenSpecToolWindowFactory.createNormalContent()` and registered with `ExplorePanelService`. The panel's inline input calls `ExploreContextAction.runExplore(project, topic)` which routes through `DeliveryMethodResolver` — if the resolved mode is CLIPBOARD or EDITOR_TAB, the prompt is delivered externally but the panel's input stays disabled (only `showResult`/`showError` re-enable it, and those are only called by the Direct API path).

`ExploreContextAction.actionPerformed()` already has a Direct API check: if Direct API mode, it activates the panel and focuses input. For other modes, it shows `ExploreTopicDialog`. The action works correctly for all modes — only the tab's inline input is broken for non-Direct-API.

## Goals / Non-Goals

**Goals:**
- Only show the Explore tab when Direct API is configured, eliminating the dead-end panel experience for clipboard/editor tab users.
- Support lazy creation so the tab appears mid-session when the user configures a Direct API provider.
- Ensure the panel's submit path always uses Direct API, regardless of the global delivery mode selector.

**Non-Goals:**
- Changing the Explore menu action behavior — it continues to work for all delivery modes via the topic dialog.
- Adding a non-interactive Explore tab for clipboard/editor tab modes.
- Handling the case where a user removes their API key after the tab is created — the existing `deliverDirectApi` fallback to clipboard is sufficient for this edge case.

## Decisions

### 1. Gate tab creation on `DirectApiService.isConfigured()`

In `OpenSpecToolWindowFactory.createNormalContent()`, check `DirectApiService.isConfigured()` before creating the Explore tab and registering it with `ExplorePanelService`.

**Why:** The tab's inline input only functions with Direct API. Showing it otherwise is misleading.

**Alternative considered:** Show the tab always but display a "Configure Direct API to use Explore" message. Rejected — adds complexity for a panel the user can't interact with. The menu action already covers non-API users.

### 2. Lazy creation in `ExplorePanelService.getAndActivate()`

If the panel is null when `getAndActivate()` is called, check if Direct API is now configured. If so, create the `ExplorePanel`, add the "Explore" content tab to the tool window, and register it — all within `getAndActivate()`.

**Why:** The user may configure an API key after project open (e.g., through Settings). The next time they trigger Explore via the menu action or Direct API delivery, `getAndActivate()` is called and the tab should materialize.

**Alternative considered:** Listen for settings changes and dynamically add/remove the tab. Rejected — over-engineered for a rare event. Lazy creation on next use is simpler and sufficient.

### 3. Add `runExploreDirect()` on `ExploreContextAction`

A new public static method that builds the explore prompt and always calls `deliverDirectApi()`, bypassing the delivery mode resolver. `ExplorePanel.submitTopic()` calls this instead of `runExplore()`.

**Why:** The panel only exists when Direct API is configured, so its submit path should always use Direct API. Routing through the resolver could send to clipboard if the user happened to select a CLI tool in the delivery dropdown, which would silently copy and leave the panel stuck.

### 4. No changes to `ExploreContextAction.actionPerformed()`

The existing flow already handles this correctly: Direct API → activate panel, otherwise → show dialog. No modification needed.

## Risks / Trade-offs

- **Tab absent for new users until they configure API** → Acceptable. The Explore menu action still works via clipboard/editor tab. Users who configure API get the richer experience.
- **Lazy creation runs on EDT** → `getAndActivate()` is already called from `invokeLater`. Creating a panel and adding content is lightweight Swing work — no I/O involved. Safe on EDT.
- **API key removed after tab exists** → `deliverDirectApi` falls back to clipboard with a notification suggesting configuration. The tab stays but becomes non-functional until the key is restored. Rare edge case, acceptable.