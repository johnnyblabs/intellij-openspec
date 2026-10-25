## ADDED Requirements

### Requirement: Explore tab position

When the Explore tab is present, it SHALL come immediately after the Browse tab, before Console and Coordination, however it was created.

#### Scenario: Created with the tool window
- **WHEN** the tool window content is created with a Direct API provider configured
- **THEN** the tabs SHALL read Browse, Explore, Console

#### Scenario: Created later
- **WHEN** the Explore tab is created lazily after the tool window already shows Browse and Console, and Coordination when present
- **THEN** the Explore tab SHALL be inserted immediately after Browse
