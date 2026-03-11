## MODIFIED Requirements

### Requirement: Apply delivery via tool selector
The system SHALL deliver the assembled Apply prompt using the same delivery mechanism as Generate — clipboard, editor tab, or Direct API — based on the user's tool selector choice.

#### Scenario: Clipboard delivery
- **WHEN** the user triggers Apply with a clipboard-based tool selected
- **THEN** the system SHALL copy the assembled prompt to the clipboard
- **AND** show a notification confirming the copy

#### Scenario: IDE panel clipboard Apply guidance
- **WHEN** the user triggers Apply with an IDE panel tool selected via clipboard
- **THEN** the guidance SHALL show the tool-specific paste action (e.g., "Open Copilot Chat and paste the prompt")
- **AND** SHALL show "Save tasks.md when the tool finishes working through the tasks"

#### Scenario: CLI tool clipboard Apply guidance
- **WHEN** the user triggers Apply with a CLI tool selected via clipboard
- **THEN** the guidance SHALL show "Paste into <tool> — watching tasks.md for progress..."

#### Scenario: Editor tab delivery
- **WHEN** the user triggers Apply with Editor Tab selected
- **THEN** the system SHALL open the assembled prompt in a new editor tab

#### Scenario: Direct API delivery
- **WHEN** the user triggers Apply with Direct API selected
- **THEN** the system SHALL send the assembled prompt to the configured AI provider
