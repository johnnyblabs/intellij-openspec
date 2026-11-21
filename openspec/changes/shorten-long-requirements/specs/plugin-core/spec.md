## MODIFIED Requirements

### Requirement: Project detection and initialization

The plugin SHALL detect OpenSpec projects by checking for `openspec/`, and initialize them through CLI delegation with a built-in fallback. The fallback SHALL honor the Default schema preference, falling back to `spec-driven`, and SHALL write only the fields upstream `openspec init` writes: a `schema:` line. It SHALL NOT write plugin-invented or empty upstream fields.

#### Scenario: Project detection
- **WHEN** a project is opened in the IDE
- **THEN** the plugin SHALL check for `openspec/` directory and enable/disable features accordingly

#### Scenario: Init delegates to CLI when available
- **WHEN** `ScaffoldingService.initOpenSpec()` is called and the CLI is detected
- **THEN** it SHALL run `openspec init --tools <detected-tools>` and perform a synchronous VFS refresh

#### Scenario: Init falls back to built-in
- **WHEN** the CLI is unavailable or fails
- **THEN** the service SHALL create `openspec/config.yaml`, `specs/`, `changes/`, and `changes/archive/` via VFS

#### Scenario: Default schema honored on built-in init
- **WHEN** the user has set Default schema to a value other than empty (e.g., `workspace-planning`) in Settings → Tools → OpenSpec, and the built-in init path runs
- **THEN** the generated `openspec/config.yaml` SHALL contain `schema:` matching the user's chosen value, not the hardcoded literal `spec-driven`

#### Scenario: Default schema fallback when unset
- **WHEN** the user has not set Default schema (the setting is empty), and the built-in init path runs
- **THEN** the generated `openspec/config.yaml` SHALL contain `schema: spec-driven` (preserving the prior default behavior)

#### Scenario: Built-in init writes a schema-only config
- **WHEN** the built-in init path generates `openspec/config.yaml`
- **THEN** the file SHALL contain a `schema:` line and SHALL NOT contain a `version:` field, a `profile:` block, or empty `context:`/`rules:` entries — matching upstream `openspec init`, which writes only `schema:`

#### Scenario: Legacy config with plugin-invented fields still reads
- **WHEN** an existing `openspec/config.yaml` (from a prior plugin version) contains `version:` and/or `profile:`
- **THEN** the plugin SHALL still parse it and still surface those values in the tree view — only newly-scaffolded configs omit them

#### Scenario: Legacy fields
- **WHEN** an existing legacy `config.yaml` carries `version:` or `profile:`
- **THEN** the plugin MAY read them for display, but SHALL NOT emit them into a config it generates

### Requirement: Configuration parsing

The plugin SHALL parse `openspec/config.yaml` and each change's `.openspec.yaml`, ignoring unrecognized or newer keys rather than treating them as errors, matching upstream's strip contract. Genuinely malformed YAML in a change's `.openspec.yaml` SHALL raise a warning notification with the change name and parse error. The plugin SHALL persist a default schema preference in `OpenSpecSettings.State`, exposed through getter and setter methods.

#### Scenario: Valid config
- **WHEN** a valid `config.yaml` exists
- **THEN** all config values SHALL be accessible via ConfigService

#### Scenario: Malformed YAML
- **WHEN** a change's `.openspec.yaml` contains genuinely invalid YAML
- **THEN** the plugin SHALL show a warning notification with the change name and parse error
- **AND WHEN** `config.yaml` contains invalid YAML
- **THEN** the plugin SHALL degrade to empty configuration without blocking (the parse failure is logged, not surfaced as a notification)

#### Scenario: Profile config retrieval via CLI
- **WHEN** the CLI is detected and `openspec config profile --json` succeeds
- **THEN** the plugin SHALL make profile name, description, and active workflows available to the Settings panel

#### Scenario: Profile config retrieval without CLI
- **WHEN** the CLI is not detected
- **THEN** the plugin SHALL fall back to the locally-stored profile name in OpenSpecSettings without workflow details

