# Design

## Context

What the real CLIs do with a 1.x project was observed on 2026-11-24, and checked again independently against the 2.0.0, 2.1.0 and 2.1.1 packages' source.

The project was set up by CLI 1.17.0 with `init --tools claude,cursor` (the default core profile: six skills). Each 2.x release ran `update --dry-run --json`, then `update`, with stdin closed. There were two variants:
- untouched;
- with one line appended to `.claude/commands/opsx/explore.md`, `.claude/skills/openspec-explore/SKILL.md` and `.cursor/skills/openspec-explore/SKILL.md`.

| | 2.0.0 | 2.1.0 | 2.1.1 |
|---|---|---|---|
| Shared copy | adds `.agents/skills/openspec-*/SKILL.md` and `.agents/skills/.openspec-target` (tool `openspec`) | same | same |
| Claude (links) | deletes each `.claude/skills/openspec-*/SKILL.md`, adds `.claude/skills/openspec-*` (the link), tool `claude` | same | same |
| Cursor (reads `.agents`) | deletes `.cursor/skills/openspec-*/SKILL.md`, tool `cursor` | same, tool **`openspec`** | same, tool `openspec` |
| Unedited command files | deleted, tool of the tool | same | same |
| Edited command file | kept, absent from the plan; the applied run prints `Kept 1 command file that does not match what OpenSpec generated (likely edited):`, the path indented by two spaces, then `Move anything worth keeping into the skill, then delete by hand.` | same | same |
| Edited tool-folder skill copy | **kept**, absent from the plan; prints `Kept 1 skill folder with your own changes: <folder>. Nothing was overwritten. …` | **deleted**, listed in the plan like any other copy; edits lost, nothing printed | same as 2.1.0 |
| Unedited copies | prints `Replaced N unchanged skill copies in tool folders with the one shared copy in .agents/skills.` | prints nothing about them | same as 2.1.0 |
| Global config | `add ~/.config/openspec/config.json` (tool `openspec`) | same | same |

The table holds when `.agents` doesn't exist yet. A 1.x project whose tools include one rooted at `.agents` (Codex, Amp, Antigravity, GSD, Zed, the shared `agents` folder) already has `.agents/skills/.openspec-target`, which 1.x wrote as a bare tool id. For those projects the plan shows `update`, not `add`, for the marker and the shared skills.

Further losses the same review observed on 2.1.0 and 2.1.1, each with nothing in the plan's notes:

- **Files beside a skill.** A `notes.md` in a tool folder's `openspec-explore/` is planned as a `delete` and removed. 2.0.0 keeps the folder.
- **Copies 2.1 writes instead of links:** Eve's `agent/skills/` (and every tool on Windows). An edited copy is overwritten, planned as an `update` of the same path.
- **An edited shared copy** in `.agents/skills/` is overwritten, planned as an `update`. 2.0.0 notes it with `edited-skills` ("Update will replace your edits in … and keep no copy") and prints `Replaced your edits in 1 skill OpenSpec installs: …`.
- **A project 2.0.0 already migrated.** 2.0.0 kept the edited copies; a later 2.1 update plans the delete of both kept copies (`.claude` and `.cursor`) and removes them. There is no marker add in that plan.

Other facts this design rests on:

- **Upstream's "is this copy edited?"** is `classifySkillFile` (`dist/core/shared/skill-links.js`). A file is untouched when it matches one of the current release's renderings, or when `isReleasedGeneratedContent` holds: a lookup of released-file fingerprints after normalizing BOM, CRLF and the `generatedBy` version. The plugin ports that lookup (`ReleasedSkillFingerprints`).
  - The set covers 1.x renderings only, so no 2.x rendering is "released".
  - Every core-profile and full-profile 1.6.0, 1.16.1 and 1.17.0 copy is released (30 of 30 for 5 tools, 48 of 48 for the full profile).
  - Copies written for a **narrow custom profile** are not released, because their text differs where it refers to other workflows. 2.0.0 itself reports those as "your own changes". This is an upstream gap.
  - Eve's copy is the shared text with `allowed-tools` dropped from the front matter (`projectSkillText`), so it never equals the shared copy byte for byte.
