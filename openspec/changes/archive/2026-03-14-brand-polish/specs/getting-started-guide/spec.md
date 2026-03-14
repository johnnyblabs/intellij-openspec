## ADDED Requirements

### Requirement: Branded onboarding visuals

The plugin's onboarding screens SHALL display a prominent OpenSpec brand icon and tagline for a polished first impression.

#### Scenario: Getting started panel shows brand icon
- **WHEN** the GettingStartedPanel displays a welcome state (NOT_INITIALIZED, NO_AI_CONFIGURED, or NO_CHANGES)
- **THEN** it SHALL display the 32x32 OpenSpec brand icon above the title

#### Scenario: Getting started panel shows tagline
- **WHEN** the GettingStartedPanel displays a welcome state
- **THEN** it SHALL display "Spec-Driven Development" as a subtitle below the "OpenSpec" title in a smaller gray font

#### Scenario: Setup wizard shows brand icon
- **WHEN** the SetupWizardDialog displays the welcome step or the done step
- **THEN** it SHALL display the 32x32 OpenSpec brand icon

#### Scenario: Brand icon has dark theme variant
- **WHEN** the IDE is using a dark theme
- **THEN** the brand icon SHALL use the dark variant (`openspec-brand_dark.svg`) automatically via IntelliJ's icon convention
