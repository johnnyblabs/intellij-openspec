## MODIFIED Requirements

### Requirement: AI tool detection

The plugin SHALL detect every AI tool in the detected CLI's tool registry, from one record per tool: id, display name, skills directory, detection paths, older directories, type, the first CLI version that accepts the id and, for a retired tool, the release that retired it. It SHALL apply the detection rules of the CLI's release line, and SHALL offer or pass a tool id only when the detected CLI accepts it.

#### Scenario: Tool detection
- **WHEN** `AiToolDetectionService.detect()` scans the project root
- **THEN** it SHALL check every registry tool by upstream's detection rule and classify each as CLI, IDE_PANEL, or unclassified when there is no evidence of either

#### Scenario: Junie detected as IDE panel
- **WHEN** the project root contains a `.junie` directory
- **THEN** the plugin SHALL list "Junie" in detected tools, classified as IDE_PANEL, with CLI ID `junie`

#### Scenario: Lingma detected as IDE panel
- **WHEN** the project root contains a `.lingma` directory
- **THEN** the plugin SHALL list "Lingma" in detected tools, classified as IDE_PANEL, with CLI ID `lingma`

#### Scenario: ForgeCode detected as CLI tool
- **WHEN** the project root contains a `.forge` directory (the upstream-emitted directory for ForgeCode)
- **THEN** the plugin SHALL list "ForgeCode" in detected tools, classified as CLI, with CLI ID `forgecode`

#### Scenario: Bob Shell detected as CLI tool
- **WHEN** the project root contains a `.bob` directory
- **THEN** the plugin SHALL list the tool under its current registry name "IBM Bob" (accepting the former name "Bob Shell" for a saved preference), classified as CLI, with CLI ID `bob`

#### Scenario: Kimi CLI detected as CLI tool
- **WHEN** the project root contains a `.kimi-code` directory (where OpenSpec 1.7+ writes it) or the older `.kimi` directory
- **THEN** the plugin SHALL list the tool under its current registry name "Kimi Code" (accepting the former name "Kimi CLI" for a saved preference), classified as CLI, with CLI ID `kimi`

#### Scenario: Mistral Vibe detected as CLI tool
- **WHEN** the project root contains a `.vibe` directory
- **THEN** the plugin SHALL list "Mistral Vibe" in detected tools, classified as CLI, with CLI ID `vibe`

#### Scenario: ForgeCode directory key differs from CLI ID
- **WHEN** the plugin maps display name "ForgeCode" to a CLI ID
- **THEN** it SHALL emit `forgecode` (matching upstream `--tools forgecode`), even though the directory key in `TOOL_DIRS` is `.forge`

#### Scenario: Pre-1.3.x tools still detected
- **WHEN** a project holds a tool's directory from before a registry change (such as `.windsurf` for Devin Desktop, `.codex` for Codex, `.agent` for Antigravity, or `.roo` for Zoo Code) or any other pre-1.3.x tool directory
- **THEN** the plugin SHALL still detect that tool, with the CLI ID the registry accepts for it (`windsurf` for Devin Desktop, which every supported CLI accepts), so existing projects don't regress; GitHub Copilot SHALL be detected only from its registry detection paths, not from any `.github` directory

#### Scenario: The plugin's tools equal the CLI's registry
- **WHEN** the plugin's tool records accepted by a captured release, from 1.3.0 to 2.1.1, are compared with that release's captured registry
- **THEN** they SHALL be the same ids in the same order, each with that release's display name, skills directory, home-directory skills folder and detection paths, and no record SHALL name an id the release lacks

#### Scenario: A tool newer than the detected CLI is not offered
- **WHEN** the detected CLI is older than the first version that accepts a tool's id (for example 1.3.0 and a tool added in 1.14.0, or 2.0.0 and a tool added in 2.1.0), or is at or past the release that retired it (Amazon Q from 2.0.0)
- **THEN** the plugin SHALL NOT offer that tool for `init` or `update`, nor pass its id in `--tools`

#### Scenario: Upstream detection rule
- **WHEN** a tool is detected under 1.x rules
- **THEN** upstream's rule SHALL apply: its detection paths when it has them, otherwise its skills directory, with older directories also counting
- **AND** a shared skills root SHALL be attributed as upstream does, by its `skills/.openspec-target` marker and then by the invocation syntax in its generated skills
- **AND** a tool with a global skills directory SHALL be detected from the user's home directory

#### Scenario: Upstream 2.x detection rule
- **WHEN** a tool is detected under 2.0 or 2.1 rules
- **THEN** nothing under `.agents` SHALL count as a path of the tool's own when its skills folder is `.agents`
- **AND** a tool listed in `.agents/skills/.openspec-target` SHALL be detected when the listed tool is still present by upstream's rule: listed without `own-files`, or listed with `own-files` and still holding a command, a skill or a link of its own
- **AND** a skills folder of the tool's own SHALL count, when the list names other tools but not it, only if it holds an OpenSpec skill

#### Scenario: Same layout, different release lines
- **WHEN** a project holds only a `.gsd` folder
- **THEN** detection under 1.x rules SHALL report the shared-folder tool `agents`, and under 2.x rules SHALL report `gsd`, as each line's CLI does

#### Scenario: Machine-wide install on 2.1
- **WHEN** the only skills are in the home directory and the detected CLI is 2.1 or later
- **THEN** detection SHALL report no tool, as 2.1 does; under 1.x and 2.0 rules a tool with a global skills directory SHALL still be detected from the home directory

