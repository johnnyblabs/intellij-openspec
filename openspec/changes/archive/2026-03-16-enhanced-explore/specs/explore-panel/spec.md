## ADDED Requirements

### Requirement: Explore tool window tab

The plugin SHALL display an Explore tab in the OpenSpec tool window that shows the assembled project context in a read-only, scrollable text area.

#### Scenario: Tab visibility
- **WHEN** the OpenSpec tool window is opened for a configured project
- **THEN** an Explore tab SHALL appear alongside the Browse, Coverage, and Console tabs

#### Scenario: Context display
- **WHEN** the Explore tab is selected
- **THEN** the panel SHALL display the assembled context including config summary, active changes with proposal summaries, spec domains, and detected AI tools

### Requirement: Context assembly service

The plugin SHALL provide an `ExploreContextService` registered as a project-level service that assembles project context from config, changes, specs, and detected tools.

#### Scenario: Service produces context
- **WHEN** `ExploreContextService.assembleContext()` is called
- **THEN** it SHALL return a Markdown-formatted string containing config summary, active changes, spec domains, and detected AI tools

#### Scenario: Service reuse
- **WHEN** both `ExplorePanel` and `ExploreContextAction` need context
- **THEN** both SHALL delegate to `ExploreContextService` for assembly

### Requirement: Copy to Clipboard button

The plugin SHALL provide a Copy to Clipboard button in the Explore panel toolbar that copies the assembled context to the system clipboard and shows a confirmation notification.

#### Scenario: Copy action
- **WHEN** the user clicks the Copy to Clipboard button
- **THEN** the assembled context SHALL be copied to the system clipboard and a success notification SHALL appear

#### Scenario: Backward compatibility
- **WHEN** the existing ExploreContextAction is invoked from the menu
- **THEN** it SHALL produce the same context output as the Explore panel's copy action

### Requirement: Open in Editor button

The plugin SHALL provide an Open in Editor button in the Explore panel toolbar that opens the assembled context as a Markdown scratch file in the editor.

#### Scenario: Scratch file creation
- **WHEN** the user clicks the Open in Editor button
- **THEN** a Markdown scratch file SHALL be created (or updated if one already exists) containing the assembled context, and it SHALL be opened in the editor

#### Scenario: Scratch file reuse
- **WHEN** the user clicks Open in Editor multiple times
- **THEN** the same scratch file SHALL be updated and focused rather than creating duplicate files

### Requirement: Auto-refresh on openspec file changes

The plugin SHALL automatically refresh the Explore panel content when files under the `openspec/` directory are created, modified, or deleted, with debounce to prevent excessive refreshes.

#### Scenario: File change triggers refresh
- **WHEN** a file under `openspec/` is created, modified, or deleted
- **THEN** the Explore panel content SHALL refresh within 1 second

#### Scenario: Rapid changes are debounced
- **WHEN** multiple file changes occur within 500 milliseconds
- **THEN** the panel SHALL refresh only once after the changes settle

#### Scenario: Listener lifecycle
- **WHEN** the Explore panel is disposed (tool window closed or project closed)
- **THEN** the VFS listener SHALL be unsubscribed and debounce timers SHALL be cancelled

### Requirement: Context assembly test coverage

The plugin SHALL include unit tests for `ExploreContextService` verifying context assembly with various project states.

#### Scenario: Empty project
- **WHEN** context is assembled for a project with no config, changes, or specs
- **THEN** the output SHALL contain section headers with "None" or empty indicators

#### Scenario: Full project
- **WHEN** context is assembled for a project with config, active changes, specs, and detected tools
- **THEN** the output SHALL contain all sections populated with correct data
