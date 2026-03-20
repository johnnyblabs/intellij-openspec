## ADDED Requirements

### Requirement: Explore panel

The plugin SHALL provide an Explore tab in the OpenSpec tool window that displays assembled project context for investigation and idea exploration.

#### Scenario: Context display
- **WHEN** the user opens the Explore tab
- **THEN** the panel SHALL display config summary, active changes overview, spec domain listing, and detected AI tools

#### Scenario: Copy to clipboard
- **WHEN** the user clicks "Copy to Clipboard"
- **THEN** the full assembled context SHALL be copied to the system clipboard (preserving existing ExploreContextAction behavior)

#### Scenario: Open in editor
- **WHEN** the user clicks "Open in Editor"
- **THEN** the context SHALL open as a scratch file in the editor for review and editing before pasting to an AI tool

### Requirement: Explore panel auto-refresh

The plugin SHALL refresh the Explore panel content when project files under `openspec/` change.

#### Scenario: File change detection
- **WHEN** files under `openspec/` are created, modified, or deleted
- **THEN** the Explore panel SHALL refresh its content to reflect the current project state
