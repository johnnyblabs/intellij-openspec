# Delivery Preferences UI

## Purpose
Configurable preferred tool and delivery method settings in the settings panel.

## Requirements

### Requirement: Preferred tool is configurable in settings
The settings panel SHALL expose a preferred tool dropdown in the "Tools & Delivery" tab, populated from detected AI tools.

#### Scenario: Detected tools populate dropdown
- **WHEN** the user views the "Tools & Delivery" tab and AI tools are detected
- **THEN** the preferred tool dropdown SHALL list each detected tool with its type indicator (e.g., "Claude Code (CLI)", "GitHub Copilot (IDE)")
- **AND** a "None" option SHALL be available

#### Scenario: No tools detected
- **WHEN** the user views the "Tools & Delivery" tab and no AI tools are detected
- **THEN** the preferred tool dropdown SHALL show only "None"
- **AND** a message SHALL indicate no AI tools were detected in the project

#### Scenario: Preferred tool default selection
- **WHEN** the user opens settings and no preferred tool has been saved
- **THEN** the dropdown SHALL pre-select the first detected tool

#### Scenario: Preferred tool persisted on apply
- **WHEN** the user selects a tool and clicks Apply or OK
- **THEN** the selection SHALL be saved to `OpenSpecSettings.State.preferredTool`

### Requirement: Delivery method is configurable in settings
The settings panel SHALL expose a delivery method dropdown in the "Tools & Delivery" tab.

#### Scenario: Delivery method options
- **WHEN** the user views the delivery method dropdown
- **THEN** the options SHALL include "Copy to Clipboard", "Open in Editor Tab", and "Generate via API"

#### Scenario: Generate via API disabled when not configured
- **WHEN** no Direct API provider is configured (provider is "None" or no API key stored)
- **THEN** the "Generate via API" option SHALL be visually disabled or annotated as unavailable
- **AND** a status note SHALL indicate that an API key is required on the Direct API tab

#### Scenario: Smart default when API is configured
- **WHEN** the user opens settings, no delivery preference has been saved, and a Direct API provider is configured
- **THEN** the delivery method SHALL default to "Generate via API"

#### Scenario: Smart default when only tools detected
- **WHEN** the user opens settings, no delivery preference has been saved, and AI tools are detected but no API is configured
- **THEN** the delivery method SHALL default to "Copy to Clipboard"

#### Scenario: Delivery method persisted on apply
- **WHEN** the user selects a delivery method and clicks Apply or OK
- **THEN** the selection SHALL be saved to `OpenSpecSettings.State.preferredDeliveryMethod`
