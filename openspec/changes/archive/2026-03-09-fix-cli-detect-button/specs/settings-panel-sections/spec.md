## MODIFIED Requirements

### Requirement: OpenSpec CLI section displays health status prominently
The OpenSpec CLI section SHALL show the CLI detection status and version prominently so users can immediately see if their installation is working.

#### Scenario: CLI is detected and available
- **WHEN** the settings panel opens and the OpenSpec CLI is detected
- **THEN** the CLI status SHALL display the version and path (e.g., "OpenSpec CLI v1.2.0")
- **AND** the status text SHALL use a success color (green)

#### Scenario: CLI is not detected
- **WHEN** the settings panel opens and the OpenSpec CLI is not found
- **THEN** the CLI status SHALL display "OpenSpec CLI not found"
- **AND** the status text SHALL use a warning color

#### Scenario: CLI detection triggered manually
- **WHEN** the user clicks the "Detect" button
- **THEN** the system SHALL scan for the OpenSpec CLI executable
- **AND** update the path field and status display with the result

#### Scenario: CLI detection fails with error
- **WHEN** the CLI detection process throws an unexpected exception
- **THEN** the status label SHALL display an error message (not remain at "Detecting...")
- **AND** the error SHALL be logged at WARN level in idea.log

#### Scenario: CLI detection always resolves
- **WHEN** the user clicks "Detect" or the panel opens
- **THEN** the status label SHALL always transition from "Detecting..." to a terminal state (found, not found, or error) regardless of any exceptions during detection
