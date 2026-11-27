# Design

## Context

Plugin v2.1.0 shipped the 2.x support work (fixtures, the version matrix, skills layout, init scope, the tool registry, update migration, validator parity, spec merge, store roots) against 2.0.0, 2.1.0 and 2.1.1 captures, and targets 2.1.1. It also moved this repository to the `.agents/skills/` layout and set the install hint's Node.js floor to 22.20.

CLI 2.2.0 was compared with 2.1.1 by running both packages from the capture cache under isolated home and XDG directories, and by diffing their dists:

- **Identical:**
  - validation verdict maps, default and strict, on both parity corpora;
  - all 41 `--help` texts;
  - every file under `dist/commands` and `dist/cli`;
  - `schemas/`, `config list`;
  - the workflow set (6 core, 12 total) and the 348 invocation rows;
  - `root-selection.js` and the 26 store-root scenarios;
  - the 87-tool registry and `engines.node >=22.20.0`.
- **New: the router skill.**
  - `dist/core/profiles.js` adds `ROUTER_WORKFLOW` outside `ALL_WORKFLOWS`, and `withRouter()` adds it to every profile with at least one workflow.
  - `dist/core/templates/workflows/router.js` renders `.agents/skills/openspec/SKILL.md` plus `references/{answers,where-things-stand,without-skills}.md`.
  - `config.js` adds `openspec` to `OPENSPEC_SKILL_NAMES`.
  - Tool folders get a link `.claude/skills/openspec → ../../.agents/skills/openspec`.
  - `update` now repairs edited reference files, which means overwriting them.
  - The `update --dry-run --json` shape is unchanged, with four extra `add` rows (tool `openspec`).
  - `init` prints "7 skills".

So this is neither the stricter Branch A nor the laxer Branch B for validation: no validator rule moves.

## Goals / Non-Goals

**Goals:**
- Target 2.2.0 with every release from 1.3.0 still supported and proven by the captured matrix.
- Recognize the router wherever the plugin recognizes OpenSpec skills.

**Non-Goals:**
- An action for `/openspec`. That is the separate workflow ↔ UI parity decision.
- Plugin changes for behavior 2.2.0 didn't change.

## Decisions

### D1. Re-capture in the repository, from the cache

Each capture script runs for 2.2.0 from `$OSP_CAPTURE_CACHE/2.2.0`, never a pinned `npx`. The research captures in scratch are a guide only. `capture-skills-scope.py` gains 2.2.0 in `RELEASES`.

Contract tests that iterate `FixtureVersions` pick 2.2.0 up automatically. A difference from 2.1.1 beyond the router is a signal to investigate, never a fixture to edit.

### D2. The router counts as a skill, not a workflow

`AiToolRegistry.SKILL_NAMES` gains `openspec`, mirroring `OPENSPEC_SKILL_NAMES`.
- Detection already works without it, because the router always arrives with the workflow skills.
- The list is documented as upstream's, so it should match.

Workflow lists stay as they are. Upstream keeps the router outside `ALL_WORKFLOWS`, and `WorkflowParityContractTest` won't see it.

### D3. Update preview: the router's link and files

`UpdateMigration`'s patterns match only `skills/openspec-<name>`.
- **Link:** `SKILL_FOLDER` widens to `skills/openspec(-<name>)?`, so the router's tool-folder link shows as a link.
- **Files:** `IN_SKILL_FOLDER` widens the same way, so router files, including `references/*.md`, are checked for user edits. Because 2.2.0 repairs edited reference files, an edited one is overwritten, and the at-risk warning must name it.

**Contract:** both are held to the 2.2.0 `update-dry-run` and `update-migration` captures.

### D4. Target version and guards

- `openspecTargetVersion=2.2.0`, guarded by `TargetVersionSingleSourceTest`.
- 2.2.0 joins `ValidatorVerdictVersionStabilityTest.FLOOR` and `CliVersionAtLeastTest`, with an anti-lexical pair (`2.2.0 > 2.1.10` style).
- **Effect without a CLI:** the built-in validator now follows 2.2.0, whose rules equal 2.1.1's, so users without a CLI see no change.
- **CHANGELOG:** says so in plain words.

### D5. This repository's own update

The installed CLI must equal the target (CLAUDE.md), so install 2.2.0 globally and run `openspec update`. Since v2.1.0 the repository is already on the `.agents/skills/` layout, so the update only adds the router: `.agents/skills/openspec/` with its `references/`, and the `.claude/skills/openspec` link. The CLAUDE.md restore recipe already covers both. The custom skills in `.claude/skills/` are untouched.

### D6. The 2.x layout journey

The existing 2.x layout uiSmoke journey moves from CLI 2.1.1 to 2.2.0 from the capture cache. It:
- initializes with project scope;
- checks that Manage AI Tools shows a tool set up through `.agents`;
- previews the update of a project seeded by 1.17.0 and checks the migration banner.

It fails rather than skips when the cache lacks 2.2.0, as the existing journey does.

## Risks / Trade-offs

- **The router changes what agents in this repository see.** Mitigation: the update lands in its own commit; the router's files are generated, not edited.
- **Screenshots go stale.** Mitigation: retake the affected ones (Manage AI Tools, the migration preview) with the screenshot tour.
- **Windows behavior of the router's nested `references/` folder under copy mode is unverified.** Mitigation: the GitHub mirror's Windows leg runs the contract tests on the 2.2.0 captures.

## Migration Plan

None for users. Projects already on 2.x see the router after their own `openspec update`, as upstream intends.

## Open Questions

- The `/openspec` action is decided in the separate parity issue.
