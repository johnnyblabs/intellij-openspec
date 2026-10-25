## ADDED Requirements

### Requirement: Short handoff to an installed explore command or skill

When the explore instructions come from a tool's generated explore command or skill, clipboard and editor-tab delivery SHALL hand off what the user would type to run that tool's explore, followed by the topic, instead of the expanded prompt. The invocation SHALL follow upstream's rules for that tool and the explore files it has installed. When no generated explore file exists, the expanded prompt SHALL be used as before.

#### Scenario: A tool with a namespaced command
- **WHEN** the instructions came from Claude Code and its `.claude/commands/opsx/explore.md` exists
- **THEN** the handoff SHALL be `/opsx:explore` followed by the topic

#### Scenario: A tool with a flat command
- **WHEN** the instructions came from a tool whose explore command file is named `opsx-explore`, such as Cursor's `.cursor/commands/opsx-explore.md`
- **THEN** the handoff SHALL be `/opsx-explore` followed by the topic

#### Scenario: A skill only
- **WHEN** the tool has its explore skill but no explore command
- **THEN** the handoff SHALL be `/openspec-explore` followed by the topic, or the tool's own skill prefix where upstream defines one (Kimi Code `/skill:openspec-explore`, Codex `$openspec-explore`)

#### Scenario: A tool that names skills in plain words
- **WHEN** the tool is one upstream gives no slash invocation for its skills
- **THEN** the handoff SHALL be "Use the openspec-explore skill:" followed by the topic

#### Scenario: No generated explore file
- **WHEN** the built-in default explore instructions are in use
- **THEN** clipboard and editor-tab delivery SHALL hand off the expanded prompt, as before

#### Scenario: The notice says what was copied
- **WHEN** the short handoff is copied
- **THEN** the notice SHALL show the copied text and name the tool to paste it into

## MODIFIED Requirements

### Requirement: Delivery mode routing

The plugin SHALL route the assembled explore prompt through the user's configured delivery mode, supporting Direct API, Editor Tab, and Clipboard delivery. Clipboard and Editor Tab delivery SHALL hand off the short invocation when the tool has explore installed, and the expanded prompt otherwise.

#### Scenario: Direct API delivery
- **WHEN** the resolved delivery mode is Direct API and an AI provider is configured
- **THEN** the plugin SHALL send the explore prompt to the configured AI provider on a background thread and display the response in the Explore tab

#### Scenario: Editor Tab delivery
- **WHEN** the resolved delivery mode is Editor Tab
- **THEN** the plugin SHALL open the handoff in an unsaved, in-memory editor tab, writing nothing to disk

#### Scenario: Clipboard delivery
- **WHEN** the resolved delivery mode is Clipboard
- **THEN** the plugin SHALL copy the handoff to the system clipboard and show a notification

#### Scenario: Direct API not configured
- **WHEN** the resolved delivery mode is Direct API but no provider is configured
- **THEN** the plugin SHALL fall back to Clipboard delivery with a notification suggesting the user configure an AI provider
