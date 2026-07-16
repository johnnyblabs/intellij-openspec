# Changelog

> **Maintenance: Living** — updated as part of every release (see the [documentation index](docs/README.md)).

## Unreleased

## v0.4.0

### Added

- **Documentation maintenance framework.** Every doc now carries an explicit `Maintenance:` class (Living / Snapshot / Reference / Retired), a new [documentation index](docs/README.md) maps every doc with its purpose, audience, and class, and the [Version support](docs/openspec-support.md#version-support) block is the single source of truth other docs link to for version facts. A doc-hygiene test enforces that every doc is labeled and indexed.
- **Coordination write actions restored for the OpenSpec 1.4.x CLI line.** On an OpenSpec CLI in the `1.4.0`–`1.4.x` range, the Coordination tab again offers the create/set-up actions — **New Initiative**, **Set Up Context Store**, and **Set Up Workspace** — at its Full tier, delegating to the CLI and refreshing the listing on success. These shipped in earlier releases and were inadvertently dropped when the tab was rebuilt around the 1.5 store/workset model; this restores them. They are **version-gated and self-retiring**: OpenSpec CLI 1.5.0 removed the underlying `initiative` / `context-store` / `workspace` commands, so when you upgrade to a 1.5 CLI these actions disappear and the tab presents the stores/worksets model instead. Actions run off the UI thread and surface the CLI's error output on failure.
- **Read-only view of the OpenSpec 1.5 store and workset model in the Coordination tab.** OpenSpec CLI 1.5.0 introduced **stores** (standalone OpenSpec repos you register on your machine) and **worksets** (purely local, composed working views over them), replacing the earlier workspace/context-store/initiative model. When the detected CLI is 1.5.0 or later, the tab now lists your stores — each with its id, root, and health from `store doctor` (metadata present/valid, whether the root is a git repository, whether its OpenSpec root is healthy) — and your worksets with their member folders. Store diagnostics, including the CLI's ready-made fix suggestion, are shown as read-only guidance. Listings come from the OpenSpec CLI, with a built-in fallback that reads OpenSpec's global data directory directly when the CLI is unavailable. Any surviving pre-1.5 coordination state is shown, read-only, in a muted "Legacy (pre-1.5)" group. The plugin never migrates anything; it only reflects the state the CLI owns.
- **The schema authoring loop is now complete in Settings.** Custom workflow schemas could be forked and created from the IDE, but checking the result meant a terminal round-trip. The Schemas section now offers **Validate** — runs `openspec schema validate` on the selected schema and shows its findings (structure errors, missing templates, circular dependencies) inline below the list — and **Open Templates**, which resolves the schema's artifact templates via the CLI and opens them as ordinary editor tabs, giving schema authors a direct edit loop. Each schema in the list also carries a **resolution provenance tag** (`[project]`, `[user]`, `[package]`) sourced from `openspec schema which`, and a copy that shadows another source says so explicitly (e.g. `[project, shadows package]`) — so name shadowing is visible instead of surprising. All three actions delegate to the CLI, run off the UI thread, and follow the existing schema-management availability gate (OpenSpec CLI 1.3.0+; verified present on 1.3.x).
- **Store and workset write actions in the Coordination tab.** With OpenSpec CLI 1.5.0 or later, the tab's Full tier now delegates create/manage actions to the CLI: create a store (`store setup`, with a required folder location) or register an existing one, unregister it, or remove it (a guarded, destructive action that deletes the store's local folder); and create a workset from a chosen set of member folders, open a workset (revealing its member folders in your file manager, behind a confirmation), or remove a saved workset (member folders are left untouched). A `store doctor`-driven health strip surfaces the highest-severity diagnostic with its suggested `fix` as an inline action. Actions run off the UI thread and surface the CLI's own error/fix text on failure, never raw output.
- **Graceful legacy-file cleanup in the Update action.** The OpenSpec CLI's skills migration can leave `openspec update` reporting leftover files to remove while exiting 0 and suggesting `--force` or an interactive run — neither of which a non-interactive IDE console can provide, so the update looked successful but re-printed the same notice forever. The Update action now recognizes this outcome and offers a resolution instead of silent success: a review dialog lists every CLI-reported file with a checkbox and an open-to-inspect link, removal happens through the IDE as a single undoable step (covered by Local History and your VCS), and a follow-up `openspec update` verifies the result. When that verification shows the CLI *regenerating* the very files it flags — a real inconsistency in some CLI versions' tool integrations — the plugin explains that nothing on your side needs fixing and stops re-raising the notice until the CLI reports something different. You can also hand off to the terminal for the CLI's own interactive flow, or dismiss without being re-nagged while the pending file set is unchanged. The plugin never runs `openspec update --force` on your behalf.

