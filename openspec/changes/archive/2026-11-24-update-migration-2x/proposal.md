# Proposal

## Why

On a project set up by CLI 1.x, the first `openspec update` with CLI 2.x is a migration:
- it deletes the `/opsx` command files;
- it replaces each tool's own skill copies with one shared copy in `.agents/skills`, plus a link from each tool folder that needs one;
- it keeps command files the user edited, and says so only in its text output.

Today the update preview shows all of this as a flat list of deletes and adds, with no explanation, and nothing tells a 1.x project that it needs the update at all.

CLI releases 2.1.0 and 2.1.1 also remove the user's own text from OpenSpec's skill folders without warning, and keep no copy:
- they delete an edited skill copy in a tool folder, including one that 2.0.0 deliberately kept;
- they delete files the user put beside a skill;
- they overwrite an edited copy that they write in place of a link.

2.0.0 kept those files or warned. This was observed with the real CLIs. The dry run gives no hint of the loss, so the preview is the only place to warn the user before it happens.

This is tracked in the linked tracker entry for the 1.x → 2.x update migration.

## What Changes

- **Migration in the update preview.** When the plan moves a 1.x layout to 2.x, the preview names it as the OpenSpec 2 migration:
  - tool-folder links read as "link → `.agents/skills/<skill>`";
  - the command-file deletes form one group, flat and nested layouts alike;
  - the tool-folder copies that the shared copy replaces form one group.
- **Files OpenSpec can't confirm as its own.** When the plan deletes or overwrites a file in an OpenSpec skill folder that OpenSpec can't confirm it generated, the preview names each such file in a warning. Apply stays disabled until the user ticks "I've backed up these files".
  - The test is upstream's own: a released OpenSpec file, which the plugin already checks, or a copy still equal to the project's shared copy.
  - Files beside a skill count, except operating-system junk.
  - The warning depends on the plan alone, so it appears whenever the plan would cause a loss, migration or not. It never appears on 2.0.0 for the files that release keeps.
- **Notes that reach further.** The preview highlights three notes above the changes:
  - `global-skills`: the update also refreshes the skills in `~/.agents/skills`, shared by every project on the machine;
  - `removed-skills`: it removes machine-wide skills;
  - `edited-skills`: 2.0.0 replaces edits in a shared copy.
- **What the update kept or replaced.** After an applied update, the plugin reads the CLI's text reports and lists the paths, with a way to open each one in the project:
  - files kept as "likely edited";
  - files left because they couldn't be removed;
  - 2.0.0's "skill folders with your own changes" and "replaced your edits".
- **One-time migration notice.** With CLI 2.0 or later and a project that still holds skill copies a 1.x release generated, the plugin says once per project: "Run Update to migrate this project to OpenSpec 2". Its Update… action opens the preview.
- **2.1.0 needs Git and a network connection** to install skills (2.1.1 installs offline). When an applied update on 2.1.0 fails for that reason, the failure message says so.
- **No CLI run can wait for input.** Every CLI run the plugin starts is non-interactive (`OPEN_SPEC_INTERACTIVE=0`) with stdin closed. Today a run is non-interactive only because its stdin isn't a terminal. 2.1 adds questions to `update`, and the guarantee shouldn't rest on that side effect. The terminal handoff stays interactive and says what 2.1 may ask.

**Corrections to the tracker entry**, from the real CLIs and their source:
- The losses happen on **2.1.0 as well as 2.1.1**, and are wider than edited copies. The warning is driven by the plan, not by a version gate.
- The `tool: "openspec"` tag on per-tool copy deletes is 2.1's, for a tool that reads `.agents/skills` itself (Cursor). 2.0.0 tags them with the tool's id.
- The home-folder line comes from the CLI's own notes, so the plugin doesn't decide it from disk.
- The `.agents/skills/.openspec-target` marker can't mark a migrated project, because 1.x already writes it for tools rooted at `.agents`. The notice keys on released 1.x copies instead.

**Out of scope:**
- A notice for Node below 22.20. On such a machine CLI 2.1 doesn't start, so the plugin never detects a 2.x CLI to migrate with, and that hint is part of the CLI bump change.
- Edits to a shared copy that a 2.x CLI wrote: the plugin can't reproduce 2.x's text to compare with, and 2.1 gives no signal.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cli-update`:
  - The update preview gains the migration grouping and the highlighted notes.
  - Legacy cleanup no longer offers skill-folder files or the marker.
  - New requirements cover:
    - the at-risk warning that gates Apply;
    - what an applied update kept or replaced;
    - the one-time migration notice;
    - the 2.1.0 Git and network hint;
    - non-interactive runs.

## Impact

- **Code:**
  - `UpdatePreviewModel` and `UpdatePreviewDialog`: migration groups, link rows, the warning and the backed-up checkbox.
  - `UpdatePreviewService`:
    - reads the at-risk files off the UI thread before the dialog opens;
    - reads the kept report after Apply;
    - adds the 2.1.0 hint;
    - narrows `legacyDeletes`.
  - `UpdateOutputParser`: the kept and replaced reports.
  - `CliRunner`: the non-interactive environment and closed stdin for every run.
  - A new startup notice for 1.x projects on CLI 2.x.
  - Existing ports are reused: `ReleasedSkillFingerprints`, the registry's command paths, and the captured workflow ids.
- **Tests and fixtures:** a new capture script records the real migration on 2.0.0, 2.1.0 and 2.1.1 from projects set up by 1.17.0. For each case it records the dry run, the forced dry run, the applied run's output, and the layout before and after. The cases cover edited, beside-skill, shared-folder, nested-command, narrow-profile, already-migrated, Eve and no-Git projects. They are documented in `fixtures/cli/README.md`.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unchanged.
- **Docs:** CHANGELOG, `docs/feature-reference.md` (the update preview), and the README and marketplace getting-started page where they describe Update.
