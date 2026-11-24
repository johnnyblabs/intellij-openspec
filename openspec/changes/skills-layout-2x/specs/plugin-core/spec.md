## MODIFIED Requirements

### Requirement: AI tool detection

The plugin SHALL detect every AI tool in the CLI's tool registry, from one record per tool: id, display name, skills directory, detection paths, older directories, type and the first CLI version that accepts the id. It SHALL apply the detection rules of the detected CLI's release line (1.x, 2.0, or 2.1 and later), and SHALL offer or pass a tool id to the CLI only when the detected CLI version accepts it.

#### Scenario: Tool detection
- **WHEN** `AiToolDetectionService.detect()` scans the project root
- **THEN** it SHALL check every registry tool by upstream's detection rule and classify each as CLI or IDE_PANEL

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
- **WHEN** the plugin's tool records are compared with the registry captured from CLI 1.14.3
- **THEN** every registry id SHALL have a record with the same display name, skills directory and detection paths, and no record SHALL name an id the registry lacks

#### Scenario: A tool newer than the detected CLI is not offered
- **WHEN** the detected CLI is older than the first version that accepts a tool's id (for example 1.3.0 and a tool added in 1.14.0)
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

### Requirement: Tool list in the shared skills folder

The plugin SHALL read `.agents/skills/.openspec-target` as upstream does: one tool per line, the first token the tool id and an optional `own-files` token after it. It SHALL resolve upstream's id aliases, skip ids the registry lacks, and ignore blank lines and line-ending differences. A one-line file from 1.x SHALL read as that one tool.

#### Scenario: Several tools listed
- **WHEN** the file holds `claude own-files`, `cursor` and `github-copilot` on separate lines
- **THEN** the list SHALL be `claude`, `cursor` and `github-copilot`, with only `claude` marked as having files of its own

#### Scenario: A 1.x marker
- **WHEN** the file holds the single line `codex`
- **THEN** the list SHALL be `codex`

#### Scenario: An alias and an unknown id
- **WHEN** a line names an id upstream maps to another (such as `windsurf`) and another line names an id no tool has
- **THEN** the alias SHALL resolve to its current id and the unknown line SHALL be skipped

#### Scenario: Empty or unreadable list under 2.x rules
- **WHEN** the file is empty, unreadable, or names no known tool
- **THEN** the owner SHALL be inferred, as upstream does, from the invocation syntax of a skill file that is exactly one an OpenSpec release generated, and then from a `skills-lock.json` recording OpenSpec's skills
- **AND** a skill file a user wrote SHALL NOT decide the owner

### Requirement: Skill lookup across the shared folder, tool folders and the home directory

To find a tool's OpenSpec skill, the plugin SHALL use the first `SKILL.md` that exists, in order: the shared `.agents/skills/<skill>/` when `.openspec-target` lists the tool; the tool's link folders (link, copy or link text); its own skills folder; its older folders; then the home directory's link and tool folders, and home `.agents/skills/` for a tool whose folder is `.agents` or the preferred tool. Explore, Revise, Continue and the panel SHALL use this lookup.

#### Scenario: Skills-only tool on 2.x
- **WHEN** Cursor is listed in the tool list, its skill exists only in `.agents/skills/openspec-explore/SKILL.md`, and the project has no `.cursor` folder
- **THEN** the explore handoff SHALL resolve to Cursor with the invocation `/openspec-explore`

#### Scenario: Linked tool on 2.x
- **WHEN** `.claude/skills/openspec-explore` is a link to `.agents/skills/openspec-explore`
- **THEN** the lookup SHALL find the skill through the link and resolve to Claude Code

#### Scenario: 1.x layout unchanged
- **WHEN** a project holds a 1.x copy of the skill under the tool's own skills folder and no `.agents/skills`
- **THEN** the lookup SHALL find that copy, as before

