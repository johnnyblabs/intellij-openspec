# Changelog

## v0.2.8 — Spec Sync & Threading Compliance

- **Threading compliance**: CliRunner and CliDetectionService use `Process` directly instead of `OSProcessHandler`, eliminating ReadAction threading violations
- **SpecSyncService fix**: file writes separated from `WriteAction` — content written via `Files.writeString()` on background thread, VFS refresh in `WriteAction` on EDT
- **Cross-thread field visibility**: volatile fields on WorkflowActionPanel for safe EDT/background thread access
- **Sync Specs icon button**: dedicated icon in the action bar between Verify and Archive, enabled when delta specs exist
- **Archive guard for unsynced specs**: three-option dialog (Sync First / Archive Without Syncing / Cancel) when archiving changes with unsynced delta specs
- **Overflow menu cleanup**: Sync Specs removed from overflow menu (now in icon bar), menu empty when idle
- **Spec sync audit**: 11 new main specs created from archived delta specs, 4 existing specs updated with missing content
- **Forgejo issue triage**: 7 completed issues closed with version references, 12 no-milestone issues assigned to v0.3.0

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