### Changed

- **Built-in validation now matches OpenSpec CLI 1.6 verdicts.** The plugin's own validator (used by editor inspections and when the CLI is unavailable) now agrees with `openspec validate` on what passes: only `SHALL`/`MUST` satisfy the requirement-keyword rule (`SHOULD`/`MAY` never satisfied the CLI, so specs relying on them were passing the plugin while failing the CLI), keywords and `#### Scenario:` headers inside fenced code blocks no longer count (matching 1.6's fence-aware evaluation), and non-canonical level-3 headers inside a change's ADDED/MODIFIED delta sections get a new advisory-level hint — mirroring the CLI's 1.6 notice that such headers are skipped by validation — without ever affecting the pass/fail verdict. Verdict agreement is enforced by a contract test against captured real 1.6.0 CLI output. The plugin now formally supports the `1.3.x`–`1.6.x` CLI lines.

- **Store health follows OpenSpec CLI 1.6 semantics.** OpenSpec CLI 1.6.0 made a brand-new store legitimate before its planning directories exist: registering such a folder succeeds (1.5 refused it), and `store doctor` reports it healthy with the directories simply marked not-yet-present. The Coordination tab reflects this faithfully — store health is read solely from the CLI's own report, so a fresh, empty-but-healthy store lists cleanly with no error marker, on both CLI generations. The 1.6 register refusals (a folder whose `openspec/config.yaml` points at an external store, or an invalid `store:` declaration) and the new register identity-confirmation prompt surface the CLI's message and suggested fix verbatim, as all store actions do. Store/workset features continue to require CLI 1.5.0+; nothing changes for 1.5 users.
- **Verify's completeness check is now schema-aware, driven by the OpenSpec CLI.** The artifact-level completeness gate reads the change's own artifact set from `openspec status` — the same source the Apply gate uses — instead of checking a fixed `proposal.md`/`design.md`/`tasks.md` list against the filesystem. Verify and Apply can no longer disagree about whether a change is complete, and schemas whose artifact set differs from the classic three-file layout (for example a not-yet-written `specs` artifact) are reported correctly. Task-level granularity is unchanged: unchecked (`- [ ]`) and in-progress (`- [~]`) checkboxes in `tasks.md` still block archive with distinct counts. When the CLI is unavailable or below the supported floor, Verify falls back to the previous filesystem checks rather than failing.
- **Spec validation now matches the OpenSpec CLI 1.4 parser.** Requirement headers (`### Requirement:`) are recognized **case-insensitively** everywhere the plugin reads them — validation, editor inspections, the spec tree, and delta→main spec sync — matching how the CLI has parsed them since 1.4.0. Previously a spec the CLI accepted (e.g. `### requirement:`) could be flagged as missing requirements or silently left out of the tree. Sync continues to write headers in the canonical `### Requirement:` casing. In addition, a requirement whose RFC 2119 keyword appears only in its header line now gets the CLI's targeted guidance — *move the keyword onto the requirement body line* — as a dedicated diagnostic with an editor quick-fix, instead of a generic missing-keyword error.

