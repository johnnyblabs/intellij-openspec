# Explore Context

## Purpose
Assembled project context for AI-assisted exploration — providing a one-click summary of project configuration, active changes, detected tools, and spec domains for pasting into any AI conversation.

## Requirements

### Requirement: Context assembly

The plugin SHALL assemble a Markdown-formatted project context from the current OpenSpec project state: a config summary with context and rules, detected AI tools, active changes with the content of every artifact file each change has, and every capability with its requirement summaries. Capabilities and artifact files at nested paths SHALL be included and named by their full path.

#### Scenario: Full context assembly
- **WHEN** the user triggers context assembly
- **THEN** the service SHALL produce a Markdown document with sections for Project Config, Detected AI Tools, Active Changes, and Specs

#### Scenario: Config section includes context and rules
- **WHEN** context is assembled and `config.yaml` has `context` and `rules` fields
- **THEN** the Project Config section SHALL include the schema, context description, and rules as a bulleted list
- **AND** it SHALL NOT include a `version` field, which upstream does not read

#### Scenario: Active changes with full artifacts
- **WHEN** an active change has artifact files and the CLI reports them in its status for the change
- **THEN** the Active Changes section SHALL include the full content of each reported file under the change heading, in the order the CLI lists the artifacts
- **AND** each file SHALL be labelled with its artifact id, and a file of an artifact that generates several files SHALL also be labelled with its path inside the change

#### Scenario: Active changes with missing artifacts
- **WHEN** an active change is missing some artifact files
- **THEN** the Active Changes section SHALL include only the artifacts that exist, silently skipping missing ones

#### Scenario: Nested delta spec
- **WHEN** an active change has a delta spec for a nested capability, such as `specs/identity/user-auth/spec.md`
- **THEN** the Active Changes section SHALL include that delta spec, labelled with its path `specs/identity/user-auth/spec.md`

#### Scenario: Custom-schema artifacts
- **WHEN** an active change uses a schema with artifacts other than proposal, specs, design and tasks
- **THEN** the Active Changes section SHALL include those artifacts' files, labelled with their artifact ids

#### Scenario: Change artifacts without the CLI
- **WHEN** the CLI is unavailable, fails, rejects the change, or reports no artifact paths for it
- **THEN** the Active Changes section SHALL include every Markdown file under the change directory, at any depth, labelled with its path inside the change
- **AND** it SHALL skip files and directories whose names start with a dot

#### Scenario: Spec domain with requirement summaries
- **WHEN** a capability has a `spec.md` with `### Requirement:` blocks
- **THEN** the Specs section SHALL list each requirement name with its description text (not scenarios), under the capability's full id

#### Scenario: Nested capability
- **WHEN** the project has a capability at `openspec/specs/identity/user-auth/spec.md`
- **THEN** the Specs section SHALL list it as `identity/user-auth`, whether the capability list came from the CLI or from the recursive walk

#### Scenario: No active changes
- **WHEN** the project has no active changes
- **THEN** the Active Changes section SHALL indicate no changes are in progress

#### Scenario: Assembled off the UI thread
- **WHEN** the user starts Explore from the menu or the Explore tab, with any delivery mode
- **THEN** the plugin SHALL assemble the context in a cancellable background task and deliver the prompt when it completes
- **AND** cancelling the task SHALL deliver nothing and, in the Explore tab, SHALL leave the input enabled

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

The Explore panel's inline input SHALL submit via Direct API delivery, bypassing the global delivery mode resolver, when the explore workflow is enabled in the active profile. A Direct API prompt SHALL begin with a plugin preamble saying the model cannot run commands, read files or write anything, that the included project context stands in for the CLI's listings, and that capturing a change means the plugin's Propose action.

#### Scenario: Panel submit uses Direct API
- **WHEN** the user submits a topic from the Explore panel's inline input
- **THEN** the plugin SHALL build the explore prompt and deliver it via `DirectApiService`, regardless of the globally selected delivery tool

#### Scenario: Menu action retains delivery mode routing
- **WHEN** the user triggers the Explore action from the menu with a non-Direct-API delivery mode
- **THEN** the plugin SHALL show the topic dialog and deliver via the resolved mode (clipboard or editor tab)

#### Scenario: Direct API prompt carries the preamble
- **WHEN** an explore prompt is built for Direct API delivery
- **THEN** it SHALL start with the preamble, followed by the explore instructions, the project context and the topic

#### Scenario: Agent deliveries carry no preamble
- **WHEN** an explore prompt is built for clipboard or editor-tab delivery
- **THEN** it SHALL NOT contain the preamble

#### Scenario: Explore disabled in the profile
- **WHEN** the active profile does not include the explore workflow and the user sends from the Explore panel
- **THEN** the plugin SHALL NOT send a request, SHALL say in the panel that Explore is not in the active profile, and SHALL leave the input enabled

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

The plugin SHALL assemble the explore prompt from the explore instructions, the project context and the topic. The instructions SHALL come from the generated explore file of the Settings-preferred tool, then of every tool in registry order: its skill, then its explore command file. Only when none exists SHALL the built-in default be used. Markdown frontmatter SHALL be stripped, and a TOML command's `prompt` body SHALL be taken.

#### Scenario: Skills-era skill file exists
- **WHEN** a tool's `<skillsDir>/skills/openspec-explore/SKILL.md` exists in the project
- **THEN** the prompt assembly SHALL use its content (frontmatter stripped) as the explore instructions, even when that tool's explore command file also exists

#### Scenario: Legacy skill file only
- **WHEN** a tool has no explore skill but has its explore command file, as `delivery: commands` generates (for example `.cursor/commands/opsx-explore.md`)
- **THEN** the prompt assembly SHALL use that file's content as the explore instructions

#### Scenario: TOML command file
- **WHEN** the only explore file is a TOML command such as `.gemini/commands/opsx/explore.toml`
- **THEN** the prompt assembly SHALL use the body of its `prompt = """…"""` value as the explore instructions

#### Scenario: Preferred tool wins
- **WHEN** Settings names a preferred tool and several tools have explore files
- **THEN** the prompt assembly SHALL use the preferred tool's file

#### Scenario: A tool detection cannot see
- **WHEN** the only explore file is one detection does not look for, such as Cline's `.clinerules/workflows/opsx-explore.md` under `delivery: commands`
- **THEN** the prompt assembly SHALL still use it

#### Scenario: Skill file missing
- **WHEN** no explore skill or command file exists for any tool
- **THEN** the prompt assembly SHALL use a built-in default explore prompt that covers the core explore stance and guardrails
- **AND** the plugin SHALL tell the user that the built-in default was used and that Update OpenSpec generates the project's explore instructions

#### Scenario: Instructions source shown
- **WHEN** the Explore panel shows a response
- **THEN** it SHALL name the instructions' source, either the project-relative file path or "built-in default"

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
