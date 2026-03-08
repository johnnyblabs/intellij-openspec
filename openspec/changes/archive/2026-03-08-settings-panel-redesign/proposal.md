## Why

The settings panel is a flat list of controls with no grouping or explanatory text. Users cannot understand the distinction between Direct API keys (for artifact generation) and detected AI tools (for prompt delivery). The `preferredDeliveryMethod` and `preferredTool` settings exist in the data model but are never exposed in the settings UI, forcing users to rely solely on the first-run setup card with no way to change preferences later.

## What Changes

- Reorganize the settings panel into distinct sections: a prominent **OpenSpec CLI** section at top with health/version status, a **General** section for project preferences, and a **tabbed pane** separating **AI Tools & Delivery** from **Direct API**
- Add contextual help text to each section explaining what it does and why
- Expose `preferredTool` and `preferredDeliveryMethod` in the AI Tool Integration section
- Show AI provider display names ("Claude") instead of enum names ("CLAUDE")
- Wrap the panel in a scroll pane to accommodate the taller layout
- Show tool type indicators (CLI / IDE) next to detected tools

## Capabilities

### New Capabilities
- `settings-panel-sections`: Grouped settings layout with titled borders and contextual help text for each section
- `delivery-preferences-ui`: UI controls for preferred tool and delivery method in settings, with smart defaults based on configuration state

### Modified Capabilities
- `ai-setup`: The first-run setup card behavior is unchanged, but users can now also modify their preferred tool and delivery method from the settings panel at any time

## Impact

- `OpenSpecSettingsPanel.java` — Complete restructure of panel layout into sections
- `OpenSpecConfigurable.java` — Wire preferred tool and delivery method fields to settings persistence
- `AiProvider.java` — May need display name improvements for combo box rendering
- No API changes, no dependency changes, no breaking changes
