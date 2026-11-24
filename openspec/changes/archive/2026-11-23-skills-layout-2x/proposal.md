# Proposal

## Why

On OpenSpec CLI 2.x every tool's skills live in one shared folder, `.agents/skills/`. Tools that read their own folder get directory links into it. Skills-only tools such as Cursor and GitHub Copilot get nothing of their own. The plugin still looks for a tool's skills under that tool's own folder, and it reads `.agents/skills/.openspec-target` as a single tool id. On 2.x that file is a list of tools, one per line. As a result, on 2.x today:

- Explore, Revise, Continue and the panel's handoffs resolve a Cursor or Copilot project to the wrong tool, or to none.
- Detection reports the wrong tools for most 2.x layouts. The captured contract cases record 56 detection and lookup failures as known gaps.
- Skills installed machine-wide by 2.1 (`~/.agents/skills/`) are never found.

This is the second change of the CLI 2.x support work. It is tracked in the linked tracker entry for the 2.x skills layout.

## What Changes

- **Skill lookup finds the skill where it actually is.**
  - Lookup tries the shared `.agents/skills/<skill>/SKILL.md` first, then the tool's own folder. A link in the tool's own folder is followed.
  - Machine-wide skills (`~/.agents/skills/` and a tool's links in the home directory) are found last.
  - A tool's own folder still shows the tool is set up, but no longer has to hold the skill.
- **Detection uses the installed CLI's rules for its release line.** The plugin ports upstream's 2.x `getAvailableTools` beside the 1.x port it already has. Upstream's verdicts differ between lines for identical layouts, so one merged rule can't match both:
  - a lone `.gsd` folder means `gsd` on 2.x but `agents` on 1.17;
  - 2.1 never detects a machine-wide install, while 2.0 does.
  - With no CLI detected, the plugin picks the line from what's on disk.
- **The tool list file is read as upstream reads it.**
  - Each line names one tool, optionally followed by `own-files`. Upstream's id aliases are resolved, and unknown ids are skipped.
  - A one-line file from 1.x reads as before.
  - The 2.0 `keep` lines and writing the list are a later change.
- **A tool folder is classified by its shape.** Each skill spot is one of: a link or junction into the shared folder, a copy, a "released" link (Git without link support checks a link out as a small text file holding its target), or broken. A broken or released link still means the tool is set up, as upstream decides.
- **Upstream's "released file" check, from captured data.** On 2.x, the CLI infers a folder's owner from skill wording only when the file is exactly one a release generated. The plugin ships upstream's fingerprint list as data, captured from the CLI package by a script and checked by a contract test.
- **Machine-wide skills are read off the UI thread and cached.** The home directory is read once on a background thread. The result is cached in a project service, which the plugin's own CLI runs and returning to the IDE invalidate.
- **The file system sees what the CLI wrote.** After the plugin runs `init` or `update`, the written paths, links included, are refreshed. The artifact watcher keys on the real `.agents` path. `.agents` stays part of the project.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: the AI tool detection requirement names the release line whose rules apply. New requirements cover the multi-line tool list, skill lookup across the shared folder, tool folders and the home directory, tool folder shapes, and reading the home directory off the UI thread.

## Impact

- **Production code:**
  - `services/AiToolDetector`: 2.x rules beside the 1.x port, chosen by release line.
  - new `services/SkillToolList`: parses the list file.
  - new `services/SkillLocator`: lookup and folder-shape classification, nio with `NOFOLLOW_LINKS`.
  - new `services/ReleasedSkillFingerprints` and a resource file holding upstream's fingerprint list.
  - new project service `services/GlobalSkillsCache`.
  - Callers that change: `AiToolRegistry.skillPath`/`exploreSkillPath`, `WorkflowInvocation.find`, `ExplorePromptService`, `AiToolDetectionService`, and the CLI-run completion path in `ScaffoldingService`/`ManageAiToolsDialog` (refresh).
  - `plugin.xml`: registers the cache service and an application-activation listener.
- **Tests and fixtures:**
  - a new capture script for the fingerprint list, and new contract tests;
  - the 56 `skills-layout-2x` entries in `KnownGaps` are removed. The `tool-registry-2x` and `openspec-target-2x` entries stay.
- **Compatibility:**
  - 1.x layouts and verdicts are unchanged, so 1.3.0 through 1.17.x behave as before.
  - IntelliJ 2024.2+ is unaffected: `NioFiles`/`refreshNioFiles` and the application-activation listener exist from 2024.2.
- **Out of scope:**
  - the 2.x tool registry (new and retired tools) — a later change;
  - writing the list file and 2.0 `keep` lines — a later change;
  - `init --scope` — a later change;
  - update migration — a later change.