- **Windows and macOS are now verified in CI alongside Linux.** The plugin's build and test suite runs on Windows and macOS hosts in addition to Linux, exercising platform-specific behavior Linux can't — Windows `%LOCALAPPDATA%\openspec` data-dir resolution and backslash/UNC path handling, the `.cmd` launcher shim invocation from paths containing spaces, and root canonicalization across symlinks (macOS/Linux) and 8.3 short paths (Windows). CRLF-vs-LF parse parity for the store and workset listings is checked on every platform. This hardens the store/workset read surface for Windows users in particular.
- **The Coordination tab stands down cleanly on OpenSpec CLI 1.5.0 and later.** CLI 1.5.0 removed the `workspace`, `context-store`, and `initiative` commands (replaced by a new store/workset model). The plugin now recognizes these as a 1.4-line-only capability: it invokes those commands only when the detected CLI is in the `[1.4.0, 1.5.0)` window, and on a 1.5.0+ CLI it no longer calls them or offers create/set-up actions that would fail — showing a read-only view when legacy on-disk coordination state exists, and hiding the tab otherwise. The built-in schema set is now `spec-driven` only, matching what a 1.5.0 CLI reports (the `workspace-planning` schema remains recognized on a 1.4.x CLI via its live schema list). The minimum supported CLI is unchanged at 1.3.0.

### Fixed

- **Explore mode honors your project's customized skill instructions again.** Since OpenSpec CLI 1.5 moved agent instructions to `.claude/skills/`, the Explore feature was still looking only in the old pre-1.5 locations — so on current projects it silently ignored any project-level customization of the explore skill and always used its built-in default. Explore now reads the current skills location first (including CLI 1.6's stamped skill files), with the old locations kept as fallbacks for pre-1.5 projects.
- **Validation results no longer drop errors on OpenSpec CLI 1.6.** CLI 1.6.0 reports some validation issues under bracketed paths (e.g. `requirements[0]`), which the plugin's previous output scanning misread — an affected error could vanish from the Validate results entirely. Validation JSON is now parsed structurally, so every error the CLI reports is shown, on every supported CLI generation.
- **The JetBrains Marketplace listing now shows the plugin's full description.** The listing had been displaying a one-line summary because the build was overwriting the rich description at packaging time. The description is now sourced from the README's overview section — one source of truth for GitHub and the Marketplace — and covers the current feature set: spec browser, change lifecycle, AI-tool handoff, schema authoring, and stores/worksets.

## v0.3.1

### Fixed

- **Coordination tab no longer silently drops to its offline view on the first OpenSpec CLI call in a fresh environment.** On its first run the OpenSpec CLI prints a one-time telemetry notice to standard output, ahead of the JSON that `--json` commands emit. That notice corrupted the plugin's JSON parsing, so the workspaces / context-stores / initiatives panel fell back to reading the global data directory instead of the live CLI result. The plugin now opts its own CLI invocations out of telemetry and tolerates any leading banner on CLI output, so the panel reflects live CLI state from the very first call.

## v0.3.0 — OpenSpec 1.4 Baseline

### ⚠️ Breaking

- **Minimum supported OpenSpec CLI is now 1.3.0.** Users on CLI 1.0, 1.1, or 1.2 will see a one-time startup notification recommending upgrade via `npm i -g @fission-ai/openspec@latest`. The plugin continues to function on its built-in fallback paths (project detection, init, spec browser, tool window) — but features that require the CLI (schema management, CLI-driven generation, agent instruction updates) gracefully degrade with the same "CLI not detected" UX the plugin already handles. To stay on a pre-1.3 CLI, pin to plugin v0.2.10.

### Added

