## ADDED Requirements

### Requirement: Adding a tool without the CLI

Without the CLI, Manage AI Tools MAY add a tool by creating its folder. On a project whose files show a CLI 2.x layout (by the rule detection uses with no CLI), the plugin SHALL NOT write `.agents/skills/.openspec-target`. A tool only that list can record SHALL NOT be added on such a project, where the list names another tool, or while another tool holds the shared folder; the plugin SHALL say the CLI is needed. Otherwise the add SHALL behave as before.

#### Scenario: A shared-folder tool on a 2.x project
- **WHEN** the project's `.agents/skills/` holds a tool folder's link or a list naming several tools, no CLI is installed, and the user adds Codex
- **THEN** no file or folder SHALL be written, and the plugin SHALL say the OpenSpec CLI is needed to set Codex up in this project

#### Scenario: A tool with its own folder on a 2.x project
- **WHEN** the project shows a 2.x layout, no CLI is installed, and the user adds Cursor
- **THEN** the plugin SHALL create `.cursor/` and set Cursor as the preferred tool, without writing `.openspec-target`, and Cursor SHALL then be detected

#### Scenario: The list already names another tool
- **WHEN** `.agents/skills/.openspec-target` names Cursor, no CLI is installed, and the user adds Codex
- **THEN** no file or folder SHALL be written and the list SHALL stay as it is, and the plugin SHALL say the OpenSpec CLI is needed

#### Scenario: Another tool holds the shared folder
- **WHEN** a 1.x project's `.agents/skills/` has no list but holds skills whose wording names Codex, and the user adds the universal tool without the CLI
- **THEN** no list SHALL be written, so Codex stays the folder's owner, and the plugin SHALL say the OpenSpec CLI is needed

#### Scenario: A 1.x project
- **WHEN** the project shows no 2.x layout and has no tool list, and the user adds Codex without the CLI
- **THEN** the plugin SHALL create `.agents/skills/` with an `.openspec-target` naming Codex, as before, and Codex SHALL then be detected
