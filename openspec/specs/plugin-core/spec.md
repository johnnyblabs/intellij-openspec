# Plugin Core

## Purpose
Core infrastructure: project detection, configuration, file operations, API compatibility, notifications, and service lifecycle.

## Requirements

### Requirement: Project detection and initialization

The plugin SHALL detect OpenSpec projects by checking for `openspec/`, and initialize them through CLI delegation with a built-in fallback. A CLI init SHALL keep the skills in the project, and a failed one SHALL be reported. The fallback SHALL honor the Default schema preference, falling back to `spec-driven`, and SHALL write only the fields upstream `openspec init` writes: a `schema:` line. It SHALL NOT write plugin-invented or empty upstream fields.

#### Scenario: Project detection
- **WHEN** a project is opened in the IDE
- **THEN** the plugin SHALL check for `openspec/` directory and enable/disable features accordingly

#### Scenario: Init delegates to CLI when available
- **WHEN** `ScaffoldingService.initOpenSpec()` is called and the CLI is detected
- **THEN** it SHALL run `openspec init --tools <detected-tools>` and perform a synchronous VFS refresh

#### Scenario: Init keeps the skills in the project on CLI 2.1 and later
- **WHEN** the wizard initializes a project with CLI 2.1.0 or later
- **THEN** the init SHALL pass `--scope project` and set `OPENSPEC_SKILLS_SCOPE=project`, and the project SHALL then hold its own skills under `.agents/skills/`

#### Scenario: Init below CLI 2.1 passes no scope flag
- **WHEN** the wizard initializes a project with a CLI from 1.3.0 to 2.0.x
- **THEN** the init SHALL NOT pass `--scope`, which those releases reject or lack, and SHALL otherwise run the same arguments as before

#### Scenario: Init falls back to built-in
- **WHEN** the CLI is unavailable or fails
- **THEN** the service SHALL create `openspec/config.yaml`, `specs/`, `changes/`, and `changes/archive/` via VFS

#### Scenario: A failed CLI init is reported
- **WHEN** the CLI init exits with an error
- **THEN** the plugin SHALL show a warning carrying the CLI's error text as the CLI wrote it, including the reason lines of its summary
- **AND** when the detected CLI is 2.1.0, the warning SHALL add that this release needs Git and network access during init

#### Scenario: The CLI's init runs off the UI thread
- **WHEN** the plugin initializes a project with the CLI, from the setup wizard, the Init action or the tool window
- **THEN** the CLI SHALL run in a background task, and the IDE SHALL stay responsive while it runs, including while CLI 2.1.0 downloads its skills
- **AND** the result SHALL come back to the caller under the caller's modality, so the setup wizard shows it while open

#### Scenario: A failed CLI init that already wrote openspec
- **WHEN** the CLI init exits with an error after creating `openspec/`, as 2.1.0 does when it can't fetch the skills
- **THEN** the plugin SHALL keep that `openspec/` as the CLI wrote it and SHALL NOT run the built-in init
- **AND** when the CLI wrote no `openspec/`, the built-in init SHALL run as before

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

### Requirement: VFS-consistent file operations

All file writes that create or update content SHALL use plain filesystem I/O (`java.nio.file`) on background threads. VFS refresh operations (`LocalFileSystem.refreshAndFindFileByPath`, `VirtualFile.refresh`) SHALL be performed within `WriteAction` on the EDT. CLI-created files SHALL be followed by synchronous VFS refresh. Tool window refresh SHALL use `syncRefresh()`.

#### Scenario: Files visible after write
- **WHEN** the plugin writes files (init, propose, generate)
- **THEN** they SHALL be immediately visible in the project tree without async delay

#### Scenario: File write does not require WriteAction
- **WHEN** the plugin writes file content via `Files.writeString()` or equivalent
- **THEN** the write SHALL execute outside of `WriteAction` and outside of `invokeAndWait`

#### Scenario: VFS refresh requires WriteAction
- **WHEN** the plugin needs to refresh the virtual file system after a file write
- **THEN** the VFS refresh SHALL execute within `WriteAction` on the EDT

### Requirement: API compatibility

The plugin SHALL avoid scheduled-for-removal APIs and use recommended replacements where available for the supported platform range (2024.2+).

#### Scenario: Plugin Verifier
- **WHEN** Plugin Verifier runs across supported IDE versions
- **THEN** zero override-only violations SHALL be reported

### Requirement: File type registration

