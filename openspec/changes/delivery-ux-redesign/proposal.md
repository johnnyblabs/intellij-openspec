## Why

The delivery method selection currently lives in two places: Settings > Tools > OpenSpec (the "Tools & Delivery" tab) and the workflow panel's dropdown chevron. This is redundant and confusing — users configure a default in settings, then override it per-artifact in the workflow panel. The settings version adds complexity to a panel that should be about configuration, not workflow decisions.

More importantly, the current UI doesn't let users say "I want to use Claude Code for this" or "switch to Copilot" — they have to think in terms of delivery *modes* (clipboard, editor tab, API) rather than *tools*. The tool should be the primary selection, with the delivery mode being an implicit consequence.

Additionally, the plugin has no way to surface an "explore" action — a lightweight entry point that copies project context for use in any AI chat tool. This bridges the gap between the plugin's structured workflow and the conversational explore mode available in Claude Code and Copilot.

## What Changes

- Remove the "Tools & Delivery" tab from the settings panel — delivery selection moves to the workflow panel
- Add a tool/delivery selector to the WorkflowActionPanel that shows detected AI tools and delivery options in one dropdown
- The selector remembers the last choice (persisted in settings) but can be changed any time
- Add an "Explore" action to the OpenSpec menu that copies project context to clipboard for use in any AI chat
- Simplify the settings panel to two areas: CLI + General (top) and Direct API (bottom, no tabs needed)

## Capabilities

### New Capabilities
- `explore-context`: Lightweight action that assembles project context and copies to clipboard for use in AI chat tools

### Modified Capabilities
- `settings-panel-sections`: Remove "Tools & Delivery" tab, flatten Direct API into the main panel
- `workflow-panel`: Add inline tool/delivery selector replacing the first-run setup card and settings-based delivery

## Impact

- **OpenSpecSettingsPanel.java**: Remove tools/delivery tab, flatten layout, remove `buildToolsAndDeliveryTab()` and related methods
- **WorkflowActionPanel.java**: Add tool selector dropdown, update first-run flow, remove setup card
- **OpenSpecSettings.java**: `preferredTool` and `preferredDeliveryMethod` fields stay (used by workflow panel now)
- **New action class**: `ExploreContextAction` in `actions/`
- **plugin.xml**: Add Explore action to OpenSpec menu
