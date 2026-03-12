## MODIFIED Requirements

### Requirement: Getting Started Panel

The tool window SHALL display a state-aware Getting Started panel instead of the normal tree view when the project is not fully set up. The panel SHALL use the OpenSpec icon for branding. Card description text SHALL wrap within the tool window width. After a successful propose action from the panel, the tool window SHALL automatically transition to the normal tree view.

#### Scenario: Project not initialized
- **WHEN** the tool window opens and no `openspec/` directory exists
- **THEN** the panel SHALL display an "Initialize your project" card with an Initialize button
- **AND** a "Run Setup Wizard" hyperlink

#### Scenario: AI not configured
- **WHEN** the project is initialized but no delivery method is configured
- **THEN** the panel SHALL display a "Configure your AI tool" card with a Configure button
- **AND** a "Run Setup Wizard" hyperlink

#### Scenario: No active changes
- **WHEN** the project is initialized, AI is configured, but no active changes exist
- **THEN** the panel SHALL display a "Create your first change" card with a Propose button

#### Scenario: Ready state
- **WHEN** the project has active changes
- **THEN** the normal tree view and workflow panel SHALL be displayed

#### Scenario: Branded icon in Getting Started cards
- **WHEN** a Getting Started card is displayed
- **THEN** it SHALL use the OpenSpec icon instead of `AllIcons.General.Information`

#### Scenario: Card description text wraps
- **WHEN** a Getting Started card is displayed in a narrow tool window
- **THEN** the description text SHALL wrap within the available width instead of clipping or overflowing

#### Scenario: Auto-transition after first propose
- **WHEN** the user clicks "Propose a Change" and successfully creates a change from the Getting Started panel
- **THEN** the tool window SHALL replace the Getting Started panel with the normal Browse and Console tabs
