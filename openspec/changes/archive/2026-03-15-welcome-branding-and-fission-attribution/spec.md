# Spec: Welcome Branding and Fission AI Attribution

## Requirements

### MUST
- The welcome screen title SHALL display "Welcome to OpenSpec Plugin"
- The welcome screen SHALL include an attribution line crediting Fission AI as the creators of OpenSpec

### SHOULD
- The attribution text SHOULD be styled subtly (gray, small font) to avoid visual clutter

## Scenarios

### Scenario: User opens Setup Wizard
- **Given** a user opens the Setup Wizard for the first time
- **When** the welcome step is displayed
- **Then** the title reads "Welcome to OpenSpec Plugin"
- **And** an attribution line reading "Built to support the amazing work of Fission AI, the creators of OpenSpec." is displayed below the description
