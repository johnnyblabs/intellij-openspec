# Proposal

## Why

OpenSpec CLI 2.2.0 is the newest release. The plugin targets 2.1.1, the release the built-in validator follows when no CLI is installed. Running the real 2.2.0 shows it matches 2.1.1 in validation, commands, flags, JSON output, workflows and root resolution. Its one addition is a router skill, `openspec`, which the plugin doesn't recognize. Moving the target to 2.2.0 makes the release plugin v2.2.0, which supports CLI 1.3.0 through 2.2.x. Tracked in the linked tracker entry.

## What Changes

- **The plugin targets CLI 2.2.0** and still supports every release from 1.3.0. With no CLI installed, the built-in validator follows 2.2.0's rules, which equal 2.1.1's.
- **2.2.0 is captured** like every supported release: the validation parity corpora, the `--help` sweep, the tool registry and detection layouts, workflows and invocations, update dry runs and migration, archive, store-root resolution and skills scope. The version guards include it.
- **The router skill is recognized.** The `openspec` skill folder counts as one of OpenSpec's own skills. The update preview shows its tool-folder link as a link, and warns about edited router files the update would overwrite. Whether the plugin offers an action for `/openspec` is decided separately.
- **This repository follows its own CLI.** `openspec update` with 2.2.0 adds the router skill to the shared `.agents/skills/` layout and its tool-folder link.
- **Public surfaces say "OpenSpec CLI 1.3.0 through 2.2.x".**
- **The 2.x layout UI smoke journey runs on CLI 2.2.0.**

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: tool detection and CLI argument fitting extend their release range to 2.2.0, and the router skill counts as an OpenSpec skill.
- `cli-update`: the update preview covers the router skill's link and edited files.
- `ui-smoke-journeys`: the 2.x layout journey runs on CLI 2.2.0.

## Impact

- **Build:** `gradle.properties` `openspecTargetVersion=2.2.0`; the installed CLI must match.
- **Code:** `AiToolRegistry.SKILL_NAMES`, `UpdateMigration` link and at-risk patterns.
- **Fixtures and scripts:** a full `fixtures/cli/2.2.0/` set and `archive-2.2.0`; `capture-skills-scope.py` gains 2.2.0.
- **Tests:** version guards (`ValidatorVerdictVersionStabilityTest`, `CliVersionAtLeastTest`), `TargetVersionSingleSourceTest`, the contract tests that iterate captured releases, the uiSmoke journey.
- **Repository surfaces:** `.agents/skills/openspec/` and its `.claude/skills/openspec` link.
- **Docs:** README, `openspec-support.md`, the `cli-versions` notes, feature reference, Marketplace page, comparison matrix, CHANGELOG.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
