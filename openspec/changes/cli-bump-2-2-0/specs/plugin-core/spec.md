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
- **WHEN** the plugin's tool records accepted by a captured release, from 1.3.0 to 2.2.0, are compared with that release's captured registry
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
- **WHEN** a tool is detected under 2.0 or 2.1 rules, which 2.2 also applies
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

### Requirement: CLI arguments match the detected CLI version

The plugin SHALL build every OpenSpec CLI invocation against the detected CLI version, using one record of the release that introduced, and any release that dropped, each command and each flag it passes. On a release that lacks an optional flag, the flag SHALL be omitted. On a release that lacks a required flag or the command itself, the plugin SHALL report the gap and SHALL NOT start the CLI. CLI version detection is exempt.

#### Scenario: An optional flag the release lacks is left out
- **WHEN** an invocation includes an optional flag that the detected release does not accept
- **THEN** the plugin SHALL run the command without that flag

#### Scenario: A required flag the release lacks is refused before the CLI runs
- **WHEN** an invocation needs a flag that the detected release does not accept
- **THEN** the plugin SHALL NOT start the CLI
- **AND** the error SHALL name the command, the flag and the release that introduced it

#### Scenario: A command the release lacks is refused before the CLI runs
- **WHEN** an invocation names a command that the detected release does not have, such as `doctor` on 1.4.x or `workspace` on 1.5.0 and later
- **THEN** the plugin SHALL NOT start the CLI and SHALL report that the command needs another release

#### Scenario: Every flag passed today reaches every release that accepts it
- **WHEN** the detected release is any supported release from 1.3.0 to 2.2.0
- **THEN** each invocation SHALL pass the same arguments it passed before this requirement, for every release where the plugin already offered it

#### Scenario: An unknown version is treated as the target release
- **WHEN** the CLI is available but its version could not be read
- **THEN** arguments SHALL be built as for the plugin's target CLI release

## ADDED Requirements

### Requirement: The CLI 2.2 router skill is an OpenSpec skill

The plugin SHALL count the `openspec` skill folder, which CLI 2.2.0 installs beside the workflow skills, as one of OpenSpec's own skills wherever it lists or recognizes them, as upstream's `OPENSPEC_SKILL_NAMES` does. It SHALL NOT treat the router as a workflow.

#### Scenario: The router folder is OpenSpec's
- **WHEN** a 2.2.0 project holds `.agents/skills/openspec/SKILL.md` beside the workflow skills
- **THEN** the plugin SHALL count `openspec` among OpenSpec's skill folders

#### Scenario: The router is not a workflow
- **WHEN** the plugin lists the workflows a project's profile offers
- **THEN** the router SHALL NOT appear among them, as upstream keeps it outside its workflow set
