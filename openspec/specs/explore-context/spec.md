# Explore Context

## Purpose
Assembled project context for AI-assisted exploration — providing a one-click summary of project configuration, active changes, detected tools, and spec domains for pasting into any AI conversation.

## Requirements

### Requirement: Context assembly

The plugin SHALL assemble a Markdown-formatted project context from the current OpenSpec project state, including config summary with context and rules, detected AI tools, active changes with full artifact content, and spec domain listings with requirement summaries.

#### Scenario: Full context assembly
- **WHEN** the user triggers context assembly
- **THEN** the service SHALL produce a Markdown document with sections for Project Config, Detected AI Tools, Active Changes, and Specs

#### Scenario: Config section includes context and rules
- **WHEN** context is assembled and `config.yaml` has `context` and `rules` fields
- **THEN** the Project Config section SHALL include the schema, version, context description, and rules as a bulleted list

#### Scenario: Active changes with full artifacts
- **WHEN** an active change has artifact files (proposal.md, design.md, tasks.md, delta specs)
- **THEN** the Active Changes section SHALL include the full content of each artifact under the change heading

#### Scenario: Active changes with missing artifacts
- **WHEN** an active change is missing some artifact files
- **THEN** the Active Changes section SHALL include only the artifacts that exist, silently skipping missing ones

#### Scenario: Spec domain with requirement summaries
- **WHEN** a spec domain has a `spec.md` with `### Requirement:` blocks
- **THEN** the Specs section SHALL list each requirement name with its description text (not scenarios)

#### Scenario: No active changes
- **WHEN** the project has no active changes
- **THEN** the Active Changes section SHALL indicate no changes are in progress

### Requirement: Explore panel

The plugin SHALL provide an Explore tab in the OpenSpec tool window only when a Direct API provider is configured (`DirectApiService.isConfigured()` returns true). The tab SHALL display AI explore responses rendered as HTML from markdown, with an inline input area for submitting topics and toolbar actions for copy and clear.

#### Scenario: Tab present when Direct API configured
- **WHEN** the tool window content is created and a Direct API provider is configured with credentials
- **THEN** the Explore tab SHALL be added to the tool window

#### Scenario: Tab absent when no Direct API configured
- **WHEN** the tool window content is created and no Direct API provider is configured
- **THEN** the Explore tab SHALL NOT be added to the tool window

#### Scenario: Panel displays rendered response
- **WHEN** the AI provider returns an explore response
- **THEN** the panel SHALL render the markdown response as styled HTML in the response area

#### Scenario: Copy button
- **WHEN** the user clicks the Copy Response button in the Explore toolbar
- **THEN** the panel SHALL copy the raw markdown response text to the system clipboard with a notification

#### Scenario: Clear button
- **WHEN** the user clicks the Clear button in the Explore toolbar
- **THEN** the panel SHALL reset to the invitation empty state and clear the input area

### Requirement: Lazy Explore tab creation

The plugin SHALL lazily create the Explore tab when `ExplorePanelService.getAndActivate()` is called and the tab does not yet exist but a Direct API provider is now configured. This supports users who configure a provider after project open.

#### Scenario: Lazy creation on first Direct API explore
- **WHEN** `getAndActivate()` is called and no Explore tab exists but Direct API is configured
- **THEN** the service SHALL create the ExplorePanel, add the Explore content tab to the tool window, register the panel, and activate the tab

#### Scenario: No lazy creation without Direct API
- **WHEN** `getAndActivate()` is called and no Explore tab exists and Direct API is NOT configured
- **THEN** the service SHALL return null without creating the tab

#### Scenario: Existing tab reused
- **WHEN** `getAndActivate()` is called and the Explore tab already exists
- **THEN** the service SHALL activate the existing tab without creating a new one

### Requirement: Direct API submit from panel

The Explore panel's inline input SHALL always submit via Direct API delivery, bypassing the global delivery mode resolver. This ensures the panel's submit path matches its rendering capability.

#### Scenario: Panel submit uses Direct API
- **WHEN** the user submits a topic from the Explore panel's inline input
- **THEN** the plugin SHALL build the explore prompt and deliver it via `DirectApiService`, regardless of the globally selected delivery tool

#### Scenario: Menu action retains delivery mode routing
- **WHEN** the user triggers the Explore action from the menu with a non-Direct-API delivery mode
- **THEN** the plugin SHALL show the topic dialog and deliver via the resolved mode (clipboard or editor tab)

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

The plugin SHALL assemble a complete explore prompt consisting of the explore skill instructions, the assembled project context, and the user's topic. The skill instructions SHALL be read from the project's skill files, searching the skills-era location first (`.claude/skills/openspec-explore/SKILL.md`, the CLI's tracked skill surface since its 1.5.0 skills-only migration) and falling back to the legacy pre-1.5 command paths (e.g. `.claude/commands/opsx/explore.md`), with a built-in fallback if no skill file exists. YAML frontmatter (including the 1.6 `allowed-tools`/`generatedBy` stamps) SHALL be stripped from the loaded instructions.

#### Scenario: Skills-era skill file exists
- **WHEN** `.claude/skills/openspec-explore/SKILL.md` exists in the project
- **THEN** the prompt assembly SHALL use its content (frontmatter stripped) as the explore instructions, even when a legacy command-path file also exists

#### Scenario: Legacy skill file only
- **WHEN** only a pre-1.5 command-path skill file exists
- **THEN** the prompt assembly SHALL use that file's content as the explore instructions

#### Scenario: Skill file missing
- **WHEN** no explore skill file exists at any searched location
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
- **THEN** the plugin SHALL write the explore prompt to a temporary file on a pooled thread, perform the VFS `refreshAndFindFileByNioFile` lookup on the same pooled thread, and open the file in an editor tab via `invokeLater` on the EDT

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