The plugin SHALL register `.openspec.yaml` as a custom file type and provide distinct icons for spec files, delta specs, and OpenSpec YAML files.

#### Scenario: File type icons
- **WHEN** a user views `openspec/` files in the project tree
- **THEN** spec files, delta specs, and `.openspec.yaml` files SHALL display their custom icons

### Requirement: Notification system

The plugin SHALL deliver categorized notifications with titles, actions, and bulk operation summaries. API errors SHALL include human-readable messages with actionable suggestions.

#### Scenario: Notifications
- **WHEN** the plugin reports status to the user
- **THEN** notifications SHALL use registered groups, include contextual titles, and provide action links where appropriate

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

### Requirement: CLI version floor notification

The plugin SHALL show a one-time notification when an OpenSpec project is opened and the detected CLI version is older than the supported floor of 1.3.0. The notification SHALL identify the detected version, explain that some CLI-dependent features may not work, and suggest the upgrade command. Users SHALL be able to dismiss the notification permanently via the standard "Don't show again" affordance.

#### Scenario: Old CLI detected at project open
- **WHEN** an OpenSpec project is opened, CLI detection succeeds with a version string older than `1.3.0` (e.g., `1.0.0`, `1.1.5`, `1.2.99`)
- **THEN** the plugin SHALL show a notification with title "OpenSpec CLI is older than 1.3.0" and a body including the detected version and the command `npm i -g @fission-ai/openspec@latest`

#### Scenario: Supported CLI detected at project open
- **WHEN** an OpenSpec project is opened and CLI detection succeeds with a version `1.3.0` or newer
- **THEN** the plugin SHALL NOT show the floor notification

#### Scenario: No CLI detected at project open
- **WHEN** an OpenSpec project is opened and CLI detection finds no CLI
- **THEN** the plugin SHALL fall back to the existing CLI-missing notification path (no floor notification — the user has no CLI to upgrade)

#### Scenario: Notification fires once per project open, not per tool window activation
- **WHEN** the floor notification has already fired during the current project session and the user opens/closes the OpenSpec tool window multiple times
- **THEN** the plugin SHALL NOT re-fire the floor notification — the per-project-open `StartupDetection` hook is the sole trigger

### Requirement: Config profile management

The plugin SHALL allow viewing and switching OpenSpec config profiles (core, expanded, custom) from the Settings panel.

#### Scenario: Profile display
- **WHEN** the user opens OpenSpec Settings
- **THEN** the current profile and active workflows SHALL be displayed

#### Scenario: Profile switch
- **WHEN** the user selects a different profile
- **THEN** the plugin SHALL update the global OpenSpec config and refresh available workflows

### Requirement: CLI update command

The plugin SHALL provide an "Update OpenSpec" action that runs `openspec update` to refresh agent instruction files.

#### Scenario: Update trigger
- **WHEN** the user triggers "Update OpenSpec"
- **THEN** the plugin SHALL run `openspec update` and display the result in the console

#### Scenario: CLI not available
- **WHEN** the CLI is not detected
- **THEN** the action SHALL be disabled with a tooltip explaining that the CLI is required

### Requirement: ComplianceService registration

The plugin SHALL register `ComplianceService` as a project-level service (`@Service(Service.Level.PROJECT)`) in `plugin.xml`. The service SHALL be injectable via `project.getService(ComplianceService.class)` and SHALL compose `BuiltInValidator`, `VerificationService`, and `SpecSyncService` to compute compliance results.

#### Scenario: Service registration
- **WHEN** the plugin starts for a project
- **THEN** `ComplianceService` SHALL be available via `project.getService(ComplianceService.class)`

#### Scenario: Service composes existing services
- **WHEN** `ComplianceService.checkCompliance(change)` is called
- **THEN** it SHALL delegate to `BuiltInValidator` for validation, `VerificationService` for artifact completeness, and `SpecSyncService` for delta spec readiness

### Requirement: CLI execution threading compliance

CLI commands executed via `CliRunner` and `CliDetectionService` SHALL use `Process` directly (via `GeneralCommandLine.createProcess()`) instead of `OSProcessHandler`. This SHALL allow CLI execution under any threading context, including ReadAction.

#### Scenario: CLI execution under ReadAction
- **WHEN** a CLI command is executed from a code path running under ReadAction (e.g., action update cycle)
- **THEN** the execution SHALL complete without throwing a threading violation

