# Explore Context (Delta)

## MODIFIED Requirements

### Requirement: Explore panel

The plugin SHALL provide an Explore tab in the OpenSpec tool window displaying AI explore responses rendered as HTML from markdown, with an inline input area for submitting topics and toolbar actions for copy and clear.

#### Scenario: Panel displays rendered response
- **WHEN** the AI provider returns an explore response
- **THEN** the panel SHALL render the markdown response as styled HTML in the response area

#### Scenario: Copy button
- **WHEN** the user clicks the Copy Response button in the Explore toolbar
- **THEN** the panel SHALL copy the raw markdown response text to the system clipboard with a notification

#### Scenario: Clear button
- **WHEN** the user clicks the Clear button in the Explore toolbar
- **THEN** the panel SHALL reset to the invitation empty state and clear the input area

## REMOVED Requirements

### Requirement: Auto-refresh on file changes
**Reason**: The Explore panel no longer displays assembled project context — it displays AI responses. Context is assembled at prompt-send time, not on VFS change. Auto-refresh of a passive context view is no longer relevant.
**Migration**: Context assembly happens automatically when the user submits a topic. No user action required.

### Requirement: Clipboard action
**Reason**: The Explore action now routes through the delivery mode system (Clipboard, Editor Tab, Direct API) rather than being a dedicated clipboard-only action. The clipboard delivery mode handles this case.
**Migration**: Use the Explore menu action with Clipboard delivery mode configured.
