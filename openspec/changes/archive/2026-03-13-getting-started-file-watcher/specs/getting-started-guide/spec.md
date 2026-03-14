## Delta: getting-started-guide

### ADD Requirement: GettingStartedPanel reacts to external filesystem changes

The GettingStartedPanel SHALL monitor the `openspec/` directory for filesystem changes and automatically re-evaluate its state when changes are detected.

#### Scenario: External change creation triggers transition
- **GIVEN** the GettingStartedPanel is displayed in the `NO_CHANGES` state
- **WHEN** a change directory is created externally (e.g., via CLI or AI tool)
- **THEN** the panel SHALL detect the new state and transition to the normal tree view

#### Scenario: External initialization triggers panel update
- **GIVEN** the GettingStartedPanel is displayed in the `NOT_INITIALIZED` state
- **WHEN** the `openspec/` directory is created externally
- **THEN** the panel SHALL rebuild to show the next step (AI configuration or propose)

#### Scenario: Listener is cleaned up on transition
- **GIVEN** the GettingStartedPanel has an active filesystem listener
- **WHEN** the panel transitions to the normal tree view
- **THEN** the listener SHALL be disposed and no longer process events