- **Coordination tab for OpenSpec 1.4 workspaces, context stores, and initiatives.** A new tool-window tab surfaces the three coordination collections, sourced from the OpenSpec CLI (`workspace`/`context-store`/`initiative`) with a built-in fallback that reads OpenSpec's global data dir directly when the CLI is unavailable. The tab appears only when coordination state (or a coordination workflow mode) is detected, so spec-driven repo-local projects are unaffected. Initiatives show a lifecycle status badge (`exploring`/`active`/`complete`/`archived`); each initiative's artifacts (`initiative.yaml`, `requirements.md`, `design.md`, `decisions.md`, `questions.md`, `tasks.md`) open in the editor. With OpenSpec CLI 1.4+ the tab also offers create-initiative, set-up-context-store, and set-up-workspace actions; without it, the tab is read-only.
- **Detection for two AI tools introduced in OpenSpec CLI 1.4.0** — Kimi CLI (Moonshot AI) and Mistral Vibe. Supported tool count expands from 28 to 30.
- **Tailored delivery guidance** for both new tools. Kimi CLI and Mistral Vibe show terminal-style copy ("Paste into Kimi CLI", "Paste into Mistral Vibe") alongside Claude Code, Gemini, Codex, OpenCode, ForgeCode, and Bob Shell, and the IDE watches `tasks.md` for completion instead of prompting for manual save. The generic "Paste into your AI tool" fallback does not appear for these tools.
- **`workspace-planning` workflow schema is accepted as valid** under the V1_2 config baseline, matching its introduction upstream in OpenSpec CLI 1.4.0.
- **`RENAMED` delta sections are now fully supported** across validation, the inline delta-spec inspection, and the scaffolded delta template. `## RENAMED Requirements` blocks with `FROM:` / `TO:` pairs are recognized and validated, completing the four-section delta contract (ADDED, MODIFIED, REMOVED, RENAMED) and matching upstream OpenSpec. Spec sync applies operations in upstream order (RENAMED → REMOVED → MODIFIED → ADDED).

### Changed

- **Workflow-profile picker aligned with the CLI.** The profile combo in Settings now lists only CLI-accepted presets (the unsupported "custom" target was removed), surfaces the underlying cause when a CLI profile switch fails, and shows recovery guidance when a profile value carried over from an older plugin version is selected — with Apply disabled until you pick a supported preset.
- **Schema-name validation is driven off the CLI runtime.** Valid schema names are now the union of the plugin's built-in set and the live list from your installed CLI, and warnings report CLI status (available / unavailable / below floor) plus the recovery action.

### Fixed

- **Built-in project init now honors your Default schema setting.** Initializing a project without the CLI previously always wrote `schema: spec-driven` into `openspec/config.yaml`, ignoring the Default schema chosen in Settings (e.g. `workspace-planning`). The setting now flows through, falling back to `spec-driven` when unset.
- **Custom (forked) schemas no longer trigger a false validation warning.** A legitimate `openspec schema fork` name is now recognized instead of being flagged as unknown.
- **Config validation no longer flags a freshly initialized project.** The plugin previously emitted plugin-only errors and warnings (`config-missing`, `config-version-required`, `config-field-required`, `config-profile-recommended`) on an untouched `openspec/config.yaml` that upstream OpenSpec considers perfectly valid. Those rules were dropped; genuine upstream-aligned checks (schema required, schema/version recognized) remain.
- **`REMOVED` requirement validation no longer rejects valid delta specs.** The plugin reported a `REMOVED` requirement missing `**Reason**`/`**Migration**` as an ERROR, and its field check didn't recognize the `**Reason:**` (colon-inside) bold form — so delta specs the OpenSpec CLI accepts were flagged as invalid. This is now an advisory WARNING (the OpenSpec client validates `REMOVED` blocks by name and does not require these fields), and both the `**Reason:**` and `**Reason**:` formats are recognized.
- **Verify now counts in-progress (`[~]`) tasks.** The pre-archive completeness check previously counted only `- [ ]` and `- [x]` checkboxes, so a task marked in progress with `- [~]` was dropped from the count entirely — a change could report "ready to archive" while in-progress work remained. In-progress tasks now count toward the total and block archiving like unchecked tasks, and are reported distinctly (e.g. "2 task(s) not done (1 in progress)").
- **Settings "Version override" no longer lists values it can't apply.** The dropdown offered `1.3.0`/`1.4.0`, but the override targets the config-format version (a single baseline, independent of your installed CLI version), so selecting those had no effect. The dropdown now lists only applicable values; a custom value can still be typed.

### Removed

- **The Coverage tab and `@spec` gutter markers have been removed.** These surfaces relied on a plugin-specific `@spec <domain>:<requirement>` code annotation that is not part of OpenSpec — OpenSpec has no concept of annotating source code or scoring spec "coverage." Spec browsing and navigation remain in the Browse tab; for a per-change completeness check, use OpenSpec's own `verify-change` workflow, which requires no annotations and works in any language.

