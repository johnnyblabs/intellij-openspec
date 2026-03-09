# Settings Panel Sections

## Purpose
Visual organization of the settings panel into distinct sections with contextual help.

## Requirements

### Requirement: Settings panel uses distinct visual sections
The settings panel SHALL organize controls into visually distinct sections: an "OpenSpec CLI" titled section at the top, a "General" section for project preferences, and a tabbed pane for AI configuration.

#### Scenario: Panel layout on open
- **WHEN** the user opens OpenSpec settings (Tools > OpenSpec)
- **THEN** the panel SHALL display an "OpenSpec CLI" section at the top with CLI path, detect button, and version status
- **AND** a "General" section below with schema profile and preference checkboxes
- **AND** a tabbed pane below with "Tools & Delivery" and "Direct API" tabs

### Requirement: OpenSpec CLI section displays health status prominently
The OpenSpec CLI section SHALL show the CLI detection status and version prominently so users can immediately see if their installation is working.

#### Scenario: CLI is detected and available
- **WHEN** the settings panel opens and the OpenSpec CLI is detected
- **THEN** the CLI status SHALL display the version and path (e.g., "OpenSpec CLI v1.2.0")
- **AND** the status text SHALL use a success color (green)

#### Scenario: CLI is not detected
- **WHEN** the settings panel opens and the OpenSpec CLI is not found
- **THEN** the CLI status SHALL display "OpenSpec CLI not found"
- **AND** the status text SHALL use a warning color

#### Scenario: CLI detection triggered manually
- **WHEN** the user clicks the "Detect" button
- **THEN** the system SHALL scan for the OpenSpec CLI executable
- **AND** update the path field and status display with the result

#### Scenario: CLI detection fails with error
- **WHEN** the CLI detection process throws an unexpected exception
- **THEN** the status label SHALL display an error message (not remain at "Detecting...")
- **AND** the error SHALL be logged at WARN level in idea.log

#### Scenario: CLI detection always resolves
- **WHEN** the user clicks "Detect" or the panel opens
- **THEN** the status label SHALL always transition from "Detecting..." to a terminal state (found, not found, or error) regardless of any exceptions during detection

### Requirement: Each section includes contextual help text
Each AI tab SHALL include a brief description label explaining what the section is for and when the user needs it.

#### Scenario: Tools & Delivery tab help text
- **WHEN** the user views the "Tools & Delivery" tab
- **THEN** a description SHALL be visible explaining that these are detected AI coding tools and delivery preferences

#### Scenario: Direct API tab help text
- **WHEN** the user views the "Direct API" tab
- **THEN** a description SHALL be visible explaining that API keys are optional and used for direct artifact generation

### Requirement: AI provider dropdown shows display names
The AI provider dropdown SHALL show human-readable display names instead of enum constant names.

#### Scenario: Provider names in dropdown
- **WHEN** the user opens the AI provider dropdown
- **THEN** the options SHALL show "None", "Claude", "OpenAI", "Gemini" (not "NONE", "CLAUDE", "OPENAI", "GEMINI")
