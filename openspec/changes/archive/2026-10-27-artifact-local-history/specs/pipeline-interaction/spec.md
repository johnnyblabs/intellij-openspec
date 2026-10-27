## ADDED Requirements

### Requirement: Show Local History from a DONE chip

A DONE chip's right-click menu SHALL offer "Show Local History", which opens the IDE's own Local History view for the artifact's file, or for the change's `specs/` folder when the artifact is `specs`. The item SHALL be left out when that file or folder doesn't exist, when Local History doesn't track it, or when the IDE has no Local History view. The wording SHALL say "Local History", never an OpenSpec "version".

#### Scenario: A single-file artifact
- **WHEN** the user right-clicks the DONE `design` chip and chooses "Show Local History"
- **THEN** the IDE's Local History view SHALL open for the change's `design.md`

#### Scenario: The specs artifact
- **WHEN** the user chooses "Show Local History" on the DONE `specs` chip
- **THEN** the IDE's Local History view SHALL open for the change's `specs/` folder

#### Scenario: Nothing to show
- **WHEN** the artifact's file doesn't exist, Local History doesn't track it, or the IDE has no Local History view
- **THEN** the menu SHALL NOT include "Show Local History"
