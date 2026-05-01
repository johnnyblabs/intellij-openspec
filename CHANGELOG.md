# Changelog

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
- **Removed in-plugin tracker integration** (Forgejo/Plane) — external AI workflows handle this better

## v0.1.0 — Ship It Clean

- **Spec browsing** with tree view (domains, capabilities, requirements)
- **Workflow automation**: Init, Propose, Apply, Archive actions
- **AI-assisted generation** via Claude, OpenAI, and Gemini APIs
- **Spec validation** and format inspections
- **Setup wizard** and onboarding
- **File type support** for `.openspec.yaml` with custom icon
- **Tool window** with Browse, Console, and Workflow tabs
