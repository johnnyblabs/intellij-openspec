## MODIFIED Requirements

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
