# Design

## Context

- **The 2.x work is already captured and tested on 2.x.** Fixtures, the version matrix, skills layout, init scope, the tool registry, update migration, validator parity, spec merge and store roots were captured from and tested on 2.0.0, 2.1.0 and 2.1.1.
- **The target stayed at 1.17.0 on purpose.** `openspecTargetVersion` stayed there so this bump lands last.
- **Version policy:** plugin X.Y supports CLI 1.3.0 up to X.Y.x. So v2.1 targets 2.1.1, and CLI 2.2 gets its own plugin minor.
- **What the target drives:**
  - **CLI-less behavior:** `ValidationRelease.followed` uses the target when no CLI is detected, and so do the CLI-less merge, detection rules and skill layout decisions.
  - **This repository:** CLAUDE.md requires the installed CLI to equal the target.
  - **Docs:** the supported range they state.

## Goals / Non-Goals

**Goals:**
- Target 2.1.1, with every release from 1.3.0 through 2.1.x still proven by the captured matrix.
- Correct the Node.js floor in the install hint.
- Keep this repository on the same layout its users get.

**Non-Goals:**
- CLI 2.2, its router skill, or its captures (plugin v2.2).
- A notice or restriction when the installed CLI is newer than the plugin's line. The plugin keeps working, and a follow-up plugin release covers the new CLI line quickly.

## Decisions

### D1. Target version

`openspecTargetVersion=2.1.1`, guarded by `TargetVersionSingleSourceTest`. 2.1.1 is already in the version guards and every release-iterating contract test, so no new captures are needed.

**Effect without a CLI:** the built-in validator, the CLI-less merge and CLI-less tool detection now follow 2.1.1 instead of 1.17.0.
- Two validation rules newly apply: MODIFIED growth past 500 characters, and delta front matter.
- New main specs gain a blank line after `## Purpose`, as 2.1.1's archive writes them.
- Tool detection uses the 2.1 rules.

**Fallout:** tests that assume the 1.17.0 target with no CLI are found by running the suite, and are fixed to state their release explicitly.

**CHANGELOG:** says so in plain words.

### D2. The install hint's Node floor

`CliInstallHints` says "Node.js 22.20 or later" for the npm route, matching `engines.node` since 2.1.0. One hint is shown regardless of the installed CLI, so it states the newest supported floor.

### D3. This repository's own update

Install 2.1.1 globally, pinned, so `openspec --version` equals the target, and run `openspec update`. In this repository it:
- moves the managed skills to `.agents/skills/` with tool-folder links;
- removes `.claude/commands/opsx/`.

**Consequences:**
- CLAUDE.md's restore recipe (`git checkout HEAD -- .claude/skills/ .claude/commands/`) is revised to cover `.agents/skills/` and the links, and proved on a scratch clone.
- The custom skills (`mirror-/advance-/close-change-trackers`, `release-*`, `lifecycle-testdrive`) are custom-named or gitignored, and the CLI leaves them alone. Confirmed after the update.
- The tests that mention `.claude/commands` read fixtures, not this repository's files, so they are unaffected. Confirmed by running them.

The move is its own commit.

### D4. The 2.x layout journey

A uiSmoke journey uses CLI 2.1.1 from the capture cache, as `installToolListOpensFromAMachineScopedProject` does. It:
- initializes with project scope;
- checks that Manage AI Tools shows a tool set up through `.agents`;
- previews the update of a project seeded by 1.17.0 and checks the migration banner.

It fails rather than skips when the cache lacks 2.1.1.

### D5. Release-gate parity audit

openspec-guru compares each 2.1.1 workflow template with the plugin action gated on it. plugin-ui-specialist checks discoverability. Gaps become tracker entries. A core workflow that no action reaches blocks the release unless the user records an exception.

## Risks / Trade-offs

- **The repository's own layout change touches the agent surfaces.** Mitigation: a separate commit; the restore recipe is proved on a scratch clone.
- **CLI-less users see new warnings.** Mitigation: they are the rules of the CLI line the plugin now targets. The CHANGELOG names them.
- **Screenshots go stale.** Mitigation: retake the affected ones (Manage AI Tools, the migration preview).

## Migration Plan

None for users.

## Open Questions

None.
