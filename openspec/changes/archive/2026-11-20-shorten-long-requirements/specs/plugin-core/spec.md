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

#### Scenario: Supported version set
- **WHEN** the plugin declares its supported CLI versions
- **THEN** the set SHALL be the `1.3.x` through `1.16.x` lines, with the `1.16.1` patch named explicitly, and the version comparison SHALL order numerically (so `1.10.0` sorts above `1.9.0`)

#### Scenario: Faithful mirroring of the client
- **WHEN** the client differs between supported versions
- **THEN** the plugin's delivered capability set MAY differ to track it. For example, the `workspace`, `context-store` and `initiative` commands exist in `[1.4.0, 1.5.0)`, so the 1.4 coordination write actions are offered for `1.4.x` and retired for `1.5.x`
- **AND** a supported version whose client relaxes a validation verdict SHALL be mirrored: the CLI-absent fallback SHALL NOT be more restrictive than that version, even where this makes the fallback laxer than for an earlier version
- **AND** such version-scoped differences SHALL still follow the delta, changelog and test rule above, and SHALL NOT be made silently
