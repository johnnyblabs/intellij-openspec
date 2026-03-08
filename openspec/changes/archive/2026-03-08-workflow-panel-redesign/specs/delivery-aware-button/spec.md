## ADDED Requirements

### Requirement: Generate button displays delivery method
The Generate button label SHALL include the delivery method so users know what will happen before clicking.

#### Scenario: Clipboard delivery label
- **WHEN** the resolved delivery method is clipboard-based (e.g., "Copy for Claude Code")
- **THEN** the Generate button SHALL display "Generate {artifactId} \u2192 clipboard"

#### Scenario: Direct API delivery label
- **WHEN** the resolved delivery method is Direct API
- **THEN** the Generate button SHALL display "Generate {artifactId} \u2192 API"

#### Scenario: Editor tab delivery label
- **WHEN** the resolved delivery method is editor tab
- **THEN** the Generate button SHALL display "Generate {artifactId} \u2192 editor tab"

#### Scenario: Button label updates on delivery method change
- **WHEN** the user selects a different delivery method from the dropdown
- **THEN** the Generate button label SHALL update immediately to reflect the new method
