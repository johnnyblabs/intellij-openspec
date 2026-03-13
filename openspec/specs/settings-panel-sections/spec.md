# Settings Panel Sections

## Purpose
Visual organization of the settings panel into distinct sections. All configuration is visible without tabs.

## Requirements

### Requirement: Settings panel uses distinct visual sections
The settings panel SHALL organize controls into visually distinct sections: a "Run Setup Wizard" button at the top, an "OpenSpec CLI" titled section, a "General" section for project preferences, and a "Direct API" section for AI provider configuration. No tabbed pane.

#### Scenario: Panel layout on open
- **WHEN** the user opens OpenSpec settings (Tools > OpenSpec)
- **THEN** the panel SHALL display a "Run Setup Wizard..." button at the top
- **AND** an "OpenSpec CLI" section below with CLI path, detect button, and version status
- **AND** a "General" section below with schema profile and preference checkboxes
- **AND** a "Direct API" section below with provider, API key, model, and test button

#### Scenario: No tabbed pane
- **WHEN** the user views the settings panel
- **THEN** all configuration SHALL be visible without switching tabs

#### Scenario: Setup Wizard button
- **WHEN** the user clicks "Run Setup Wizard..."
- **THEN** the Setup Wizard dialog SHALL open

### Requirement: OpenSpec CLI section displays health status prominently
The OpenSpec CLI section SHALL show the CLI detection status and version prominently so users can immediately see if their installation is working. Status colors SHALL use `JBColor` for theme compatibility.

#### Scenario: CLI is detected and available
- **WHEN** the settings panel opens and the OpenSpec CLI is detected
- **THEN** the CLI status SHALL display the version and path (e.g., "OpenSpec CLI v1.2.0")
- **AND** the status text SHALL use a `JBColor` success color that renders correctly in both light and dark themes

#### Scenario: CLI is not detected
- **WHEN** the settings panel opens and the OpenSpec CLI is not found
- **THEN** the CLI status SHALL display "OpenSpec CLI not found"
- **AND** the status text SHALL use a `JBColor` warning color that renders correctly in both light and dark themes

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

### Requirement: AI provider dropdown shows display names
The AI provider dropdown SHALL show human-readable display names instead of enum constant names.

#### Scenario: Provider names in dropdown
- **WHEN** the user opens the AI provider dropdown
- **THEN** the options SHALL show "None", "Claude", "OpenAI", "Gemini" (not "NONE", "CLAUDE", "OPENAI", "GEMINI")

### Requirement: CLI command timeout configuration

The settings panel SHALL include a CLI timeout setting that controls how long CLI commands are allowed to run before being terminated.

#### Scenario: CLI timeout control in General section
- **WHEN** the user opens OpenSpec settings
- **THEN** the General section SHALL include a "CLI Timeout (seconds)" spinner with a range of 1-3600 and a default of 30

#### Scenario: CLI timeout persisted
- **WHEN** the user changes the CLI timeout value and clicks Apply
- **THEN** the new timeout value SHALL be persisted in `OpenSpecSettings`

#### Scenario: CliRunner reads timeout from settings
- **WHEN** a CLI command is executed via `CliRunner.run(project, args)`
- **THEN** the timeout SHALL be read from `OpenSpecSettings.getCliTimeoutSeconds()` instead of using a hardcoded default

#### Scenario: Timeout exceeded shows clear error
- **WHEN** a CLI command exceeds the configured timeout
- **THEN** the error message SHALL include the timeout duration