#### Scenario: Default schema persistence
- **WHEN** the user selects a default schema in Settings
- **THEN** the value SHALL be persisted in `OpenSpecSettings.State.defaultSchema` and available via `OpenSpecSettings.getDefaultSchema()`

#### Scenario: Unknown or newer change-metadata keys parse without warning
- **WHEN** a change's `.openspec.yaml` carries upstream-valid keys the plugin does not model (`goal`, `affected_areas`, `initiative`, `skip_specs`) or any other unknown or newer key
- **THEN** the plugin SHALL parse it to non-null metadata (retaining `schema`, `status`, and `created`) with no warning notification
- **AND** the unmodeled keys SHALL be ignored, so no validation gate treats them as errors

#### Scenario: Profile workflows
- **WHEN** the CLI is available
- **THEN** the plugin SHALL read the active profile's workflow configuration

### Requirement: AI tool detection

The plugin SHALL detect every AI tool in the CLI's tool registry, from one record per tool: id, display name, skills directory, detection paths, older directories, type and the first CLI version that accepts the id. It SHALL offer or pass a tool id to the CLI only when the detected CLI version accepts it.

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
- **WHEN** a tool is detected
- **THEN** upstream's rule SHALL apply: its detection paths when it has them, otherwise its skills directory, with older directories also counting
- **AND** a shared skills root SHALL be attributed as upstream does, by its `skills/.openspec-target` marker and then by the invocation syntax in its generated skills
- **AND** a tool with a global skills directory SHALL be detected from the user's home directory

### Requirement: CLI re-detection on tool window activation

The plugin SHALL re-run CLI detection when the tool window is shown and when its project's IDE window regains focus, so a CLI upgraded during a session is noticed. Focus-regain detection SHALL apply only to that project. Detection SHALL be skipped when the last one ran less than 30 seconds ago, and the status bar SHALL update afterwards.

#### Scenario: Tool window activated with stale detection
- **WHEN** the OpenSpec tool window is shown and the last detection was more than 30 seconds ago
- **THEN** the plugin SHALL re-run CLI detection on a background thread and update the status bar on the EDT

#### Scenario: Tool window activated with fresh detection
- **WHEN** the OpenSpec tool window is shown and the last detection was within 30 seconds
- **THEN** the plugin SHALL skip re-detection

#### Scenario: CLI installed after project open
- **WHEN** the user installs the OpenSpec CLI after the project is opened and then activates the tool window
- **THEN** the status bar SHALL update to show "CLI: available" after re-detection completes

#### Scenario: CLI upgraded mid-session is picked up on focus-regain
- **WHEN** the OpenSpec CLI is upgraded while the IDE is running, the OpenSpec tool window has stayed visible, and the user returns focus to that project's IDE window more than 30 seconds after the last detection
- **THEN** the plugin SHALL re-run CLI detection on a background thread and the status SHALL show the new version, without reopening the project or hiding and re-showing the tool window

#### Scenario: Focus-regain within the throttle window does not re-detect
- **WHEN** the IDE window regains focus within 30 seconds of the last detection
- **THEN** the plugin SHALL skip re-detection, so rapid window switching never launches repeated CLI runs

#### Scenario: Only the focused project's window triggers re-detection
- **WHEN** several projects are open and the IDE window of one of them regains focus
- **THEN** only that project SHALL re-run CLI detection

### Requirement: Cross-platform CLI detection

CliDetectionService SHALL detect the CLI on Windows under the same scenarios that succeed on macOS and Linux. It SHALL NOT run an installer, edit PATH, or remove another install. On every OS, after the fixed install locations, it SHALL probe the Homebrew Linux prefix and the standalone installer's location; a fixed candidate SHALL be probed only when a regular, executable file exists there.

#### Scenario: Windows bare-name resolution falls back through executable suffixes
- **WHEN** the host OS is Windows and `tryPath("openspec")` is called and bare invocation fails
- **THEN** the service SHALL retry the candidate with `.cmd`, `.bat`, and `.exe` suffixes in that order, accepting the first variant whose `--version` invocation exits 0

#### Scenario: Windows extension fallback only applies when no extension is present
- **WHEN** the host OS is Windows and the candidate path already ends in `.cmd`, `.bat`, or `.exe`
- **THEN** the service SHALL invoke that exact path without appending further suffixes

