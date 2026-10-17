## MODIFIED Requirements

### Requirement: CLI re-detection on tool window activation

The plugin SHALL re-run CLI detection when the OpenSpec tool window is activated (shown), and when the IDE window of a project with the OpenSpec tool window regains focus — so a CLI upgraded during a long-running session is noticed without reopening the project or re-showing the tool window. Focus-regain re-detection SHALL apply only to the project whose window regained focus. Detection SHALL be throttled to skip if the last detection was within the last 30 seconds. After re-detection, the status bar SHALL update to reflect the current CLI availability.

#### Scenario: Tool window activated with stale detection
- **WHEN** the OpenSpec tool window is shown and the last detection was more than 30 seconds ago
- **THEN** the plugin SHALL re-run CLI detection on a background thread and update the status bar on the EDT

#### Scenario: Tool window activated with fresh detection
- **WHEN** the OpenSpec tool window is shown and the last detection was within 30 seconds
- **THEN** the plugin SHALL skip re-detection

#### Scenario: CLI installed after project open
- **WHEN** the user installs the OpenSpec CLI after the project is opened and then activates the tool window
- **THEN** the status bar SHALL update to show "CLI: available" after re-detection completes

#### Scenario: CLI upgraded mid-session is picked up on focus-regain
- **WHEN** the OpenSpec CLI is upgraded while the IDE is running, the OpenSpec tool window has stayed visible, and the user returns focus to that project's IDE window more than 30 seconds after the last detection
- **THEN** the plugin SHALL re-run CLI detection on a background thread and the status SHALL show the new version, without reopening the project or hiding and re-showing the tool window

#### Scenario: Focus-regain within the throttle window does not re-detect
- **WHEN** the IDE window regains focus within 30 seconds of the last detection
- **THEN** the plugin SHALL skip re-detection, so rapid window switching never launches repeated CLI runs

#### Scenario: Only the focused project's window triggers re-detection
- **WHEN** several projects are open and the IDE window of one of them regains focus
- **THEN** only that project SHALL re-run CLI detection
