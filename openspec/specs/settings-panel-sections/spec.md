# Settings Panel Sections

## Purpose
Visual organization of the settings panel into distinct sections. All configuration is visible without tabs.

## Requirements

### Requirement: Settings panel uses distinct visual sections
The settings panel SHALL organize controls into visually distinct sections: an "OpenSpec CLI" titled section at the top, a "General" section for project preferences, a "Direct API" section for AI provider configuration, and an "Issue Tracking" section for Forgejo and Plane tracker configuration. No tabbed pane.

#### Scenario: Panel layout on open
- **WHEN** the user opens OpenSpec settings (Tools > OpenSpec)
- **THEN** the panel SHALL display an "OpenSpec CLI" section at the top with CLI path, detect button, and version status
- **AND** a "General" section below with schema profile and preference checkboxes
- **AND** a "Direct API" section below with provider, API key, model, and test button
- **AND** an "Issue Tracking" section below with Forgejo and Plane sub-groups

#### Scenario: No tabbed pane
- **WHEN** the user views the settings panel
- **THEN** all configuration SHALL be visible without switching tabs

#### Scenario: Issue Tracking section layout
- **WHEN** the user scrolls to the "Issue Tracking" section
- **THEN** the section SHALL display a Forgejo sub-group with enable checkbox, server URL, repository owner, repository name, token field, and "Test Connection" button
- **AND** a Plane sub-group with enable checkbox, server URL, workspace slug, project identifier, API key field, and "Test Connection" button

#### Scenario: Tracker fields disabled when unchecked
- **WHEN** the user unchecks the enable checkbox for a tracker
- **THEN** all fields in that tracker's sub-group SHALL be disabled

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

### Requirement: AI provider dropdown shows display names
The AI provider dropdown SHALL show human-readable display names instead of enum constant names.

#### Scenario: Provider names in dropdown
- **WHEN** the user opens the AI provider dropdown
- **THEN** the options SHALL show "None", "Claude", "OpenAI", "Gemini" (not "NONE", "CLAUDE", "OPENAI", "GEMINI")
