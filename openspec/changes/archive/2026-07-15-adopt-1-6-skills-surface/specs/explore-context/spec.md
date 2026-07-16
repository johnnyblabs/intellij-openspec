## MODIFIED Requirements

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
