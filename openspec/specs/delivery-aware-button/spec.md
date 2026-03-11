# Delivery Aware Button

## Purpose
Generate button that displays the active delivery method in its label.

## Requirements

### Requirement: Generate button displays delivery method
The Generate button label SHALL include the delivery method so users know what will happen before clicking.

#### Scenario: Clipboard delivery label
- **WHEN** the resolved delivery method is clipboard-based (e.g., "Copy for Claude Code")
- **THEN** the Generate button SHALL display "Generate {artifactId} -> clipboard"

#### Scenario: Direct API delivery label
- **WHEN** the resolved delivery method is Direct API
- **THEN** the Generate button SHALL display "Generate {artifactId} -> API"

#### Scenario: Editor tab delivery label
- **WHEN** the resolved delivery method is editor tab
- **THEN** the Generate button SHALL display "Generate {artifactId} -> editor tab"

#### Scenario: Button label updates on delivery method change
- **WHEN** the user selects a different delivery method from the dropdown
- **THEN** the Generate button label SHALL update immediately to reflect the new method

### Requirement: Post-click inline guidance area

The workflow panel SHALL display an inline guidance area below the Generate/Apply button after a delivery action, showing tool-specific instructions.

#### Scenario: Guidance area appears after clipboard delivery
- **WHEN** a Generate or Apply action is delivered via clipboard
- **THEN** an inline guidance area SHALL appear below the button with status, action, and save instructions

#### Scenario: Guidance area appears after editor tab delivery
- **WHEN** a Generate or Apply action is delivered via editor tab
- **THEN** an inline guidance area SHALL appear with instructions to copy from the editor tab to the tool

#### Scenario: Guidance area hidden before first delivery
- **WHEN** no delivery action has been performed yet
- **THEN** the inline guidance area SHALL not be visible