- **Prompts.** `isInteractive()` is the same from 1.3.0 to 2.1.1: false for `OPEN_SPEC_INTERACTIVE=0`, for any `CI` variable, and for a stdin that is not a terminal. The plugin's runs pipe stdin, so they never prompt today. On a terminal, 2.1's `update` can ask two questions:
  - "Where should OpenSpec keep this project’s skills?" (project or global), skipped when `config.yaml` has `skillsScope: project`;
  - "Set up OpenSpec skills for your coding tools on this machine?", for a project with no skills when none are installed for the machine.
- **The home folder.** 2.1's dry run reports a machine-wide refresh as the note `global-skills`: "Update will also refresh the OpenSpec skills installed for this machine in ~/.agents/skills." (captured in `2.1.x/update-dry-run/global-skills-*`). When workflows are dropped it adds `removed-skills`, which removes machine-wide skills.
- **2.1.0 without Git** exits 1 and changes nothing. Its stdout starts `✗ Could not write the shared skills in .agents/skills: Git is needed to install the OpenSpec skills, and it is not on your PATH. …`, followed by `✗ Failed: Claude Code (…), Cursor (…)`. 2.1.0's dry run without Git is clean, so the preview can't predict the failure. 2.1.1 installs offline.

## Goals / Non-Goals

**Goals:**
- The preview explains the migration.
- It warns before the update deletes or overwrites edits, wherever they are.
- After the run, the user sees what the update kept.
- A 1.x project learns, once, that it needs the update.
- No run can block on a prompt.

**Non-Goals:**
- Running the migration any other way than through the CLI's own `update`.
- Backing up edited files for the user. Upstream keeps no copy; the plugin asks the user to back up.
- Node-version reporting: a CLI 2.1 that can't start is part of the CLI bump change.

## Decisions

### 1. The plan drives every decision; the disk is read only to judge planned changes

The preview already renders the CLI's plan.

- **Migration banner:** the plan **adds or updates** `.agents/skills/.openspec-target` **and** deletes at least one tool-folder skill copy or command file (decision 4 defines both).
  - A 1.6-era Antigravity project (`update-dry-run/earlier-release`) gets the banner, correctly: its skills move from the former `.agent/` root to `.agents/`. A pre-skills project (`update-dry-run/legacy-unedited`) does not, because it deletes only 0.x `commands/openspec/*` files, which are neither skill copies nor 1.x command paths.
- **The at-risk warning (decision 2) does not depend on the banner.** It appears in any plan, including one for a project that 2.0.0 already migrated.
- **No version gate.** 2.0.0 leaves kept files out of its plan, so a plan-driven warning is correct on 2.0.0 by construction. The 2.0.0 `edited` capture is the negative control.

*Alternatives rejected:*
- Gating on "CLI ≥ 2.1.1", as the tracker proposed. 2.1.0 loses edits too.
- Keying the banner on the marker being *added*. 1.x already writes the marker for tools rooted at `.agents`.

### 2. At-risk files: what the plan deletes or overwrites that OpenSpec can't confirm as its own

A planned in-project `delete` or `update` is **at risk** when its path lies under `<folder>/skills/openspec-*/` (any `<folder>`, `.agents` included), the file on disk is not a link, and:

- **For a file other than `SKILL.md`:** always, unless it is operating-system junk (upstream's `isOsJunk`: `.DS_Store`, `Thumbs.db`, `desktop.ini`, `._*`).
- **For a `SKILL.md` whose front matter `generatedBy` is a 1.x version, or absent:** when it is not released (`ReleasedSkillFingerprints.isReleased`).
- **For a `SKILL.md` with a 2.x `generatedBy` in a tool folder:** when it differs from that skill's shared copy in `.agents/skills/` on disk, compared after upstream's normalization and with `allowed-tools` removed from both front matters (upstream's Eve transform). The comparison is with the shared copy *before* the update, so it asks "is this still the copy the last update wrote?"
- **For a 2.x shared copy in `.agents/skills/`:** not judged. The plugin can't reproduce a 2.x rendering, and 2.1 gives no signal. This is recorded in Risks.
- **For a file that can't be read:** always. Upstream calls an unreadable file the user's.

A delete of a tool folder's skill-folder link or the marker is never at risk.

The files are read on the background thread that ran the dry run, before the dialog opens. The model receives the at-risk paths as data, so it stays free of the disk and of Swing.

The warning's wording must also hold for an unedited file that OpenSpec doesn't recognize, the narrow-profile case: "OpenSpec can't confirm it generated these files. Update will delete or overwrite them and keep no copy."

### 3. The warning gates Apply; it doesn't block it

The preview lists each at-risk path, with its action, in a red block above the changes. A checkbox, "I've backed up these files", starts unticked. Apply is enabled only when the box is ticked and the plan has no failures. Cancel stays the default button.

The warning doesn't refuse the update outright: the user may want the migration once their text is safe, and the narrow-profile files may be unedited.

### 4. Migration groups, link rows and command files

When the banner applies, the model regroups the in-project changes:

- **"Shared skills (`.agents/skills`)":** adds and updates under `.agents/skills/`.
- **"Skill copies replaced by the shared copy":** every delete under a tool folder's `skills/openspec-*/`, whatever its tool tag. This absorbs 2.1's `openspec` tag on Cursor's deletes.
- **"Command files (OpenSpec 2 installs skills only)":** a delete is a command file when its path is one of the tool's 1.x command paths. That path is derived from the registry's captured explore path (`EXPLORE_COMMAND_PATHS`) by putting each workflow id from the captured workflow list in place of `explore`. That covers both the flat layouts (`.cursor/commands/opsx-apply.md`, `.github/prompts/opsx-apply.prompt.md`) and the nested ones (`.claude/commands/opsx/apply.md`, `.gemini/commands/opsx/apply.toml`).
- **Links:** a link row reads `<path> → .agents/skills/<skill>`, with a folder icon. A link is an add whose last path segment is `openspec-*` and which has no file extension. It stays in its tool's group.
- Anything else stays in its tool's group as today, and "Outside this project" stays last.

Without the banner, the grouping is exactly today's.

*Alternative rejected:* keeping groups by tool. With 20 tools that is 20 groups of identical deletes, with the edited file buried among them.

### 5. Notes the preview highlights

The preview already shows every note, whatever its code. It highlights, above the changes as it does `legacy-tool-directories`:
- `global-skills`: the refresh reaches the machine-wide skills;
- `removed-skills`: machine-wide skills are removed;
- `edited-skills`: on 2.0.0, edits in a shared copy are replaced.

All three are quoted as the CLI printed them.

### 6. `legacyDeletes` ignores skill folders and the marker

The legacy cleanup flow offers the forced plan's in-project deletes tagged `openspec`. On 2.1 that tag also covers:
- Cursor-style copy deletes and the files beside them;
- shared-skill deletes on a profile shrink;
- the marker.

Upstream's legacy cleanup never targets a `skills/` path (checked in `legacy-cleanup.js` for 1.17.0, 2.0.0 and 2.1.1). So `legacyDeletes` excludes every path under `*/skills/openspec-*/`, and `.agents/skills/.openspec-target`.

### 7. What the update kept, from its text output

`UpdateOutputParser.parseKept(stdout)` reads each report as a header line followed by indented paths. The paths run up to the first line that isn't indented; that line is the guidance, quoted as printed. The reports, from 2.x's source (`update.js`, `command-reconcile.js`, `retired-tools.js`, 2.0.0's `shared-skill-install.js`):

- `Kept <n> command file(s) that do(es) not match what OpenSpec generated (likely edited):`
- `Kept <n> prompt file(s) in your home folder that differ(s) from what OpenSpec generated (likely edited):`
- `Left <n> command file(s) in place because it/they could not be read or removed:`
- A retired tool's `  Kept <n> file(s) in <folder>/ that differ(s) … (likely edited); delete it/them when you're done:`. The header itself is indented two spaces and its paths four.
- One-line: `Left <n> OpenSpec prompt file(s) in your home folder: <a>, …` (2.1.1 `update.js`), listing prompt files the update leaves for the user.
- One-line, comma-separated (2.0.0):
  - `Kept <n> skill folder(s) with your own changes: <a>, <b>. …`;
  - `Kept <n> skill(s) in .agents/skills as your own: <a>. …`.
- The loss report, shown as a warning rather than a kept file: `Replaced your edits in <n> skill(s) OpenSpec installs: <a>. …`.

Each report becomes one entry with a kind (kept, left in place, replaced), its paths and its guidance. Every format but the home-folder prompt reports is captured (`update-migration/`). Those two print only after the legacy-cleanup flow, which a non-interactive run doesn't reach, so their tests build the text from the format strings and name the source file. Degradation is one-directional: text that doesn't match yields nothing, and the parser never invents a path.

After an applied update with any entry, the plugin shows one notification listing each path, with a link that opens a path inside the project, and the CLI's guidance. The applied run's stdout is already in hand, so no extra CLI run is needed. Because the CLI repeats a kept report on every later update while the file stays, the notification repeats too, as the CLI does.

### 8. One-time migration notice

The notice runs at project open, off the UI thread, after CLI detection. It shows when all of these hold:
- the detected CLI is 2.0.0 or later;
- the project is an OpenSpec project;
- the project holds a file a 2.x update replaces and that a 1.x release generated: a released (fingerprint-matching) `SKILL.md` that is not a link, under a tool folder's `skills/openspec-*/` other than `.agents`.

The notice reads "This project was set up by OpenSpec 1.x. Run Update to migrate it to OpenSpec 2." Its **Update…** action opens the preview (`UpdatePreviewService.previewAndApply`, origin `ACTION`). Showing the notice records it for the project in `PropertiesComponent`, so it appears once per project.

- **Why released files rather than the marker:** 1.x writes the marker too. Files 2.0.0 kept as edited are not released, so a project 2.0.0 migrated doesn't trigger the notice. Any 2.x update removes the released copies, so the notice can't reappear after a migration.
- **A project with only a shared folder** (Codex, say) has nothing to migrate but its shared copies, which a 2.x update refreshes like any update. No notice.
- **A machine-scoped 2.1 project** writes no project skill files (`2.1.1/skills-scope/machine-only`), so the notice stays silent.

### 9. The 2.1.0 Git and network hint

When an applied update on detected CLI 2.1.0 exits non-zero and its output contains `Git is needed` or `Could not fetch`, the failure report gets one more sentence: "OpenSpec 2.1.0 installs skills with Git over the network; check both, or use 2.1.1, which installs offline."

Other 2.1.0 failures (a read-only file, say) and other releases get no such sentence. The recognition is tested against the captured `no-git` run.

### 10. Every run is non-interactive

`CliRunner.applyPlainOutputEnvironment` adds `OPEN_SPEC_INTERACTIVE=0`, and `CliRunner.run` closes the process's stdin right after it starts.

- **Behaviour today:** unchanged on every release, since `isInteractive` already returns false for a piped stdin. The variable makes the guarantee explicit and independent of how the IDE wires stdin.
- **`updateWithoutPrompts`** keeps its own setting, so its pinned command line stays the same.
- **The terminal handoff** (`OpenSpecTerminalLauncher`) is a real terminal and stays interactive. On CLI 2.1+ the cleanup dialog's hint says the CLI may ask where to keep the project's skills, or whether to set skills up for the machine.

## Risks / Trade-offs

- **[Edited 2.x shared copies]** An edit to a shared copy that 2.x wrote is overwritten by 2.1 with no warning from the plugin, as from the CLI. → Out of reach without 2.x renderings; recorded. 2.0.0 announces it (`edited-skills`, highlighted).
- **[Narrow-profile 1.x files]** Unedited copies written for a narrow custom profile are reported at risk, and the user must tick the box. → The wording says OpenSpec can't confirm them, which is true; 2.0.0 itself calls them edited.
- **[A release that changes a report's wording]** The kept entry yields nothing, and the notification doesn't appear; the update itself is unaffected. → Contract tests on each captured release; the next capture shows the drift.
- **[A copy edited back to match a released file]** It is reported as unedited, upstream's own verdict.
- **[Closing stdin breaks a CLI that reads it]** No `openspec` command the plugin runs reads stdin except a prompt. → Existing CLI tests run with the closed stdin.

## Migration Plan

Additive. No setting or stored data changes. The notice's shown key is new. Rollback is reverting the change.

## Open Questions

None.
