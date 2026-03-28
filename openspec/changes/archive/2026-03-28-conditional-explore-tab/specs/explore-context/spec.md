## MODIFIED Requirements

### Requirement: Explore panel

The plugin SHALL provide an Explore tab in the OpenSpec tool window only when a Direct API provider is configured (`DirectApiService.isConfigured()` returns true). The tab SHALL display AI explore responses rendered as HTML from markdown, with an inline input area for submitting topics and toolbar actions for copy and clear.

#### Scenario: Tab present when Direct API configured
- **WHEN** the tool window content is created and a Direct API provider is configured with credentials
- **THEN** the Explore tab SHALL be added to the tool window

#### Scenario: Tab absent when no Direct API configured
- **WHEN** the tool window content is created and no Direct API provider is configured
- **THEN** the Explore tab SHALL NOT be added to the tool window

#### Scenario: Panel displays rendered response
- **WHEN** the AI provider returns an explore response
- **THEN** the panel SHALL render the markdown response as styled HTML in the response area

#### Scenario: Copy button
- **WHEN** the user clicks the Copy Response button in the Explore toolbar
- **THEN** the panel SHALL copy the raw markdown response text to the system clipboard with a notification

#### Scenario: Clear button
- **WHEN** the user clicks the Clear button in the Explore toolbar
- **THEN** the panel SHALL reset to the invitation empty state and clear the input area

## ADDED Requirements

### Requirement: Lazy Explore tab creation

The plugin SHALL lazily create the Explore tab when `ExplorePanelService.getAndActivate()` is called and the tab does not yet exist but a Direct API provider is now configured. This supports users who configure a provider after project open.

#### Scenario: Lazy creation on first Direct API explore
- **WHEN** `getAndActivate()` is called and no Explore tab exists but Direct API is configured
- **THEN** the service SHALL create the ExplorePanel, add the Explore content tab to the tool window, register the panel, and activate the tab

#### Scenario: No lazy creation without Direct API
- **WHEN** `getAndActivate()` is called and no Explore tab exists and Direct API is NOT configured
- **THEN** the service SHALL return null without creating the tab

#### Scenario: Existing tab reused
- **WHEN** `getAndActivate()` is called and the Explore tab already exists
- **THEN** the service SHALL activate the existing tab without creating a new one

### Requirement: Direct API submit from panel

The Explore panel's inline input SHALL always submit via Direct API delivery, bypassing the global delivery mode resolver. This ensures the panel's submit path matches its rendering capability.

#### Scenario: Panel submit uses Direct API
- **WHEN** the user submits a topic from the Explore panel's inline input
- **THEN** the plugin SHALL build the explore prompt and deliver it via `DirectApiService`, regardless of the globally selected delivery tool

#### Scenario: Menu action retains delivery mode routing
- **WHEN** the user triggers the Explore action from the menu with a non-Direct-API delivery mode
- **THEN** the plugin SHALL show the topic dialog and deliver via the resolved mode (clipboard or editor tab)