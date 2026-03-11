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

### Requirement: Preferred tool detection is regression-tested
The system SHALL provide automated tests that verify preferred tool detection outcomes consumed by the "Tools & Delivery" settings UI.

#### Scenario: Detected tools are returned for settings population
- **WHEN** automated tests run with valid project and tool fixtures
- **THEN** the detection flow SHALL return one or more tool entries with stable labels for settings display

#### Scenario: No detected tools falls back to None
- **WHEN** automated tests run with fixtures where no supported tools are detectable
- **THEN** the settings flow SHALL expose only the "None" option as the preferred tool fallback

#### Scenario: Invalid detection inputs are handled safely
- **WHEN** automated tests run with invalid or incomplete detection inputs
- **THEN** detection SHALL complete without unhandled exceptions and SHALL return a safe fallback result

#### Scenario: Tool type indicators remain correct
- **WHEN** automated tests run for detected tools with known classification types
- **THEN** each detected tool SHALL retain the expected type indicator used by the settings dropdown

### Requirement: Preferred tool fallback behavior is regression-tested
The system SHALL provide automated tests that verify default selection and persistence fallback behavior for preferred tool settings.

#### Scenario: Default selection uses first detected tool when preference is unset
- **WHEN** automated tests run with no saved preferred tool and one or more detected tools
- **THEN** preferred tool resolution SHALL select the first detected tool

#### Scenario: Saved preferred tool remains selected when available
- **WHEN** automated tests run with a saved preferred tool that is present in detected tools
- **THEN** preferred tool resolution SHALL preserve the saved selection

#### Scenario: Missing saved preferred tool falls back safely
- **WHEN** automated tests run with a saved preferred tool that is not present in detected tools
- **THEN** preferred tool resolution SHALL fall back to a valid detected tool or "None" when no tools are available
