## ADDED Requirements

### Requirement: CLI status reflects the latest completed detection

The plugin's CLI status SHALL always reflect the most recent completed CLI detection, whichever path triggered it (project open, tool-window activation, focus regain, Settings, or a manual refresh). A detection in progress SHALL NOT be observable as "CLI unavailable": readers SHALL see either the previous result or the new one. Before the first detection completes, the status SHALL show that detection is in progress rather than that the CLI is missing.

#### Scenario: Tool window built while startup detection is running
- **WHEN** the OpenSpec tool window is created while the project-open CLI detection is still running and the CLI is installed
- **THEN** the status SHALL show detection in progress, then "CLI: available" when detection completes, without the user refreshing

#### Scenario: Detection in progress never reads as unavailable
- **WHEN** a CLI detection is re-run while a previous detection found the CLI
- **THEN** any reader during the re-run SHALL still see the CLI as available until the new result is published

#### Scenario: Any completed detection updates the status
- **WHEN** a CLI detection completes from any trigger, including one the throttled tool-window re-check skipped
- **THEN** the tool window's CLI status SHALL update to the new result