#### Scenario: CLI stream draining prevents deadlock
- **WHEN** `CliRunner` executes a CLI command that produces output on both stdout and stderr
- **THEN** both streams SHALL be drained concurrently to prevent buffer deadlock

#### Scenario: CLI detection stream handling
- **WHEN** `CliDetectionService` runs detection commands (version check, which, PATH resolution)
- **THEN** stdout SHALL be fully read before waiting for process exit

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

### Requirement: Cross-thread field visibility

Fields in UI components that are written on background threads and read on the EDT (or vice versa) SHALL be declared `volatile` to ensure cross-thread visibility under the Java Memory Model.

#### Scenario: WorkflowActionPanel field visibility
- **WHEN** a pooled thread updates `activeChangeName`, `nextArtifactId`, `lastPrompt`, `lastOutputPath`, or `hasDeltaSpecs`
- **THEN** the updated value SHALL be visible to the EDT on its next read without requiring explicit synchronization

#### Scenario: UI state captured on EDT before dispatch
- **WHEN** a UI action dispatches work to a background thread that depends on Swing component state
- **THEN** the component state SHALL be read on the EDT before the background dispatch

### Requirement: Supported CLI versions and capability preservation

The plugin SHALL declare its supported OpenSpec CLI versions, with floor `1.3.0`, and SHALL treat its per-version behavior as a preserved contract. Where the installed client provides the underlying command, a capability for that version SHALL NOT be removed, disabled or re-gated without a spec delta, a changelog entry and a test. Changing that set with `Modified Capabilities: None` SHALL be treated as a defect.
#### Scenario: A supported version's client-backed capability is not silently dropped
- **WHEN** a change would remove, disable, or re-gate a user-facing capability that the plugin currently delivers for a supported CLI version whose client still provides the underlying command
- **THEN** the change SHALL include a delta on that capability's spec, a changelog entry, and a test asserting the new behavior, and absent any of these it SHALL be treated as a defect

#### Scenario: The 1.6.x line is a supported generation
- **WHEN** the detected CLI is in the `1.6.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.5.x` (no re-gating), applying the 1.6-generation semantics specified by the affected capabilities (store health, registration outcomes, validator behavior), and per-generation contract coverage SHALL exist for the behaviors that differ

#### Scenario: The 1.7.x line is a supported generation
- **WHEN** the detected CLI is in the `1.7.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.6.x` (no re-gating — the store/workset model and all coordination JSON shapes are unchanged), applying the additive 1.7-generation semantics (a lenient `.openspec.yaml` metadata parse that reads newer keys including `skip_specs` display-only, tolerated `operations`/`defaultStore` config keys, and additive `status --json` `requires` edges keyed by artifact id so the schema-order reorder is inert), and per-generation contract coverage against captured 1.7.0 CLI output SHALL exist for the behaviors that differ

#### Scenario: The 1.8.x line is a supported generation
- **WHEN** the detected CLI is in the `1.8.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.7.x` (no re-gating — the store/workset model and all coordination JSON shapes are unchanged), applying the 1.8-generation semantics: the built-in validator's CLI-absent fallback treats a body-carrying requirement lacking `SHALL`/`MUST` as a **non-failing WARNING in default mode** (re-promoted to a failing verdict under `--strict`), matching the CLI's 1.8 demotion, and the plugin's cross-generation default verdict-parity anchor is `1.8.0` (see the `validation` capability); additive `.openspec.yaml` `retire_capabilities` and `config.yaml` `githubCopilot` keys are tolerated (ignored) by the lenient parsers, and `status --json` `isPlanningComplete` is tolerated alongside the retained `isComplete` alias the plugin reads
- **AND** per-generation contract coverage against captured 1.8.0 CLI output SHALL exist for the behaviors that differ, and a version-floor/at-least assertion SHALL include `1.8.0`

#### Scenario: The 1.9.x line is a supported generation
- **WHEN** the detected CLI is in the `1.9.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.8.x` (no re-gating — the store/workset model, all coordination JSON shapes, and the validator fallback semantics are unchanged), because 1.9 is a strict additive superset of 1.8: the `validate --all --json` default and `--strict` verdict maps over the parity corpus are byte-identical to `1.8.0`, so the cross-generation default verdict-parity anchor remains `1.8.0` (see the `validation` capability); the new `validate --archived` flag and the new task-numbering WARNING are client-side additions that make the CLI *stricter*, so the CLI-absent fallback — which validates neither — stays never-more-restrictive; and the client's "honest root resolution" `no_openspec_root` error envelope (returned when `validate` / `list --json` run outside an OpenSpec root) is not reachable through the plugin, which gates CLI invocation on the on-disk `openspec/` root
- **AND** per-generation contract coverage against captured 1.9.0 CLI output SHALL exist (the parity-corpus twins the version-stability guard auto-discovers), and a version-floor/at-least assertion SHALL include `1.9.0`

