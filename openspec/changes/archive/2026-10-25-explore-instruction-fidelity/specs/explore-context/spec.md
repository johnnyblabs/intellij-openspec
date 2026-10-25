## MODIFIED Requirements

### Requirement: Context assembly

The plugin SHALL assemble a Markdown-formatted project context from the current OpenSpec project state, including config summary with context and rules, detected AI tools, active changes with full artifact content, and spec domain listings with requirement summaries.

#### Scenario: Full context assembly
- **WHEN** the user triggers context assembly
- **THEN** the service SHALL produce a Markdown document with sections for Project Config, Detected AI Tools, Active Changes, and Specs

#### Scenario: Config section includes context and rules
- **WHEN** context is assembled and `config.yaml` has `context` and `rules` fields
- **THEN** the Project Config section SHALL include the schema, context description, and rules as a bulleted list
- **AND** it SHALL NOT include a `version` field, which upstream does not read

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