## v0.2.10 — Windows Support & OpenSpec 1.3 Tools

### Fixed

- **OpenSpec CLI is now auto-detected on Windows.** The plugin previously could not find `openspec.cmd` from npm or winget installs because Java's process launcher does not consult Windows `PATHEXT`. Detection now searches `%APPDATA%\npm`, `%LOCALAPPDATA%\npm`, and winget shim locations, and falls back through `.cmd` / `.bat` / `.exe` suffixes for any candidate path. The Settings panel and Setup Wizard surface a Windows-specific hint when a manual path is needed. macOS and Linux detection paths are unchanged. Fixes #11.

### Added

- **Detection for four AI tools introduced in OpenSpec CLI 1.3.0** — Junie (JetBrains), Lingma (Alibaba), ForgeCode, and Bob Shell. Supported tool count expands from 24 to 28.
- **Tailored delivery guidance** for each of the four new tools. ForgeCode and Bob Shell show terminal-style copy ("Paste into ForgeCode", "Paste into Bob Shell") alongside Claude Code, Gemini, Codex, and OpenCode. Junie and Lingma show panel-style copy ("Open Junie and paste the prompt", "Open Lingma chat and paste the prompt") alongside GitHub Copilot, Cursor, Cline, and Kiro. The generic "Paste into your AI tool" fallback no longer appears for these tools.

## v0.2.9 — EDT Threading Compliance

- **Deadlock fix**: Replaced `invokeAndWait` with `invokeLater` in WorkflowActionPanel archive path — eliminates deadlock when EDT is blocked on a modal
- **Deadlock fix**: Replaced `invokeAndWait` with `invokeLater` + `CountDownLatch` in BulkArchiveDialog archive loop
- **Deadlock fix**: Replaced `invokeAndWait` with `invokeLater` + `CountDownLatch` in SpecSyncService VFS refresh loop
- **EDT unblock**: OpenSpecInitAction scaffolding/CLI now runs via `ProgressManager.Backgroundable`
- **EDT unblock**: OpenSpecProposeAction file creation dispatched to pooled thread
- **VFS threading**: ExploreContextAction VFS refresh moved to background thread — only editor open on EDT

## v0.2.8 — Spec Sync & Threading Compliance

- **Threading compliance**: CliRunner and CliDetectionService use `Process` directly instead of `OSProcessHandler`, eliminating ReadAction threading violations
- **SpecSyncService fix**: file writes separated from `WriteAction` — content written via `Files.writeString()` on background thread, VFS refresh in `WriteAction` on EDT
- **Cross-thread field visibility**: volatile fields on WorkflowActionPanel for safe EDT/background thread access
- **Sync Specs icon button**: dedicated icon in the action bar between Verify and Archive, enabled when delta specs exist
- **Archive guard for unsynced specs**: three-option dialog (Sync First / Archive Without Syncing / Cancel) when archiving changes with unsynced delta specs
- **Overflow menu cleanup**: Sync Specs removed from overflow menu (now in icon bar), menu empty when idle

## v0.2.7 — Explore Thinking Space & Multi-Agent

- **Explore thinking space**: topic dialog for focused exploration with structured prompt assembly and markdown rendering
- **Profile-aware action visibility**: actions show/hide based on active workflow profile capabilities
- **Multi-agent commands**: skill and command files for Augment, Codex, Gemini, and GitHub Copilot agents
- **Conditional Explore tab**: tab appears only when profile supports explore features
- **CLI detection on activation**: detect CLI availability when tool window activates, not just at startup
- **FF requires Direct API**: fast-forward gated on Direct API profile to ensure generation works
- **EDT threading fix**: workflow panel updates dispatched to Event Dispatch Thread correctly
- **Explore action alignment**: ExploreContextAction integrated with new prompt service and panel service
- **Enriched explore context**: expanded context assembly with project structure and spec summaries
- **Delta spec quick-fix**: inspection quick-fix for delta spec issues
- **Config YAML parse warning**: graceful handling of malformed config YAML
- **Empty PSI inspection crash fix**: guard against null PSI elements in inspections

## v0.2.6 — Stability & Context

