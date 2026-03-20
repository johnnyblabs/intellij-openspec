# Explore Context

## Purpose
Assembled project context for AI-assisted exploration — providing a one-click summary of project configuration, active changes, detected tools, and spec domains for pasting into any AI conversation.

## Requirements

### Requirement: Context assembly

The plugin SHALL assemble a Markdown-formatted project context from the current OpenSpec project state, including config summary, detected AI tools, active changes with proposal summaries, and spec domain listings.

#### Scenario: Full context assembly
- **WHEN** the user triggers context assembly
- **THEN** the service SHALL produce a Markdown document with sections for Project Config, Detected AI Tools, Active Changes, and Specs

#### Scenario: Config section
- **WHEN** context is assembled
- **THEN** the Project Config section SHALL include the schema name and version from `openspec/config.yaml`

#### Scenario: Active changes with proposal summary
- **WHEN** an active change has a `proposal.md` file
- **THEN** the Active Changes section SHALL include the change name and a truncated summary of the proposal (maximum 500 characters)

#### Scenario: Spec domain listing
- **WHEN** the project has spec domains under `openspec/specs/`
- **THEN** the Specs section SHALL list each domain that contains a `spec.md` file

#### Scenario: No active changes
- **WHEN** the project has no active changes
- **THEN** the Active Changes section SHALL be omitted or indicate no changes are in progress

### Requirement: Clipboard action

The plugin SHALL provide a menu action that assembles context and copies it to the system clipboard, notifying the user on completion.

#### Scenario: Copy to clipboard
- **WHEN** the user triggers the Explore Context action from the menu
- **THEN** the plugin SHALL assemble context on a background thread, copy the result to the system clipboard, and show a notification: "Context copied — paste into your AI tool to start exploring."

### Requirement: Explore panel

The plugin SHALL provide an Explore tab in the OpenSpec tool window displaying the assembled context with toolbar actions for refresh, copy, and open in editor.

#### Scenario: Panel displays context
- **WHEN** the user opens the Explore tab
- **THEN** the panel SHALL display the assembled context in a read-only text area

#### Scenario: Refresh button
- **WHEN** the user clicks the Refresh button in the Explore toolbar
- **THEN** the panel SHALL re-assemble the context and update the display

#### Scenario: Copy button
- **WHEN** the user clicks the Copy to Clipboard button in the Explore toolbar
- **THEN** the panel SHALL copy the current text area content to the system clipboard with a notification

#### Scenario: Open in editor
- **WHEN** the user clicks the Open in Editor button
- **THEN** the plugin SHALL create or reuse a scratch file named "OpenSpec-Explore.md" and open it in the editor with the assembled context

### Requirement: Auto-refresh on file changes

The plugin SHALL auto-refresh the Explore panel when files under the `openspec/` directory change, with a debounce to avoid excessive updates.

#### Scenario: VFS change triggers refresh
- **WHEN** a file under `openspec/` is created, modified, or deleted
- **THEN** the panel SHALL schedule a refresh after a 500ms debounce delay

#### Scenario: Rapid changes debounced
- **WHEN** multiple file changes occur within 500ms
- **THEN** the panel SHALL execute only one refresh after the final change
