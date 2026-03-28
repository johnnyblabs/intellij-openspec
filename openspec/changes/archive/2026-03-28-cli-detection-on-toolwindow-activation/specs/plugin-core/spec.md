## ADDED Requirements

### Requirement: CLI re-detection on tool window activation

The plugin SHALL re-run CLI detection when the OpenSpec tool window is activated (shown). Detection SHALL be throttled to skip if the last detection was within the last 30 seconds. After re-detection, the status bar SHALL update to reflect the current CLI availability.

#### Scenario: Tool window activated with stale detection
- **WHEN** the OpenSpec tool window is shown and the last detection was more than 30 seconds ago
- **THEN** the plugin SHALL re-run CLI detection on a background thread and update the status bar on the EDT

#### Scenario: Tool window activated with fresh detection
- **WHEN** the OpenSpec tool window is shown and the last detection was within 30 seconds
- **THEN** the plugin SHALL skip re-detection

#### Scenario: CLI installed after project open
- **WHEN** the user installs the OpenSpec CLI after the project is opened and then activates the tool window
- **THEN** the status bar SHALL update to show "CLI: available" after re-detection completes