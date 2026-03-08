## MODIFIED Requirements

### Requirement: First-Run AI Setup
The plugin SHALL display an inline setup card in the Workflow Action Panel when the user first attempts to generate an artifact and no preferred delivery method is configured. When multiple AI tools are detected, the setup card SHALL include a tool selector. Preferences set via the setup card SHALL be editable later in the settings panel (Tools > OpenSpec > Tools & Delivery tab).

#### Scenario: First generate with multiple detected tools
- **WHEN** the user clicks Generate for the first time and multiple AI tools are detected
- **THEN** the setup card SHALL display a tool selector (combo box) listing all detected tools, pre-selecting the first
- **AND** the user SHALL select which tool they are targeting before choosing a delivery method

#### Scenario: First generate with single detected tool
- **WHEN** the user clicks Generate for the first time and exactly one AI tool is detected
- **THEN** the setup card SHALL auto-select that tool and show delivery method options directly

#### Scenario: First generate with no tools detected
- **WHEN** the user clicks Generate for the first time and no AI tools are detected
- **THEN** the setup card SHALL display delivery method options without a tool selector

#### Scenario: Tool selection persisted
- **WHEN** the user selects a tool from the setup card
- **THEN** the selected tool SHALL be saved to project settings and used for all subsequent guidance labels and instructions
- **AND** the selection SHALL be reflected in the settings panel's preferred tool dropdown

#### Scenario: Setup card dismissal
- **WHEN** the user selects a delivery method from the setup card
- **THEN** the setup card SHALL be replaced by the normal Generate button using the selected method
- **AND** the selection SHALL be reflected in the settings panel's delivery method dropdown