#### Scenario: No CLI detected
- **WHEN** no CLI is detected
- **THEN** the plugin SHALL use 2.1 rules if the project holds a tool list with more than one line, an `own-files` token, or a link from a tool folder into `.agents/skills`, and 1.x rules otherwise

## ADDED Requirements

### Requirement: Retired tools on CLI 2.x

On CLI 2.0 and later the plugin SHALL NOT offer Amazon Q, which those releases retired, nor show it as a tool of the project, as the CLI doesn't. The CLI's own `openspec update` cleans up OpenSpec's Amazon Q files and gives the advice. Below 2.0 nothing SHALL change.

#### Scenario: Amazon Q is refused from 2.0
- **WHEN** the detected CLI is 2.0.0 or later
- **THEN** `acceptedBy(amazon-q)` SHALL be false, as the captured `init --tools amazon-q` refusal shows, and Amazon Q SHALL NOT appear among the tools to add

#### Scenario: A project with Amazon Q files on 2.x
- **WHEN** the detected CLI is 2.0.0 or later and the project's `.amazonq/` holds OpenSpec's Amazon Q files
- **THEN** Manage AI Tools SHALL NOT list Amazon Q, and SHALL NOT pass it to `init` or `update`

#### Scenario: Amazon Q on 1.x
- **WHEN** the detected CLI is below 2.0.0
- **THEN** Amazon Q SHALL be detected, offered and set up as before

### Requirement: Tools added in CLI 2.1

The plugin SHALL know the 38 tools CLI 2.1 added, with upstream's ids, names, folders and detection paths, and SHALL offer them only on CLI 2.1.0 or later. Each SHALL have the plugin's CLI or IDE type from its own documentation, or be unclassified when that documentation doesn't settle it. MiniMax Code's skills SHALL be read from the home folder up to 2.0 and from the project's `.minimax` from 2.1.

#### Scenario: A 2.1 tool on CLI 2.1
- **WHEN** the detected CLI is 2.1.0 or later
- **THEN** each tool 2.1 added SHALL be offered, passed to `init` by its upstream id, and ticked in "Install into this project" when its machine marker exists

#### Scenario: A 2.1 tool on an older CLI
- **WHEN** the detected CLI is 2.0.x or 1.x
- **THEN** no tool 2.1 added SHALL be offered or passed to `init`

#### Scenario: MiniMax Code by release
- **WHEN** the plugin reads MiniMax Code's record for a release
- **THEN** up to 2.0 it SHALL have no project skills folder and its skills SHALL be in the home directory's `.minimax`, and from 2.1 its project skills folder SHALL be `.minimax`, as each release's captured registry records

#### Scenario: Adding a 2.1 tool without the CLI
- **WHEN** the user adds, without the CLI, a tool CLI 2.1 added, which upstream detects only by OpenSpec skills of its own or by the CLI's tool list
- **THEN** the plugin SHALL write nothing and SHALL say the OpenSpec CLI is needed to add it, as it does for a tool only the CLI's tool list can record

#### Scenario: An unclassified tool
- **WHEN** a tool's type is unclassified
- **THEN** Manage AI Tools SHALL show no CLI or IDE badge for it, and delivery guidance SHALL use the generic paste wording

### Requirement: A tool set up through the shared folder says so

On a 2.x project, a set-up tool whose OpenSpec skills are reached only through the shared `.agents/skills/` copy, and whose own skills folder isn't `.agents`, SHALL show a secondary line "via shared .agents skills" in Manage AI Tools.

#### Scenario: Skills only in the shared folder
- **WHEN** a tool whose own folder isn't `.agents` (for example Cursor) is listed in `.agents/skills/.openspec-target` and has no link, copy or skill in that folder
- **THEN** its row SHALL show "via shared .agents skills" under its name

#### Scenario: A tool whose own folder is the shared one
- **WHEN** a tool's own skills folder is `.agents` (for example Codex or Amp)
- **THEN** its row SHALL show no shared-folder line

#### Scenario: A tool with its own link
- **WHEN** a tool's own folder links into `.agents/skills/` (for example `.claude/skills`)
- **THEN** its row SHALL show no shared-folder line

### Requirement: Regional variants sharing a folder

On CLI 2.1 and later, when tools share a project folder (Trae and Trae CN on `.trae`, Qoder and Qoder CN on `.qoder`, Zencoder and Zenflow on `.zencoder`) and the tool list in `.agents/skills/.openspec-target` names none of them, the plugin SHALL count only the first of them, in the registry's order, as set up, as the CLI does. A tool the list names SHALL count on its own.

#### Scenario: An unlisted shared folder
- **WHEN** the detected CLI is 2.1.0 or later and a project's `.trae` holds OpenSpec's skills with no tool list naming Trae or Trae CN
- **THEN** Trae SHALL be set up and Trae CN SHALL NOT, matching the captured 2.1.1 verdict

#### Scenario: A listed variant
- **WHEN** the tool list names Trae CN
- **THEN** Trae CN SHALL be set up, and Trae only if it is listed too, matching the captured verdict

#### Scenario: Before 2.1
- **WHEN** the detected CLI is 2.0.x or 1.x
- **THEN** no such rule SHALL apply, since those releases have no regional variants
