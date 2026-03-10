## MODIFIED Requirements

### Requirement: Settings panel uses distinct visual sections
The settings panel SHALL organize controls into visually distinct sections: an "OpenSpec CLI" titled section at the top, a "General" section for project preferences, and a "Direct API" section for AI provider configuration. No tabbed pane.

#### Scenario: Panel layout on open
- **WHEN** the user opens OpenSpec settings (Tools > OpenSpec)
- **THEN** the panel SHALL display an "OpenSpec CLI" section at the top with CLI path, detect button, and version status
- **AND** a "General" section below with schema profile and preference checkboxes
- **AND** a "Direct API" section below with provider, API key, model, and test button

#### Scenario: No tabbed pane
- **WHEN** the user views the settings panel
- **THEN** all configuration SHALL be visible without switching tabs

## REMOVED Requirements

### Requirement: Each section includes contextual help text
**Reason:** The "Tools & Delivery" tab and its help text are removed. Delivery selection moves to the workflow panel. The Direct API section retains its own help text inline.
**Migration:** Users select delivery tools in the workflow panel's tool selector dropdown instead of in settings.
