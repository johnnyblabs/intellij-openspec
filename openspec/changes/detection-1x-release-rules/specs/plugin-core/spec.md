## MODIFIED Requirements

### Requirement: AI tool detection

The plugin SHALL detect every AI tool in the detected CLI's tool registry, from one record per tool: id, display name, skills directory, detection paths, older directories, type, the first CLI version that accepts the id and, for a retired tool, the release that retired it. It SHALL apply the detection rules of the detected CLI's release, within 1.x as across lines, and SHALL offer or pass a tool id only when the detected CLI accepts it.

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
- **WHEN** a project holds a tool's directory from before a registry change (such as `.windsurf` for Devin Desktop, `.codex` for Codex, `.agent` for Antigravity, or `.roo` for Zoo Code)
- **THEN** the plugin SHALL detect that tool exactly when the detected CLI's release does: through that release's detection paths or older directories (`.windsurf` and `.kimi` stay detection paths after 1.7.0, a bare `.codex` counts only before 1.8.0), with the CLI ID the registry accepts for it (`windsurf` for Devin Desktop, which every supported CLI accepts)
- **AND** GitHub Copilot SHALL be detected only from its registry detection paths, not from any `.github` directory

#### Scenario: The plugin's tools equal the CLI's registry
- **WHEN** the plugin's tool records accepted by a captured release, from 1.3.0 to 2.2.0, are compared with that release's captured registry
- **THEN** they SHALL be the same ids (a release before 1.7.0 names Devin Desktop `windsurf`), in the same order from 1.17.0, each with that release's skills directory, home-directory skills folder, detection paths and older directories, and from 1.17.0 its display name; no record SHALL name an id the release lacks

#### Scenario: A tool newer than the detected CLI is not offered
- **WHEN** the detected CLI is older than the first version that accepts a tool's id (for example 1.3.0 and a tool added in 1.14.0, or 2.0.0 and a tool added in 2.1.0), or is at or past the release that retired it (Amazon Q from 2.0.0)
- **THEN** the plugin SHALL NOT offer that tool for `init` or `update`, nor pass its id in `--tools`

#### Scenario: Upstream detection rule
- **WHEN** a tool is detected under 1.x rules
- **THEN** upstream's rule SHALL apply: its detection paths when it has them, otherwise its skills directory, with older directories also counting
- **AND** from 1.8.0, a shared skills root SHALL be attributed as upstream does, by its `skills/.openspec-target` marker and then by the invocation syntax in its generated skills, and a tool with a global skills directory SHALL be detected from the user's home directory

#### Scenario: A rule applies from the 1.x release that introduced it
- **WHEN** the detected CLI is a 1.x release older than the release that introduced a detection rule or tool
- **THEN** that rule or tool SHALL NOT apply: no marker or skill-syntax attribution below 1.8.0, no own detection path counting beside a shared root it doesn't own below 1.11.0, and no tool before the release that added it (Kimi before 1.4.0, Amp and GSD before 1.14.0, MiniMax Code, the only home-directory tool, before 1.8.0, a 2.1 tool on any 1.x release)

#### Scenario: A marker alone, before and after 1.8.0
- **WHEN** a project holds only `.agents/skills/.openspec-target` naming `codex`
- **THEN** detection SHALL report no tool on 1.7.0 and Codex on 1.8.0 and later, as each release does

#### Scenario: Upstream 2.x detection rule
- **WHEN** a tool is detected under 2.0 or 2.1 rules, which 2.2 also applies
- **THEN** nothing under `.agents` SHALL count as a path of the tool's own when its skills folder is `.agents`
- **AND** a tool listed in `.agents/skills/.openspec-target` SHALL be detected when the listed tool is still present by upstream's rule: listed without `own-files`, or listed with `own-files` and still holding a command, a skill or a link of its own
- **AND** a skills folder of the tool's own SHALL count, when the list names other tools but not it, only if it holds an OpenSpec skill

#### Scenario: Same layout, different release lines
- **WHEN** a project's `.agents/skills` holds OpenSpec skills and a marker naming `gsd`, and the project has no `.gsd` folder
- **THEN** detection under 1.17 rules SHALL report the shared-folder tool `agents`, and under 2.x rules SHALL report `gsd`, as each line's CLI does; a lone `.gsd` folder SHALL be `gsd` from 1.14.0, which added the tool, and no tool before

#### Scenario: Machine-wide install on 2.1
- **WHEN** the only skills are in the home directory and the detected CLI is 2.1 or later
- **THEN** detection SHALL report no tool, as 2.1 does; under 1.x and 2.0 rules a tool with a global skills directory SHALL still be detected from the home directory

#### Scenario: No CLI detected
- **WHEN** no CLI is detected
- **THEN** the plugin SHALL use 2.1 rules if the project holds a tool list with more than one line, an `own-files` token, or a link from a tool folder into `.agents/skills`, and 1.x rules otherwise
- **AND** 1.x rules with no release known SHALL be 1.17's shapes and rules with no release filter, as before