- **Release pipeline spec**: CI-only signing and publishing to JetBrains Marketplace via `v*` tag
- **Local publish prohibition**: `signPlugin` and `publishPlugin` blocked from local execution

## v0.2.5 — Validation & Onboarding

- **Config version validation**: version field presence, recognition, and required-fields-per-version checks
- **Schema-version cross-check**: warn when a change uses a schema incompatible with the project version
- **Smart onboarding**: skip setup wizard for already-initialized projects, show tree view directly
- **Icon bar redesign**: Apply and Compliance promoted to first-class icon bar actions
- **Overflow menu cleanup**: trimmed to change-scoped actions only (Sync Specs, Cancel Generation)

## v0.2.4 — Open Source & Workflow Engine

- **Fast-Forward**: one-click change creation + full artifact generation
- **Continue**: incremental one-artifact-at-a-time generation
- **Verify**: check artifact completeness and requirement coverage
- **Pipeline redesign**: interactive chips with click-to-generate, context menus, compact icon bar, and status strip
- **Explore panel**: assembled project context for AI conversations with auto-refresh and VFS listener
- **Custom schemas**: list, fork, and create workflow schemas via OpenSpec CLI
- **Config viewer**: browse `openspec/config.yaml` as tree nodes in the Browse tab
- **Compliance pre-flight**: three-category check (artifacts, validation, sync readiness) gating archive
- **Delta spec sync**: merge ADDED/MODIFIED/REMOVED/RENAMED sections into main specs with diff preview
- **Bulk archive**: multi-change archive with conflict detection and sequential sync
- **Built-in validation**: 14 rule codes covering config, specs, changes, and delta specs with IDE inspections
- **CLI update action**: refresh agent instruction files from the IDE
- **Open source**: Apache 2.0 license, GitHub repo, GitHub Actions CI, community files (CONTRIBUTING, CODE_OF_CONDUCT, SECURITY)
- **Plugin signing**: signed builds on CI for JetBrains Marketplace trust badge

## v0.2.3 — AI Tool Management

- **Manage AI Tools dialog**: configure detected AI tools from within the IDE
- **Wizard tool selector fix**: tool selector in setup wizard now works correctly
- **Dialog crash fix**: hardened null safety in Manage AI Tools dialog
- **Welcome screen branding**: updated welcome panel with Fission AI attribution

## v0.2.2 — Review Ready

- **Monochrome tool window icon**: matching JetBrains platform conventions
- **API compliance**: updated Anthropic API to version 2024-06-01, fixed OpenAI o1-series model compatibility
- **Wording consistency**: corrected CLI install instructions and standardized UI terminology
- **Vendor info**: updated vendor URL and contact information

## v0.2.1 — Patch Fixes

- **CLI-aligned init**: delegates to `openspec init` when CLI detected, generating skills and commands for all 24 supported AI tools
- **Branded onboarding**: 32x32 OpenSpec icon and "Spec-Driven Development" tagline in getting started panel and setup wizard
- **Fix: first-run state detection**: projects with archived changes skip onboarding correctly
- **Fix: wizard propose button**: "Create Your First Change" now actually persists the change to disk
- **Fix: VFS refresh timing**: no more false config-missing errors after init
- **Fix: deprecated API cleanup**: replaced `ActionUtil.performActionDumbAwareWithCallbacks`
- **HiDPI-safe text widths** and improved dark theme tree colors

## v0.2.0 — Spec Intelligence

- **Gutter markers**: `@spec` references in Java source are annotated with clickable icons linking back to the spec
- **Coverage panel**: new Coverage tab in the OpenSpec tool window shows which requirements are referenced in code
- **Removed in-plugin issue-tracker integration** — external AI workflows handle this better

## v0.1.0 — Ship It Clean

- **Spec browsing** with tree view (domains, capabilities, requirements)
- **Workflow automation**: Init, Propose, Apply, Archive actions
- **AI-assisted generation** via Claude, OpenAI, and Gemini APIs
- **Spec validation** and format inspections
- **Setup wizard** and onboarding
- **File type support** for `.openspec.yaml` with custom icon
- **Tool window** with Browse, Console, and Workflow tabs
