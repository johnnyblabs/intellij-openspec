## MODIFIED Requirements

### Requirement: Clipboard action

The plugin SHALL provide a menu action that prompts the user for an optional explore topic, assembles the full explore prompt (skill instructions + project context + topic), and copies it to the system clipboard, notifying the user on completion.

#### Scenario: Copy explore prompt to clipboard
- **WHEN** the user triggers the Explore action from the menu and the resolved delivery mode is Clipboard
- **THEN** the plugin SHALL show a topic input dialog, assemble the explore prompt with project context and the user's topic, copy the result to the system clipboard, and show a notification: "Explore prompt copied — paste into your AI tool to start exploring."

#### Scenario: Blank topic defaults to open exploration
- **WHEN** the user triggers the Explore action and leaves the topic field empty
- **THEN** the assembled prompt SHALL include a default open-ended topic indicating open exploration mode

#### Scenario: User cancels the topic dialog
- **WHEN** the user dismisses the topic input dialog without confirming
- **THEN** the action SHALL do nothing and return without copying or delivering

## ADDED Requirements

### Requirement: Topic input dialog

The plugin SHALL display a lightweight dialog prompting the user for an optional explore topic before delivery. The dialog SHALL have a single text field with placeholder text and OK/Cancel buttons.

#### Scenario: Dialog with topic
- **WHEN** the user enters a topic and clicks OK
- **THEN** the action SHALL proceed with the entered topic

#### Scenario: Dialog with empty topic
- **WHEN** the user clicks OK without entering text
- **THEN** the action SHALL proceed with a default open exploration topic

#### Scenario: Dialog cancelled
- **WHEN** the user clicks Cancel or presses Escape
- **THEN** the action SHALL abort without any delivery

### Requirement: Explore prompt assembly

The plugin SHALL assemble a complete explore prompt consisting of the explore skill instructions, the assembled project context, and the user's topic. The skill instructions SHALL be read from the project's skill files (e.g., `.claude/commands/opsx/explore.md`) with a built-in fallback if the file does not exist.

#### Scenario: Skill file exists
- **WHEN** the explore skill file exists in the project
- **THEN** the prompt assembly SHALL use the skill file content as the explore instructions

#### Scenario: Skill file missing
- **WHEN** the explore skill file does not exist
- **THEN** the prompt assembly SHALL use a built-in default explore prompt that covers the core explore stance and guardrails

#### Scenario: Prompt structure
- **WHEN** the prompt is assembled
- **THEN** it SHALL contain three sections in order: explore instructions, project context (from ExploreContextService), and the user's topic

### Requirement: Delivery mode routing

The plugin SHALL route the assembled explore prompt through the user's configured delivery mode, supporting Direct API, Editor Tab, and Clipboard delivery.

#### Scenario: Direct API delivery
- **WHEN** the resolved delivery mode is Direct API and an AI provider is configured
- **THEN** the plugin SHALL send the explore prompt to the configured AI provider on a background thread and display the response in the Explore tab

#### Scenario: Editor Tab delivery
- **WHEN** the resolved delivery mode is Editor Tab
- **THEN** the plugin SHALL write the explore prompt to a temporary scratch file and open it in an editor tab

#### Scenario: Clipboard delivery
- **WHEN** the resolved delivery mode is Clipboard
- **THEN** the plugin SHALL copy the explore prompt to the system clipboard and show a notification

#### Scenario: Direct API not configured
- **WHEN** the resolved delivery mode is Direct API but no provider is configured
- **THEN** the plugin SHALL fall back to Clipboard delivery with a notification suggesting the user configure an AI provider

### Requirement: Explore panel rework

The plugin SHALL rework the Explore tab from a passive read-only context viewer into an explore results panel displaying the topic, AI response, and toolbar actions for re-exploring and copying.

#### Scenario: Display explore response
- **WHEN** the AI provider returns a successful response via Direct API
- **THEN** the plugin SHALL activate the Explore tab, display the topic as a header, and display the AI response in the content area

#### Scenario: Display API error
- **WHEN** the AI provider returns an error
- **THEN** the plugin SHALL display the error in the Explore tab with error styling and show a notification

#### Scenario: Background execution with progress
- **WHEN** the explore prompt is sent via Direct API
- **THEN** the API call SHALL execute on a background thread without blocking the UI, with a progress indicator in the Explore tab

#### Scenario: Toolbar actions
- **WHEN** the Explore tab displays a response
- **THEN** the toolbar SHALL provide actions for: New Explore (re-open topic dialog), Copy Response (copy to clipboard), and Refresh (re-run the last explore)

### Requirement: Explore panel removed requirements

The Explore panel SHALL no longer display raw assembled project context as its primary view. The previous read-only context viewer, auto-refresh on VFS changes, and Open in Editor functionality are replaced by the explore results display.

#### Scenario: No passive context display
- **WHEN** the user opens the Explore tab without having run an explore
- **THEN** the panel SHALL display a prompt encouraging the user to start an explore session (e.g., "Run Explore from the menu or click New Explore to start.")
