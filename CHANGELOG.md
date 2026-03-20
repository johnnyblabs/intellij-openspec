# Changelog

## v0.3.0 — Open Source & Workflow Engine

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