#### Scenario: Machine-wide skills
- **WHEN** the project has no OpenSpec skill and the home directory holds `.agents/skills/openspec-explore/SKILL.md`, or a tool's linked `.claude/skills/openspec-explore`
- **THEN** the lookup SHALL find the home directory's skill for the tool the link names, a tool whose folder is `.agents`, or the tool preferred in Settings

#### Scenario: The shared copy is not an unlisted tool's
- **WHEN** `.agents/skills/.openspec-target` lists only Cursor, and the shared skill exists
- **THEN** the lookup SHALL NOT resolve the skill to another tool, including a tool whose own folder is `.agents`, so a handoff never names a tool the project didn't set up

### Requirement: Tool folder shapes

The plugin SHALL classify each OpenSpec skill spot in a tool's own folder without following links: a link or junction pointing into the shared folder, a copy (a folder holding `SKILL.md`), "released" (a small text file holding the link's target, as Git without link support checks a link out), or broken (a link whose target is missing). Upstream treats a broken or released link as the tool still being set up, and the plugin SHALL do the same.

#### Scenario: A link
- **WHEN** a tool's skill spot is a symbolic link or a Windows junction into `.agents/skills`
- **THEN** it SHALL be classified as a link, and lookup SHALL read the skill through it

#### Scenario: A released link on Windows
- **WHEN** a tool's skill spot is a regular file whose whole content is the relative path of its shared skill folder
- **THEN** it SHALL be classified as released, the tool SHALL count as set up, and lookup SHALL read the shared skill

#### Scenario: A broken link
- **WHEN** a tool's skill spot is a link whose target no longer exists
- **THEN** it SHALL be classified as broken, the tool SHALL count as set up, and lookup SHALL find no skill there

#### Scenario: Link text written on another operating system
- **WHEN** a released link's text uses `\` separators, or `/` separators on Windows
- **THEN** it SHALL resolve the same way, with `\` read as `/`, as upstream does

#### Scenario: Paths that differ only by a link above the project
- **WHEN** the project or its shared folder is reached through a link, such as macOS's `/var` for `/private/var`
- **THEN** a link or released link into the shared folder SHALL still be classified by comparing real paths

#### Scenario: Copies on Windows
- **WHEN** the CLI wrote copies instead of links into the tool folders, as 2.1 does for project scope on Windows and 2.0 does where links can't be created
- **THEN** each copy SHALL be classified as a copy, ignoring OS junk files beside it, and lookup SHALL read it

#### Scenario: A spot that changed type after update
- **WHEN** the plugin's `update` run replaced a released link file with a copy folder, or a copy with a link
- **THEN** the IDE's file system SHALL show the new type after the run, because the tool's whole skills folder is refreshed

### Requirement: Home-directory skills are read off the UI thread

The plugin SHALL read the home directory's skills (`~/.agents/skills` and each tool's home link folder) only on a background thread, and keep the result in a project-level cache. The cache SHALL be cleared when a CLI run the plugin started finishes and when the IDE regains focus. A read on the UI thread SHALL return the cached result and SHALL NOT touch the disk. Tests SHALL use an injected home directory and never read the real one.

#### Scenario: Home directory as the CLI resolves it
- **WHEN** the plugin needs the home directory, for detection or lookup
- **THEN** it SHALL use the `USERPROFILE` environment variable, then `HOME`, then the JVM's `user.home`, skipping blank values, so it reads where the CLI wrote on every operating system

#### Scenario: Read in the background
- **WHEN** the cache is empty and a lookup runs on a background thread
- **THEN** the home directory SHALL be read and the result cached

#### Scenario: UI thread never reads the disk
- **WHEN** a lookup runs on the UI thread and the cache is empty
- **THEN** it SHALL treat the home directory as holding no skills and schedule a background read, without blocking

#### Scenario: Invalidated after a CLI run
- **WHEN** the plugin's `init` or `update` run finishes
- **THEN** the cache SHALL be cleared, and the paths the run wrote, links included, SHALL be refreshed in the IDE's file system
