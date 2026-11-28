## MODIFIED Requirements

### Requirement: Set-up status follows the CLI's tool list, folder ownership and folders

The plugin SHALL count a tool as set up exactly when the CLI's `getConfiguredTools` does, including a tool it doesn't detect. A tool the tool list names counts by the CLI's rule. From CLI 1.8, where tools share a skills folder on 1.x, only its owner counts. From CLI 1.7, a folder that is only a detection path SHALL NOT make a tool set up. Before the release that introduced a rule, it SHALL NOT apply. A skill SHALL count only as `<name>/SKILL.md` for a name the release uses.

#### Scenario: A tool the list names
- **WHEN** `.agents/skills/.openspec-target` names `codex` with no skill file yet, on CLI 1.17 or 2.x
- **THEN** Codex SHALL show as set up, matching the CLI's captured verdict

#### Scenario: The owner of a shared skills folder
- **WHEN** the detected CLI is 1.8 to 1.x and `.openspec-target` names `gsd` above OpenSpec's skills in `.agents/skills/`, with no `.gsd` folder
- **THEN** GSD SHALL show as set up and the universal `.agents` tool SHALL NOT, matching the CLI's captured verdict

#### Scenario: Listed with its own files but holding none
- **WHEN** the detected CLI is 2.x and the list marks a tool `own-files`, but the tool has no command, link or skill of its own
- **THEN** the tool SHALL show as detected, not set up, when its folder exists

#### Scenario: A 1.6-era detection folder
- **WHEN** the detected CLI is 1.7 or later and Kimi Code's OpenSpec skills exist only in `.kimi/skills/`, or Devin Desktop's only in `.windsurf/skills/`
- **THEN** the tool SHALL show as detected, not set up, so Manage AI Tools offers to set it up again

#### Scenario: An older skills folder the CLI still reads
- **WHEN** the detected CLI is 1.7 or later and Codex's skills exist only in `.codex/skills/`, or Antigravity's only in `.agent/skills/`
- **THEN** the tool SHALL show as set up, because the CLI lists those folders as the tool's older skills folders

#### Scenario: Before 1.7
- **WHEN** the detected CLI is below 1.7 and Kimi Code's skills exist in `.kimi/skills/`, or Devin Desktop's in `.windsurf/skills/`
- **THEN** the tool SHALL show as set up, since that was its skills folder then

#### Scenario: A skill folder without its skill file
- **WHEN** a tool's skills folder holds `openspec-explore/` with no `SKILL.md`, or a `SKILL.md` under a name no release uses (such as `openspec-custom`)
- **THEN** the tool SHALL NOT show as set up from it, on any release, as the CLI's captured verdicts show

#### Scenario: A skill name the release doesn't have
- **WHEN** a tool's only skill is `openspec-update-change` and the CLI is 1.5, or its only skill is the `openspec` router and the CLI is older than 2.2
- **THEN** the tool SHALL NOT show as set up; from 1.6 (`openspec-update-change`) and from 2.2 (`openspec`) it SHALL