#### Scenario: Windows shell PATH is resolved via where.exe
- **WHEN** the host OS is Windows and the bare-name and settings-path branches have not produced a hit
- **THEN** the service SHALL invoke `where.exe openspec` (not `/bin/zsh -lc`) and use the first non-empty stdout line as the next candidate path

#### Scenario: Windows common npm paths are probed
- **WHEN** the host OS is Windows and prior detection branches have not produced a hit
- **THEN** the service SHALL try, in order, `%APPDATA%\npm\openspec.cmd`, `%LOCALAPPDATA%\npm\openspec.cmd`, and `%LOCALAPPDATA%\Microsoft\WinGet\Links\openspec.cmd`, skipping any candidate whose environment variable is unset

#### Scenario: User-supplied path with extension works as-is
- **WHEN** the host OS is Windows and the user has set the Settings CLI path to a path ending in `.cmd`, `.bat`, or `.exe`
- **THEN** the service SHALL invoke that path verbatim and SHALL NOT attempt suffix variants

#### Scenario: User-supplied bare path is extended on Windows
- **WHEN** the host OS is Windows and the user has set the Settings CLI path to a value with no extension
- **THEN** the service SHALL apply the same `.cmd` / `.bat` / `.exe` fallback as for auto-detected bare names

#### Scenario: macOS and Linux behavior unchanged
- **WHEN** the host OS is macOS or Linux
- **THEN** detection SHALL try, in order: the Settings path; `openspec` found on the IDE's PATH; the bare name; `openspec` found on the PATH the user's interactive login shell sets; then `/opt/homebrew/bin`, `/usr/local/bin`, `/usr/bin`, Homebrew on Linux (`/home/linuxbrew/.linuxbrew/bin`) and the standalone-binary candidates. It SHALL NOT invoke any Windows-specific code path

#### Scenario: Windows detection completes within the existing timeout
- **WHEN** detection runs on Windows and traverses the suffix-fallback, `where.exe`, and common-paths branches
- **THEN** the total wall-clock time SHALL be bounded by the existing `TIMEOUT_MS` budget per `runAndCapture` invocation, with no additional global wait introduced

#### Scenario: Standalone binary in its default Unix location
- **WHEN** the host OS is macOS or Linux, the standalone binary is at `~/.local/bin/openspec`, and no earlier branch finds the CLI
- **THEN** detection SHALL find the CLI at `~/.local/bin/openspec` and report the version it prints

#### Scenario: Standalone binary in its default Windows location
- **WHEN** the host OS is Windows, the standalone binary is at `%LOCALAPPDATA%\Programs\openspec\openspec.exe`, and no earlier branch finds the CLI
- **THEN** detection SHALL find the CLI at that path, after the npm and winget candidates and skipping it when `%LOCALAPPDATA%` is unset

#### Scenario: Homebrew formula on Linux
- **WHEN** the host OS is Linux, OpenSpec was installed with the Homebrew formula at `/home/linuxbrew/.linuxbrew/bin/openspec`, and no earlier branch finds the CLI
- **THEN** detection SHALL find the CLI there, before the standalone-binary candidates

#### Scenario: Installer overrides in the IDE's environment
- **WHEN** the IDE's environment sets `OPENSPEC_INSTALL_DIR`, optionally with `OPENSPEC_INSTALL_NAME`
- **THEN** detection SHALL probe that directory joined with that name, or with `openspec` on Unix and `openspec.exe` on Windows when the name is unset, before the default standalone location
- **AND** an unset or empty `OPENSPEC_INSTALL_DIR` SHALL add no candidate

#### Scenario: A missing fixed location is not run
- **WHEN** detection reaches a fixed install-location candidate that has no regular, executable file
- **THEN** detection SHALL skip it without starting a process and SHALL go on to the next candidate

#### Scenario: Standalone version output
- **WHEN** the standalone binary's `--version` prints a bare version such as `1.15.0`
- **THEN** the detected version SHALL be `1.15.0`

