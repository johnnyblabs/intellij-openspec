## ADDED Requirements

### Requirement: Preferred tool detection is regression-tested
The system SHALL provide automated tests that verify preferred tool detection outcomes consumed by the "Tools & Delivery" settings UI.

#### Scenario: Detected tools are returned for settings population
- **WHEN** automated tests run with valid project and tool fixtures
- **THEN** the detection flow SHALL return one or more tool entries with stable labels for settings display

#### Scenario: No detected tools falls back to None
- **WHEN** automated tests run with fixtures where no supported tools are detectable
- **THEN** the settings flow SHALL expose only the "None" option as the preferred tool fallback

#### Scenario: Invalid detection inputs are handled safely
- **WHEN** automated tests run with invalid or incomplete detection inputs
- **THEN** detection SHALL complete without unhandled exceptions and SHALL return a safe fallback result

#### Scenario: Tool type indicators remain correct
- **WHEN** automated tests run for detected tools with known classification types
- **THEN** each detected tool SHALL retain the expected type indicator used by the settings dropdown

### Requirement: Preferred tool fallback behavior is regression-tested
The system SHALL provide automated tests that verify default selection and persistence fallback behavior for preferred tool settings.

#### Scenario: Default selection uses first detected tool when preference is unset
- **WHEN** automated tests run with no saved preferred tool and one or more detected tools
- **THEN** preferred tool resolution SHALL select the first detected tool

#### Scenario: Saved preferred tool remains selected when available
- **WHEN** automated tests run with a saved preferred tool that is present in detected tools
- **THEN** preferred tool resolution SHALL preserve the saved selection

#### Scenario: Missing saved preferred tool falls back safely
- **WHEN** automated tests run with a saved preferred tool that is not present in detected tools
- **THEN** preferred tool resolution SHALL fall back to a valid detected tool or "None" when no tools are available