#### Scenario: The 1.10.x line is a supported generation
- **WHEN** the detected CLI is in the `1.10.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.9.x` (no re-gating — the store/workset model, all coordination JSON shapes, and the validator fallback semantics are unchanged), because 1.10 is a strict additive superset of 1.9: the validation engine source is byte-identical to 1.9 and the `validate --all --json` default and `--strict` verdict maps over the parity corpus are byte-identical to `1.8.0`, so the cross-generation default verdict-parity anchor remains `1.8.0` and the strict anchor remains `1.6.0` (see the `validation` capability); the new `init --language` flag, Zed adapter target, first-run completion tip relocated to stderr (deferred on `--json` and non-TTY runs so it never enters parsed stdout), and runtime-only `completionTipSeen` global-config field are off-model or inert client-side additions that change no contract the plugin parses; and the plugin's version comparison SHALL order `1.10.0` above `1.9.0` (numeric segment comparison, not lexical) so the first two-digit minor is not misplaced below the floor or an earlier line
- **AND** per-generation contract coverage against captured 1.10.0 CLI output SHALL exist (the parity-corpus twins the version-stability guard auto-discovers), and a version-floor/at-least assertion SHALL include `1.10.0`

#### Scenario: The 1.11.x line is a supported generation
- **WHEN** the detected CLI is in the `1.11.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.10.x` (no re-gating — the store/workset model, all coordination JSON shapes, and the validator fallback semantics are unchanged), because 1.11 is a strict additive superset of 1.10 in the safe direction: the `validate --all --json` default and `--strict` verdict maps over the parity corpus are byte-identical to `1.8.0`, so the cross-generation default verdict-parity anchor remains `1.8.0` and the strict anchor remains `1.6.0` (see the `validation` capability); the new `PURPOSE_IS_PLACEHOLDER` validation rule — a `WARNING` on `overview` for a main spec whose `## Purpose` opens with a `TBD`/`TODO` marker or retains the archive-generated placeholder sentence, non-failing in default mode and re-promoted to a failing verdict under `--strict` — is a client-side addition that makes the CLI *stricter*, so the CLI-absent fallback (which has no placeholder rule) stays never-more-restrictive; and the new batch `status --all`, `show <change> --diff`, Antigravity adapter directory rename, shell-completion generation, and transactional `schema init` are off-model or inert client-side additions that change no contract the plugin parses; and the plugin's version comparison SHALL order `1.11.0` above `1.10.0` and `1.9.0` (numeric segment comparison, not lexical)
- **AND** per-generation contract coverage against captured 1.11.0 CLI output SHALL exist — the parity-corpus twins the version-stability guard auto-discovers, plus a positive-control fixture pinning the `PURPOSE_IS_PLACEHOLDER` rule (its captured `WARNING`-on-`overview` shape asserted against the real capture, and its `valid: true` → `valid: false` verdict flip under `--strict` exercised through the plugin's real validate-output parser) — and a version-floor/at-least assertion SHALL include `1.11.0`

#### Scenario: The 1.12.x line is a supported generation
- **WHEN** the detected CLI is in the `1.12.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.11.x` (no re-gating — the store/workset model, all coordination JSON shapes, and the validator fallback semantics are unchanged), because 1.12 is an additive superset of 1.11 in the safe direction: the validation verdict logic is byte-identical to 1.11, so the `validate --all --json` default and `--strict` verdict maps over the parity corpus remain byte-identical to `1.8.0`, and the cross-generation verdict-parity anchors stay default `1.8.0` and strict `1.6.0` (see the `validation` capability); 1.12's one new validation behavior — a **verdict-neutral `INFO` diagnostic** on `validate` reporting that archiving a change would refuse a delta whose target main spec does not yet exist — never contributes to the `valid` verdict, and the plugin's validate-output parser already surfaces `INFO` at its own severity while taking the verdict from the CLI-authoritative `valid` field, so the diagnostic is mirrored without a plugin change; and the new opt-in `validate --report full|findings` selector (a JSON projection the plugin never requests, leaving the default shape it parses unchanged), the `codeassistant` AI-tool adapter, and per-surface IDE-restart hint wording are off-model or inert client-side additions that change no contract the plugin parses; and the plugin's version comparison SHALL order `1.12.0` above `1.11.0` (numeric segment comparison, not lexical)
- **AND** per-generation contract coverage against captured 1.12.0 CLI output SHALL exist — the parity-corpus twins the version-stability guard auto-discovers, plus a positive-control fixture pinning the archive-blocker `INFO` diagnostic (its captured `INFO` shape asserted against the real capture, and a `valid: false` change carrying both an `ERROR` and the archive-blocker `INFO` on different delta paths exercised through the plugin's real validate-output parser to confirm the `INFO` surfaces at `INFO` severity while the failing verdict is preserved) — and a version-floor/at-least assertion SHALL include `1.12.0`

#### Scenario: The 1.13.x line is a supported generation
- **WHEN** the detected CLI is in the `1.13.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.12.x` (no re-gating — the store/workset model and every coordination JSON shape the plugin parses are unchanged or purely additive), because over the parity corpus the `validate --all --json` default and `--strict` verdict maps of `1.13.2` are byte-identical to `1.12.0`, so the cross-generation verdict-parity anchors stay default `1.8.0` and strict `1.6.0` (see the `validation` capability)
- **AND** where 1.13 made the client **laxer** — `RENAMED` and `REMOVED` delta entries written with any CommonMark bullet marker (`-`, `*`, `+`) now parse — the plugin's CLI-absent fallback, its delta IDE inspection, and its spec sync SHALL accept the same forms, so the fallback is never more restrictive than the client
- **AND** where 1.13 made the client **stricter** with new verdict-affecting ERRORs (an unpaired `RENAMED FROM:` line, a delta file ignored beside a capability's `spec.md`, a scenario header with no body inside a delta, a `### Requirement: Name ###` closing-hash duplicate), the fallback MAY remain laxer and SHALL NOT adopt them as verdict-affecting, because doing so would make the fallback more restrictive than the earlier supported clients it also serves
- **AND** the plugin's task progress (counts and done-ness) SHALL follow the client's 1.13 task-line grammar (see the `verify-workflow` capability), and an item-less `validate --json` error response introduced in 1.13.1 SHALL be treated as a failed run rather than a pass (see the `validation` capability)
- **AND** the plugin's version comparison SHALL order `1.13.0` above `1.12.0` and `1.9.0` (numeric segment comparison, not lexical); per-generation contract coverage against captured 1.13.2 CLI output SHALL exist — the parity-corpus twins the version-stability guard auto-discovers plus a positive-control capture for each mirrored 1.13 behavior — and a version-floor/at-least assertion SHALL include `1.13.0`

#### Scenario: The 1.14.x line is a supported generation
- **WHEN** the detected CLI is in the `1.14.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.13.x` (no re-gating — the store/workset model and every coordination JSON shape the plugin parses are unchanged or purely additive: `show --json` gains requirement and scenario `name` fields, `status --json` and `instructions --json` gain optional `warnings`, and `instructions apply` gains task `sourcePath`/`line`, all of which the plugin's parsers SHALL tolerate)
- **AND** 1.14's new validation warnings — an unrecognized `.openspec.yaml` key (with lowercase `x-*` keys exempt from 1.14.2), an over-long requirement promoted from INFO to WARNING, and text that archiving would not carry into the main spec — SHALL NOT be adopted by the CLI-absent fallback, because each makes the client stricter and the fallback MAY remain laxer; when the CLI is present they SHALL surface at their own severity through the CLI's verdict
- **AND** over the parity corpus the 1.14 default verdicts SHALL remain a subset of the default anchor, and its `--strict` verdicts — stricter than the strict anchor by exactly the corpus item whose pre-requirement text archiving would drop — SHALL remain a subset of the strict anchor (see the `validation` capability)
- **AND** the plugin's version comparison SHALL order `1.14.0` above `1.13.0` and `1.9.0`; per-generation contract coverage against captured 1.14.3 CLI output SHALL exist — the parity-corpus twins, a positive-control capture for each new warning, and captures of the additive output shapes — and a version-floor/at-least assertion SHALL include `1.14.0`

#### Scenario: The 1.15.x line is a supported generation
- **WHEN** the detected CLI is in the `1.15.x` line, whether installed from npm or as the standalone binary
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.14.x` with no re-gating, because every JSON shape the plugin parses is unchanged; over the parity corpus the `validate --all --json` default and `--strict` documents of `1.15.0` match `1.14.3`, so the verdict-parity anchors stay default `1.8.0` and strict `1.6.0`, with the archive-drop divergence pinned from `1.14.2` (see the `validation` capability)
- **AND** the standalone binary's `--version` output SHALL be read by the plugin's CLI detection as version `1.15.0`, the same as the npm install
- **AND** the plugin's version comparison SHALL order `1.15.0` above `1.14.3` and `1.9.0`; per-generation contract coverage against captured 1.15.0 CLI output SHALL exist (the parity-corpus twins and the standalone binary's version output), and a version-floor/at-least assertion SHALL include `1.15.0`

#### Scenario: The 1.16.x line is a supported generation
- **WHEN** the detected CLI is in the `1.16.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.15.x` with no re-gating, because 1.16 changes no command, flag, workflow, AI-tool registry entry or JSON shape the plugin parses; over the parity corpus the `validate --all --json` default and `--strict` documents of `1.16.0` match `1.15.0`, so the verdict-parity anchors stay default `1.8.0` and strict `1.6.0`, with the archive-drop divergence pinned from `1.14.2` (see the `validation` capability)
- **AND** 1.16's new scenario-step WARNING (a scenario with no WHEN/GIVEN step or no THEN step) SHALL surface through the CLI's verdict: a change carrying only that warning reads as passing in default mode and as failing under `--strict`, taken from the CLI-authoritative `valid` field
- **AND** 1.16's configuration error response (an item-less `validate --json` result whose `status` entry carries `unparseable_config` or `unreadable_config`) SHALL be read by the plugin's validate-output reader as an error response with no verdict, never as a passing report
- **AND** the plugin's version comparison SHALL order `1.16.0` above `1.15.0` and `1.9.0`; per-generation contract coverage against captured 1.16.0 CLI output SHALL exist (the parity-corpus twins, the scenario-step warning in default and `--strict` mode, and both configuration error responses), and a version-floor/at-least assertion SHALL include `1.16.0`

#### Scenario: The 1.16.1 patch is supported
- **WHEN** the detected CLI is `1.16.1`
- **THEN** the plugin SHALL deliver the capability set it delivers for `1.16.0` (no re-gating), and per-generation contract coverage against captured 1.16.1 CLI output SHALL exist for the closing-`#`-run heading behaviour and the unrecognized delta-section error
- **AND** a version-floor/at-least assertion SHALL include `1.16.1`

#### Scenario: The 1.17.x line is a supported generation
- **WHEN** the detected CLI is in the `1.17.x` line
- **THEN** the plugin SHALL deliver the capability set it delivers for `1.16.x` with no re-gating, because 1.17 changes no command, flag, workflow, AI-tool registry entry or JSON shape the plugin parses; over the parity corpus the `validate --all --json` default and `--strict` documents of `1.17.0` match `1.16.1`, so the verdict-parity anchors stay default `1.8.0` and strict `1.6.0`
- **AND** the plugin's version comparison SHALL order `1.17.0` above `1.16.1` and `1.9.0`; per-generation contract coverage against captured 1.17.0 CLI output SHALL exist, and a version-floor/at-least assertion SHALL include `1.17.0`

#### Scenario: Supported version set
- **WHEN** the plugin declares its supported CLI versions
- **THEN** the set SHALL be the `1.3.x` through `1.17.x` lines, with the `1.16.1` patch named explicitly, and the version comparison SHALL order numerically (so `1.10.0` sorts above `1.9.0`)

#### Scenario: Faithful mirroring of the client
- **WHEN** the client differs between supported versions
- **THEN** the plugin's delivered capability set MAY differ to track it. For example, the `workspace`, `context-store` and `initiative` commands exist in `[1.4.0, 1.5.0)`, so the 1.4 coordination write actions are offered for `1.4.x` and retired for `1.5.x`
- **AND** a supported version whose client relaxes a validation verdict SHALL be mirrored: the CLI-absent fallback SHALL NOT be more restrictive than that version, even where this makes the fallback laxer than for an earlier version
- **AND** such version-scoped differences SHALL still follow the delta, changelog and test rule above, and SHALL NOT be made silently

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

### Requirement: CLI JSON responses follow one reading contract

Every CLI command the plugin runs with `--json` SHALL have its output read the same way. The JSON payload SHALL be taken from stdout even when other text comes before it. A response that carries an error-severity entry in its top-level `status` array SHALL be treated as a failed run whatever its `code`, and its `message` and `fix` SHALL be kept for display. A failed run SHALL NOT be reported as an empty success.

#### Scenario: No-root envelope is a failure, not an empty result
- **WHEN** a `--json` command prints the captured 1.14.3 `no_openspec_root` response (`root: null`, an error `status` entry) and exits 1
- **THEN** the plugin SHALL report the run as failed with the CLI's message, and SHALL NOT present it as an empty list or a passing result

#### Scenario: Any error code is recognised
- **WHEN** the response's error entry has a code the plugin has never seen, such as `no_root_with_registered_stores`
- **THEN** the plugin SHALL still treat the run as failed and show its message

#### Scenario: Text before the JSON is tolerated
- **WHEN** stdout contains a line of text before a valid JSON payload
- **THEN** the plugin SHALL read the payload exactly as it would read the payload without that text

#### Scenario: A successful archive with text before its JSON
- **WHEN** `openspec archive --json` exits 0 and prints a line of text before its archive result
- **THEN** the plugin SHALL report the archive as completed, not as refused

#### Scenario: Non-JSON output on a non-zero exit is a failure
- **WHEN** a `--json` command exits non-zero and stdout holds no parseable JSON
- **THEN** the plugin SHALL report the run as failed, using stderr as the reason when it has text

### Requirement: Artifact status and instructions failures show the CLI's reason

When `openspec status --change` or `openspec instructions` is rejected by the CLI, the plugin SHALL show the reason from the response's `status` envelope. It SHALL NOT show an earlier cached status for a change the CLI now rejects, and SHALL NOT show a generic "empty response" message when the envelope carries a reason.

#### Scenario: Status for a change that no longer exists
- **WHEN** a change's status was shown earlier and `openspec status --change` now returns the captured 1.14.3 `change_error` response
- **THEN** the plugin SHALL report the CLI's message for that change and SHALL NOT keep showing the earlier artifact status as current

#### Scenario: Instructions rejected for an unknown artifact
- **WHEN** `openspec instructions` returns the captured 1.14.3 `change_error` response with empty stderr
- **THEN** the error shown to the user SHALL contain the envelope's message

#### Scenario: A failure to run keeps the previous status
- **WHEN** the CLI cannot be run at all (not found or timed out) for a change whose status was shown earlier
- **THEN** the plugin MAY keep showing the earlier status, because the CLI gave no answer about the change

### Requirement: CLI runs request plain output

Every CLI process the plugin starts SHALL run with colour and update notices turned off: `NO_COLOR=1`, `FORCE_COLOR=0`, `OPENSPEC_NO_UPDATE_CHECK=1`, together with the existing `OPENSPEC_TELEMETRY=0`. Text output the plugin shows to the user or matches with patterns SHALL have any remaining ANSI escape sequences removed first.

#### Scenario: Coloured output still parses
- **WHEN** `openspec update` output captured with colour codes contains the legacy-cleanup block
- **THEN** the plugin SHALL recognise the block and offer the same files as for the uncoloured capture

#### Scenario: Messages show no escape codes
- **WHEN** a CLI failure message containing ANSI escape sequences is shown to the user
- **THEN** the shown text SHALL contain no escape sequences

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
- **WHEN** the detected release is any supported release from 1.3.0 to 2.1.1
- **THEN** each invocation SHALL pass the same arguments it passed before this requirement, for every release where the plugin already offered it

#### Scenario: An unknown version is treated as the target release
- **WHEN** the CLI is available but its version could not be read
- **THEN** arguments SHALL be built as for the plugin's target CLI release

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

### Requirement: Adding a tool keeps the project's skills scope

With CLI 2.1.0 or later, adding or configuring a tool from Manage AI Tools SHALL pass `--scope project` when the project holds its own skills or has none installed anywhere. When the project's skills are installed only for the machine, it SHALL pass no scope, so the CLI keeps them there. Below 2.1.0 it SHALL pass the same arguments as before.

#### Scenario: A project that keeps its own skills
- **WHEN** the project holds OpenSpec skills under `.agents/skills/` or a tool folder, the CLI is 2.1.0 or later, and the user adds Cursor
- **THEN** the plugin SHALL run `init` with `--tools cursor --scope project`

#### Scenario: A project using the machine's skills
- **WHEN** the project has `openspec/` and no skills of its own, the home directory holds OpenSpec skills, the CLI is 2.1.0 or later, and the user adds Cursor
- **THEN** the plugin SHALL run `init --tools cursor` without `--scope` and without `OPENSPEC_SKILLS_SCOPE`

#### Scenario: An older CLI
- **WHEN** the CLI is 2.0.0 and the user adds Cursor
- **THEN** the plugin SHALL run `init --tools cursor` with no `--scope`

### Requirement: Recorded skills scope is read, never written

The plugin SHALL read `skillsScope` from `openspec/config.yaml` as the team's recorded choice: `project` or `global`. A missing key SHALL mean no recorded choice. Any other value SHALL be flagged in the `config.yaml` editor with CLI 2.1's warning and treated as no recorded choice, and the rest of the config SHALL load. The plugin SHALL NOT write, change or remove `skillsScope`.

#### Scenario: A recorded project scope
- **WHEN** `openspec/config.yaml` holds `skillsScope: project`, as CLI 2.1 writes it
- **THEN** the plugin SHALL read the recorded choice as project

#### Scenario: No key
- **WHEN** `openspec/config.yaml` has no `skillsScope`, as CLI 1.x and 2.0 always leave it
- **THEN** the plugin SHALL read no recorded choice, and the config SHALL load without a warning

#### Scenario: An unknown value
- **WHEN** `openspec/config.yaml` holds `skillsScope: team`
- **THEN** the `config.yaml` editor SHALL flag the value as CLI 2.1 does: `Invalid 'skillsScope' field in config (must be "project" or "global")`
- **AND** the plugin SHALL read no recorded choice, and SHALL still load the config's other fields

#### Scenario: Never written
- **WHEN** the plugin initializes a project, adds a tool, or saves Settings
- **THEN** it SHALL NOT add, change or remove `skillsScope` in `openspec/config.yaml`

### Requirement: The skills scope is shown

Manage AI Tools and the OpenSpec Settings page SHALL show one read-only line saying where the project's OpenSpec skills are: in this project, on this machine, or not installed, and SHALL say when `skillsScope: project` is recorded. On a project whose skills are only on the machine, Manage AI Tools SHALL offer, on CLI 2.1.0 or later, to install them into the project for tools the user picks, and SHALL offer no action below 2.1.0.

#### Scenario: Skills in the project
- **WHEN** the project holds its own OpenSpec skills under `.agents/skills/`
- **THEN** the line SHALL read "Skills: in this project (.agents/skills)"

#### Scenario: Skills only on the machine
- **WHEN** the project holds no OpenSpec skills and the home directory's `.agents/skills/` does
- **THEN** the line SHALL read "Skills: on this machine (~/.agents/skills)"
- **AND** with CLI 2.1.0 or later, Manage AI Tools SHALL offer "Install into this project"

#### Scenario: Picking the tools to install into the project
- **WHEN** the user chooses "Install into this project"
- **THEN** a tool list SHALL open with ticks set as the CLI's own picker sets them for a project with no configured tools: each tool detected in the project, and each tool the CLI detects as installed on the machine, except the "Other / Universal" entry
- **AND** the preferred AI tool SHALL also be ticked
- **AND** confirming SHALL run `init --tools <ticked ids> --scope project`, and confirming SHALL NOT be possible with no tool ticked

#### Scenario: Cancelling the tool list
- **WHEN** the user closes the tool list without confirming
- **THEN** no command SHALL run and nothing in the project or the home directory SHALL change

#### Scenario: Skills on the machine, older CLI
- **WHEN** the project's skills are only on the machine and the CLI is 2.0.x or 1.x
- **THEN** the line SHALL say the skills are installed for this machine, Manage AI Tools SHALL offer no install action, and the plugin SHALL NOT prompt to configure tools

#### Scenario: A recorded choice
- **WHEN** `openspec/config.yaml` holds `skillsScope: project`
- **THEN** the line SHALL also say the choice is recorded in `openspec/config.yaml`

#### Scenario: The line is read off the UI thread
- **WHEN** Manage AI Tools or Settings opens
- **THEN** the home directory SHALL be read off the UI thread, as the home-directory skills already are

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

### Requirement: Set-up status follows the CLI's tool list, folder ownership and folders

The plugin SHALL count a tool as set up exactly when the CLI's `getConfiguredTools` does, including a tool it doesn't detect. A tool the tool list names counts by the CLI's rule. From CLI 1.8, where tools share a skills folder on 1.x, only its owner counts. From CLI 1.7, a folder that is only a detection path SHALL NOT make a tool set up. Before the release that introduced a rule, it SHALL NOT apply.

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
