# Proposal

## Why

The plugin's CLI 2.x support was built and contract-tested against CLI 2.0.0, 2.1.0 and 2.1.1, but its target release is still 1.17.0. That is the release the built-in validator follows when no CLI is installed, and the release this repository's own OpenSpec files are kept with. Plugin v2.1 supports OpenSpec CLI 1.3.0 through 2.1.x, so it targets 2.1.1. The npm install hint also names the wrong Node.js floor for 2.1. Tracked in the linked tracker entry.

## What Changes

- **The plugin targets CLI 2.1.1** and supports every release from 1.3.0 through 2.1.x. With no CLI installed, the built-in validator follows 2.1.1's rules: a MODIFIED requirement that grows past 500 characters is a warning, and a delta spec may start with front matter.
- **The npm install hint names Node.js 22.20 or later.** It says 22.12 today, which has been wrong since CLI 2.1.0.
- **This repository follows its own CLI.** `openspec update` with 2.1.1 moves its skills to the shared `.agents/skills/` layout, and the agent instructions' restore recipe is revised for it.
- **Public surfaces say "OpenSpec CLI 1.3.0 through 2.1.x".** A 2.x support note and a diagram of the 1.x → 2.x skills layout are added.
- **A UI smoke journey covers the 2.x layout:** project-scoped init, a tool set up through `.agents`, and the migration banner.
- **The workflow ↔ UI parity audit runs against 2.1.1**, as the release gate requires.
- **Not here:** CLI 2.2, its router skill and its captures belong to plugin v2.2.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `onboarding`: the npm install route states upstream's Node.js floor.
- `ui-smoke-journeys`: a 2.x layout journey joins the suite.

## Impact

- **Build:** `gradle.properties` `openspecTargetVersion=2.1.1`; the installed CLI must match (a pinned global npm install of 2.1.1).
- **Code:** `CliInstallHints`.
- **Tests:** `TargetVersionSingleSourceTest`, the tests that follow the target release when no CLI is installed, and the uiSmoke journey.
- **Repository surfaces:** the managed skills move to `.agents/skills/` with links; `.claude/commands/opsx/` goes; the agent instructions' restore recipe changes.
- **Docs:** README, `openspec-support.md`, a `cli-versions/2.x` note, feature reference, marketplace page, comparison matrix, CHANGELOG.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