#### Scenario: A PATH entry kept only in the interactive rc file
- **WHEN** the CLI's directory is added to PATH only in `~/.zshrc` (for example by nvm, fnm, volta or asdf) and the IDE was launched from the Dock or a desktop launcher
- **THEN** detection SHALL find the CLI, from the IDE's PATH when the IDE loaded the shell environment, and otherwise from the interactive login shell's PATH

#### Scenario: Output from rc files is ignored
- **WHEN** the user's rc files print text (a banner, a greeting, a version manager's notice) while the shell lookup runs
- **THEN** the service SHALL read only the PATH it asked the shell to print, between markers it chose, and SHALL ignore everything else

#### Scenario: Shell flags follow the user's shell
- **WHEN** the shell lookup runs
- **THEN** it SHALL start `$SHELL` as an interactive login shell (`-l -i -c`), leaving out `-i` for fish and `-l` for csh and tcsh, as the IDE's own environment loading does, within the existing per-command timeout

#### Scenario: CLI runs keep the IDE's PATH
- **WHEN** the plugin starts a CLI process on macOS or Linux after resolving the shell PATH
- **THEN** the process's PATH SHALL be the IDE's PATH followed by any shell PATH entries it doesn't already contain, never the shell PATH alone

#### Scenario: A process that keeps the shell's output open
- **WHEN** an rc file starts a background process that inherits the shell's output, and the shell itself exits
- **THEN** the shell lookup SHALL complete with the PATH the shell printed, without waiting for the background process
- **AND** no detection step SHALL wait longer than the per-command timeout for a process's output

#### Scenario: The shell PATH is reused
- **WHEN** detection runs again within 10 minutes of resolving the shell PATH, from any caller other than Settings
- **THEN** detection SHALL reuse that PATH and SHALL NOT start the shell again

#### Scenario: Detecting from Settings resolves the shell PATH again
- **WHEN** the user starts detection from Settings
- **THEN** detection SHALL start the shell and use the PATH it prints now, whatever was resolved before

#### Scenario: Windows lookup
- **WHEN** detection runs on Windows
- **THEN** it SHALL try `.cmd`, `.bat` and `.exe` suffixes when direct invocation fails, resolve the executable with `where.exe`, and probe install locations under `%APPDATA%` and `%LOCALAPPDATA%`

#### Scenario: Standalone installer location
- **WHEN** the IDE environment carries the installer's `OPENSPEC_INSTALL_DIR` and `OPENSPEC_INSTALL_NAME` overrides
- **THEN** they SHALL be probed before the installer's default location

#### Scenario: Shell PATH
- **WHEN** detection runs on macOS or Linux
- **THEN** after the Settings path it SHALL look the CLI up on the IDE's PATH, and its shell lookup SHALL read the PATH an interactive login shell sets
- **AND** every CLI process SHALL keep the IDE's PATH, adding only the shell PATH entries it lacks
- **AND** the shell PATH SHALL be reused for 10 minutes once resolved, except that detection the user starts from Settings SHALL resolve it afresh

#### Scenario: Bounded probes
- **WHEN** a detection process writes output
- **THEN** that output SHALL NOT hold detection past the per-command timeout

### Requirement: CLI status reflects the latest completed detection

The CLI status SHALL reflect the most recent completed detection, whichever path triggered it. A detection in progress SHALL NOT be observable as "CLI unavailable": readers SHALL see either the previous result or the new one. Before the first detection completes, the status SHALL show that detection is in progress.

#### Scenario: Tool window built while startup detection is running
- **WHEN** the OpenSpec tool window is created while the project-open CLI detection is still running and the CLI is installed
- **THEN** the status SHALL show detection in progress, then "CLI: available" when detection completes, without the user refreshing

#### Scenario: Detection in progress never reads as unavailable
- **WHEN** a CLI detection is re-run while a previous detection found the CLI
- **THEN** any reader during the re-run SHALL still see the CLI as available until the new result is published

#### Scenario: Any completed detection updates the status
- **WHEN** a CLI detection completes from any trigger, including one the throttled tool-window re-check skipped
- **THEN** the tool window's CLI status SHALL update to the new result
