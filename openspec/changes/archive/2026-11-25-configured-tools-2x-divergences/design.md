# Design

## Context

- **The CLI's rule, 2.x** (`dist/core/shared/tool-detection.js`). `getConfiguredTools` counts a skills-capable tool when any of these holds:
  - a `SKILL.md` in its skills folders (its own and `legacySkillsDirs`);
  - a configured command;
  - legacy skills or commands, for adapter-backed tools;
  - `isListedToolPresent`;
  - skill links.
  
  `isListedToolPresent` needs `usesSharedSkills` (a skills folder and no home-only one) and a listing by `readSkillToolList`, which includes pre-list inference, alias resolution and, on 2.1.1, the `skills-lock.json` inference. Then:
  - without an `own-files` mark, the tool is present;
  - with the mark, it needs a command, a skill in its link folder (or, on 2.1, its former own skills folder), or skill links.
  
  On 2.1 a folder-group filter runs first: when tools share a project folder and the list names none of them, only the first counts. 2.0.0 composes the same checks, without that filter and without the former-own-folder fallback.
- **The CLI's rule, 1.8 to 1.17.** The same per-tool checks apply, with the 1.x marker: a tool counts when `readSharedSkillTarget(skillsDir)` names it. `reconcileSharedSkillTargets` then keeps, for each skills folder written by more than one counted tool, only its owner, tried in this order:
  1. the marker's tool;
  2. the tool inferred from the skills' invocation syntax, except that an inferred universal tool gives way to a Codex with legacy skills;
  3. the universal tool when the folder holds current skills;
  4. a tool with legacy skills;
  5. the universal tool.
  
  Tools with a home-only skills folder, commands, or adapter-backed legacy files bypass this filter.
- **The boundaries** (from the published packages and captured registries, 1.6.0 to 1.17.0):
  - **1.7.0:** Kimi's skills folder moved from `.kimi` to `.kimi-code`, and Windsurf became Devin, moving from `.windsurf` to `.devin`. The old folders stayed only as detection paths. Neither tool has `legacySkillsDirs`. The captures show it: `.kimi` and `.windsurf` count on 1.6.0 and don't on 1.7.0.
  - **1.8.0:** the marker check and `reconcileSharedSkillTargets` arrived in `getConfiguredTools`. 1.7.0's `getConfiguredTools` is only skills and commands.
  - The GSD tool arrived in 1.14.0; it changes the registry, not the rule.
- **Detection paths are separate from configuration.** `detectionPaths` decides whether a tool is detected. The CLI can count a tool as configured that it doesn't detect (GSD without `.gsd`).
- **The plugin today** (`AiToolDetectionService.statusOfDetected`):
  - It runs only for detected tools.
  - It searches the skills folder, the older folders and every detection path's folder for OpenSpec files.
  - It then asks `SkillLocator`.
  - It applies the 2.1 folder-group rule.
  - It has no 1.x ownership rule, so every detected `.agents` tool with current skills counts.
- **What the plugin already ports.** `Detector2x` ports the 2.x listing, the inference (`inferPreListTool`, `inferSkillsLockTool`) and `isListedToolPresent`, for detection.
- **Captures.** `fixtures/cli/{1.17.0,2.0.0,2.1.0,2.1.1}/detect/` hold the layouts and the CLI's `configured.json`. With every release checked and no known differences recorded, exactly the cases in the proposal differ.

## Goals / Non-Goals

**Goals:**
- The plugin's set-up status equals the CLI's on every captured layout, on 1.17.0, 2.0.0, 2.1.0 and 2.1.1.
- Each 1.x rule starts at the release that introduced it (1.7.0, 1.8.0); below that, verdicts don't change.

**Non-Goals:**
- Changing which tools are detected.
- Commands-only tools whose command folder lies outside their skills folder (Cline, Kilo Code, CoStrict). No capture covers them; they are left as they are.

## Decisions

### 1. Detection-path folders stop counting from CLI 1.7

`statusOfDetected` adds detection-path folders to the folders it searches only when the detected CLI is below 1.7. That includes a 1.x project with no CLI detected, whose rules follow the plugin's target release, 1.17.

### 2. On 2.x, ask the existing port of `isListedToolPresent`

Expose `Detector2x`'s `isListedToolPresent` and its `Context` (the listing with inference, aliases and `own-files` marks) package-private. Under 2.x rules, a tool counts as set up when it returns true, after the folder-group rule. This replaces the plugin's narrower checks for listed tools rather than adding a second version of them.

### 3. On 1.8 to 1.x, reuse the shared-folder ownership port

`AiToolDetector.reconcile` already ports `reconcileSharedSkillTargets` step for step, for detection. The verdict reuses it rather than adding a second port. 1.x's `getToolSkillStatus` counts a shared folder's skills only for the tool `isSharedSkillTargetActive` picks: the owner among every registry tool sharing that folder. So a skills-based verdict on a shared folder holds only for that owner. Commands still count for any tool. The marker's tool also counts when it has no skill yet.

`Detector2x.inferPreListTool` is not reused: it reads only wording a release generated byte for byte, a 2.x check that 1.x's `inferSharedSkillTarget` doesn't make.

### 4. A set-up tool shows even when it isn't detected

`getAllToolsWithStatus` adds tools the verdict counts as set up that detection didn't find, with the CONFIGURED status, as the CLI's list includes them.

## Risks / Trade-offs

- **A user who keeps using a 1.6-era `.kimi` folder with CLI 1.7 or later** sees Kimi as detected, not set up. → That is the CLI's own view; setting the tool up again writes the current layout.
- **The plugin has one tool registry, not one per release.** → Under 1.x rules it reads every tool, as detection already does. The ownership rule only matters where tools share a folder. 1.17.0's captures are the oracle for it; 1.6.0 and 1.7.0's are the oracle for the folder boundary.

- **1.x detection doesn't vary by release.** → 1.7.0 detects nothing from a list-only `.agents/skills/.openspec-target`, but the plugin's 1.x detection follows 1.17 and detects Codex. Changing detection is a non-goal here, so that one capture is a recorded known gap for a later change; the set-up verdict itself matches.

## Migration Plan

None. Rollback is reverting the change.

## Open Questions

None.
