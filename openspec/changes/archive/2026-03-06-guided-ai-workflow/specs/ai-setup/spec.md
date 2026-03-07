## ADDED Requirements

### Requirement: First-Run AI Setup

The plugin SHALL display an inline setup card in the Workflow Action Panel when the user first attempts to generate an artifact and no preferred delivery method is configured.

#### Scenario: First generate with detected tools
- **WHEN** the user clicks Generate for the first time and AI tools are detected in the project
- **THEN** the panel SHALL display an inline card listing detected tools and available delivery methods
- **THEN** the user SHALL be able to select a method which becomes the preferred default

#### Scenario: First generate with no tools detected
- **WHEN** the user clicks Generate for the first time and no AI tools are detected
- **THEN** the panel SHALL display an inline card with options to configure a Direct API provider or use clipboard mode

#### Scenario: Setup card dismissal
- **WHEN** the user selects a delivery method from the setup card
- **THEN** the setup card SHALL be replaced by the normal Generate button using the selected method
